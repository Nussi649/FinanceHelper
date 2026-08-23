package com.privat.pitz.financehelper.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

import com.privat.pitz.financehelper.data.BudgetAccountBE;
import com.privat.pitz.financehelper.data.TxBE;

/**
 * End-to-end regression tests for the bug where every budget account created in the app vanished
 * on the next start.
 *
 * <p>The chain was: no constructor set {@code nextRenewal}, so it was null;
 * {@code JSONObject.put(key, null)} <em>removes</em> the key rather than storing a null, so the
 * account was written to the save file without {@code renew_next}; and
 * {@link Util#parseJSON_BudgetAccount} treated that missing key as fatal and returned null, which
 * the caller then dropped without reporting anything. The asset accounts in the same file were
 * unaffected, which is what made it look like an isolated glitch.
 */
public class BudgetPersistenceTest {

    private static final String FILE = "2026-08-User.jso";

    private Date date(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day, 12, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private Controller freshController(InMemorySavefileStorage storage) {
        Controller controller = new Controller(storage);
        controller.getModel().currentFileName = FILE;
        return controller;
    }

    @Test
    public void budgetAccountsCreatedInApp_surviveSaveAndReload() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = freshController(storage);

        controller.createAssetAccount("Girokonto");
        BudgetAccountBE lebensmittel = controller.createRootBudget("Lebensmittel", 100f, 1200f);
        controller.createRootBudget("Restaurants", 50f, 600f);
        controller.createSubBudget(lebensmittel, "Wocheneinkauf", 40f, 480f);

        lebensmittel.addTx(new TxBE(-20f, "Markt", date(2026, 8, 10)));
        controller.saveAccountsToInternal();

        Controller reader = new Controller(storage);
        reader.readAccountsFromInternal(FILE);
        Model reloaded = reader.getModel();

        assertNotNull(reloaded.getAssetAccountByName("Girokonto"));
        assertEquals(2, reloaded.budget_accounts.size());

        BudgetAccountBE reloadedLebensmittel = reloaded.getBudgetAccountByName("Lebensmittel");
        assertNotNull(reloadedLebensmittel);
        assertEquals(1200f, reloadedLebensmittel.indivYearlyBudget, 0.001f);
        assertEquals(-20f, reloadedLebensmittel.getSum(), 0.001f);
        assertEquals(1, reloadedLebensmittel.getDirectSubBudgets().size());

        assertNotNull(reloaded.getBudgetAccountByName("Restaurants"));
        assertNotNull(reloaded.getBudgetAccountByName("Wocheneinkauf"));
    }

    @Test
    public void newBudgetAccount_isWrittenWithARenewalDateInTheFuture()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = freshController(storage);
        controller.createRootBudget("Lebensmittel", 100f, 1200f);

        JSONObject saved = new JSONObject(storage.read(FILE));
        JSONObject budget = saved.getJSONArray(Const.JSON_TAG_BUDGET_ACCOUNTS).getJSONObject(0);

        assertTrue("renew_next must be written, not dropped by put(key, null)",
                budget.has(Const.JSON_TAG_RENEWAL_NEXT));
        // strictly after the present period, or tryRenew would wipe the account immediately
        assertTrue(Util.isAfter(budget.getString(Const.JSON_TAG_RENEWAL_NEXT),
                Util.getPresentPeriod()));
    }

    @Test
    public void saveFileWrittenByTheBrokenVersion_stillLoadsItsBudgetAccounts()
            throws JSONException, IOException {
        // Recovery path: reproduce a file as the broken version wrote it - every budget account
        // present and complete, but with no renew_next key at all - and check nothing is dropped.
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = freshController(storage);
        writer.createRootBudget("Lebensmittel", 100f, 1200f);
        writer.createRootBudget("Restaurants", 50f, 600f);

        JSONObject saved = new JSONObject(storage.read(FILE));
        JSONArray budgets = saved.getJSONArray(Const.JSON_TAG_BUDGET_ACCOUNTS);
        for (int i = 0; i < budgets.length(); i++) {
            budgets.getJSONObject(i).remove(Const.JSON_TAG_RENEWAL_NEXT);
        }
        storage.write(FILE, saved.toString());

        Controller reader = new Controller(storage);
        reader.readAccountsFromInternal(FILE);

        assertEquals(2, reader.getModel().budget_accounts.size());
        assertNotNull(reader.getModel().getBudgetAccountByName("Lebensmittel"));
        assertNotNull(reader.getModel().getBudgetAccountByName("Restaurants"));
    }
}
