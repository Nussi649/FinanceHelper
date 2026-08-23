package com.privat.pitz.financehelper.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;
import com.privat.pitz.financehelper.data.ProjectBudgetBE;
import com.privat.pitz.financehelper.data.RecurringTxBE;
import com.privat.pitz.financehelper.data.TxBE;

/**
 * Field-level save-file round-trip coverage for every persisted type.
 *
 * <p>The rule each test follows: set one field to a distinctive value, serialise, parse the result
 * back, and assert the value survived. This is deliberately exhaustive rather than interesting -
 * the budget-account data-loss bug was a single field that serialised to nothing and was then
 * treated as fatal on read, and nothing structural stops that from happening to another field.
 *
 * <p>Two limits of the format are asserted rather than worked around, so that a future change to
 * either is a deliberate one: {@link Util#formatFloatSave} keeps two decimal places, and
 * {@link Const#DATE_FORMAT_SAVE} keeps minutes - seconds and milliseconds are dropped.
 */
public class SaveFileRoundTripTest {

    private static final float DELTA = 0.001f;
    private static final String FILE = "2026-08-User.jso";

    /** A date with zero seconds/millis, so the minute-resolution save format is lossless for it. */
    private static Date date(int year, int month, int day, int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day, hour, minute, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private static TxBE tx() {
        return new TxBE(-12.34f, "Einkauf", date(2026, 8, 10, 14, 30));
    }

    private static AccountBE roundTrip(AccountBE account) {
        return Util.parseJSON_Account(Util.serialise_Account(account));
    }

    private static BudgetAccountBE roundTrip(BudgetAccountBE account) {
        return Util.parseJSON_BudgetAccount(Util.serialise_BudgetAccount(account));
    }

    private static TxBE roundTrip(TxBE entry) {
        return Util.parseJSON_Entry(Util.serialise_Entry(entry));
    }

    private static RecurringTxBE roundTrip(RecurringTxBE order) {
        return Util.parseJSON_RecurringOrder(Util.serialise_RecurringOrder(order));
    }

    private static Model.Settings roundTrip(Model.Settings settings) throws JSONException {
        return Util.parseJSON_Settings(Util.serialise_Settings(settings));
    }

    // region TxBE

    @Test
    public void txAmount_survivesRoundTrip() {
        TxBE parsed = roundTrip(new TxBE(-1234.56f, "d", date(2026, 8, 10, 14, 30)));
        assertNotNull(parsed);
        assertEquals(-1234.56f, parsed.getAmount(), DELTA);
    }

    @Test
    public void txAmount_keepsTwoDecimalPlaces() {
        // documents the precision the format actually offers, so a change to it is deliberate
        TxBE parsed = roundTrip(new TxBE(1.005f, "d", date(2026, 8, 10, 14, 30)));
        assertNotNull(parsed);
        assertEquals(1.0f, parsed.getAmount(), 0.011f);
    }

    @Test
    public void txDescription_survivesRoundTrip() {
        TxBE parsed = roundTrip(new TxBE(1f, "Käse & Brot \"Bio\"", date(2026, 8, 10, 14, 30)));
        assertNotNull(parsed);
        assertEquals("Käse & Brot \"Bio\"", parsed.getDescription());
    }

    @Test
    public void txEmptyDescription_survivesRoundTrip() {
        TxBE parsed = roundTrip(new TxBE(1f, "", date(2026, 8, 10, 14, 30)));
        assertNotNull(parsed);
        assertEquals("", parsed.getDescription());
    }

    @Test
    public void txDate_survivesRoundTripToTheMinute() {
        Date when = date(2026, 12, 31, 23, 59);
        TxBE parsed = roundTrip(new TxBE(1f, "d", when));
        assertNotNull(parsed);
        assertEquals(when, parsed.getDate());
    }

    // endregion

    // region AccountBE

    @Test
    public void accountName_survivesRoundTrip() {
        AccountBE parsed = roundTrip(new AccountBE("Girokonto Süd"));
        assertNotNull(parsed);
        assertEquals("Girokonto Süd", parsed.getName());
    }

    @Test
    public void accountIsActive_survivesRoundTrip() {
        AccountBE account = new AccountBE("A");
        account.setActive(false);
        AccountBE parsed = roundTrip(account);
        assertNotNull(parsed);
        assertFalse(parsed.getIsActive());
    }

    @Test
    public void accountAutoRenew_survivesRoundTrip() {
        AccountBE account = new AccountBE("A");
        account.setAutoRenew(false);
        AccountBE parsed = roundTrip(account);
        assertNotNull(parsed);
        assertFalse(parsed.getAutoRenew());
    }

    @Test
    public void accountTxList_survivesRoundTripInOrder() {
        AccountBE account = new AccountBE("A");
        account.addTx(new TxBE(1f, "first", date(2026, 8, 1, 8, 0)));
        account.addTx(new TxBE(2f, "second", date(2026, 8, 2, 8, 0)));

        AccountBE parsed = roundTrip(account);
        assertNotNull(parsed);
        assertEquals(2, parsed.getTxList().size());
        assertEquals("first", parsed.getTxList().get(0).getDescription());
        assertEquals("second", parsed.getTxList().get(1).getDescription());
    }

    @Test
    public void accountWithNoTx_survivesRoundTrip() {
        AccountBE parsed = roundTrip(new AccountBE("A"));
        assertNotNull(parsed);
        assertTrue(parsed.getTxList().isEmpty());
    }

    // endregion

    // region BudgetAccountBE

    @Test
    public void budgetAccountName_survivesRoundTrip() {
        BudgetAccountBE parsed = roundTrip(new BudgetAccountBE("Lebensmittel"));
        assertNotNull(parsed);
        assertEquals("Lebensmittel", parsed.getName());
    }

    @Test
    public void budgetAccountIsActive_survivesRoundTrip() {
        BudgetAccountBE account = new BudgetAccountBE("B");
        account.setActive(false);
        BudgetAccountBE parsed = roundTrip(account);
        assertNotNull(parsed);
        assertFalse(parsed.getIsActive());
    }

    @Test
    public void budgetAccountAutoRenew_survivesRoundTrip() {
        // SettingsActivity lists budget accounts too (it iterates model.getAllAccounts()), so this
        // checkbox is reachable for them and its value has to survive a save/load cycle.
        BudgetAccountBE account = new BudgetAccountBE("B");
        account.setAutoRenew(false);
        BudgetAccountBE parsed = roundTrip(account);
        assertNotNull(parsed);
        assertFalse(parsed.getAutoRenew());
    }

    @Test
    public void budgetAccountYearlyBudget_survivesRoundTrip() {
        BudgetAccountBE account = new BudgetAccountBE("B");
        account.setIndivYearlyBudget(1234.56f);
        BudgetAccountBE parsed = roundTrip(account);
        assertNotNull(parsed);
        assertEquals(1234.56f, parsed.indivYearlyBudget, DELTA);
    }

    @Test
    public void budgetAccountAvailableBudget_survivesRoundTrip() {
        BudgetAccountBE account = new BudgetAccountBE("B");
        account.setIndivYearlyBudget(1200f);
        account.setIndivAvailableBudget(87.65f);
        BudgetAccountBE parsed = roundTrip(account);
        assertNotNull(parsed);
        assertEquals(87.65f, parsed.indivAvailableBudget, DELTA);
    }

    @Test
    public void budgetAccountNegativeAvailableBudget_survivesRoundTrip() {
        // an overspent budget is an ordinary state, and -1 is the sentinel for "not set" - so a
        // genuinely negative balance has to be distinguishable from it
        BudgetAccountBE account = new BudgetAccountBE("B");
        account.setIndivYearlyBudget(1200f);
        account.setIndivAvailableBudget(-42.5f);
        BudgetAccountBE parsed = roundTrip(account);
        assertNotNull(parsed);
        assertEquals(-42.5f, parsed.indivAvailableBudget, DELTA);
    }

    @Test
    public void budgetAccountRenewalPeriod_survivesRoundTrip() {
        BudgetAccountBE account = new BudgetAccountBE("B");
        account.setRenewalPeriod(3);
        BudgetAccountBE parsed = roundTrip(account);
        assertNotNull(parsed);
        assertEquals(3, parsed.getRenewalPeriod());
    }

    @Test
    public void budgetAccountNextRenewal_survivesRoundTrip() {
        BudgetAccountBE account = new BudgetAccountBE("B");
        account.setNextRenewal("2027-03");
        BudgetAccountBE parsed = roundTrip(account);
        assertNotNull(parsed);
        assertEquals("2027-03", parsed.getNextRenewal());
    }

    @Test
    public void budgetAccountOtherEntity_survivesRoundTrip() {
        BudgetAccountBE account = new BudgetAccountBE("B");
        account.setToOtherEntity("Partner");
        BudgetAccountBE parsed = roundTrip(account);
        assertNotNull(parsed);
        assertEquals("Partner", parsed.getOtherEntity());
    }

    @Test
    public void budgetAccountTxList_survivesRoundTrip() {
        BudgetAccountBE account = new BudgetAccountBE("B");
        account.addTx(tx());
        BudgetAccountBE parsed = roundTrip(account);
        assertNotNull(parsed);
        assertEquals(1, parsed.getTxList().size());
        assertEquals("Einkauf", parsed.getTxList().get(0).getDescription());
    }

    @Test
    public void budgetAccountSubBudgets_surviveThreeLevelsDeep() {
        BudgetAccountBE root = new BudgetAccountBE("Root");
        BudgetAccountBE level1 = new BudgetAccountBE("Level1");
        BudgetAccountBE level2 = new BudgetAccountBE("Level2");
        BudgetAccountBE level3 = new BudgetAccountBE("Level3");
        level3.setIndivYearlyBudget(36f);
        level3.addTx(new TxBE(-3f, "deep", date(2026, 8, 10, 14, 30)));
        level2.addSubBudget(level3);
        level1.addSubBudget(level2);
        root.addSubBudget(level1);

        BudgetAccountBE parsed = roundTrip(root);
        assertNotNull(parsed);
        assertEquals(3, parsed.getAllSubBudgets().size());

        BudgetAccountBE parsedLevel3 = parsed.getDirectSubBudgets().get(0)
                .getDirectSubBudgets().get(0)
                .getDirectSubBudgets().get(0);
        assertEquals("Level3", parsedLevel3.getName());
        assertEquals(36f, parsedLevel3.indivYearlyBudget, DELTA);
        assertEquals(-3f, parsedLevel3.getSum(), DELTA);
    }

    @Test
    public void budgetAccountWithNoSubBudgets_survivesRoundTrip() {
        BudgetAccountBE parsed = roundTrip(new BudgetAccountBE("B"));
        assertNotNull(parsed);
        assertTrue(parsed.getDirectSubBudgets().isEmpty());
    }

    // endregion

    // region ProjectBudgetBE

    @Test
    public void projectBudget_staysAProjectBudgetAcrossRoundTrip() {
        BudgetAccountBE parsed = roundTrip(new ProjectBudgetBE("Küche", 5000f));
        assertNotNull(parsed);
        assertTrue(parsed instanceof ProjectBudgetBE);
    }

    @Test
    public void projectBudgetTotalBudget_survivesRoundTrip() {
        BudgetAccountBE parsed = roundTrip(new ProjectBudgetBE("Küche", 5000f));
        assertNotNull(parsed);
        assertEquals(5000f, parsed.indivYearlyBudget, DELTA);
        assertEquals(5000f, parsed.indivAvailableBudget, DELTA);
    }

    @Test
    public void projectBudget_keepsNoRenewalConfigurationAcrossRoundTrip() {
        // ProjectBudgetBE overrides tryRenew and every renewal setter to a no-op; the invariant
        // that it carries no renewal date has to hold for a reloaded one too
        BudgetAccountBE parsed = roundTrip(new ProjectBudgetBE("Küche", 5000f));
        assertNotNull(parsed);
        assertEquals(null, parsed.getNextRenewal());
    }

    @Test
    public void projectBudgetTxAndSubBudgets_surviveRoundTrip() {
        ProjectBudgetBE project = new ProjectBudgetBE("Küche", 5000f);
        project.addTx(new TxBE(-250f, "Fliesen", date(2026, 8, 10, 14, 30)));
        project.addSubBudget(new BudgetAccountBE("Arbeitsplatte"));

        BudgetAccountBE parsed = roundTrip(project);
        assertNotNull(parsed);
        assertEquals(-250f, parsed.getSum(), DELTA);
        assertEquals(1, parsed.getDirectSubBudgets().size());
        assertEquals("Arbeitsplatte", parsed.getDirectSubBudgets().get(0).getName());
    }

    // endregion

    // region RecurringTxBE

    @Test
    public void recurringOrderFields_surviveRoundTrip() {
        Date when = date(2026, 8, 1, 6, 15);
        RecurringTxBE parsed = roundTrip(
                new RecurringTxBE(-49.99f, "Miete", when, "Girokonto", "Wohnen"));
        assertNotNull(parsed);
        assertEquals(-49.99f, parsed.getAmount(), DELTA);
        assertEquals("Miete", parsed.getDescription());
        assertEquals(when, parsed.getDate());
        assertEquals("Girokonto", parsed.getSenderStr());
        assertEquals("Wohnen", parsed.getReceiverStr());
    }

    @Test
    public void recurringIncome_keepsItsEmptySender() {
        // an empty sender is how a recurring *income* is represented - if it round-tripped to
        // something else the rollover would book it as a transfer
        RecurringTxBE parsed = roundTrip(
                new RecurringTxBE(1500f, "Gehalt", date(2026, 8, 1, 6, 15), "", "Girokonto"));
        assertNotNull(parsed);
        assertEquals("", parsed.getSenderStr());
    }

    // endregion

    // region Settings

    @Test
    public void settingsDefaultEntity_survivesRoundTrip() throws JSONException {
        Model.Settings settings = new Model.Settings("Haushalt");
        Model.Settings parsed = roundTrip(settings);
        assertEquals("Haushalt", parsed.defaultEntityName);
    }

    @Test
    public void settingsSyncFolderUri_survivesRoundTrip() throws JSONException {
        Model.Settings settings = new Model.Settings("User");
        settings.syncFolderUri = "content://com.android.externalstorage.documents/tree/primary%3AFinance";
        Model.Settings parsed = roundTrip(settings);
        assertEquals("content://com.android.externalstorage.documents/tree/primary%3AFinance",
                parsed.syncFolderUri);
    }

    @Test
    public void settingsAbsentSyncFolderUri_staysNull() throws JSONException {
        Model.Settings parsed = roundTrip(new Model.Settings("User"));
        assertEquals(null, parsed.syncFolderUri);
    }

    @Test
    public void settingsEntityDefaults_surviveRoundTripForEveryEntity() throws JSONException {
        Model.Settings settings = new Model.Settings("User");
        settings.setEntityDefaults("User", "Girokonto", "Lebensmittel");
        settings.setEntityDefaults("Partner", "Sparbuch", "Freizeit");

        Model.Settings parsed = roundTrip(settings);
        assertEquals(2, parsed.entityDefaultsMap.size());
        assertEquals("Girokonto", parsed.getEntityDefaults("User").defaultSender);
        assertEquals("Lebensmittel", parsed.getEntityDefaults("User").defaultReceiver);
        assertEquals("Sparbuch", parsed.getEntityDefaults("Partner").defaultSender);
        assertEquals("Freizeit", parsed.getEntityDefaults("Partner").defaultReceiver);
    }

    // endregion

    // region whole-file round trip

    @Test
    public void wholeSaveFile_survivesAControllerSaveAndReload() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = new Controller(storage);
        writer.getModel().currentFileName = FILE;

        AccountBE giro = new AccountBE("Girokonto");
        giro.setAutoRenew(false);
        giro.addTx(new TxBE(1000f, "Gehalt", date(2026, 8, 1, 8, 0)));

        BudgetAccountBE lebensmittel = new BudgetAccountBE("Lebensmittel", 100f, 1200f);
        lebensmittel.setNextRenewal("2026-09");
        lebensmittel.setRenewalPeriod(2);
        lebensmittel.setToOtherEntity("Partner");
        lebensmittel.addTx(new TxBE(-20f, "Markt", date(2026, 8, 10, 14, 30)));
        BudgetAccountBE wocheneinkauf = new BudgetAccountBE("Wocheneinkauf", 40f, 480f);
        lebensmittel.addSubBudget(wocheneinkauf);

        ProjectBudgetBE kueche = new ProjectBudgetBE("Küche", 5000f);

        List<AccountBE> assets = new ArrayList<>();
        assets.add(giro);
        List<BudgetAccountBE> budgets = new ArrayList<>();
        budgets.add(lebensmittel);
        budgets.add(kueche);
        List<RecurringTxBE> recurring = new ArrayList<>();
        recurring.add(new RecurringTxBE(-49.99f, "Miete", date(2026, 8, 1, 6, 15),
                "Girokonto", "Lebensmittel"));
        List<TxBE> income = new ArrayList<>();
        income.add(new TxBE(1500f, "Gehalt", date(2026, 8, 1, 8, 0)));

        writer.getModel().asset_accounts = assets;
        writer.getModel().budget_accounts = budgets;
        writer.getModel().recurringTx = recurring;
        writer.getModel().currentIncome = income;
        writer.saveAccountsToInternal();

        Controller reader = new Controller(storage);
        reader.readAccountsFromInternal(FILE);
        Model reloaded = reader.getModel();

        assertEquals(1, reloaded.asset_accounts.size());
        assertEquals(2, reloaded.budget_accounts.size());
        assertEquals(1, reloaded.recurringTx.size());
        assertEquals(1, reloaded.currentIncome.size());

        AccountBE reloadedGiro = reloaded.getAssetAccountByName("Girokonto");
        assertNotNull(reloadedGiro);
        assertFalse(reloadedGiro.getAutoRenew());
        assertEquals(1000f, reloadedGiro.getSum(), DELTA);

        BudgetAccountBE reloadedLebensmittel = reloaded.getBudgetAccountByName("Lebensmittel");
        assertNotNull(reloadedLebensmittel);
        assertEquals("2026-09", reloadedLebensmittel.getNextRenewal());
        assertEquals(2, reloadedLebensmittel.getRenewalPeriod());
        assertEquals("Partner", reloadedLebensmittel.getOtherEntity());
        assertEquals(1200f, reloadedLebensmittel.indivYearlyBudget, DELTA);
        assertEquals(100f, reloadedLebensmittel.indivAvailableBudget, DELTA);
        assertEquals(1, reloadedLebensmittel.getDirectSubBudgets().size());

        BudgetAccountBE reloadedKueche = reloaded.getBudgetAccountByName("Küche");
        assertNotNull(reloadedKueche);
        assertTrue(reloadedKueche instanceof ProjectBudgetBE);
        assertEquals(5000f, reloadedKueche.indivYearlyBudget, DELTA);

        assertEquals("Miete", reloaded.recurringTx.get(0).getDescription());
        assertEquals("Girokonto", reloaded.recurringTx.get(0).getSenderStr());
        assertEquals(1500f, reloaded.currentIncome.get(0).getAmount(), DELTA);
    }

