package com.privat.pitz.financehelper.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

public class BudgetAccountBETest {

    private Date dateFor(int year, int month1based, int day) {
        Calendar cal = new GregorianCalendar();
        cal.clear();
        cal.set(year, month1based - 1, day, 10, 30, 0);
        return cal.getTime();
    }

    // region constructors

    @Test
    public void nameOnlyConstructor_defaultsAvailableBudgetToMinusOne() {
        BudgetAccountBE b = new BudgetAccountBE("Groceries");
        assertEquals(-1.0f, b.indivAvailableBudget, 0.0001f);
        assertEquals(0.0f, b.indivYearlyBudget, 0.0001f);
        assertTrue(b.getDirectSubBudgets().isEmpty());
    }

    @Test
    public void yearlyBudgetConstructor_setsAvailableBudgetToOneTwelfth() {
        BudgetAccountBE b = new BudgetAccountBE("Groceries", 1200f);
        assertEquals(1200f, b.indivYearlyBudget, 0.0001f);
        assertEquals(100f, b.indivAvailableBudget, 0.0001f);
    }

    @Test
    public void fullConstructor_setsBothBudgetsExplicitly() {
        BudgetAccountBE b = new BudgetAccountBE("Groceries", 55f, 1200f);
        assertEquals(55f, b.indivAvailableBudget, 0.0001f);
        assertEquals(1200f, b.indivYearlyBudget, 0.0001f);
    }

    @Test
    public void accountBESourceConstructor_copiesTxListAndActiveState() {
        AccountBE source = new AccountBE("Legacy");
        source.setActive(false);
        source.addTx(new TxBE(10f, "x", dateFor(2024, 1, 1)));

        BudgetAccountBE b = new BudgetAccountBE(source);

        assertEquals("Legacy", b.getName());
        assertFalse(b.getIsActive());
        assertEquals(1, b.getTxList().size());
        assertTrue(b.getDirectSubBudgets().isEmpty());
    }

    // endregion

    // region sub budget tree roll-ups

    @Test
    public void getSubSum_emptyTree_returnsZero() {
        BudgetAccountBE root = new BudgetAccountBE("Root");
        assertEquals(0f, root.getSubSum(), 0.0001f);
        assertEquals(0f, root.getSubSum("2024-01"), 0.0001f);
    }

    @Test
    public void getSubSum_multiLevelTree_sumsAllDescendants() {
        BudgetAccountBE root = new BudgetAccountBE("Root");
        BudgetAccountBE child = new BudgetAccountBE("Child");
        BudgetAccountBE grandchild = new BudgetAccountBE("Grandchild");

        root.addSubBudget(child);
        child.addSubBudget(grandchild);

        child.addTx(new TxBE(50f, "c", dateFor(2024, 1, 1)));
        grandchild.addTx(new TxBE(20f, "g", dateFor(2024, 1, 1)));

        assertEquals(70f, root.getSubSum(), 0.0001f);
        assertEquals(20f, child.getSubSum(), 0.0001f);
    }

    @Test
    public void getTotalSum_includesOwnAndSubBudgetSums() {
        BudgetAccountBE root = new BudgetAccountBE("Root");
        BudgetAccountBE child = new BudgetAccountBE("Child");
        root.addSubBudget(child);

        root.addTx(new TxBE(10f, "r", dateFor(2024, 1, 1)));
        child.addTx(new TxBE(5f, "c", dateFor(2024, 1, 1)));

        assertEquals(15f, root.getTotalSum(), 0.0001f);
    }

    @Test
    public void getTotalSumWithPeriod_filtersBothOwnAndSubBudgets() {
        BudgetAccountBE root = new BudgetAccountBE("Root");
        BudgetAccountBE child = new BudgetAccountBE("Child");
        root.addSubBudget(child);

        root.addTx(new TxBE(10f, "r-jan", dateFor(2024, 1, 1)));
        root.addTx(new TxBE(100f, "r-feb", dateFor(2024, 2, 1)));
        child.addTx(new TxBE(5f, "c-jan", dateFor(2024, 1, 1)));

        assertEquals(15f, root.getTotalSum("2024-01"), 0.0001f);
        assertEquals(100f, root.getTotalSum("2024-02"), 0.0001f);
    }

    @Test
    public void getDirectSubBudgets_returnsOnlyImmediateChildren() {
        BudgetAccountBE root = new BudgetAccountBE("Root");
        BudgetAccountBE child = new BudgetAccountBE("Child");
        BudgetAccountBE grandchild = new BudgetAccountBE("Grandchild");
        root.addSubBudget(child);
        child.addSubBudget(grandchild);

        assertEquals(1, root.getDirectSubBudgets().size());
        assertEquals(child, root.getDirectSubBudgets().get(0));
    }

