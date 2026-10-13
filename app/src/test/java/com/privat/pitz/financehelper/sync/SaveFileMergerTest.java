package com.privat.pitz.financehelper.sync;

import static com.privat.pitz.financehelper.sync.Docs.account;
import static com.privat.pitz.financehelper.sync.Docs.array;
import static com.privat.pitz.financehelper.sync.Docs.doc;
import static com.privat.pitz.financehelper.sync.Docs.find;
import static com.privat.pitz.financehelper.sync.Docs.set;
import static com.privat.pitz.financehelper.sync.Docs.tx;
import static com.privat.pitz.financehelper.sync.Docs.txOf;
import static com.privat.pitz.financehelper.sync.Docs.withSub;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Date;

import com.privat.pitz.financehelper.core.Util;
import com.privat.pitz.financehelper.sync.SaveFileMerger.Result;
import com.privat.pitz.financehelper.sync.SaveFileMerger.Side;

public class SaveFileMergerTest {

    private static final String M1 = "2026-08-01T10:00:00Z";
    private static final String M2 = "2026-08-02T10:00:00Z";
    private static final String M3 = "2026-08-03T10:00:00Z";
    private static final Date FILE_TIME = Util.parseModified("2026-08-04T10:00:00Z");

    private static Result merge(JSONObject base, JSONObject local, JSONObject remote) throws Exception {
        return SaveFileMerger.merge(base, local, remote, FILE_TIME, FILE_TIME);
    }

    private static boolean hasLoss(Result r, Side side) {
        for (SaveFileMerger.Loss l : r.losses) if (l.heldBy == side) return true;
        return false;
    }

    // region transactions: one side changes

    @Test
    public void localAddition_isKept() throws Exception {
        JSONObject base = doc(account("Giro", tx("a", "Miete", "-850.00", M1)));
        JSONObject local = doc(account("Giro", tx("a", "Miete", "-850.00", M1), tx("b", "Kaffee", "-3.00", M2)));
        Result r = merge(base, local, base);
        assertEquals(2, txOf(r.merged, "Giro").length());
        assertNotNull(find(txOf(r.merged, "Giro"), "b"));
        assertTrue(r.losses.isEmpty());
    }

    @Test
    public void remoteEdit_isTakenWithoutNeedingATimestampComparison() throws Exception {
        JSONObject base = doc(account("Giro", tx("a", "Miete", "-850.00", M1)));
        JSONObject remote = doc(account("Giro", tx("a", "Miete", "-900.00", M2)));
        Result r = merge(base, base, remote);
        assertEquals("-900.00", find(txOf(r.merged, "Giro"), "a").getString("a"));
        assertTrue(r.losses.isEmpty());
    }

    @Test
    public void nothingChanged_producesTheSameDocument() throws Exception {
        JSONObject base = doc(account("Giro", tx("a", "x", "1.00", M1), tx("b", "y", "2.00", M1)),
                account("Bar", tx("c", "z", "3.00", M1)));
        Result r = merge(base, base, base);
        assertEquals(SaveFileMerger.canonical(base), SaveFileMerger.canonical(r.merged));
        assertTrue(r.losses.isEmpty());
        assertTrue(r.notes.isEmpty());
    }

    // endregion

    // region transactions: both sides change

    @Test
    public void bothEdit_newerModifiedWins_andTheOtherIsReportedAsLoss() throws Exception {
        JSONObject base = doc(account("Giro", tx("a", "Miete", "-850.00", M1)));
        JSONObject local = doc(account("Giro", tx("a", "Miete", "-860.00", M3)));
        JSONObject remote = doc(account("Giro", tx("a", "Miete", "-870.00", M2)));
        Result r = merge(base, local, remote);
        assertEquals("-860.00", find(txOf(r.merged, "Giro"), "a").getString("a"));
        assertTrue(hasLoss(r, Side.REMOTE));
        assertFalse(hasLoss(r, Side.LOCAL));
    }

    @Test
    public void bothEdit_sameModified_goesToRemote() throws Exception {
        JSONObject base = doc(account("Giro", tx("a", "Miete", "-850.00", M1)));
        JSONObject local = doc(account("Giro", tx("a", "Miete", "-860.00", M2)));
        JSONObject remote = doc(account("Giro", tx("a", "Miete", "-870.00", M2)));
        Result r = merge(base, local, remote);
        assertEquals("-870.00", find(txOf(r.merged, "Giro"), "a").getString("a"));
        assertTrue(hasLoss(r, Side.LOCAL));
    }

