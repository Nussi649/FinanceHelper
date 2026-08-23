package com.privat.pitz.financehelper.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class ProjectBudgetBETest {

    private Date dateFor(int year, int month1based, int day) {
        Calendar cal = new GregorianCalendar();
        cal.clear();
        cal.set(year, month1based - 1, day, 10, 30, 0);
        return cal.getTime();
    }

    @Test
    public void constructor_setsYearlyAndAvailableBudgetToTotalBudget() {
        ProjectBudgetBE p = new ProjectBudgetBE("Renovation", 5000f);

        assertEquals(5000f, p.indivYearlyBudget, 0.0001f);
        assertEquals(5000f, p.indivAvailableBudget, 0.0001f);
    }

    @Test
    public void accountBESourceConstructor_copiesTxListButLeavesBudgetsAtDefault() {
        AccountBE source = new AccountBE("Legacy");
        source.addTx(new TxBE(10f, "x", dateFor(2024, 1, 1)));

        ProjectBudgetBE p = new ProjectBudgetBE(source);

        assertEquals(1, p.getTxList().size());
        // BudgetAccountBE(AccountBE) does not set indivYearlyBudget/indivAvailableBudget explicitly
        assertEquals(0f, p.indivYearlyBudget, 0.0001f);
        assertEquals(-1.0f, p.indivAvailableBudget, 0.0001f);
    }

    @Test
    public void reset_clearsTxListOnly_doesNotRecomputeAvailableBudget() {
        // Characterizes current behavior: unlike BudgetAccountBE.reset(), ProjectBudgetBE.reset()
        // does NOT call super.reset() - it does not touch indivAvailableBudget and does not
        // recurse into sub budgets.
        ProjectBudgetBE p = new ProjectBudgetBE("Renovation", 5000f);
        p.addTx(new TxBE(-1200f, "spend", dateFor(2024, 1, 1)));
        p.indivAvailableBudget = 3800f;

        BudgetAccountBE child = new BudgetAccountBE("Child", 100f);
        child.addTx(new TxBE(-10f, "spend", dateFor(2024, 1, 1)));
        p.addSubBudget(child);

        p.reset();

        assertTrue(p.getTxList().isEmpty());
        // available budget is left as-is, NOT recomputed like the parent class would do
        assertEquals(3800f, p.indivAvailableBudget, 0.0001f);
        // sub budget is NOT reset (still has its transaction)
        assertFalse(child.getTxList().isEmpty());
    }

    @Test
    public void tryRenew_isNoOp() {
        ProjectBudgetBE p = new ProjectBudgetBE("Renovation", 5000f);
        p.addTx(new TxBE(-100f, "spend", dateFor(2024, 1, 1)));
        float availableBefore = p.indivAvailableBudget;
        int txCountBefore = p.getTxList().size();

        p.tryRenew();

        assertEquals(availableBefore, p.indivAvailableBudget, 0.0001f);
        assertEquals(txCountBefore, p.getTxList().size());
    }

    @Test
    public void setAutoRenew_alwaysForcesFalse() {
        ProjectBudgetBE p = new ProjectBudgetBE("Renovation", 5000f);

        p.setAutoRenew(true);

        assertFalse(p.getAutoRenew());
    }

    @Test
    public void renewalConfigurationSetters_areNoOpsAndDoNotThrow() throws Exception {
        ProjectBudgetBE p = new ProjectBudgetBE("Renovation", 5000f);

        p.setRenewalPeriod(3);
        p.setNextRenewal("2024-05");
        p.incrementRenewalPeriod();

        assertEquals(1, p.getRenewalPeriod()); // default value from BudgetAccountBE, never changed
        assertEquals(null, p.getNextRenewal()); // never set, incrementRenewalPeriod() is also a no-op
    }

    @Test
    public void adjustIndivYearlyBudget_alsoResetsAvailableBudgetToNewYearlyBudget() {
        // Characterizes current behavior: adjusting the yearly budget on a project also
        // overwrites indivAvailableBudget with the new yearly budget, discarding whatever was
        // already spent/available.
        ProjectBudgetBE p = new ProjectBudgetBE("Renovation", 5000f);
        p.indivAvailableBudget = 1200f; // simulate partial spend

        p.adjustIndivYearlyBudget(500f);

        assertEquals(5500f, p.indivYearlyBudget, 0.0001f);
        assertEquals(5500f, p.indivAvailableBudget, 0.0001f);
    }

    @Test
    public void getMeanAllottedIndivBudget_returnsYearlyBudgetDirectly() {
        ProjectBudgetBE p = new ProjectBudgetBE("Renovation", 5000f);
        assertEquals(5000f, p.getMeanAllottedIndivBudget(), 0.0001f);
    }
}
