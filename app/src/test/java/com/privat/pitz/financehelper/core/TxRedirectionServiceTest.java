package com.privat.pitz.financehelper.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;
import com.privat.pitz.financehelper.data.TxBE;

/**
 * Characterization tests for {@link TxRedirectionService#completeTxRedirection}, which writes a
 * transaction into <em>another</em> financial entity's save file. Written before that method's two
 * near-identical account loops are collapsed into one, so the collapse has something to be checked
 * against.
 *
 * <p>The target save file is produced by a real {@link Controller} rather than hand-written JSON,
 * so these tests exercise the actual on-disk format instead of a guess at it.
 */
public class TxRedirectionServiceTest {

    private static final String OTHER_FILE = "2026-08-Other.jso";

    private Date date(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day, 12, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    /**
     * Builds a save file for the "Other" entity holding one asset account and one budget account,
     * the latter with a sub-budget so the tests can pin that the loops only scan the top level.
     */
    private JSONObject buildOtherEntitySavefile(InMemorySavefileStorage storage)
            throws JSONException, IOException {
        Controller writer = new Controller(storage);
        Model model = writer.getModel();

        AccountBE girokonto = new AccountBE("Girokonto");
        girokonto.addTx(new TxBE(1000f, "Eroeffnung", date(2026, 8, 1)));
        model.asset_accounts.add(girokonto);

        BudgetAccountBE lebensmittel = new BudgetAccountBE("Lebensmittel", 100f, 1200f);
        lebensmittel.setNextRenewal("2026-09");
        BudgetAccountBE restaurants = new BudgetAccountBE("Restaurants", 50f, 600f);
        restaurants.setNextRenewal("2026-09");
        lebensmittel.addSubBudget(restaurants);
        model.budget_accounts.add(lebensmittel);

        model.currentIncome.add(new TxBE(2000f, "Gehalt", date(2026, 8, 1)));

        model.currentFileName = OTHER_FILE;
        writer.saveAccountsToInternal();

        return new JSONObject(storage.read(OTHER_FILE));
    }

    private Model reloadOtherEntity(InMemorySavefileStorage storage)
            throws JSONException, IOException {
        Controller reader = new Controller(storage);
        reader.readAccountsFromInternal(OTHER_FILE);
        return reader.getModel();
    }

    @Test
    public void completeTxRedirection_assetAccount_addsTxAndIncomeEntryAndPersists()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        JSONObject data = buildOtherEntitySavefile(storage);
        Controller controller = new Controller(storage);

        boolean result = controller.completeTxRedirection(
                OTHER_FILE, "User", "Miete", 400f, "Girokonto", data);

        assertTrue(result);

        Model reloaded = reloadOtherEntity(storage);
        AccountBE girokonto = reloaded.getAssetAccountByName("Girokonto");
        assertEquals(2, girokonto.getTxList().size());
        assertEquals(1400f, girokonto.getSum(), 0.001f);

        // the sender name is prefixed onto the description in the recipient income list
        assertEquals(2, reloaded.currentIncome.size());
        TxBE incomeEntry = reloaded.currentIncome.get(reloaded.currentIncome.size() - 1);
        assertEquals("User: Miete", incomeEntry.getDescription());
        assertEquals(400f, incomeEntry.getAmount(), 0.001f);
    }

    @Test
    public void completeTxRedirection_budgetAccount_addsTxAndPreservesSubBudgets()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        JSONObject data = buildOtherEntitySavefile(storage);
        Controller controller = new Controller(storage);

        boolean result = controller.completeTxRedirection(
                OTHER_FILE, "User", "Einkauf", -25f, "Lebensmittel", data);

        assertTrue(result);

        Model reloaded = reloadOtherEntity(storage);
        BudgetAccountBE lebensmittel = reloaded.getBudgetAccountByName("Lebensmittel");
        assertEquals(1, lebensmittel.getTxList().size());
        assertEquals(-25f, lebensmittel.getSum(), 0.001f);
        // re-serialising the budget account must not drop its children
        assertEquals(1, lebensmittel.getDirectSubBudgets().size());
        assertEquals("Restaurants", lebensmittel.getDirectSubBudgets().get(0).getName());

        assertEquals(2, reloaded.currentIncome.size());
        assertEquals("User: Einkauf",
                reloaded.currentIncome.get(reloaded.currentIncome.size() - 1).getDescription());
    }

    @Test
    public void completeTxRedirection_subBudgetTarget_isNotFound()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        JSONObject data = buildOtherEntitySavefile(storage);
        Controller controller = new Controller(storage);

        // "Restaurants" is a sub-budget, and both loops only scan the top-level arrays
        boolean result = controller.completeTxRedirection(
                OTHER_FILE, "User", "Mittagessen", -12f, "Restaurants", data);

        assertFalse(result);

        Model reloaded = reloadOtherEntity(storage);
        assertEquals(0f, reloaded.getBudgetAccountByName("Restaurants").getSum(), 0.001f);
    }

    @Test
    public void completeTxRedirection_unknownAccount_returnsFalseAndLeavesFileUntouched()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        JSONObject data = buildOtherEntitySavefile(storage);
        String before = storage.read(OTHER_FILE);
        Controller controller = new Controller(storage);

        boolean result = controller.completeTxRedirection(
                OTHER_FILE, "User", "Miete", 400f, "GibtEsNicht", data);

        assertFalse(result);
        assertEquals(before, storage.read(OTHER_FILE));
    }

    @Test
    public void completeTxRedirection_missingIncomeList_returnsFalseImmediately()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        JSONObject data = buildOtherEntitySavefile(storage);
        data.remove(Const.JSON_TAG_CURRENT_INCOME);
        String before = storage.read(OTHER_FILE);
        Controller controller = new Controller(storage);

        boolean result = controller.completeTxRedirection(
                OTHER_FILE, "User", "Miete", 400f, "Girokonto", data);

        assertFalse(result);
        assertEquals(before, storage.read(OTHER_FILE));
    }

    @Test
    public void completeTxRedirection_assetTakesPrecedenceOverBudgetOfSameName()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();

        Controller writer = new Controller(storage);
        Model model = writer.getModel();
        model.asset_accounts.add(new AccountBE("Doppelt"));
        BudgetAccountBE budget = new BudgetAccountBE("Doppelt", 100f, 1200f);
        budget.setNextRenewal("2026-09");
        model.budget_accounts.add(budget);
        model.currentFileName = OTHER_FILE;
        writer.saveAccountsToInternal();

        JSONObject data = new JSONObject(storage.read(OTHER_FILE));
        Controller controller = new Controller(storage);

        boolean result = controller.completeTxRedirection(
                OTHER_FILE, "User", "Test", 50f, "Doppelt", data);

        assertTrue(result);

        // the asset loop runs first and short-circuits the budget loop entirely
        Model reloaded = reloadOtherEntity(storage);
        assertEquals(50f, reloaded.getAssetAccountByName("Doppelt").getSum(), 0.001f);
        assertEquals(0f, reloaded.getBudgetAccountByName("Doppelt").getSum(), 0.001f);
    }
}
