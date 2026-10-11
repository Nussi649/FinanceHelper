package com.privat.pitz.financehelper.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;
import com.privat.pitz.financehelper.data.TxBE;

/**
 * Covers {@link TxService#moveTx}, {@link TxService#mergeTx}, {@link TxService#undoMerge} and
 * {@link TxService#mergedAmount}, which back the "move entries to another account" and "merge
 * entries" actions.
 *
 * <p>Both operations rewrite the user's ledger in bulk, so the properties worth pinning are the
 * ones that would silently corrupt it: a move must change exactly the two affected account totals
 * and nothing else, a merge must never change the account total (not even by float residue), a
 * refused request must leave everything untouched, and a failed save must put every list back in
 * its exact original order. Undo has to restore the very same TxBE instances, not look-alikes,
 * because the UI keeps references to them.
 */
public class MoveMergeTxTest {

    private static final float DELTA = 0.001f;
    private static final String FILE = "2026-08-User.jso";

    private InMemorySavefileStorage storage;
    private Controller controller;
    private AccountBE girokonto;
    private AccountBE sparkonto;
    private BudgetAccountBE haushalt;
    private BudgetAccountBE lebensmittel;
    private BudgetAccountBE wocheneinkauf;

    private Date date(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day, 12, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    @Before
    public void setUp() {
        storage = new InMemorySavefileStorage();
        controller = new Controller(storage);
        controller.getModel().currentFileName = FILE;
        girokonto = new AccountBE("Girokonto");
        sparkonto = new AccountBE("Sparkonto");
        haushalt = new BudgetAccountBE("Haushalt", 500f, 6000f);
        lebensmittel = new BudgetAccountBE("Lebensmittel", 200f, 2400f);
        wocheneinkauf = new BudgetAccountBE("Wocheneinkauf", 50f, 600f);
        lebensmittel.addSubBudget(wocheneinkauf);
        haushalt.addSubBudget(lebensmittel);
        controller.getModel().asset_accounts.add(girokonto);
        controller.getModel().asset_accounts.add(sparkonto);
        controller.getModel().budget_accounts.add(haushalt);
    }

    private TxBE tx(AccountBE account, float amount, String description, Date when) {
        TxBE tx = new TxBE(amount, description, when);
        account.addTx(tx);
        return tx;
    }

    /** Snapshot of an account's list (the instances, in order). */
    private List<TxBE> snapshot(AccountBE account) {
        return new ArrayList<>(account.getTxList());
    }

    private void assertSameList(String message, List<TxBE> expected, AccountBE account) {
        assertEquals(message + " (size)", expected.size(), account.getTxList().size());
        for (int i = 0; i < expected.size(); i++)
            assertSame(message + " (index " + i + ")", expected.get(i), account.getTxList().get(i));
    }

    // region moveTx
    @Test
    public void move_entryLeavesSourceAndJoinsTargetUnchanged() throws JSONException, IOException {
        Date when = date(2026, 8, 10);
        TxBE entry = tx(girokonto, -30f, "Markt", when);
        tx(girokonto, -5f, "Kaffee", date(2026, 8, 11));

        assertTrue(controller.moveTx(girokonto, Collections.singletonList(entry), sparkonto));

        assertEquals(-5f, girokonto.getSum(), DELTA);
        assertEquals(1, girokonto.getTxList().size());
        assertEquals(1, sparkonto.getTxList().size());
        TxBE moved = sparkonto.getTxList().get(0);
        assertSame(entry, moved);
        assertEquals(-30f, moved.getAmount(), DELTA);
        assertEquals("Markt", moved.getDescription());
        assertEquals(when, moved.getDate());
    }

    @Test
    public void move_targetEndsSortedByDate() throws JSONException, IOException {
        TxBE early = tx(sparkonto, 1f, "frueh", date(2026, 8, 1));
        TxBE late = tx(sparkonto, 3f, "spaet", date(2026, 8, 20));
        TxBE middle = tx(girokonto, 2f, "mitte", date(2026, 8, 10));

        assertTrue(controller.moveTx(girokonto, Collections.singletonList(middle), sparkonto));

        assertSameList("target sorted by date", Arrays.asList(early, middle, late), sparkonto);
    }

    @Test
    public void move_severalEntriesAtOnce() throws JSONException, IOException {
        TxBE a = tx(girokonto, -1f, "a", date(2026, 8, 3));
        TxBE b = tx(girokonto, -2f, "b", date(2026, 8, 1));
        TxBE keep = tx(girokonto, -4f, "keep", date(2026, 8, 2));
        TxBE c = tx(girokonto, -8f, "c", date(2026, 8, 2));

        assertTrue(controller.moveTx(girokonto, Arrays.asList(a, b, c), sparkonto));

        assertSameList("source keeps the rest", Collections.singletonList(keep), girokonto);
        assertEquals(3, sparkonto.getTxList().size());
        assertEquals(-11f, sparkonto.getSum(), DELTA);
        assertSame(b, sparkonto.getTxList().get(0));
        assertSame(a, sparkonto.getTxList().get(2));
        assertEquals(-4f, girokonto.getSum(), DELTA);
    }

    @Test
    public void move_intoAndOutOfASubBudgetTwoLevelsDeep() throws JSONException, IOException {
        TxBE entry = tx(girokonto, 30f, "Markt", date(2026, 8, 10));

        assertTrue(controller.moveTx(girokonto, Collections.singletonList(entry), wocheneinkauf));
        assertEquals(0, girokonto.getTxList().size());
        assertSame(entry, wocheneinkauf.getTxList().get(0));

        assertTrue(controller.moveTx(wocheneinkauf, Collections.singletonList(entry), sparkonto));
        assertEquals(0, wocheneinkauf.getTxList().size());
        assertSame(entry, sparkonto.getTxList().get(0));
    }

    @Test
    public void move_isRefusedWhenSourceIsTarget() throws JSONException, IOException {
        TxBE entry = tx(girokonto, -30f, "Markt", date(2026, 8, 10));
        List<TxBE> before = snapshot(girokonto);

        assertFalse(controller.moveTx(girokonto, Collections.singletonList(entry), girokonto));

        assertSameList("untouched", before, girokonto);
        assertEquals(0, storage.writeCount);
    }

    @Test
    public void move_isRefusedWhenAnEntryIsNotOnTheSource() throws JSONException, IOException {
        TxBE onSource = tx(girokonto, -30f, "Markt", date(2026, 8, 10));
        TxBE elsewhere = tx(sparkonto, 5f, "Zins", date(2026, 8, 11));
        List<TxBE> giroBefore = snapshot(girokonto);
        List<TxBE> sparBefore = snapshot(sparkonto);

        assertFalse(controller.moveTx(girokonto, Arrays.asList(onSource, elsewhere), sparkonto));

        assertSameList("source untouched, even for the valid entry", giroBefore, girokonto);
        assertSameList("target untouched", sparBefore, sparkonto);
        assertEquals(0, storage.writeCount);
    }

    @Test
    public void move_isRefusedForAnOrphanTarget() throws JSONException, IOException {
        TxBE entry = tx(girokonto, -30f, "Markt", date(2026, 8, 10));
        AccountBE orphan = new AccountBE("Geist");
        List<TxBE> before = snapshot(girokonto);

        assertFalse(controller.moveTx(girokonto, Collections.singletonList(entry), orphan));

        assertSameList("untouched", before, girokonto);
        assertEquals(0, orphan.getTxList().size());
        assertEquals(0, storage.writeCount);
    }

    @Test
    public void move_isRefusedForAnEmptyList() throws JSONException, IOException {
        tx(girokonto, -30f, "Markt", date(2026, 8, 10));

        assertFalse(controller.moveTx(girokonto, new ArrayList<>(), sparkonto));

        assertEquals(1, girokonto.getTxList().size());
        assertEquals(0, sparkonto.getTxList().size());
        assertEquals(0, storage.writeCount);
    }

    @Test
    public void move_isPersisted() throws JSONException, IOException {
        TxBE entry = tx(girokonto, -30f, "Markt", date(2026, 8, 10));
        tx(girokonto, -5f, "Kaffee", date(2026, 8, 11));

        controller.moveTx(girokonto, Collections.singletonList(entry), sparkonto);

        Controller reader = new Controller(storage);
        reader.readAccountsFromInternal(FILE);
        assertEquals(-5f, reader.getModel().getAssetAccountByName("Girokonto").getSum(), DELTA);
        AccountBE readSpar = reader.getModel().getAssetAccountByName("Sparkonto");
        assertEquals(1, readSpar.getTxList().size());
        assertEquals("Markt", readSpar.getTxList().get(0).getDescription());
        assertEquals(-30f, readSpar.getSum(), DELTA);
    }

    @Test
    public void move_aFailedSaveRevertsBothListsExactly() throws JSONException {
        TxBE s1 = tx(girokonto, -1f, "s1", date(2026, 8, 5));
        TxBE s2 = tx(girokonto, -2f, "s2", date(2026, 8, 1));
        TxBE s3 = tx(girokonto, -3f, "s3", date(2026, 8, 9));
        tx(sparkonto, 10f, "t1", date(2026, 8, 7));
        tx(sparkonto, 20f, "t2", date(2026, 8, 2));
        List<TxBE> giroBefore = snapshot(girokonto);
        List<TxBE> sparBefore = snapshot(sparkonto);
        storage.failWrites = true;

        try {
            controller.moveTx(girokonto, Arrays.asList(s1, s3), sparkonto);
            fail("the save failure must propagate");
        } catch (IOException expected) {
            // saveOrRevert rethrows after undoing the mutation
        }

        assertSameList("source order restored", giroBefore, girokonto);
        assertSameList("target order restored (not left sorted)", sparBefore, sparkonto);
        assertSame(s2, girokonto.getTxList().get(1));
    }
    // endregion

    // region mergedAmount
    @Test
    public void mergedAmount_roundsFloatResidueToCents() {
        TxBE anchor = new TxBE(-3.68f, "a", date(2026, 8, 1));
        TxBE other = new TxBE(-9.38f, "b", date(2026, 8, 2));

        float merged = TxService.mergedAmount(anchor, Collections.singletonList(other));

        assertEquals("exact, not merely close", -13.06f, merged, 0f);
    }

    @Test
    public void mergedAmount_sumsAllEntries() {
        TxBE anchor = new TxBE(10f, "a", date(2026, 8, 1));
        List<TxBE> others = Arrays.asList(
                new TxBE(-2.5f, "b", date(2026, 8, 2)), new TxBE(0.1f, "c", date(2026, 8, 3)));

        assertEquals(7.6f, TxService.mergedAmount(anchor, others), 0f);
    }
    // endregion

    // region mergeTx
    @Test
    public void merge_anchorKeepsDateAndGetsDescriptionAndSum() throws JSONException, IOException {
        Date anchorDate = date(2026, 8, 3);
        TxBE first = tx(girokonto, -3.68f, "Brot", date(2026, 8, 1));
        TxBE anchor = tx(girokonto, -9.38f, "Milch", anchorDate);
        TxBE third = tx(girokonto, -1f, "Obst", date(2026, 8, 5));
        float totalBefore = girokonto.getSum();

        TxService.Merge merge = controller.mergeTx(girokonto, anchor,
                Arrays.asList(first, third), "Einkauf");

        assertNotNull(merge);
        assertFalse(merge.cancelledOut);
        assertSameList("only the anchor remains", Collections.singletonList(anchor), girokonto);
        assertEquals(anchorDate, anchor.getDate());
        assertEquals("Einkauf", anchor.getDescription());
        assertEquals(-14.06f, anchor.getAmount(), 0f);
        assertEquals("the account total must not move", totalBefore, girokonto.getSum(), DELTA);
    }

    @Test
    public void merge_floatResidueIsRoundedOnTheAnchor() throws JSONException, IOException {
        TxBE anchor = tx(girokonto, -3.68f, "a", date(2026, 8, 1));
        TxBE other = tx(girokonto, -9.38f, "b", date(2026, 8, 2));

        controller.mergeTx(girokonto, anchor, Collections.singletonList(other), "ab");

        assertEquals(-13.06f, anchor.getAmount(), 0f);
    }

    @Test
    public void merge_offsettingPairCancelsOutAndRemovesTheAnchorToo()
            throws JSONException, IOException {
        TxBE anchor = tx(girokonto, -1195f, "Kaution", date(2026, 8, 1));
        TxBE other = tx(girokonto, 1195f, "Kaution zurueck", date(2026, 8, 20));
        TxBE untouched = tx(girokonto, -7f, "Rest", date(2026, 8, 10));

        TxService.Merge merge = controller.mergeTx(girokonto, anchor,
                Collections.singletonList(other), "egal");

        assertNotNull(merge);
        assertTrue(merge.cancelledOut);
        assertSameList("no 0,00 entry left behind",
                Collections.singletonList(untouched), girokonto);
        assertEquals(-7f, girokonto.getSum(), DELTA);
    }

    @Test
    public void merge_threeEntriesSummingToZeroCancelOut() throws JSONException, IOException {
        TxBE anchor = tx(girokonto, -10f, "a", date(2026, 8, 1));
        TxBE b = tx(girokonto, -5f, "b", date(2026, 8, 2));
        TxBE c = tx(girokonto, 15f, "c", date(2026, 8, 3));

        TxService.Merge merge = controller.mergeTx(girokonto, anchor, Arrays.asList(b, c), "x");

        assertNotNull(merge);
        assertTrue(merge.cancelledOut);
        assertEquals(0, girokonto.getTxList().size());
    }

    @Test
    public void merge_isPersisted() throws JSONException, IOException {
        TxBE anchor = tx(girokonto, -3.68f, "a", date(2026, 8, 1));
        TxBE other = tx(girokonto, -9.38f, "b", date(2026, 8, 2));

        controller.mergeTx(girokonto, anchor, Collections.singletonList(other), "ab");

        Controller reader = new Controller(storage);
        reader.readAccountsFromInternal(FILE);
        AccountBE read = reader.getModel().getAssetAccountByName("Girokonto");
        assertEquals(1, read.getTxList().size());
        assertEquals("ab", read.getTxList().get(0).getDescription());
        assertEquals(-13.06f, read.getTxList().get(0).getAmount(), DELTA);
    }

    @Test
    public void merge_isRefusedWhenTheAnchorIsAmongTheOthers() throws JSONException, IOException {
        TxBE anchor = tx(girokonto, -1f, "a", date(2026, 8, 1));
        TxBE other = tx(girokonto, -2f, "b", date(2026, 8, 2));

        assertRefused(anchor, Arrays.asList(other, anchor));
    }

    @Test
    public void merge_isRefusedWhenAnEntryIsOnAnotherAccount() throws JSONException, IOException {
        TxBE anchor = tx(girokonto, -1f, "a", date(2026, 8, 1));
        TxBE foreign = tx(sparkonto, -2f, "b", date(2026, 8, 2));

        assertRefused(anchor, Collections.singletonList(foreign));
        assertEquals(1, sparkonto.getTxList().size());
    }

    @Test
    public void merge_isRefusedForDuplicatesInOthers() throws JSONException, IOException {
        TxBE anchor = tx(girokonto, -1f, "a", date(2026, 8, 1));
        TxBE other = tx(girokonto, -2f, "b", date(2026, 8, 2));

        assertRefused(anchor, Arrays.asList(other, other));
    }

    @Test
    public void merge_isRefusedWhenThereIsNothingToMerge() throws JSONException, IOException {
        TxBE anchor = tx(girokonto, -1f, "a", date(2026, 8, 1));
        tx(girokonto, -2f, "b", date(2026, 8, 2));

        assertRefused(anchor, new ArrayList<>());
    }

    private void assertRefused(TxBE anchor, List<TxBE> others) throws JSONException, IOException {
        List<TxBE> before = snapshot(girokonto);
        float amountBefore = anchor.getAmount();
        String descriptionBefore = anchor.getDescription();

        assertNull(controller.mergeTx(girokonto, anchor, others, "neu"));

        assertSameList("list untouched", before, girokonto);
        assertEquals(amountBefore, anchor.getAmount(), 0f);
        assertEquals(descriptionBefore, anchor.getDescription());
        assertEquals(0, storage.writeCount);
    }

    @Test
    public void merge_aFailedSaveRevertsExactly() throws JSONException {
        TxBE x = tx(girokonto, -4f, "x", date(2026, 8, 1));
        TxBE anchor = tx(girokonto, -3.68f, "a", date(2026, 8, 2));
        TxBE y = tx(girokonto, -6f, "y", date(2026, 8, 3));
        TxBE other = tx(girokonto, -9.38f, "b", date(2026, 8, 4));
        List<TxBE> before = snapshot(girokonto);
        storage.failWrites = true;

        try {
            controller.mergeTx(girokonto, anchor, Collections.singletonList(other), "ab");
            fail("the save failure must propagate");
        } catch (IOException expected) {
            // saveOrRevert rethrows after undoing the mutation
        }

        assertSameList("order restored", before, girokonto);
        assertEquals(-3.68f, anchor.getAmount(), 0f);
        assertEquals("a", anchor.getDescription());
        assertSame(x, girokonto.getTxList().get(0));
        assertSame(y, girokonto.getTxList().get(2));
    }

    @Test
    public void merge_aFailedSaveRevertsACancelledOutMergeToo() throws JSONException {
        TxBE anchor = tx(girokonto, -1195f, "a", date(2026, 8, 1));
        TxBE other = tx(girokonto, 1195f, "b", date(2026, 8, 2));
        List<TxBE> before = snapshot(girokonto);
        storage.failWrites = true;

        try {
            controller.mergeTx(girokonto, anchor, Collections.singletonList(other), "x");
            fail("the save failure must propagate");
        } catch (IOException expected) {
            // saveOrRevert rethrows after undoing the mutation
        }

        assertSameList("anchor is back as well", before, girokonto);
    }
    // endregion

    // region undoMerge
    @Test
    public void undo_restoresANormalMergeExactly() throws JSONException, IOException {
        TxBE first = tx(girokonto, -3.68f, "Brot", date(2026, 8, 1));
        TxBE anchor = tx(girokonto, -9.38f, "Milch", date(2026, 8, 3));
        TxBE third = tx(girokonto, -1f, "Obst", date(2026, 8, 5));
        List<TxBE> before = snapshot(girokonto);
        TxService.Merge merge = controller.mergeTx(girokonto, anchor,
                Arrays.asList(first, third), "Einkauf");

        controller.undoMerge(merge);

        assertSameList("order and identities", before, girokonto);
        assertEquals(-3.68f, first.getAmount(), 0f);
        assertEquals("Brot", first.getDescription());
        assertEquals(-9.38f, anchor.getAmount(), 0f);
        assertEquals("Milch", anchor.getDescription());
        assertEquals(-1f, third.getAmount(), 0f);
    }

    @Test
    public void undo_restoresACancelledOutMergeExactly() throws JSONException, IOException {
        TxBE anchor = tx(girokonto, -10f, "a", date(2026, 8, 1));
        TxBE b = tx(girokonto, -5f, "b", date(2026, 8, 2));
        TxBE keep = tx(girokonto, -2f, "keep", date(2026, 8, 3));
        TxBE c = tx(girokonto, 15f, "c", date(2026, 8, 4));
        List<TxBE> before = snapshot(girokonto);
        TxService.Merge merge = controller.mergeTx(girokonto, anchor, Arrays.asList(b, c), "x");
        assertTrue(merge.cancelledOut);
        assertSameList("only the bystander is left", Collections.singletonList(keep), girokonto);

        controller.undoMerge(merge);

        assertSameList("order and identities", before, girokonto);
        assertSame(anchor, girokonto.getTxList().get(0));
        assertEquals(-10f, anchor.getAmount(), 0f);
        assertEquals("a", anchor.getDescription());
    }

    @Test
    public void undo_isPersisted() throws JSONException, IOException {
        TxBE anchor = tx(girokonto, -3.68f, "a", date(2026, 8, 1));
        TxBE other = tx(girokonto, -9.38f, "b", date(2026, 8, 2));
        TxService.Merge merge = controller.mergeTx(girokonto, anchor,
                Collections.singletonList(other), "ab");

        controller.undoMerge(merge);

        Controller reader = new Controller(storage);
        reader.readAccountsFromInternal(FILE);
        AccountBE read = reader.getModel().getAssetAccountByName("Girokonto");
        assertEquals(2, read.getTxList().size());
        assertEquals("a", read.getTxList().get(0).getDescription());
        assertEquals("b", read.getTxList().get(1).getDescription());
    }

    @Test
    public void undo_aFailedSaveKeepsTheMergedState() throws JSONException, IOException {
        TxBE anchor = tx(girokonto, -3.68f, "a", date(2026, 8, 1));
        TxBE other = tx(girokonto, -9.38f, "b", date(2026, 8, 2));
        TxService.Merge merge = controller.mergeTx(girokonto, anchor,
                Collections.singletonList(other), "ab");
        storage.failWrites = true;

        try {
            controller.undoMerge(merge);
            fail("the save failure must propagate");
        } catch (IOException expected) {
            // saveOrRevert rethrows after undoing the mutation
        }

        assertSameList("still merged", Collections.singletonList(anchor), girokonto);
        assertEquals(-13.06f, anchor.getAmount(), 0f);
        assertEquals("ab", anchor.getDescription());
    }
    // endregion
}
