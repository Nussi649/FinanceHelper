package com.privat.pitz.financehelper.core;

import android.annotation.SuppressLint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;
import com.privat.pitz.financehelper.data.TxBE;

/**
 * Read-only sanity checker for save files, meant to catch corruption introduced by anything
 * editing save files outside the app itself (e.g. an out-of-band script/agent syncing
 * transactions in via the folder-sync feature). Never modifies anything it checks; parses save
 * files into scratch objects instead of touching the live {@link Model}, so it can run against
 * any file(s) regardless of which one is currently loaded in the app.
 *
 * The core signal is total-sum conservation, not per-transaction pairing: every normal
 * transaction (Controller.createTx / triggerRecurringTx) is a paired debit/credit of equal
 * magnitude on two different accounts, so summing *all* asset + budget account sums together
 * should stay perfectly constant across edits - the only things that legitimately move that
 * total are (a) income (Controller.addFunds - a single-sided entry, no offsetting debit) and
 * (b) a budget account renewal/reset (BudgetAccountBE.tryRenew()/reset() clears its tx list
 * without carrying the spent-this-period amount forward anywhere). An earlier version of this
 * checker tried to verify pairing per-transaction (matching date+description+opposite-amount
 * across accounts); that turned out to be too noisy/unreliable on real data and was dropped in
 * favor of this before/after total comparison.
 */
public class IntegrityChecker {
    private static final float AMOUNT_EPSILON = 0.005f;

    public static class Finding {
        public final String message;
        public Finding(String message) { this.message = message; }
    }

    public static class Result {
        public final String fileName;
        public final List<Finding> findings = new ArrayList<>();
        public boolean parsedOk = true;
        public Result(String fileName) { this.fileName = fileName; }
        public boolean isClean() { return parsedOk && findings.isEmpty(); }
    }

    private static class ParsedFile {
        final String fileName;
        final List<AccountBE> assetAccounts;
        final List<BudgetAccountBE> budgetAccounts;
        final List<TxBE> income;
        ParsedFile(String fileName, List<AccountBE> assetAccounts, List<BudgetAccountBE> budgetAccounts, List<TxBE> income) {
            this.fileName = fileName;
            this.assetAccounts = assetAccounts;
            this.budgetAccounts = budgetAccounts;
            this.income = income;
        }
    }

    private final Controller controller;

    public IntegrityChecker(Controller controller) {
        this.controller = controller;
    }

    // Structural checks on a single file only - no before/after comparison, so total-sum
    // conservation can't be checked here (there's nothing to compare against).
    public Result check(String fileName) {
        String data;
        try {
            data = controller.readFromInternal(fileName);
        } catch (IOException e) {
            Result result = new Result(fileName);
            result.parsedOk = false;
            result.findings.add(new Finding("Datei konnte nicht gelesen werden: " + e.getMessage()));
            return result;
        }
        return checkParsed(fileName, data);
    }

    // The real primary check: compares a "before" and an "after" file (e.g. a save saved before
    // an out-of-band edit, and the current state after it) and verifies the total sum only moved
    // by an explainable amount, plus runs the structural checks on the "after" file.
    public Result compare(String beforeFileName, String afterFileName) {
        String beforeData, afterData;
        try {
            beforeData = controller.readFromInternal(beforeFileName);
        } catch (IOException e) {
            Result result = new Result(afterFileName);
            result.parsedOk = false;
            result.findings.add(new Finding("\"Vorher\"-Datei konnte nicht gelesen werden: " + e.getMessage()));
            return result;
        }
        try {
            afterData = controller.readFromInternal(afterFileName);
        } catch (IOException e) {
            Result result = new Result(afterFileName);
            result.parsedOk = false;
            result.findings.add(new Finding("\"Nachher\"-Datei konnte nicht gelesen werden: " + e.getMessage()));
            return result;
        }
        return compareParsed(beforeFileName, beforeData, afterFileName, afterData);
    }

    Result checkParsed(String fileName, String data) {
        Result result = new Result(fileName);
        ParsedFile parsed = parse(fileName, data, result);
        if (parsed == null)
            return result;
        runStructuralChecks(parsed, result);
        return result;
    }

    Result compareParsed(String beforeFileName, String beforeData, String afterFileName, String afterData) {
        Result result = new Result(afterFileName);
        Result beforeResult = new Result(beforeFileName);
        ParsedFile before = parse(beforeFileName, beforeData, beforeResult);
        if (before == null) {
            result.parsedOk = false;
            result.findings.addAll(beforeResult.findings);
            return result;
        }
        ParsedFile after = parse(afterFileName, afterData, result);
        if (after == null)
            return result;

        runStructuralChecks(after, result);
        checkTotalSumConservation(before, after, result);
        return result;
    }

