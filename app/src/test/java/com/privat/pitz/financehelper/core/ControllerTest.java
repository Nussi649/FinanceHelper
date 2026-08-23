package com.privat.pitz.financehelper.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONException;
import org.junit.Test;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;
import com.privat.pitz.financehelper.data.RecurringTxBE;
import com.privat.pitz.financehelper.data.TxBE;

public class ControllerTest {

    private Date date(int year, int month, int day, int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day, hour, minute, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    // region JSON round trip - pins the on-disk save file format

    @Test
    public void saveThenLoad_roundTripsAccountsAndSubBudgets() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = new Controller(storage);
        Model writeModel = writer.getModel();

        AccountBE girokonto = new AccountBE("Girokonto");
        girokonto.addTx(new TxBE(1000f, "Eroeffnung", date(2026, 8, 1, 9, 0)));
        girokonto.addTx(new TxBE(-50f, "Einkauf", date(2026, 8, 5, 14, 30)));
        writeModel.asset_accounts.add(girokonto);

        AccountBE sparkonto = new AccountBE("Sparkonto");
        sparkonto.addTx(new TxBE(500f, "Eroeffnung", date(2026, 8, 1, 9, 0)));
        writeModel.asset_accounts.add(sparkonto);

        BudgetAccountBE lebensmittel = new BudgetAccountBE("Lebensmittel", 100f, 1200f);
        lebensmittel.setNextRenewal("2026-09");
        lebensmittel.addTx(new TxBE(-20f, "Wocheneinkauf", date(2026, 8, 10, 12, 0)));
        BudgetAccountBE restaurants = new BudgetAccountBE("Restaurants", 50f, 600f);
        restaurants.setNextRenewal("2026-09");
        restaurants.addTx(new TxBE(-15f, "Mittagessen", date(2026, 8, 12, 12, 30)));
        lebensmittel.addSubBudget(restaurants);
        writeModel.budget_accounts.add(lebensmittel);

        RecurringTxBE recurring = new RecurringTxBE(30f, "Abo", date(2026, 8, 1, 0, 0), "Girokonto", "Lebensmittel");
        writeModel.recurringTx.add(recurring);

        TxBE income = new TxBE(2000f, "Gehalt", date(2026, 8, 1, 9, 0));
        writeModel.currentIncome.add(income);

        writeModel.currentFileName = "2026-08-User.jso";
        writer.saveAccountsToInternal();

        // read back through a fresh Controller sharing the same fake storage, so assertions are
        // based on freshly-parsed objects rather than the original in-memory ones
        Controller reader = new Controller(storage);
        reader.readAccountsFromInternal("2026-08-User.jso");
        Model readModel = reader.getModel();

        assertEquals("User", readModel.currentEntity);
        assertEquals("2026-08-User.jso", readModel.currentFileName);

        assertEquals(2, readModel.asset_accounts.size());
        AccountBE readGirokonto = readModel.getAssetAccountByName("Girokonto");
        assertEquals(950f, readGirokonto.getSum(), 0.001f);
        assertEquals(2, readGirokonto.getTxList().size());
        AccountBE readSparkonto = readModel.getAssetAccountByName("Sparkonto");
        assertEquals(500f, readSparkonto.getSum(), 0.001f);

        assertEquals(1, readModel.budget_accounts.size());
        BudgetAccountBE readLebensmittel = readModel.budget_accounts.get(0);
        assertEquals("Lebensmittel", readLebensmittel.getName());
        assertEquals(-20f, readLebensmittel.getSum(), 0.001f);
        assertEquals(1200f, readLebensmittel.indivYearlyBudget, 0.001f);
        assertEquals("2026-09", readLebensmittel.getNextRenewal());

        assertEquals(1, readLebensmittel.getDirectSubBudgets().size());
        BudgetAccountBE readRestaurants = readLebensmittel.getDirectSubBudgets().get(0);
        assertEquals("Restaurants", readRestaurants.getName());
        assertEquals(-15f, readRestaurants.getSum(), 0.001f);
        assertEquals(600f, readRestaurants.indivYearlyBudget, 0.001f);

        assertEquals(1, readModel.recurringTx.size());
        RecurringTxBE readRecurring = readModel.recurringTx.get(0);
        assertEquals("Abo", readRecurring.getDescription());
        assertEquals("Girokonto", readRecurring.getSenderStr());
        assertEquals("Lebensmittel", readRecurring.getReceiverStr());
        assertEquals(30f, readRecurring.getAmount(), 0.001f);

        assertEquals(1, readModel.currentIncome.size());
        assertEquals("Gehalt", readModel.currentIncome.get(0).getDescription());
        assertEquals(2000f, readModel.currentIncome.get(0).getAmount(), 0.001f);
    }

