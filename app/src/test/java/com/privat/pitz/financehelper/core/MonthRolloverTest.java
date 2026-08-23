package com.privat.pitz.financehelper.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONException;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;
import com.privat.pitz.financehelper.data.ProjectBudgetBE;
import com.privat.pitz.financehelper.data.RecurringTxBE;
import com.privat.pitz.financehelper.data.TxBE;

/**
 * End-to-end coverage for {@link EntityService#initiateNewPeriod()}, the month rollover.
 *
 * <p>This is the most destructive path in the app - it closes every account, carries balances
 * into a new period, renews budgets and fires every recurring order - and it had no test of its
 * own at all. When it goes wrong it goes wrong across the whole save file.
 *
 * <p>The tests run against the real current date rather than a frozen one, because the production
 * code reads {@link Calendar#getInstance()} directly in several places. Fixtures are therefore
 * built with {@link Const#getLastMonthFileName}, so "the previous period" is whatever it actually
 * is when the suite runs - including across a real year boundary.
 */
public class MonthRolloverTest {

    private static final float DELTA = 0.001f;
    private static final String ENTITY = "User";

    private final String previousFile = Const.getLastMonthFileName(ENTITY);
    private final String currentFile = Const.getCurrentMonthFileName(ENTITY);

    private static Date date(int daysAgo) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -daysAgo);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    /** "YYYY-MM" for the present period shifted by {@code monthsAhead}. */
    private static String period(int monthsAhead) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, monthsAhead);
        return String.format(Locale.US, "%04d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
    }

    /** A controller holding the previous period's model, not yet written anywhere. */
    private Controller previousPeriod(InMemorySavefileStorage storage) {
        Controller controller = new Controller(storage);
        controller.getModel().currentEntity = ENTITY;
        controller.getModel().currentFileName = previousFile;
        return controller;
    }

    private void writePreviousPeriod(Controller controller) throws JSONException, IOException {
        controller.saveAccountsToInternal(previousFile);
    }

    /** Rolls over from whatever is in storage, exactly as the app does on first start of a month. */
    private Controller rollOver(InMemorySavefileStorage storage) throws JSONException, IOException {
        Controller controller = new Controller(storage);
        controller.getModel().currentEntity = ENTITY;
        controller.initiateNewPeriod();
        return controller;
    }

    // region balances carry forward

    @Test
    public void assetBalances_carryForwardAsASingleOpeningEntry()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = previousPeriod(storage);
        AccountBE giro = new AccountBE("Girokonto");
        giro.addTx(new TxBE(1000f, "Gehalt", date(20)));
        giro.addTx(new TxBE(-250.50f, "Miete", date(18)));
        giro.addTx(new TxBE(-99.50f, "Einkauf", date(5)));
        writer.getModel().asset_accounts.add(giro);
        writePreviousPeriod(writer);

        Model rolled = rollOver(storage).getModel();

        AccountBE carried = rolled.getAssetAccountByName("Girokonto");
        assertNotNull(carried);
        assertEquals("the closing balance must survive the rollover exactly",
                650f, carried.getSum(), DELTA);
        assertEquals("last period's individual entries must not be carried over one by one",
                1, carried.getTxList().size());
        assertEquals(Const.DESC_OPENING, carried.getTxList().get(0).getDescription());
    }

    @Test
    public void assetAccountWithAutoRenewOff_keepsItsTransactions()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = previousPeriod(storage);
        AccountBE giro = new AccountBE("Bargeld");
        giro.setAutoRenew(false);
        giro.addTx(new TxBE(50f, "Abhebung", date(10)));
        giro.addTx(new TxBE(-20f, "Kaffee", date(4)));
        writer.getModel().asset_accounts.add(giro);
        writePreviousPeriod(writer);

        Model rolled = rollOver(storage).getModel();

        AccountBE carried = rolled.getAssetAccountByName("Bargeld");
        assertEquals("auto-renew off means the account is left exactly as it was",
                2, carried.getTxList().size());
        assertEquals(30f, carried.getSum(), DELTA);
    }

    // endregion

    // region budget renewal

    @Test
    public void budgetDueForRenewal_clearsTransactionsAndRollsTheBudgetForward()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = previousPeriod(storage);
        BudgetAccountBE lebensmittel = new BudgetAccountBE("Lebensmittel", 100f, 1200f);
        lebensmittel.setNextRenewal(period(0));  // due: not after the present period
        // spending on a budget account is stored POSITIVE - the matching negative sits on the
        // asset account that paid for it, which is what makes the two sides net to zero
        lebensmittel.addTx(new TxBE(30f, "Markt", date(6)));
        writer.getModel().budget_accounts.add(lebensmittel);
        writePreviousPeriod(writer);

        Model rolled = rollOver(storage).getModel();

        BudgetAccountBE carried = rolled.getBudgetAccountByName("Lebensmittel");
        assertNotNull(carried);
        assertTrue("a renewed budget starts the period empty", carried.getTxList().isEmpty());
        // 100 carried + 100 monthly allotment - 30 spent
        assertEquals(170f, carried.indivAvailableBudget, DELTA);
        assertEquals("the next renewal must advance", period(1), carried.getNextRenewal());
    }

    @Test
    public void budgetNotYetDue_isLeftAlone() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = previousPeriod(storage);
        BudgetAccountBE quartal = new BudgetAccountBE("Versicherung", 300f, 1200f);
        quartal.setRenewalPeriod(3);
        quartal.setNextRenewal(period(2));  // still in the future
        quartal.addTx(new TxBE(40f, "Beitrag", date(9)));
        writer.getModel().budget_accounts.add(quartal);
        writePreviousPeriod(writer);

        Model rolled = rollOver(storage).getModel();

        BudgetAccountBE carried = rolled.getBudgetAccountByName("Versicherung");
        assertEquals("a budget that is not due keeps its running transactions",
                1, carried.getTxList().size());
        assertEquals(300f, carried.indivAvailableBudget, DELTA);
        assertEquals(period(2), carried.getNextRenewal());
    }

    @Test
    public void multiMonthBudget_advancesByItsFullRenewalPeriod()
            throws JSONException, IOException {
        // a quarterly budget must jump three months, not one - including across a year boundary,
        // which is why the expected value is computed rather than written out
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = previousPeriod(storage);
        BudgetAccountBE quartal = new BudgetAccountBE("Versicherung", 300f, 1200f);
        quartal.setRenewalPeriod(3);
        quartal.setNextRenewal(period(0));  // due now
        writer.getModel().budget_accounts.add(quartal);
        writePreviousPeriod(writer);

        Model rolled = rollOver(storage).getModel();

        BudgetAccountBE carried = rolled.getBudgetAccountByName("Versicherung");
        assertEquals(period(3), carried.getNextRenewal());
        assertEquals(3, carried.getRenewalPeriod());
        // 300 carried + (1200 * 3 / 12) allotted - 0 spent
        assertEquals(600f, carried.indivAvailableBudget, DELTA);
    }

    @Test
    public void subBudgets_renewAtEveryLevel() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = previousPeriod(storage);
        BudgetAccountBE root = new BudgetAccountBE("Haushalt", 100f, 1200f);
        root.setNextRenewal(period(0));
        BudgetAccountBE level1 = new BudgetAccountBE("Lebensmittel", 50f, 600f);
        level1.setNextRenewal(period(0));
        level1.addTx(new TxBE(10f, "Markt", date(3)));
        BudgetAccountBE level2 = new BudgetAccountBE("Wocheneinkauf", 25f, 300f);
        level2.setNextRenewal(period(0));
        level2.addTx(new TxBE(5f, "Bäcker", date(2)));
        level1.addSubBudget(level2);
        root.addSubBudget(level1);
        writer.getModel().budget_accounts.add(root);
        writePreviousPeriod(writer);

        Model rolled = rollOver(storage).getModel();

        BudgetAccountBE carriedLevel2 = rolled.getBudgetAccountByName("Wocheneinkauf");
        assertNotNull("a 2nd-level sub-budget must survive the rollover", carriedLevel2);
        assertTrue("and must renew too", carriedLevel2.getTxList().isEmpty());
        assertEquals(period(1), carriedLevel2.getNextRenewal());
        // 25 carried + 25 monthly allotment - 5 spent
        assertEquals(45f, carriedLevel2.indivAvailableBudget, DELTA);
    }

    @Test
    public void projectBudget_isExcludedFromRenewal() throws JSONException, IOException {
        // a project budget runs to completion rather than per month: tryRenew is a deliberate
        // no-op, so its transactions and its remaining budget must both survive untouched
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = previousPeriod(storage);
        ProjectBudgetBE kueche = new ProjectBudgetBE("Küche", 5000f);
        kueche.addTx(new TxBE(1200f, "Fliesen", date(12)));
        kueche.addTx(new TxBE(800f, "Arbeitsplatte", date(7)));
        writer.getModel().budget_accounts.add(kueche);
        writePreviousPeriod(writer);

        Model rolled = rollOver(storage).getModel();

        BudgetAccountBE carried = rolled.getBudgetAccountByName("Küche");
        assertNotNull(carried);
        assertTrue(carried instanceof ProjectBudgetBE);
        assertEquals("a project budget must not be cleared by a month rollover",
                2, carried.getTxList().size());
        assertEquals(2000f, carried.getSum(), DELTA);
        assertEquals(5000f, carried.indivAvailableBudget, DELTA);
    }

    // endregion

    // region recurring transactions

    @Test
    public void recurringOrder_firesExactlyOnce() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = previousPeriod(storage);
        writer.getModel().asset_accounts.add(new AccountBE("Girokonto"));
        BudgetAccountBE wohnen = new BudgetAccountBE("Wohnen", 0f, 0f);
        wohnen.setNextRenewal(period(1));  // not due, so its tx list is not cleared afterwards
        writer.getModel().budget_accounts.add(wohnen);
        writer.getModel().recurringTx.add(new RecurringTxBE(
                800f, "Miete", date(30), "Girokonto", "Wohnen"));
        writePreviousPeriod(writer);

        Model rolled = rollOver(storage).getModel();

        AccountBE giro = rolled.getAssetAccountByName("Girokonto");
        BudgetAccountBE carriedWohnen = rolled.getBudgetAccountByName("Wohnen");
        // the opening entry plus exactly one booking of the order
        assertEquals(2, giro.getTxList().size());
        assertEquals(-800f, giro.getSum(), DELTA);
        assertEquals(1, carriedWohnen.getTxList().size());
        assertEquals(800f, carriedWohnen.getSum(), DELTA);
        assertEquals("the order itself must survive for next month",
                1, rolled.recurringTx.size());
    }

    @Test
    public void recurringIncome_isBookedAndDoesNotCrashTheRollover()
            throws JSONException, IOException {
        // an empty sender means "recurring income". Its guard was written with `assert`, which is
        // a no-op on Android, so this case used to fall through into a NullPointerException and
        // crash the rollover every single month.
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = previousPeriod(storage);
        writer.getModel().asset_accounts.add(new AccountBE("Girokonto"));
        writer.getModel().recurringTx.add(new RecurringTxBE(
                2000f, "Gehalt", date(30), "", "Girokonto"));
        writePreviousPeriod(writer);

        Model rolled = rollOver(storage).getModel();

        assertEquals(2000f, rolled.getAssetAccountByName("Girokonto").getSum(), DELTA);
        assertEquals("the income list is reset first, then the recurring income is booked into it",
                1, rolled.currentIncome.size());
        assertEquals("Gehalt", rolled.currentIncome.get(0).getDescription());
    }

    @Test
    public void recurringOrderReferencingADeletedAccount_isSkippedWithoutCrashing()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = previousPeriod(storage);
        AccountBE giro = new AccountBE("Girokonto");
        giro.addTx(new TxBE(500f, "Gehalt", date(25)));
        writer.getModel().asset_accounts.add(giro);
        writer.getModel().recurringTx.add(new RecurringTxBE(
                100f, "Sparrate", date(30), "Girokonto", "SeitdemGelöscht"));
        writePreviousPeriod(writer);

        Model rolled = rollOver(storage).getModel();

        AccountBE carried = rolled.getAssetAccountByName("Girokonto");
        assertEquals("the order is skipped whole - no half-booked transfer",
                1, carried.getTxList().size());
        assertEquals(500f, carried.getSum(), DELTA);
    }

    // endregion

    // region file handling

    @Test
    public void incomeList_isReset() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = previousPeriod(storage);
        AccountBE giro = new AccountBE("Girokonto");
        TxBE income = new TxBE(1500f, "Gehalt", date(28));
        giro.addTx(income);
        writer.getModel().asset_accounts.add(giro);
        writer.getModel().currentIncome.add(income);
        writePreviousPeriod(writer);

        Model rolled = rollOver(storage).getModel();

        assertTrue("last period's income must not count towards the new one",
                rolled.currentIncome.isEmpty());
    }

    @Test
    public void previousPeriodFile_isLeftByteForByteIntact() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = previousPeriod(storage);
        AccountBE giro = new AccountBE("Girokonto");
        giro.addTx(new TxBE(1000f, "Gehalt", date(20)));
        writer.getModel().asset_accounts.add(giro);
        BudgetAccountBE lebensmittel = new BudgetAccountBE("Lebensmittel", 100f, 1200f);
        lebensmittel.setNextRenewal(period(0));
        writer.getModel().budget_accounts.add(lebensmittel);
        writePreviousPeriod(writer);
        String before = storage.read(previousFile);

        rollOver(storage);

        assertEquals("the closed period is the historical record and must never be rewritten",
                before, storage.read(previousFile));
    }

    @Test
    public void newPeriodFile_isWrittenAndBecomesTheCurrentFile()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = previousPeriod(storage);
        writer.getModel().asset_accounts.add(new AccountBE("Girokonto"));
        writePreviousPeriod(writer);

        Controller rolled = rollOver(storage);

        assertTrue("the new period must be persisted, not just held in memory",
                storage.exists(currentFile));
        assertEquals(currentFile, rolled.getModel().currentFileName);
    }

    @Test
    public void rollover_isRefusedWhenTheCurrentPeriodAlreadyExists()
            throws JSONException, IOException {
        // otherwise a second rollover would close and clear the month already in progress
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = previousPeriod(storage);
        writer.getModel().asset_accounts.add(new AccountBE("Girokonto"));
        writePreviousPeriod(writer);
        writer.saveAccountsToInternal(currentFile);

        try {
            rollOver(storage);
            fail("expected the rollover to refuse to run over an existing current period");
        } catch (FileNotFoundException expected) {
            // the current month is already open - nothing to roll over
        }
    }

    @Test
    public void rollover_withNoSaveFilesAtAll_reportsRatherThanCrashing() {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        try {
            rollOver(storage);
            fail("expected a FileNotFoundException when there is nothing to roll over from");
        } catch (FileNotFoundException expected) {
            // nothing to carry forward
        } catch (JSONException | IOException e) {
            fail("expected FileNotFoundException, got: " + e);
        }
    }

    // endregion

    // region the whole thing at once

    @Test
    public void fullRollover_carriesEverythingCorrectlyInOnePass()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = previousPeriod(storage);

        AccountBE giro = new AccountBE("Girokonto");
        giro.addTx(new TxBE(2000f, "Gehalt", date(28)));
        giro.addTx(new TxBE(-450f, "Miete", date(27)));
        AccountBE bargeld = new AccountBE("Bargeld");
        bargeld.setAutoRenew(false);
        bargeld.addTx(new TxBE(100f, "Abhebung", date(15)));
        writer.getModel().asset_accounts.add(giro);
        writer.getModel().asset_accounts.add(bargeld);

        BudgetAccountBE lebensmittel = new BudgetAccountBE("Lebensmittel", 100f, 1200f);
        lebensmittel.setNextRenewal(period(0));
        lebensmittel.addTx(new TxBE(80f, "Markt", date(10)));
        BudgetAccountBE wocheneinkauf = new BudgetAccountBE("Wocheneinkauf", 40f, 480f);
        wocheneinkauf.setNextRenewal(period(0));
        lebensmittel.addSubBudget(wocheneinkauf);
        ProjectBudgetBE kueche = new ProjectBudgetBE("Küche", 5000f);
        kueche.addTx(new TxBE(1200f, "Fliesen", date(12)));
        writer.getModel().budget_accounts.add(lebensmittel);
        writer.getModel().budget_accounts.add(kueche);

        writer.getModel().recurringTx.add(new RecurringTxBE(
                1800f, "Gehalt", date(30), "", "Girokonto"));
        writer.getModel().currentIncome.add(new TxBE(2000f, "Gehalt", date(28)));
        writePreviousPeriod(writer);

        Model rolled = rollOver(storage).getModel();

        // asset accounts: balance carried, plus the recurring income booked on top
        assertEquals(1550f + 1800f, rolled.getAssetAccountByName("Girokonto").getSum(), DELTA);
        assertEquals(100f, rolled.getAssetAccountByName("Bargeld").getSum(), DELTA);
        assertEquals(1, rolled.getAssetAccountByName("Bargeld").getTxList().size());

        // budgets renewed, project budget untouched
        BudgetAccountBE carriedLebensmittel = rolled.getBudgetAccountByName("Lebensmittel");
        assertTrue(carriedLebensmittel.getTxList().isEmpty());
        assertEquals(120f, carriedLebensmittel.indivAvailableBudget, DELTA);
        assertEquals(1, carriedLebensmittel.getDirectSubBudgets().size());
        assertEquals(1200f, rolled.getBudgetAccountByName("Küche").getSum(), DELTA);

        // income reset, then the recurring income recorded
        assertEquals(1, rolled.currentIncome.size());
        assertEquals(1800f, rolled.currentIncome.get(0).getAmount(), DELTA);

        // and the whole thing survives being written and read back
        Controller reader = new Controller(storage);
        reader.readAccountsFromInternal(currentFile);
        assertFalse("the rolled-over file must load cleanly",
                reader.getModel().takeLoadReport().hasDiscards());
        assertEquals(3350f, reader.getModel().getAssetAccountByName("Girokonto").getSum(), DELTA);
        assertNotNull(reader.getModel().getBudgetAccountByName("Wocheneinkauf"));
    }

    // endregion
}