    private ParsedFile parse(String fileName, String data, Result result) {
        try {
            JSONObject json = new JSONObject(data);
            List<AccountBE> assetAccounts = new ArrayList<>();
            List<BudgetAccountBE> budgetAccounts = new ArrayList<>();

            // The parser reports each field it defaults and each record it discards. Folding
            // those in says *which* entry was lost and why, where the count comparison below can
            // only say that one was - the two are complementary, so both run.
            ParseReport report = new ParseReport();

            JSONArray assetJson = json.getJSONArray(Const.JSON_TAG_ASSET_ACCOUNTS);
            for (int i = 0; i < assetJson.length(); i++) {
                AccountBE acc = Util.parseJSON_Account(assetJson.getJSONObject(i), report);
                if (acc != null)
                    assetAccounts.add(acc);
            }
            JSONArray budgetJson = json.getJSONArray(Const.JSON_TAG_BUDGET_ACCOUNTS);
            for (int i = 0; i < budgetJson.length(); i++) {
                BudgetAccountBE acc = Util.parseJSON_BudgetAccount(budgetJson.getJSONObject(i), report);
                if (acc != null)
                    budgetAccounts.add(acc);
            }
            List<TxBE> income = Util.parseJSON_IncomeList(json.getJSONArray(Const.JSON_TAG_CURRENT_INCOME), report);

            ParsedFile parsed = new ParsedFile(fileName, assetAccounts, budgetAccounts, income);
            for (ParseReport.Note note : report.getNotes())
                result.findings.add(new Finding(note.message));
            checkForSilentlyDroppedEntries(json, parsed, result);
            return parsed;
        } catch (JSONException e) {
            result.parsedOk = false;
            result.findings.add(new Finding("Datei ist kein gültiges JSON oder hat ein unerwartetes Format: " + e.getMessage()));
            return null;
        }
    }

    private void runStructuralChecks(ParsedFile parsed, Result result) {
        checkDuplicateNames(parsed, result);
        checkNumericSanity(parsed, result);
        checkIncomeCrossReference(parsed, result);
    }

    // Util.parseJSON_Account/parseJSON_BudgetAccount silently return null (and the entry gets
    // silently skipped) for a malformed entry instead of throwing - e.g. a non-project budget
    // account JSON object missing "renew_next" fails to parse and vanishes with no error shown
    // anywhere. This compares raw JSON entry counts against successfully-parsed counts to catch
    // exactly that class of silent data loss.
    @SuppressLint("DefaultLocale")
    private void checkForSilentlyDroppedEntries(JSONObject json, ParsedFile parsed, Result result) {
        try {
            int rawCount = json.getJSONArray(Const.JSON_TAG_ASSET_ACCOUNTS).length();
            if (rawCount != parsed.assetAccounts.size())
                result.findings.add(new Finding(String.format(
                        "%d von %d Bestandskonten-Einträgen konnten beim Parsen nicht verarbeitet werden und wurden stillschweigend verworfen (fehlendes/ungültiges Pflichtfeld?).",
                        rawCount - parsed.assetAccounts.size(), rawCount)));
        } catch (JSONException ignored) { }

        try {
            int rawCount = countBudgetEntriesRaw(json.getJSONArray(Const.JSON_TAG_BUDGET_ACCOUNTS));
            int parsedCount = countBudgetEntriesParsed(parsed.budgetAccounts);
            if (rawCount != parsedCount)
                result.findings.add(new Finding(String.format(
                        "%d von %d Budgetkonten-Einträgen (inkl. Unterbudgets) konnten beim Parsen nicht verarbeitet werden und wurden stillschweigend verworfen (fehlendes/ungültiges Pflichtfeld, z.B. \"renew_next\"?).",
                        rawCount - parsedCount, rawCount)));
        } catch (JSONException ignored) { }
    }

    private int countBudgetEntriesRaw(JSONArray budgetJson) throws JSONException {
        int count = budgetJson.length();
        for (int i = 0; i < budgetJson.length(); i++) {
            JSONObject entry = budgetJson.getJSONObject(i);
            if (entry.has(Const.JSON_TAG_SUB_BUDGETS))
                count += countBudgetEntriesRaw(entry.getJSONArray(Const.JSON_TAG_SUB_BUDGETS));
        }
        return count;
    }

    private int countBudgetEntriesParsed(List<BudgetAccountBE> budgetAccounts) {
        int count = 0;
        for (BudgetAccountBE acc : budgetAccounts)
            count += 1 + acc.getAllSubBudgets().size();
        return count;
    }