    @Test
    public void getAllSubBudgets_returnsEntireDescendantTree() {
        BudgetAccountBE root = new BudgetAccountBE("Root");
        BudgetAccountBE child = new BudgetAccountBE("Child");
        BudgetAccountBE grandchild = new BudgetAccountBE("Grandchild");
        root.addSubBudget(child);
        child.addSubBudget(grandchild);

        List<BudgetAccountBE> all = root.getAllSubBudgets();
        assertEquals(2, all.size());
        assertTrue(all.contains(child));
        assertTrue(all.contains(grandchild));
    }

    @Test
    public void getAllSubBudgets_emptyTree_returnsEmptyList() {
        BudgetAccountBE root = new BudgetAccountBE("Root");
        assertTrue(root.getAllSubBudgets().isEmpty());
    }

    @Test
    public void getTotalYearlyBudget_sumsIndivAndSubYearlyBudgets() {
        BudgetAccountBE root = new BudgetAccountBE("Root", 1200f);
        BudgetAccountBE child = new BudgetAccountBE("Child", 600f);
        root.addSubBudget(child);

        assertEquals(1800f, root.getTotalYearlyBudget(), 0.0001f);
    }

    @Test
    public void getTotalAvailableBudget_sumsIndivAndSubAvailableBudgets() {
        BudgetAccountBE root = new BudgetAccountBE("Root", 100f, 1200f);
        BudgetAccountBE child = new BudgetAccountBE("Child", 50f, 600f);
        root.addSubBudget(child);

        assertEquals(150f, root.getTotalAvailableBudget(), 0.0001f);
    }

    @Test
    public void getSubBudgetParent_directChild_returnsParent() {
        BudgetAccountBE root = new BudgetAccountBE("Root");
        BudgetAccountBE child = new BudgetAccountBE("Child");
        root.addSubBudget(child);

        assertEquals(root, root.getSubBudgetParent(child));
    }

    @Test
    public void getSubBudgetParent_deepDescendant_returnsImmediateParent() {
        BudgetAccountBE root = new BudgetAccountBE("Root");
        BudgetAccountBE child = new BudgetAccountBE("Child");
        BudgetAccountBE grandchild = new BudgetAccountBE("Grandchild");
        root.addSubBudget(child);
        child.addSubBudget(grandchild);

        assertEquals(child, root.getSubBudgetParent(grandchild));
    }

    @Test
    public void getSubBudgetParent_notPresent_returnsNull() {
        BudgetAccountBE root = new BudgetAccountBE("Root");
        BudgetAccountBE unrelated = new BudgetAccountBE("Unrelated");

        assertNull(root.getSubBudgetParent(unrelated));
    }

    // endregion

    // region mean allotted budget

    @Test
    public void getMeanAllottedIndivBudget_scalesByRenewalPeriodOverTwelve() {
        BudgetAccountBE b = new BudgetAccountBE("Root", 1200f);
        b.setRenewalPeriod(3);
        assertEquals(300f, b.getMeanAllottedIndivBudget(), 0.0001f);
    }

    @Test
    public void getMeanAllottedTotalBudget_includesSubBudgets() {
        BudgetAccountBE root = new BudgetAccountBE("Root", 1200f);
        root.setRenewalPeriod(1);
        BudgetAccountBE child = new BudgetAccountBE("Child", 600f);
        child.setRenewalPeriod(1);
        root.addSubBudget(child);

        assertEquals(100f + 50f, root.getMeanAllottedTotalBudget(), 0.0001f);
    }

    // endregion

    // region incrementRenewalPeriod

    @Test
    public void incrementRenewalPeriod_withinSameYear_incrementsMonthOnly() throws Exception {
        BudgetAccountBE b = new BudgetAccountBE("Root");
        b.setRenewalPeriod(1);
        b.setNextRenewal("2024-05");

        b.incrementRenewalPeriod();

        assertEquals("2024-06", b.getNextRenewal());
    }

    @Test
    public void incrementRenewalPeriod_monthTwelveToOne_rollsOverYear() throws Exception {
        BudgetAccountBE b = new BudgetAccountBE("Root");
        b.setRenewalPeriod(1);
        b.setNextRenewal("2024-12");

        b.incrementRenewalPeriod();

        assertEquals("2025-01", b.getNextRenewal());
    }

    @Test
    public void incrementRenewalPeriod_multiMonthPeriod_canRollOverMultipleYears() throws Exception {
        BudgetAccountBE b = new BudgetAccountBE("Root");
        b.setRenewalPeriod(15); // more than a year
        b.setNextRenewal("2024-01");

        b.incrementRenewalPeriod();

        // 2024-01 + 15 months = 2025-04
        assertEquals("2025-04", b.getNextRenewal());
    }

    // endregion

    // region tryRenew / reset