    @Test
    public void bothMakeTheSameEdit_isNotAConflict() throws Exception {
        JSONObject base = doc(account("Giro", tx("a", "Miete", "-850.00", M1)));
        JSONObject local = doc(account("Giro", tx("a", "Miete", "-900.00", M2)));
        JSONObject remote = doc(account("Giro", tx("a", "Miete", "-900.00", M3)));
        Result r = merge(base, local, remote);
        assertTrue(r.losses.isEmpty());
        assertEquals(M3, find(txOf(r.merged, "Giro"), "a").getString("m"));
    }

    @Test
    public void externalEditThatKeepsOldM_isDatedByTheRemoteFileTime_andBeatsAnOlderLocalEdit() throws Exception {
        JSONObject base = doc(account("Giro", tx("a", "Miete", "-850.00", M1)));
        JSONObject local = doc(account("Giro", tx("a", "Miete", "-860.00", M2)));
        JSONObject remote = doc(account("Giro", tx("a", "Miete", "-870.00", M1)));   // m not advanced
        Result r = SaveFileMerger.merge(base, local, remote, FILE_TIME, Util.parseModified(M3));
        JSONObject winner = find(txOf(r.merged, "Giro"), "a");
        assertEquals("-870.00", winner.getString("a"));
        assertEquals(M3, winner.getString("m"));
    }

    @Test
    public void externalEditThatKeepsOldM_losesAgainstANewerLocalEdit() throws Exception {
        JSONObject base = doc(account("Giro", tx("a", "Miete", "-850.00", M1)));
        JSONObject local = doc(account("Giro", tx("a", "Miete", "-860.00", M3)));
        JSONObject remote = doc(account("Giro", tx("a", "Miete", "-870.00", M1)));
        Result r = SaveFileMerger.merge(base, local, remote, FILE_TIME, Util.parseModified(M2));
        assertEquals("-860.00", find(txOf(r.merged, "Giro"), "a").getString("a"));
    }

    // endregion

    // region deletion

    @Test
    public void localDeleteOfUntouchedEntry_deletesIt_andRemoteStillHoldsTheLoss() throws Exception {
        JSONObject base = doc(account("Giro", tx("a", "x", "1.00", M1), tx("b", "y", "2.00", M1)));
        JSONObject local = doc(account("Giro", tx("a", "x", "1.00", M1)));
        Result r = merge(base, local, base);
        assertNull(find(txOf(r.merged, "Giro"), "b"));
        assertTrue(hasLoss(r, Side.REMOTE));
    }

    @Test
    public void remoteDeleteOfUntouchedEntry_deletesIt() throws Exception {
        JSONObject base = doc(account("Giro", tx("a", "x", "1.00", M1), tx("b", "y", "2.00", M1)));
        JSONObject remote = doc(account("Giro", tx("a", "x", "1.00", M1)));
        Result r = merge(base, base, remote);
        assertNull(find(txOf(r.merged, "Giro"), "b"));
        assertTrue(hasLoss(r, Side.LOCAL));
    }

    @Test
    public void deletedLocally_editedRemotely_theEditSurvives() throws Exception {
        JSONObject base = doc(account("Giro", tx("a", "x", "1.00", M1)));
        JSONObject local = doc(account("Giro"));
        JSONObject remote = doc(account("Giro", tx("a", "x", "5.00", M2)));
        Result r = merge(base, local, remote);
        assertEquals("5.00", find(txOf(r.merged, "Giro"), "a").getString("a"));
        assertTrue(r.losses.isEmpty());
        assertFalse(r.notes.isEmpty());
    }

    @Test
    public void deletedRemotely_editedLocally_theEditSurvives() throws Exception {
        JSONObject base = doc(account("Giro", tx("a", "x", "1.00", M1)));
        JSONObject local = doc(account("Giro", tx("a", "x", "5.00", M2)));
        JSONObject remote = doc(account("Giro"));
        Result r = merge(base, local, remote);
        assertEquals("5.00", find(txOf(r.merged, "Giro"), "a").getString("a"));
        assertTrue(r.losses.isEmpty());
    }

    @Test
    public void deletedOnBothSides_staysDeleted() throws Exception {
        JSONObject base = doc(account("Giro", tx("a", "x", "1.00", M1)));
        JSONObject both = doc(account("Giro"));
        Result r = merge(base, both, both);
        assertEquals(0, txOf(r.merged, "Giro").length());
        assertTrue(r.losses.isEmpty());
    }

    // endregion

    // region first sync

    @Test
    public void sameIdOnBothSidesWithoutBase_newerWinsAndNothingIsDuplicated() throws Exception {
        JSONObject local = doc(account("Giro", tx("a", "alt", "1.00", M1)));
        JSONObject remote = doc(account("Giro", tx("a", "neu", "2.00", M2)));
        Result r = merge(null, local, remote);
        assertEquals(1, txOf(r.merged, "Giro").length());
        assertEquals("2.00", find(txOf(r.merged, "Giro"), "a").getString("a"));
        assertTrue(hasLoss(r, Side.LOCAL));
    }