    // endregion

    // region entity/period discovery

    @Test
    public void getAllAvailableEntities_collapsesDuplicatesAndIgnoresJunk() {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        putEmpty(storage, "2026-08-User.jso");
        putEmpty(storage, "2026-07-User.jso"); // same entity, different period -> collapses
        putEmpty(storage, "2026-08-Sri.jso");
        putEmpty(storage, "settings.json"); // not a savefile name -> ignored
        putEmpty(storage, "notes.txt"); // junk -> ignored
        putEmpty(storage, "2026-08-User.jso.bak"); // doesn't match the "$" anchored pattern -> ignored

        Controller controller = new Controller(storage);
        List<String> entities = controller.getAllAvailableEntities();

        assertEquals(2, entities.size());
        assertTrue(entities.contains("User"));
        assertTrue(entities.contains("Sri"));
    }

    @Test
    public void getAllAvailableEntities_emptyStorage_returnsEmptyList() {
        Controller controller = new Controller(new InMemorySavefileStorage());
        assertTrue(controller.getAllAvailableEntities().isEmpty());
    }

    @Test
    public void getAllPeriodsForEntity_returnsOnlyMatchingEntityPeriods() {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        putEmpty(storage, "2026-08-User.jso");
        putEmpty(storage, "2026-07-User.jso");
        putEmpty(storage, "2026-08-Sri.jso"); // different entity -> excluded
        putEmpty(storage, "settings.json"); // junk -> excluded

        Controller controller = new Controller(storage);
        List<String> periods = controller.getAllPeriodsForEntity("User");

        assertEquals(2, periods.size());
        assertTrue(periods.contains("2026-08"));
        assertTrue(periods.contains("2026-07"));
    }

    @Test
    public void getAllPeriodsForEntity_noMatches_returnsEmptyList() {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        putEmpty(storage, "2026-08-Sri.jso");

        Controller controller = new Controller(storage);
        assertTrue(controller.getAllPeriodsForEntity("User").isEmpty());
    }

    @Test
    public void getAvailableEntitiesForPeriod_returnsEntitiesForThatPeriodOnly() {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        putEmpty(storage, "2026-08-User.jso");
        putEmpty(storage, "2026-08-Sri.jso");
        putEmpty(storage, "2026-07-User.jso"); // different period -> excluded

        Controller controller = new Controller(storage);
        List<String> entities = controller.getAvailableEntitiesForPeriod("2026-08");

        assertEquals(2, entities.size());
        assertTrue(entities.contains("User"));
        assertTrue(entities.contains("Sri"));
    }