    @Test
    public void tryRenew_periodNotYetDue_leavesStateUnchanged() throws Exception {
        BudgetAccountBE b = new BudgetAccountBE("Root", 100f, 1200f);
        b.setRenewalPeriod(1);
        b.setNextRenewal("2050-12"); // far future, never due in practice
        b.addTx(new TxBE(20f, "spend", dateFor(2024, 1, 1)));

        b.tryRenew();

        assertEquals(100f, b.indivAvailableBudget, 0.0001f);
        assertEquals("2050-12", b.getNextRenewal());
        assertEquals(1, b.getTxList().size());
    }

    @Test
    public void tryRenew_periodDue_rollsBudgetForwardAndClearsTxList() throws Exception {
        BudgetAccountBE b = new BudgetAccountBE("Root", 100f, 1200f);
        b.setRenewalPeriod(1);
        b.setNextRenewal("2000-01"); // far past, guaranteed due
        b.addTx(new TxBE(40f, "spend", dateFor(2024, 1, 1)));

        b.tryRenew();

        // new available = old available + meanAllotted - sum(txList before reset)
        // meanAllotted = 1200 * 1 / 12 = 100
        assertEquals(100f + 100f - 40f, b.indivAvailableBudget, 0.0001f);
        assertEquals("2000-02", b.getNextRenewal());
        assertTrue(b.getTxList().isEmpty());
    }

    @Test
    public void tryRenew_recursesIntoSubBudgetsRegardlessOfOwnDueState() throws Exception {
        BudgetAccountBE root = new BudgetAccountBE("Root", 100f, 1200f);
        root.setRenewalPeriod(1);
        root.setNextRenewal("2050-12"); // not due

        BudgetAccountBE child = new BudgetAccountBE("Child", 10f, 120f);
        child.setRenewalPeriod(1);
        child.setNextRenewal("2000-01"); // due
        child.addTx(new TxBE(5f, "spend", dateFor(2024, 1, 1)));
        root.addSubBudget(child);

        root.tryRenew();

        // root unchanged
        assertEquals(100f, root.indivAvailableBudget, 0.0001f);
        // child renewed: meanAllotted = 120/12 = 10; new available = 10 + 10 - 5 = 15
        assertEquals(15f, child.indivAvailableBudget, 0.0001f);
        assertTrue(child.getTxList().isEmpty());
        assertEquals("2000-02", child.getNextRenewal());
    }

    @Test(expected = NullPointerException.class)
    public void tryRenew_withoutEverSettingNextRenewal_throwsNullPointerException() {
        // Characterizes CURRENT (surprising) behavior: a freshly constructed BudgetAccountBE
        // has nextRenewal == null. tryRenew() only catches IllegalArgumentException, but
        // Util.isAfter(null, ...) -> Util.validatePeriod(null) throws NullPointerException
        // (Pattern.matcher(null).matches() dereferences a null CharSequence), which is NOT an
        // IllegalArgumentException and therefore propagates out of tryRenew() uncaught.
        BudgetAccountBE b = new BudgetAccountBE("Root", 100f, 1200f);
        b.tryRenew();
    }

    @Test
    public void reset_recomputesIndivAvailableBudgetAndClearsTxList() {
        BudgetAccountBE root = new BudgetAccountBE("Root", 1200f);
        root.setRenewalPeriod(1);
        root.addTx(new TxBE(999f, "spend", dateFor(2024, 1, 1)));

        BudgetAccountBE child = new BudgetAccountBE("Child", 600f);
        child.setRenewalPeriod(1);
        child.addTx(new TxBE(50f, "spend", dateFor(2024, 1, 1)));
        root.addSubBudget(child);

        root.reset();

        assertEquals(100f, root.indivAvailableBudget, 0.0001f); // 1200/12
        assertTrue(root.getTxList().isEmpty());
        assertEquals(50f, child.indivAvailableBudget, 0.0001f); // 600/12
        assertTrue(child.getTxList().isEmpty());
    }

    // endregion

    // region transferSubBudget

    @Test
    public void transferSubBudget_movesChildFromSourceToTarget() {
        BudgetAccountBE source = new BudgetAccountBE("Source");
        BudgetAccountBE target = new BudgetAccountBE("Target");
        BudgetAccountBE child = new BudgetAccountBE("Child");
        source.addSubBudget(child);

        boolean result = source.transferSubBudget(child, target);

        assertTrue(result);
        assertTrue(source.getDirectSubBudgets().isEmpty());
        assertEquals(1, target.getDirectSubBudgets().size());
        assertEquals(child, target.getDirectSubBudgets().get(0));
    }

    @Test
    public void transferSubBudget_childNotPresentInSource_returnsFalseAndDoesNotModifyTarget() {
        BudgetAccountBE source = new BudgetAccountBE("Source");
        BudgetAccountBE target = new BudgetAccountBE("Target");
        BudgetAccountBE child = new BudgetAccountBE("Child"); // never added to source

        boolean result = source.transferSubBudget(child, target);

        assertFalse(result);
        assertTrue(target.getDirectSubBudgets().isEmpty());
    }

    // endregion
}