    @Test
    public void firstSync_legacyEntriesWithoutIds_arePairedByContent_notDuplicated() throws Exception {
        JSONObject local = doc(account("Giro", tx(null, "Miete", "-850.00", null), tx(null, "Kaffee", "-3.00", null)));
        JSONObject remote = doc(account("Giro", tx(null, "Miete", "-850.00", null), tx(null, "Kaffee", "-3.00", null)));
        Result r = merge(null, local, remote);
        assertEquals(2, txOf(r.merged, "Giro").length());
    }

    @Test
    public void firstSync_identicalLegacyEntriesAreMatchedOneToOne() throws Exception {
        // two identical bookings on one side, one on the other: the second one exists only locally
        JSONObject local = doc(account("Giro", tx(null, "Kaffee", "-3.00", null), tx(null, "Kaffee", "-3.00", null)));
        JSONObject remote = doc(account("Giro", tx(null, "Kaffee", "-3.00", null)));
        Result r = merge(null, local, remote);
        assertEquals(2, txOf(r.merged, "Giro").length());
    }

    @Test
    public void firstSync_differentEntriesOnEachSide_areBothKept() throws Exception {
        JSONObject local = doc(account("Giro", tx("a", "Phone", "-1.00", M1)));
        JSONObject remote = doc(account("Giro", tx("b", "Agent", "-2.00", M1)));
        Result r = merge(null, local, remote);
        assertEquals(2, txOf(r.merged, "Giro").length());
        assertTrue(r.losses.isEmpty());
    }

    // endregion

    // region foreign writers

    @Test
    public void entryAddedByAgentWithoutIdAndM_getsBoth() throws Exception {
        JSONObject base = doc(account("Giro", tx("a", "x", "1.00", M1)));
        JSONObject remote = doc(account("Giro", tx("a", "x", "1.00", M1), tx(null, "Agent", "-9.00", null)));
        Result r = merge(base, base, remote);
        JSONArray txs = txOf(r.merged, "Giro");
        assertEquals(2, txs.length());
        JSONObject added = txs.getJSONObject(1);
        assertFalse(added.getString("i").isEmpty());
        assertEquals(Util.formatModified(FILE_TIME), added.getString("m"));
    }

    @Test
    public void entryCopiedWithItsIdByAgent_becomesTwoTransactions() throws Exception {
        JSONObject base = doc(account("Giro", tx("a", "x", "1.00", M1)));
        JSONObject remote = doc(account("Giro", tx("a", "x", "1.00", M1), tx("a", "x", "1.00", M1)));
        Result r = merge(base, base, remote);
        JSONArray txs = txOf(r.merged, "Giro");
        assertEquals(2, txs.length());
        assertNotEquals(txs.getJSONObject(0).getString("i"), txs.getJSONObject(1).getString("i"));
    }

    @Test
    public void unknownExtraFieldsOnATransaction_arePreserved() throws Exception {
        JSONObject base = doc(account("Giro", tx("a", "x", "1.00", M1)));
        JSONObject withNote = tx("a", "x", "1.00", M2);
        withNote.put("note", "vom Agenten");
        Result r = merge(base, base, doc(account("Giro", withNote)));
        assertEquals("vom Agenten", find(txOf(r.merged, "Giro"), "a").getString("note"));
    }

    // endregion

    // region structure

    @Test
    public void transactionMovedToAnotherAccountOnOneSide_staysMovedAndAppearsOnce() throws Exception {
        JSONObject base = doc(account("Giro", tx("a", "x", "1.00", M1)), account("Bar"));
        JSONObject remote = doc(account("Giro"), account("Bar", tx("a", "x", "1.00", M2)));
        Result r = merge(base, base, remote);
        assertEquals(0, txOf(r.merged, "Giro").length());
        assertEquals(1, txOf(r.merged, "Bar").length());
    }

    @Test
    public void accountDeletedOnOneSideAndUntouchedOnTheOther_isDeleted() throws Exception {
        JSONObject base = doc(account("Giro", tx("a", "x", "1.00", M1)), account("Alt"));
        JSONObject local = doc(account("Giro", tx("a", "x", "1.00", M1)));
        Result r = merge(base, local, base);
        assertNull(txOf(r.merged, "Alt"));
        assertTrue(hasLoss(r, Side.REMOTE));
    }

