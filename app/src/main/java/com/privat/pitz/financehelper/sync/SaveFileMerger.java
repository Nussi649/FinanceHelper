package com.privat.pitz.financehelper.sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.privat.pitz.financehelper.core.Const;
import com.privat.pitz.financehelper.core.Util;
import com.privat.pitz.financehelper.data.TxBE;

/**
 * Three-way merge of two versions of one save file ("local" and "remote") against the version
 * both were last known to agree on ("base"). Pure JSON in, JSON out - no Android, no I/O - so the
 * rules can be tested exhaustively on the JVM.
 *
 * <p>The rules, in the order they matter:
 * <ul>
 *   <li><b>Transactions are matched by id</b> ({@code "i"}), wherever in the file they sit, so a
 *       transaction moved to another account stays the same transaction.</li>
 *   <li><b>Only one side changed it: that side's version wins.</b> This is the common case and
 *       needs no timestamp.</li>
 *   <li><b>Both sides changed it differently: newer {@code "m"} wins</b>, a tie goes to remote.
 *       A side that changed the content but did not advance {@code "m"} (an external writer that
 *       does not know the field) is dated by its file's modification time instead.</li>
 *   <li><b>Deleted on one side, edited on the other: the edit survives.</b> Silently dropping
 *       an edit is worse than keeping something that was meant to go.</li>
 *   <li><b>Deleted on one side, untouched on the other: it is deleted.</b></li>
 * </ul>
 * Accounts are matched by name, their scalar properties merge per key under the same
 * only-one-side-changed rule (a true conflict goes to remote), and recurring orders - which have
 * no identity - merge as a set of whole values, so an edit is a remove plus an add.
 *
 * <p>Whatever the merge throws away is reported as a {@link Loss}, naming the side whose file
 * still holds it, so the caller can keep a copy of that file before overwriting it.
 */
public final class SaveFileMerger {

    public enum Side { LOCAL, REMOTE }

    /** Data that one side still holds but the merged result does not. */
    public static final class Loss {
        public final Side heldBy;
        public final String what;

        Loss(Side heldBy, String what) {
            this.heldBy = heldBy;
            this.what = what;
        }

        @Override
        public String toString() {
            return heldBy + ": " + what;
        }
    }

    public static final class Result {
        public final JSONObject merged;
        public final List<Loss> losses;
        /** Conflicts and resurrections, human readable, for the sync log. */
        public final List<String> notes;

        Result(JSONObject merged, List<Loss> losses, List<String> notes) {
            this.merged = merged;
            this.losses = losses;
            this.notes = notes;
        }
    }

    private static final String SUB = Const.JSON_TAG_SUB_BUDGETS;
    private static final String TX = Const.JSON_TAG_TRANSACTIONS;
    private static final String ASSETS = Const.JSON_TAG_ASSET_ACCOUNTS;
    private static final String BUDGETS = Const.JSON_TAG_BUDGET_ACCOUNTS;
    private static final String RECUR = Const.JSON_TAG_RECURRING_TX;
    private static final String INCOME = Const.JSON_TAG_CURRENT_INCOME;
    private static final Set<String> ROOT_SPECIAL = new HashSet<>(Arrays.asList(ASSETS, BUDGETS, RECUR, INCOME));
    private static final Set<String> ACCOUNT_SPECIAL = new HashSet<>(Arrays.asList(TX, SUB));
    private static final List<String> LOC_INCOME = Collections.singletonList(INCOME);

    private SaveFileMerger() {
    }

    private static final class Ctx {
        final List<Loss> losses = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        final Date localTime;
        final Date remoteTime;

        Ctx(Date localTime, Date remoteTime) {
            this.localTime = localTime != null ? localTime : new Date();
            this.remoteTime = remoteTime != null ? remoteTime : new Date();
        }
    }

    private static final class TxRec {
        final String id;
        final JSONObject json;
        final List<String> loc;

        TxRec(String id, JSONObject json, List<String> loc) {
            this.id = id;
            this.json = json;
            this.loc = loc;
        }
    }

    // region public entry points