    // Duplicate account names break the by-name lookups the rest of the app relies on
    // (Model.getAccountByName et al. silently return the first match), so a duplicate introduced
    // by an out-of-band edit can cause transactions to land on the wrong account without any error.
    private void checkDuplicateNames(ParsedFile parsed, Result result) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (AccountBE acc : parsed.assetAccounts)
            if (!seen.add(acc.getName()))
                duplicates.add(acc.getName());
        for (BudgetAccountBE acc : flattenBudgets(parsed.budgetAccounts).values())
            if (!seen.add(acc.getName()))
                duplicates.add(acc.getName());
        for (String name : duplicates)
            result.findings.add(new Finding(String.format(
                    "Der Kontoname \"%s\" kommt mehrfach vor. Konto-Lookups im Rest der App liefern dann immer nur den ersten Treffer.", name)));
    }

    private void checkNumericSanity(ParsedFile parsed, Result result) {
        for (BudgetAccountBE acc : flattenBudgets(parsed.budgetAccounts).values()) {
            if (isBad(acc.indivYearlyBudget))
                result.findings.add(new Finding(String.format(
                        "Konto \"%s\": Jahresbudget ist NaN/Infinity.", acc.getName())));
            if (isBad(acc.indivAvailableBudget))
                result.findings.add(new Finding(String.format(
                        "Konto \"%s\": verfügbares Budget ist NaN/Infinity.", acc.getName())));
            for (TxBE tx : acc.getTxList())
                if (isBad(tx.getAmount()))
                    result.findings.add(new Finding(String.format(
                            "Konto \"%s\": Eintrag \"%s\" hat einen NaN/Infinity-Betrag.", acc.getName(), tx.getDescription())));
        }
    }

    private boolean isBad(float v) {
        return Float.isNaN(v) || Float.isInfinite(v);
    }

    // model.currentIncome entries are meant to be the exact same TxBE instances also present in
    // some account's tx list (see Controller.addFunds) - after a JSON round trip that collapses to
    // "an entry with the same date/description/amount must exist somewhere". If it doesn't, either
    // the income entry or its account-side counterpart was dropped/edited independently.
    private void checkIncomeCrossReference(ParsedFile parsed, Result result) {
        List<TxBE> allAccountEntries = new ArrayList<>();
        for (AccountBE acc : parsed.assetAccounts)
            allAccountEntries.addAll(acc.getTxList());
        for (BudgetAccountBE acc : flattenBudgets(parsed.budgetAccounts).values())
            allAccountEntries.addAll(acc.getTxList());

        for (TxBE incomeTx : parsed.income) {
            if (!containsEquivalentEntry(allAccountEntries, incomeTx))
                result.findings.add(new Finding(String.format(
                        "Einkommens-Eintrag \"%s\" (%s, %s) hat keinen passenden Eintrag in einem Konto - Einkommensliste und Kontoauszug sind auseinandergelaufen.",
                        incomeTx.getDescription(), Util.formatDateSave(incomeTx.getDate()), Util.formatFloatDisplay(incomeTx.getAmount()))));
        }
    }

    // The main check. See the class doc for the underlying invariant.
    @SuppressLint("DefaultLocale")
    private void checkTotalSumConservation(ParsedFile before, ParsedFile after, Result result) {
        float totalBefore = sumAccounts(before.assetAccounts) + sumBudgetAccounts(before.budgetAccounts);
        float totalAfter = sumAccounts(after.assetAccounts) + sumBudgetAccounts(after.budgetAccounts);
        float incomeDelta = sumIncome(after.income) - sumIncome(before.income);

        Map<String, BudgetAccountBE> beforeBudgets = flattenBudgets(before.budgetAccounts);
        Map<String, BudgetAccountBE> afterBudgets = flattenBudgets(after.budgetAccounts);

        float resetExplainedDecrease = 0f;
        List<String> resetNotes = new ArrayList<>();
        for (Map.Entry<String, BudgetAccountBE> entry : beforeBudgets.entrySet()) {
            BudgetAccountBE beforeAcc = entry.getValue();
            BudgetAccountBE afterAcc = afterBudgets.get(entry.getKey());
            if (afterAcc == null)
                continue; // account removed between snapshots entirely - not attempting to explain that here
            float beforeSum = beforeAcc.getSum();
            float afterSum = afterAcc.getSum();
            if (afterSum >= beforeSum - AMOUNT_EPSILON)
                continue;
            int survivingCount = 0;
            for (TxBE tx : beforeAcc.getTxList())
                if (containsEquivalentEntry(afterAcc.getTxList(), tx))
                    survivingCount++;
            if (survivingCount == 0) {
                // every prior entry is gone - consistent with BudgetAccountBE.tryRenew()/reset()
                // clearing the tx list wholesale, treat as a legitimate renewal/reset
                resetExplainedDecrease += (beforeSum - afterSum);
                resetNotes.add(String.format("\"%s\" (%s -> %s)", entry.getKey(),
                        Util.formatFloatDisplay(beforeSum), Util.formatFloatDisplay(afterSum)));
            } else if (survivingCount < beforeAcc.getTxList().size()) {
                result.findings.add(new Finding(String.format(
                        "Budgetkonto \"%s\": %d von %d vorherigen Buchungen sind verschwunden, aber nicht alle - das sieht nicht nach einem regulären Reset aus (dabei würden ALLE alten Buchungen auf einmal verschwinden), eher nach fehlenden/gelöschten Buchungen.",
                        entry.getKey(), beforeAcc.getTxList().size() - survivingCount, beforeAcc.getTxList().size())));
            }
        }

        float expectedDelta = incomeDelta - resetExplainedDecrease;
        float actualDelta = totalAfter - totalBefore;
        if (Math.abs(actualDelta - expectedDelta) > AMOUNT_EPSILON) {
            result.findings.add(new Finding(String.format(
                    "Gesamtsumme aller Bestands- und Budgetkonten hat sich um %s verändert. Erklärbar wären nur %s (Einkommen: %s%s). Unerklärte Differenz: %s.",
                    Util.formatFloatDisplay(actualDelta), Util.formatFloatDisplay(expectedDelta), Util.formatFloatDisplay(incomeDelta),
                    resetNotes.isEmpty() ? "" : ", Resets: -" + Util.formatFloatDisplay(resetExplainedDecrease) + " [" + String.join("; ", resetNotes) + "]",
                    Util.formatFloatDisplay(actualDelta - expectedDelta))));
            result.findings.add(new Finding(buildPerAccountBreakdown(before, after)));
        }
    }

    private String buildPerAccountBreakdown(ParsedFile before, ParsedFile after) {
        Map<String, Float> beforeSums = new LinkedHashMap<>();
        for (AccountBE a : before.assetAccounts)
            beforeSums.put(a.getName(), a.getSum());
        for (BudgetAccountBE a : flattenBudgets(before.budgetAccounts).values())
            beforeSums.put(a.getName(), a.getSum());

        Map<String, Float> afterSums = new LinkedHashMap<>();
        for (AccountBE a : after.assetAccounts)
            afterSums.put(a.getName(), a.getSum());
        for (BudgetAccountBE a : flattenBudgets(after.budgetAccounts).values())
            afterSums.put(a.getName(), a.getSum());

        Set<String> allNames = new LinkedHashSet<>();
        allNames.addAll(beforeSums.keySet());
        allNames.addAll(afterSums.keySet());

        StringBuilder sb = new StringBuilder("Konten-Übersicht (nur veränderte Konten):\n");
        boolean any = false;
        for (String name : allNames) {
            float b = beforeSums.containsKey(name) ? beforeSums.get(name) : 0f;
            float a = afterSums.containsKey(name) ? afterSums.get(name) : 0f;
            if (!floatsEqual(a, b)) {
                any = true;
                sb.append(String.format("  %s: %s -> %s (Δ %s)\n", name,
                        Util.formatFloatDisplay(b), Util.formatFloatDisplay(a), Util.formatFloatDisplay(a - b)));
            }
        }
        return any ? sb.toString().trim() : "Konten-Übersicht: kein einzelnes Konto hat sich verändert (Differenz liegt an neu hinzugekommenen/entfernten Konten).";
    }

    private boolean containsEquivalentEntry(List<TxBE> haystack, TxBE needle) {
        for (TxBE tx : haystack) {
            if (floatsEqual(tx.getAmount(), needle.getAmount())
                    && tx.getDescription().equals(needle.getDescription())
                    && Util.formatDateSave(tx.getDate()).equals(Util.formatDateSave(needle.getDate())))
                return true;
        }
        return false;
    }

    private float sumAccounts(List<AccountBE> accounts) {
        float sum = 0f;
        for (AccountBE acc : accounts)
            sum += acc.getSum();
        return sum;
    }

    private float sumBudgetAccounts(List<BudgetAccountBE> budgetAccounts) {
        float sum = 0f;
        for (BudgetAccountBE acc : flattenBudgets(budgetAccounts).values())
            sum += acc.getSum();
        return sum;
    }

    private float sumIncome(List<TxBE> income) {
        float sum = 0f;
        for (TxBE tx : income)
            sum += tx.getAmount();
        return sum;
    }

    // flattens every root budget account plus its full sub-budget tree, keyed by name (matching
    // how the rest of the app looks accounts up - Model.getAccountByName et al.)
    private Map<String, BudgetAccountBE> flattenBudgets(List<BudgetAccountBE> roots) {
        Map<String, BudgetAccountBE> map = new LinkedHashMap<>();
        for (BudgetAccountBE root : roots) {
            map.put(root.getName(), root);
            for (BudgetAccountBE sub : root.getAllSubBudgets())
                map.put(sub.getName(), sub);
        }
        return map;
    }

    private boolean floatsEqual(float a, float b) {
        return Math.abs(a - b) < AMOUNT_EPSILON;
    }
}