    @Test
    public void accountDeletedOnOneSideButChangedOnTheOther_stays() throws Exception {
        JSONObject base = doc(account("Giro"), account("Alt"));
        JSONObject local = doc(account("Giro"));
        JSONObject remote = doc(account("Giro"), account("Alt", tx("n", "neu", "1.00", M2)));
        Result r = merge(base, local, remote);
        assertEquals(1, txOf(r.merged, "Alt").length());
    }

    @Test
    public void accountAddedOnOneSide_isKept() throws Exception {
        JSONObject base = doc(account("Giro"));
        JSONObject remote = doc(account("Giro"), account("Neu", tx("n", "x", "1.00", M1)));
        Result r = merge(base, base, remote);
        assertEquals(1, txOf(r.merged, "Neu").length());
    }

    @Test
    public void accountProperties_mergePerKey() throws Exception {
        JSONObject base = doc(account("Giro"));
        JSONObject local = doc(set(account("Giro"), "active", false));
        JSONObject remote = doc(set(account("Giro"), "renew_auto", false));
        JSONObject acc = account(merge(base, local, remote).merged, "Giro");
        assertEquals(false, acc.getBoolean("active"));       // only local changed it
        assertEquals(false, acc.getBoolean("renew_auto"));   // only remote changed it
    }

    @Test
    public void accountProperty_changedDifferentlyOnBothSides_goesToRemote() throws Exception {
        JSONObject base = doc(account("Giro"));
        JSONObject local = doc(set(account("Giro"), "active", false));
        JSONObject remote = doc(set(account("Giro"), "active", 7));
        Result r = merge(base, local, remote);
        assertEquals(7, account(r.merged, "Giro").getInt("active"));
        assertTrue(hasLoss(r, Side.LOCAL));
    }

    @Test
    public void subBudgetTransactions_areMergedInPlace() throws Exception {
        JSONObject base = doc();
        base.put("budgets", array(withSub(account("Lebensmittel"), account("Bio", tx("s", "x", "1.00", M1)))));
        JSONObject local = doc();
        local.put("budgets", array(withSub(account("Lebensmittel"),
                account("Bio", tx("s", "x", "1.00", M1), tx("t", "y", "2.00", M2)))));
        Result r = merge(base, local, base);
        JSONArray bio = r.merged.getJSONArray("budgets").getJSONObject(0)
                .getJSONArray("sub_budgets").getJSONObject(0).getJSONArray("tx");
        assertEquals(2, bio.length());
    }

    @Test
    public void income_isMergedLikeAnyOtherList() throws Exception {
        JSONObject base = doc();
        JSONObject local = doc();
        local.put("income", array(tx("i1", "Gehalt", "2000.00", M1)));
        JSONObject remote = doc();
        remote.put("income", array(tx("i2", "Bonus", "100.00", M2)));
        Result r = merge(base, local, remote);
        assertEquals(2, r.merged.getJSONArray("income").length());
    }

    @Test
    public void recurringOrders_mergeAsASet() throws Exception {
        String rest = "\"t\":\"01.08.2026 09:00\",\"sender\":\"Giro\",\"receiver\":\"\"}";
        JSONObject o1 = new JSONObject("{\"a\":\"-10.00\",\"d\":\"Netflix\"," + rest);
        JSONObject o2 = new JSONObject("{\"a\":\"-5.00\",\"d\":\"Spotify\"," + rest);
        JSONObject o3 = new JSONObject("{\"a\":\"-7.00\",\"d\":\"Cloud\"," + rest);
        JSONObject base = doc();
        base.put("recur_tx", array(o1, o2));
        JSONObject local = doc();
        local.put("recur_tx", array(o1, o2, o3));      // added Cloud
        JSONObject remote = doc();
        remote.put("recur_tx", array(o1));              // removed Spotify
        Result r = merge(base, local, remote);
        JSONArray out = r.merged.getJSONArray("recur_tx");
        assertEquals(2, out.length());
        assertEquals("Netflix", out.getJSONObject(0).getString("d"));
        assertEquals("Cloud", out.getJSONObject(1).getString("d"));
    }

    // endregion

    // region order

    @Test
    public void localOrder_isKeptWhenLocalChangedTheAccount() throws Exception {
        JSONObject base = doc(account("Giro", tx("a", "x", "1.00", M1), tx("b", "y", "2.00", M1)));
        JSONObject local = doc(account("Giro", tx("b", "y", "2.00", M1), tx("a", "x", "1.00", M1), tx("c", "z", "3.00", M2)));
        Result r = merge(base, local, base);
        JSONArray txs = txOf(r.merged, "Giro");
        assertEquals("b", txs.getJSONObject(0).getString("i"));
        assertEquals("a", txs.getJSONObject(1).getString("i"));
        assertEquals("c", txs.getJSONObject(2).getString("i"));
    }

    // endregion
}