    @Test
    public void wholeSaveFile_isStableAcrossASecondRoundTrip() throws JSONException, IOException {
        // A save file that changes shape every time it is loaded and written back is a sign that
        // some field is not surviving the trip. Two round trips must produce identical bytes.
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = new Controller(storage);
        writer.getModel().currentFileName = FILE;
        writer.createAssetAccount("Girokonto");
        BudgetAccountBE lebensmittel = writer.createRootBudget("Lebensmittel", 100f, 1200f);
        writer.createSubBudget(lebensmittel, "Wocheneinkauf", 40f, 480f);
        writer.saveAccountsToInternal();
        String firstWrite = storage.read(FILE);

        Controller reader = new Controller(storage);
        reader.readAccountsFromInternal(FILE);
        reader.saveAccountsToInternal();

        assertEquals(firstWrite, storage.read(FILE));
    }

    @Test
    public void emptySaveFile_survivesRoundTrip() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = new Controller(storage);
        writer.getModel().currentFileName = FILE;
        writer.saveAccountsToInternal();

        Controller reader = new Controller(storage);
        reader.readAccountsFromInternal(FILE);

        assertTrue(reader.getModel().asset_accounts.isEmpty());
        assertTrue(reader.getModel().budget_accounts.isEmpty());
        assertTrue(reader.getModel().recurringTx.isEmpty());
        assertTrue(reader.getModel().currentIncome.isEmpty());
    }

    // endregion

    // region null-valued fields must not silently delete their key

    @Test
    public void txWithNullDescription_doesNotLoseTheEntry() {
        // JSONObject.put(key, null) REMOVES the key. If that happens here, parseJSON_Entry's
        // getString throws and the whole transaction is dropped without anyone being told.
        JSONObject serialised = Util.serialise_Entry(
                new TxBE(-5f, null, date(2026, 8, 10, 14, 30)));
        assertNotNull("a null description must not fail serialisation", serialised);
        TxBE parsed = Util.parseJSON_Entry(serialised);
        assertNotNull("a null description must not cost the whole transaction", parsed);
        assertEquals(-5f, parsed.getAmount(), DELTA);
    }

    @Test
    public void txWithNullDate_doesNotLoseTheEntryOrAbortTheSave() {
        // formatDateSave throws on null, which would take down the whole save; the amount is the
        // part that matters financially, so it is kept and the date is substituted.
        JSONObject serialised = Util.serialise_Entry(new TxBE(-5f, "Einkauf", null));
        assertNotNull("a null date must not abort serialisation", serialised);
        TxBE parsed = Util.parseJSON_Entry(serialised);
        assertNotNull("a null date must not cost the whole transaction", parsed);
        assertEquals(-5f, parsed.getAmount(), DELTA);
        assertEquals("Einkauf", parsed.getDescription());
        assertNotNull(parsed.getDate());
    }

    @Test
    public void accountWithANullDatedTx_stillSerialisesItsOtherEntries() {
        AccountBE account = new AccountBE("A");
        account.addTx(new TxBE(1f, "good", date(2026, 8, 1, 8, 0)));
        account.addTx(new TxBE(2f, "no date", null));

        AccountBE parsed = roundTrip(account);
        assertNotNull(parsed);
        assertEquals(2, parsed.getTxList().size());
        assertEquals(3f, parsed.getSum(), DELTA);
    }

    @Test
    public void recurringOrderWithNullDate_doesNotLoseTheOrder() {
        JSONObject serialised = Util.serialise_RecurringOrder(
                new RecurringTxBE(1f, "Order", null, "Girokonto", "Wohnen"));
        assertNotNull("a null date must not abort serialisation", serialised);
        RecurringTxBE parsed = Util.parseJSON_RecurringOrder(serialised);
        assertNotNull("a null date must not cost the whole recurring order", parsed);
        assertEquals("Girokonto", parsed.getSenderStr());
    }

    @Test
    public void budgetAccountWithNullOtherEntity_survivesRoundTrip() {
        BudgetAccountBE account = new BudgetAccountBE("B");
        account.setToOtherEntity(null);
        BudgetAccountBE parsed = roundTrip(account);
        assertNotNull(parsed);
        assertEquals("", parsed.getOtherEntity());
    }

    @Test
    public void recurringOrderWithNullSender_doesNotLoseTheOrder() {
        JSONObject serialised = Util.serialise_RecurringOrder(
                new RecurringTxBE(1f, "Order", date(2026, 8, 1, 6, 15), null, "Wohnen"));
        assertNotNull("a null sender must not fail serialisation", serialised);
        RecurringTxBE parsed = Util.parseJSON_RecurringOrder(serialised);
        assertNotNull("a null sender must not cost the whole recurring order", parsed);
        assertEquals("Wohnen", parsed.getReceiverStr());
    }

    @Test
    public void settingsWithNullDefaultEntity_doNotLoseTheEntityDefaults() throws JSONException {
        // parseJSON_Settings rethrows, and loadAppSettings then falls back to a blank Settings -
        // so one missing key here discards every entity default the user has.
        Model.Settings settings = new Model.Settings();
        settings.setEntityDefaults("User", "Girokonto", "Lebensmittel");

        Model.Settings parsed = roundTrip(settings);
        assertEquals(1, parsed.entityDefaultsMap.size());
        assertEquals("Girokonto", parsed.getEntityDefaults("User").defaultSender);
    }

    @Test
    public void settingsWithNullEntityDefaults_doNotLoseTheEntity() throws JSONException {
        Model.Settings settings = new Model.Settings("User");
        settings.entityDefaultsMap.put("Partner", new Model.EntityDefaults(null, null));
        settings.getEntityDefaults("Partner").defaultSender = null;

        Model.Settings parsed = roundTrip(settings);
        assertNotNull(parsed.getEntityDefaults("Partner"));
        assertEquals("", parsed.getEntityDefaults("Partner").defaultSender);
    }

    // endregion
}
