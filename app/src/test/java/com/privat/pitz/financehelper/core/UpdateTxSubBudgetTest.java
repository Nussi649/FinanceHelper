package com.privat.pitz.financehelper.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;
import com.privat.pitz.financehelper.data.TxBE;

/**
 * Covers {@link TxService#updateTx} (both overloads) matching the counterpart of a transfer
 * against the whole sub-budget tree, not just one level deep.
 *
 * <p>The old code hand-built its search list from every asset account plus every top-level budget
 * account's {@code getDirectSubBudgets()} - one level of sub-budget only. A transaction whose
 * counterpart lives in a 2nd-level-or-deeper sub-budget was therefore never found: only the side
 * the caller already held a reference to got updated, and the two sides of the transfer silently
 * drifted apart. The fix routes the search through {@link Model#getAllAccounts()}, which recurses
 * every level via {@code getAllSubBudgets()}.
 *
 * <p>Fixtures use a three-level budget tree - {@code Haushalt} (top-level) &gt;
 * {@code Lebensmittel} (1st-level sub-budget) &gt; {@code Wocheneinkauf} (2nd-level sub-budget) -
 * so the two counterpart depths are genuinely distinct: the old hand-built list could reach
 * {@code Lebensmittel} but never {@code Wocheneinkauf}.
 */
public class UpdateTxSubBudgetTest {

    private static final float DELTA = 0.001f;
    private static final String FILE = "2026-08-User.jso";

    private Date date(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day, 12, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private Controller controllerWith(InMemorySavefileStorage storage) {
        Controller controller = new Controller(storage);
        controller.getModel().currentFileName = FILE;
        return controller;
    }

    @Test
    public void amountEdit_counterpartInSecondLevelSubBudget_updatesBothSides()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = controllerWith(storage);
        AccountBE girokonto = new AccountBE("Girokonto");
        BudgetAccountBE haushalt = new BudgetAccountBE("Haushalt", 500f, 6000f);
        BudgetAccountBE lebensmittel = new BudgetAccountBE("Lebensmittel", 200f, 2400f);
        BudgetAccountBE wocheneinkauf = new BudgetAccountBE("Wocheneinkauf", 50f, 600f);
        lebensmittel.addSubBudget(wocheneinkauf);
        haushalt.addSubBudget(lebensmittel);
        controller.getModel().asset_accounts.add(girokonto);
        controller.getModel().budget_accounts.add(haushalt);

        Date txDate = date(2026, 8, 10);
        // spending on a budget account is stored POSITIVE; the matching negative sits on the
        // asset account that paid
        girokonto.addTx(new TxBE(-30f, "Markt", txDate));
        wocheneinkauf.addTx(new TxBE(30f, "Markt", txDate));

        // updateTx assigns newAmount to the source's own entry and the negation to the
        // counterpart, so the pair stays net-zero; editing from the asset side means the new
        // amount is negative, same as the convention for a freshly created transfer
        boolean updated = controller.updateTx(txDate, "Markt", girokonto, -45f);

        assertTrue("the counterpart, two levels deep, must be found", updated);
        assertEquals(-45f, girokonto.getSum(), DELTA);
        assertEquals(45f, wocheneinkauf.getSum(), DELTA);
    }

    @Test
    public void amountEdit_counterpartInFirstLevelSubBudget_stillUpdatesBothSides()
            throws JSONException, IOException {
        // regression guard: the old one-level hand-built list already handled this depth, and
        // must keep handling it
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = controllerWith(storage);
        AccountBE girokonto = new AccountBE("Girokonto");
        BudgetAccountBE haushalt = new BudgetAccountBE("Haushalt", 500f, 6000f);
        BudgetAccountBE lebensmittel = new BudgetAccountBE("Lebensmittel", 200f, 2400f);
        haushalt.addSubBudget(lebensmittel);
        controller.getModel().asset_accounts.add(girokonto);
        controller.getModel().budget_accounts.add(haushalt);

        Date txDate = date(2026, 8, 10);
        girokonto.addTx(new TxBE(-30f, "Markt", txDate));
        lebensmittel.addTx(new TxBE(30f, "Markt", txDate));

        boolean updated = controller.updateTx(txDate, "Markt", girokonto, -45f);

        assertTrue(updated);
        assertEquals(-45f, girokonto.getSum(), DELTA);
        assertEquals(45f, lebensmittel.getSum(), DELTA);
    }

    @Test
    public void descriptionEdit_counterpartInSecondLevelSubBudget_updatesBothSides()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = controllerWith(storage);
        AccountBE girokonto = new AccountBE("Girokonto");
        BudgetAccountBE haushalt = new BudgetAccountBE("Haushalt", 500f, 6000f);
        BudgetAccountBE lebensmittel = new BudgetAccountBE("Lebensmittel", 200f, 2400f);
        BudgetAccountBE wocheneinkauf = new BudgetAccountBE("Wocheneinkauf", 50f, 600f);
        lebensmittel.addSubBudget(wocheneinkauf);
        haushalt.addSubBudget(lebensmittel);
        controller.getModel().asset_accounts.add(girokonto);
        controller.getModel().budget_accounts.add(haushalt);

        Date txDate = date(2026, 8, 10);
        girokonto.addTx(new TxBE(-30f, "Markt", txDate));
        wocheneinkauf.addTx(new TxBE(30f, "Markt", txDate));

        boolean updated = controller.updateTx(txDate, "Markt", girokonto, "Wocheneinkauf-Extra");

        assertTrue("the counterpart, two levels deep, must be found", updated);
        assertEquals("Wocheneinkauf-Extra", girokonto.getTxList().get(0).getDescription());
        assertEquals("Wocheneinkauf-Extra", wocheneinkauf.getTxList().get(0).getDescription());
    }

    @Test
    public void descriptionEdit_counterpartInFirstLevelSubBudget_stillUpdatesBothSides()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = controllerWith(storage);
        AccountBE girokonto = new AccountBE("Girokonto");
        BudgetAccountBE haushalt = new BudgetAccountBE("Haushalt", 500f, 6000f);
        BudgetAccountBE lebensmittel = new BudgetAccountBE("Lebensmittel", 200f, 2400f);
        haushalt.addSubBudget(lebensmittel);
        controller.getModel().asset_accounts.add(girokonto);
        controller.getModel().budget_accounts.add(haushalt);

        Date txDate = date(2026, 8, 10);
        girokonto.addTx(new TxBE(-30f, "Markt", txDate));
        lebensmittel.addTx(new TxBE(30f, "Markt", txDate));

        boolean updated = controller.updateTx(txDate, "Markt", girokonto, "Wocheneinkauf-Extra");

        assertTrue(updated);
        assertEquals("Wocheneinkauf-Extra", girokonto.getTxList().get(0).getDescription());
        assertEquals("Wocheneinkauf-Extra", lebensmittel.getTxList().get(0).getDescription());
    }
}