    @Test
    public void getAvailableEntitiesForPeriod_invalidPeriod_throwsIllegalArgumentException() {
        Controller controller = new Controller(new InMemorySavefileStorage());
        try {
            controller.getAvailableEntitiesForPeriod("not-a-period");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // endregion

    // region revert-on-failed-save paths (saveOrRevert)

    @Test
    public void createAssetAccount_revertsOnFailedSave() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = new Controller(storage);
        controller.getModel().currentFileName = "2026-08-User.jso";

        storage.failWrites = true;
        try {
            controller.createAssetAccount("Girokonto");
            fail("expected IOException");
        } catch (IOException expected) {
            // expected
        }

        assertTrue(controller.getModel().asset_accounts.isEmpty());
        assertNull(controller.getModel().getAssetAccountByName("Girokonto"));
    }

    @Test
    public void createRootBudget_revertsOnFailedSave() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = new Controller(storage);
        controller.getModel().currentFileName = "2026-08-User.jso";

        storage.failWrites = true;
        try {
            controller.createRootBudget("Lebensmittel", 100f, 1200f);
            fail("expected IOException");
        } catch (IOException expected) {
            // expected
        }

        assertTrue(controller.getModel().budget_accounts.isEmpty());
        assertNull(controller.getModel().getRootBudgetAccountByName("Lebensmittel"));
    }

    @Test
    public void addFunds_revertsOnFailedSave() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = new Controller(storage);
        Model model = controller.getModel();
        model.currentFileName = "2026-08-User.jso";
        AccountBE girokonto = new AccountBE("Girokonto");
        model.currentReceiver = girokonto;

        storage.failWrites = true;
        try {
            controller.addFunds(100f, "Gehalt");
            fail("expected IOException");
        } catch (IOException expected) {
            // expected
        }

        assertTrue(girokonto.getTxList().isEmpty());
        assertTrue(model.currentIncome.isEmpty());
    }

    @Test
    public void deleteTx_revertsOnFailedSave() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = new Controller(storage);
        Model model = controller.getModel();
        model.currentFileName = "2026-08-User.jso";
        AccountBE girokonto = new AccountBE("Girokonto");
        TxBE tx = new TxBE(-50f, "Einkauf", date(2026, 8, 5, 14, 30));
        girokonto.addTx(tx);
        model.asset_accounts.add(girokonto);

        storage.failWrites = true;
        try {
            controller.deleteTx(girokonto, tx);
            fail("expected IOException");
        } catch (IOException expected) {
            // expected
        }

        assertEquals(1, girokonto.getTxList().size());
        assertTrue(girokonto.getTxList().contains(tx));
    }

    // endregion

    // region createTx (core, UI-independent)

    @Test
    public void createTx_movesFundsBetweenSenderAndReceiverAndSaves() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = new Controller(storage);
        Model model = controller.getModel();
        model.currentFileName = "2026-08-User.jso";

        AccountBE girokonto = new AccountBE("Girokonto");
        AccountBE sparkonto = new AccountBE("Sparkonto");
        model.currentSender = girokonto;
        model.currentReceiver = sparkonto;

        // receiver is a plain AccountBE, not a BudgetAccountBE, so startTxRedirection is never
        // reached and a null RedirectionPrompt is safe here.
        boolean result = controller.createTx("Ueberweisung", 100f, null);

        assertTrue(result);
        assertEquals(1, girokonto.getTxList().size());
        assertEquals(-100f, girokonto.getTxList().get(0).getAmount(), 0.001f);
        assertEquals(1, sparkonto.getTxList().size());
        assertEquals(100f, sparkonto.getTxList().get(0).getAmount(), 0.001f);
        assertFalse(storage.files.isEmpty());
        assertTrue(storage.writeCount > 0);
    }

    // endregion

    // region deleteAccount(AccountBE) null-safety

    @Test
    public void deleteAccount_nullAccount_returnsFalseInsteadOfThrowing() throws JSONException, IOException {
        // Regression guard. deleteAccount(AccountBE) used to open with a bare `assert account !=
        // null;`, which is a no-op on Android (the expression is never evaluated), so a null
        // account fell straight through to `account instanceof BudgetAccountBE` and threw NPE.
        // The single-argument String overload already returns false for an unknown name, so
        // returning false here for a null account is consistent, not a new contract.
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = new Controller(storage);

        boolean result = controller.deleteAccount((AccountBE) null);

        assertFalse(result);
    }

    // endregion

    private void putEmpty(InMemorySavefileStorage storage, String name) {
        storage.files.put(name, new byte[0]);
    }
}