    /**
     * @param base       the last version both sides agreed on, or null if they never synced
     * @param localTime  when the local file last changed (dates content changes whose "m" was not advanced)
     * @param remoteTime the same for the remote file
     */
    public static Result merge(JSONObject base, JSONObject local, JSONObject remote,
                               Date localTime, Date remoteTime) throws JSONException {
        JSONObject b = base == null ? null : copy(base);
        JSONObject l = copy(local);
        JSONObject r = copy(remote);
        ensureIds(l);
        ensureIds(r);
        if (b == null) {
            adoptIdsByContent(l, r);
        }
        Ctx ctx = new Ctx(localTime, remoteTime);

        Map<String, TxRec> bt = b == null ? new LinkedHashMap<>() : collect(b);
        Map<String, TxRec> lt = collect(l);
        Map<String, TxRec> rt = collect(r);
        Map<String, TxRec> mergedTx = mergeTx(bt, lt, rt, ctx);

        JSONObject out = new JSONObject();
        mergeProps(b, l, r, out, ROOT_SPECIAL, ctx, "Datei");
        for (String key : new String[]{ASSETS, BUDGETS}) {
            if (has(b, key) || has(l, key) || has(r, key)) {
                out.put(key, mergeAccounts(arr(b, key), arr(l, key), arr(r, key),
                        Collections.singletonList(key), ctx));
            }
        }
        if (has(b, RECUR) || has(l, RECUR) || has(r, RECUR)) {
            out.put(RECUR, mergeValueSet(arr(b, RECUR), arr(l, RECUR), arr(r, RECUR), ctx, "Dauerauftrag"));
        }
        placeTx(out, mergedTx, bt, lt, rt, b, l, r);
        return new Result(out, ctx.losses, ctx.notes);
    }

    /**
     * Gives every transaction without an id one (and repeats get fresh ones). Run on a file that
     * is about to become the base: ids assigned at random must be written down exactly once, or
     * the next merge would see the same legacy entries as new ones.
     */
    public static JSONObject withIds(JSONObject file) throws JSONException {
        JSONObject c = copy(file);
        ensureIds(c);
        return c;
    }

    /** Canonical, key-order independent text of a document, for equality checks. */
    public static String canonical(JSONObject file) {
        return canon(file);
    }

    // endregion

    // region transactions

    private interface TxVisitor {
        void visit(JSONObject tx, List<String> loc) throws JSONException;
    }

    private static void walk(JSONObject file, TxVisitor v) throws JSONException {
        walkTxArray(arr(file, INCOME), LOC_INCOME, v);
        walkAccounts(arr(file, ASSETS), Collections.singletonList(ASSETS), v);
        walkAccounts(arr(file, BUDGETS), Collections.singletonList(BUDGETS), v);
    }

    private static void walkAccounts(JSONArray accounts, List<String> parentLoc, TxVisitor v) throws JSONException {
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject account = accounts.optJSONObject(i);
            if (account == null) continue;
            List<String> loc = child(parentLoc, account.optString(Const.JSON_TAG_NAME, ""));
            walkTxArray(arr(account, TX), loc, v);
            walkAccounts(arr(account, SUB), loc, v);
        }
    }

    private static void walkTxArray(JSONArray txs, List<String> loc, TxVisitor v) throws JSONException {
        for (int i = 0; i < txs.length(); i++) {
            JSONObject tx = txs.optJSONObject(i);
            if (tx != null) v.visit(tx, loc);
        }
    }

    private static void ensureIds(JSONObject file) throws JSONException {
        final Set<String> seen = new HashSet<>();
        walk(file, (tx, loc) -> {
            String id = tx.optString(Const.JSON_TAG_ID, "").trim();
            if (id.isEmpty() || seen.contains(id)) {
                id = TxBE.newId();
            }
            seen.add(id);
            tx.put(Const.JSON_TAG_ID, id);
        });
    }

    private static Map<String, TxRec> collect(JSONObject file) throws JSONException {
        final Map<String, TxRec> out = new LinkedHashMap<>();
        walk(file, (tx, loc) -> {
            String id = tx.getString(Const.JSON_TAG_ID);
            out.put(id, new TxRec(id, tx, loc));
        });
        return out;
    }

    /**
     * First sync of a file both sides already have: neither has seen the other's ids, so the same
     * booking would appear twice. Entries with identical content in the same account are taken to
     * be the same transaction, and remote adopts local's id.
     */
    private static void adoptIdsByContent(JSONObject l, JSONObject r) throws JSONException {
        Map<String, TxRec> lt = collect(l);
        Map<String, TxRec> rt = collect(r);
        Map<String, Deque<TxRec>> localOnly = new HashMap<>();
        for (TxRec rec : lt.values()) {
            if (!rt.containsKey(rec.id)) {
                localOnly.computeIfAbsent(contentKey(rec), k -> new ArrayDeque<>()).add(rec);
            }
        }
        for (TxRec rec : rt.values()) {
            if (lt.containsKey(rec.id)) continue;
            Deque<TxRec> queue = localOnly.get(contentKey(rec));
            if (queue != null && !queue.isEmpty()) {
                rec.json.put(Const.JSON_TAG_ID, queue.poll().id);
            }
        }
    }

    private static Map<String, TxRec> mergeTx(Map<String, TxRec> bt, Map<String, TxRec> lt,
                                              Map<String, TxRec> rt, Ctx ctx) throws JSONException {
        Set<String> ids = new LinkedHashSet<>(lt.keySet());
        ids.addAll(rt.keySet());
        Map<String, TxRec> out = new LinkedHashMap<>();
        for (String id : ids) {
            TxRec winner = decide(bt.get(id), lt.get(id), rt.get(id), ctx);
            if (winner != null) out.put(id, winner);
        }
        return out;
    }

    private static TxRec decide(TxRec b, TxRec l, TxRec r, Ctx ctx) throws JSONException {
        if (b == null) {
            if (l == null) return stamp(r, effectiveAdded(r, ctx.remoteTime));
            if (r == null) return stamp(l, effectiveAdded(l, ctx.localTime));
            return pickNewer(l, r, effectiveAdded(l, ctx.localTime), effectiveAdded(r, ctx.remoteTime), ctx);
        }
        if (l == null) {
            if (changed(r, b)) {
                ctx.notes.add("Remote geändert, lokal gelöscht - bleibt erhalten: " + describe(r));
                return stamp(r, effectiveChanged(r, b, ctx.remoteTime));
            }
            ctx.losses.add(new Loss(Side.REMOTE, "lokal gelöscht: " + describe(r)));
            return null;
        }
        if (r == null) {
            if (changed(l, b)) {
                ctx.notes.add("Lokal geändert, remote gelöscht - bleibt erhalten: " + describe(l));
                return stamp(l, effectiveChanged(l, b, ctx.localTime));
            }
            ctx.losses.add(new Loss(Side.LOCAL, "remote gelöscht: " + describe(l)));
            return null;
        }
        boolean lc = changed(l, b);
        boolean rc = changed(r, b);
        if (!lc && !rc) {
            Date lm = mOf(l);
            Date rm = mOf(r);
            return stamp(l, lm.after(rm) ? lm : rm);
        }
        if (lc && !rc) return stamp(l, effectiveChanged(l, b, ctx.localTime));
        if (!lc) return stamp(r, effectiveChanged(r, b, ctx.remoteTime));
        return pickNewer(l, r, effectiveChanged(l, b, ctx.localTime), effectiveChanged(r, b, ctx.remoteTime), ctx);
    }

    private static TxRec pickNewer(TxRec l, TxRec r, Date lm, Date rm, Ctx ctx) throws JSONException {
        if (contentKey(l).equals(contentKey(r))) {
            return stamp(l, lm.after(rm) ? lm : rm);
        }
        boolean localWins = lm.after(rm);
        TxRec loser = localWins ? r : l;
        ctx.losses.add(new Loss(localWins ? Side.REMOTE : Side.LOCAL, "Konflikt, unterlegene Fassung: " + describe(loser)));
        ctx.notes.add("Konflikt, " + (localWins ? "lokal" : "remote") + " ist neuer: " + describe(localWins ? l : r));
        return localWins ? stamp(l, lm) : stamp(r, rm);
    }

    private static TxRec stamp(TxRec rec, Date m) throws JSONException {
        rec.json.put(Const.JSON_TAG_MODIFIED, Util.formatModified(m));
        return rec;
    }

    private static Date mOf(TxRec rec) {
        return Util.parseModified(rec.json.optString(Const.JSON_TAG_MODIFIED, null));
    }

    private static Date effectiveAdded(TxRec x, Date fileTime) {
        Date m = mOf(x);
        return m.getTime() > 0 ? m : fileTime;
    }

    private static Date effectiveChanged(TxRec x, TxRec b, Date fileTime) {
        Date m = mOf(x);
        return m.after(mOf(b)) ? m : fileTime;
    }

    private static boolean changed(TxRec x, TxRec b) {
        return !contentKey(x).equals(contentKey(b));
    }

    /** Where a transaction is and what it says - everything except its id and modification stamp. */
    private static String contentKey(TxRec rec) {
        JSONObject c = copyQuiet(rec.json);
        c.remove(Const.JSON_TAG_ID);
        c.remove(Const.JSON_TAG_MODIFIED);
        return String.join("/", rec.loc) + "|" + canon(c);
    }

    private static String describe(TxRec rec) {
        return rec.json.optString(Const.JSON_TAG_DESCRIPTION, "") + " "
                + rec.json.optString(Const.JSON_TAG_AMOUNT, "?") + " ("
                + rec.json.optString(Const.JSON_TAG_TIME, "?") + ", "
                + String.join("/", rec.loc.subList(1, rec.loc.size())) + ")";
    }

    // endregion

    // region structure

    private static JSONArray mergeAccounts(JSONArray b, JSONArray l, JSONArray r, List<String> parentLoc,
                                           Ctx ctx) throws JSONException {
        Map<String, JSONObject> bm = byName(b);
        Map<String, JSONObject> lm = byName(l);
        Map<String, JSONObject> rm = byName(r);
        Set<String> names = new LinkedHashSet<>(lm.keySet());
        names.addAll(rm.keySet());

        JSONArray out = new JSONArray();
        for (String name : names) {
            JSONObject merged = mergeAccount(name, bm.get(name), lm.get(name), rm.get(name),
                    child(parentLoc, name), ctx);
            if (merged != null) out.put(merged);
        }
        return out;
    }

    private static JSONObject mergeAccount(String name, JSONObject b, JSONObject l, JSONObject r,
                                           List<String> loc, Ctx ctx) throws JSONException {
        if (b != null && (l == null || r == null)) {
            JSONObject other = l == null ? r : l;
            if (canon(other).equals(canon(b))) {
                ctx.losses.add(new Loss(l == null ? Side.REMOTE : Side.LOCAL, "Konto gelöscht: " + name));
                return null;
            }
            ctx.notes.add("Konto \"" + name + "\" auf einer Seite gelöscht, auf der anderen geändert - bleibt erhalten");
            // the side that deleted it counts as unchanged, so the other side's changes win
            if (l == null) l = b; else r = b;
        }
        JSONObject out = new JSONObject();
        mergeProps(b, l, r, out, ACCOUNT_SPECIAL, ctx, "Konto " + name);
        out.put(TX, new JSONArray());
        if (has(b, SUB) || has(l, SUB) || has(r, SUB)) {
            out.put(SUB, mergeAccounts(arr(b, SUB), arr(l, SUB), arr(r, SUB), loc, ctx));
        }
        return out;
    }

    private static Map<String, JSONObject> byName(JSONArray accounts) {
        Map<String, JSONObject> out = new LinkedHashMap<>();
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject a = accounts.optJSONObject(i);
            if (a != null) out.put(a.optString(Const.JSON_TAG_NAME, ""), a);
        }
        return out;
    }

    /** Per-key three-way merge of the scalar members of an object. */
    private static void mergeProps(JSONObject b, JSONObject l, JSONObject r, JSONObject out,
                                   Set<String> skip, Ctx ctx, String label) throws JSONException {
        Set<String> keys = new LinkedHashSet<>();
        addKeys(keys, l);
        addKeys(keys, r);
        addKeys(keys, b);
        keys.removeAll(skip);
        for (String key : keys) {
            String bv = canonOrNull(b, key);
            String lv = canonOrNull(l, key);
            String rv = canonOrNull(r, key);
            JSONObject source;
            if (eq(lv, bv)) {
                source = r;
            } else if (eq(rv, bv) || eq(lv, rv)) {
                source = l;
            } else {
                source = r;
                ctx.losses.add(new Loss(Side.LOCAL, label + ": \"" + key + "\" lokal " + lv));
                ctx.notes.add(label + ": \"" + key + "\" an beiden Seiten verschieden geändert, remote gewinnt");
            }
            Object value = source == null ? null : source.opt(key);
            if (value != null) out.put(key, value);
        }
    }

    /**
     * Merges a list of whole values without identity as a multiset: a value both sides still have
     * stays, one side removing it removes it, one side adding it adds it.
     */
    private static JSONArray mergeValueSet(JSONArray b, JSONArray l, JSONArray r, Ctx ctx, String label) {
        Map<String, Object> bs = occurrences(b);
        Map<String, Object> ls = occurrences(l);
        Map<String, Object> rs = occurrences(r);
        Set<String> keys = new LinkedHashSet<>(ls.keySet());
        keys.addAll(rs.keySet());
        JSONArray out = new JSONArray();
        for (String key : keys) {
            boolean inL = ls.containsKey(key);
            boolean inR = rs.containsKey(key);
            if (bs.containsKey(key) && !(inL && inR)) {
                ctx.losses.add(new Loss(inL ? Side.LOCAL : Side.REMOTE, label + " auf der anderen Seite entfernt: " + key));
                continue;
            }
            out.put(inL ? ls.get(key) : rs.get(key));
        }
        return out;
    }

    private static Map<String, Object> occurrences(JSONArray values) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Integer> count = new HashMap<>();
        for (int i = 0; i < values.length(); i++) {
            Object v = values.opt(i);
            String c = canon(v);
            int n = count.merge(c, 1, Integer::sum);
            out.put(c + "#" + n, v);
        }
        return out;
    }

    // endregion

    // region placing merged transactions

    private static void placeTx(JSONObject out, Map<String, TxRec> mergedTx, Map<String, TxRec> bt,
                                Map<String, TxRec> lt, Map<String, TxRec> rt,
                                JSONObject b, JSONObject l, JSONObject r) throws JSONException {
        Map<String, List<TxRec>> groups = new LinkedHashMap<>();
        for (TxRec rec : mergedTx.values()) {
            groups.computeIfAbsent(String.join("/", rec.loc), k -> new ArrayList<>()).add(rec);
        }
        for (List<TxRec> group : groups.values()) {
            List<String> loc = group.get(0).loc;
            JSONArray target;
            if (loc.equals(LOC_INCOME)) {
                target = new JSONArray();
                out.put(INCOME, target);
            } else {
                JSONObject account = ensureAccount(out, loc, b, l, r);
                target = account.getJSONArray(TX);
            }
            for (TxRec rec : ordered(group, loc, bt, lt, rt)) {
                target.put(rec.json);
            }
        }
        if (!out.has(INCOME) && (has(b, INCOME) || has(l, INCOME) || has(r, INCOME))) {
            out.put(INCOME, new JSONArray());
        }
    }

    /**
     * Keeps the file order of whichever side the account's transactions changed on, so a sync that
     * changes nothing leaves the file alone instead of reshuffling it.
     */
    private static List<TxRec> ordered(List<TxRec> group, List<String> loc, Map<String, TxRec> bt,
                                       Map<String, TxRec> lt, Map<String, TxRec> rt) {
        Map<String, TxRec> byId = new LinkedHashMap<>();
        for (TxRec rec : group) byId.put(rec.id, rec);

        List<TxRec> localOrder = at(lt, loc);
        List<TxRec> remoteOrder = at(rt, loc);
        boolean localChanged = !signature(localOrder).equals(signature(at(bt, loc)));

        List<List<TxRec>> sources = new ArrayList<>();
        sources.add(localChanged ? localOrder : remoteOrder);
        sources.add(localChanged ? remoteOrder : localOrder);

        List<TxRec> out = new ArrayList<>();
        for (List<TxRec> source : sources) {
            for (TxRec rec : source) {
                TxRec winner = byId.remove(rec.id);
                if (winner != null) out.add(winner);
            }
        }
        out.addAll(byId.values());
        return out;
    }

    private static List<TxRec> at(Map<String, TxRec> all, List<String> loc) {
        List<TxRec> out = new ArrayList<>();
        for (TxRec rec : all.values()) {
            if (rec.loc.equals(loc)) out.add(rec);
        }
        return out;
    }

    private static String signature(List<TxRec> recs) {
        List<String> parts = new ArrayList<>();
        for (TxRec rec : recs) parts.add(rec.id + "=" + contentKey(rec));
        Collections.sort(parts);
        return String.join("\n", parts);
    }

    /** The account at {@code loc} in the merged tree, created from whichever input has it if absent. */
    private static JSONObject ensureAccount(JSONObject out, List<String> loc, JSONObject... sources) throws JSONException {
        JSONObject existing = findAccount(out, loc);
        if (existing != null) return existing;

        JSONArray siblings;
        if (loc.size() == 2) {
            if (!out.has(loc.get(0))) out.put(loc.get(0), new JSONArray());
            siblings = out.getJSONArray(loc.get(0));
        } else {
            JSONObject parent = ensureAccount(out, loc.subList(0, loc.size() - 1), sources);
            if (!parent.has(SUB)) parent.put(SUB, new JSONArray());
            siblings = parent.getJSONArray(SUB);
        }
        JSONObject stub = null;
        for (JSONObject source : sources) {
            JSONObject found = source == null ? null : findAccount(source, loc);
            if (found != null) {
                stub = copy(found);
                break;
            }
        }
        if (stub == null) {
            stub = new JSONObject();
            stub.put(Const.JSON_TAG_NAME, loc.get(loc.size() - 1));
        }
        stub.put(TX, new JSONArray());
        stub.remove(SUB);
        siblings.put(stub);
        return stub;
    }

    private static JSONObject findAccount(JSONObject file, List<String> loc) {
        JSONArray level = arr(file, loc.get(0));
        JSONObject account = null;
        for (int depth = 1; depth < loc.size(); depth++) {
            account = null;
            for (int i = 0; i < level.length(); i++) {
                JSONObject candidate = level.optJSONObject(i);
                if (candidate != null && loc.get(depth).equals(candidate.optString(Const.JSON_TAG_NAME, ""))) {
                    account = candidate;
                    break;
                }
            }
            if (account == null) return null;
            level = arr(account, SUB);
        }
        return account;
    }

    // endregion

    // region small helpers

    private static boolean has(JSONObject o, String key) {
        return o != null && o.has(key);
    }

    private static JSONArray arr(JSONObject o, String key) {
        JSONArray a = o == null ? null : o.optJSONArray(key);
        return a != null ? a : new JSONArray();
    }

    private static List<String> child(List<String> parent, String name) {
        List<String> out = new ArrayList<>(parent);
        out.add(name);
        return out;
    }

    private static void addKeys(Set<String> into, JSONObject o) {
        if (o == null) return;
        Iterator<String> it = o.keys();
        while (it.hasNext()) into.add(it.next());
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String canonOrNull(JSONObject o, String key) {
        if (o == null || !o.has(key)) return null;
        return canon(o.opt(key));
    }

    private static JSONObject copy(JSONObject o) throws JSONException {
        return new JSONObject(o.toString());
    }

    private static JSONObject copyQuiet(JSONObject o) {
        try {
            return copy(o);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Key-order independent text of any JSON value. */
    static String canon(Object o) {
        if (o == null || o == JSONObject.NULL) return "null";
        if (o instanceof JSONObject) {
            JSONObject obj = (JSONObject) o;
            Map<String, String> sorted = new TreeMap<>();
            Iterator<String> it = obj.keys();
            while (it.hasNext()) {
                String k = it.next();
                sorted.put(k, canon(obj.opt(k)));
            }
            StringBuilder sb = new StringBuilder("{");
            for (Map.Entry<String, String> e : sorted.entrySet()) {
                if (sb.length() > 1) sb.append(',');
                sb.append(JSONObject.quote(e.getKey())).append(':').append(e.getValue());
            }
            return sb.append('}').toString();
        }
        if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < a.length(); i++) {
                if (i > 0) sb.append(',');
                sb.append(canon(a.opt(i)));
            }
            return sb.append(']').toString();
        }
        if (o instanceof String) return JSONObject.quote((String) o);
        return String.valueOf(o);
    }

    // endregion
}
