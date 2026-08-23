package com.privat.pitz.financehelper.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
 * Covers {@link TxService#updateTxPair}, which backs the edit dialog's opt-in "also change the
 * counterpart" checkbox.
 *
 * <p>Editing a transaction used to be strictly one-sided: the dialog mutated the TxBE in place and
 * never looked for the other half of the transfer, so the two accounts drifted apart on every
 * edit. Propagating unconditionally would be wrong too - an opening balance or an income has no
 * counterpart, and a same-day same-description entry elsewhere may be a coincidence - so it is the
 * user's choice, and this method is what honours it.
 *
 * <p>The critical property is that the pair is matched on the values as they were <em>before</em>
 * the edit. Applying the change to one side first would make the other unfindable.
 */
public class UpdateTxPairTest {

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

    /** Girokonto -30 / Wocheneinkauf +30, with Wocheneinkauf two sub-budget levels deep. */
    private Controller deepPair(InMemorySavefileStorage storage, Date when) {
        Controller controller = controllerWith(storage);
        AccountBE girokonto = new AccountBE("Girokonto");
        BudgetAccountBE haushalt = new BudgetAccountBE("Haushalt", 500f, 6000f);
        BudgetAccountBE lebensmittel = new BudgetAccountBE("Lebensmittel", 200f, 2400f);
        BudgetAccountBE wocheneinkauf = new BudgetAccountBE("Wocheneinkauf", 50f, 600f);
        lebensmittel.addSubBudget(wocheneinkauf);
        haushalt.addSubBudget(lebensmittel);
        controller.getModel().asset_accounts.add(girokonto);
        controller.getModel().budget_accounts.add(haushalt);

        girokonto.addTx(new TxBE(-30f, "Markt", when));
        wocheneinkauf.addTx(new TxBE(30f, "Markt", when));
        return controller;
    }

    @Test
    public void updatesDateDescriptionAndAmountOnBothSides() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Date when = date(2026, 8, 10);
        Date newWhen = date(2026, 8, 12);
        Controller controller = deepPair(storage, when);
        AccountBE girokonto = controller.getModel().getAssetAccountByName("Girokonto");
        BudgetAccountBE wocheneinkauf =
                controller.getModel().getBudgetAccountByName("Wocheneinkauf");

        boolean paired = controller.updateTxPair(when, "Markt", girokonto,
                newWhen, "Wochenmarkt", -45f);

        assertTrue("the counterpart, two levels deep, must be found", paired);
        TxBE source = girokonto.getTxList().get(0);
        TxBE counterpart = wocheneinkauf.getTxList().get(0);
        assertEquals(-45f, source.getAmount(), DELTA);
        assertEquals("the counterpart carries the opposite sign",
                45f, counterpart.getAmount(), DELTA);
        assertEquals("Wochenmarkt", source.getDescription());
        assertEquals("Wochenmarkt", counterpart.getDescription());
        assertEquals(newWhen, source.getDate());
        assertEquals("the date must move too, or the pair stops matching",
                newWhen, counterpart.getDate());
    }

    @Test
    public void appliesTheWholeEditInOneGo() throws JSONException, IOException {
        // Applying the fields one at a time would move the goalposts: the pair is matched on
        // date+description, so changing the description first makes the second lookup fail.
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Date when = date(2026, 8, 10);
        Controller controller = deepPair(storage, when);
        AccountBE girokonto = controller.getModel().getAssetAccountByName("Girokonto");

        assertTrue(controller.updateTxPair(when, "Markt", girokonto,
                date(2026, 8, 12), "Wochenmarkt", -45f));
        // and the pair is still matchable afterwards, under its new identity
        assertTrue(controller.updateTxPair(date(2026, 8, 12), "Wochenmarkt", girokonto,
                date(2026, 8, 13), "Supermarkt", -50f));

        assertEquals(-50f, girokonto.getSum(), DELTA);
        assertEquals(50f,
                controller.getModel().getBudgetAccountByName("Wocheneinkauf").getSum(), DELTA);
    }

    @Test
    public void firstLevelCounterpart_stillWorks() throws JSONException, IOException {
        // regression guard: the old one-level search could reach this depth
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Date when = date(2026, 8, 10);
        Controller controller = controllerWith(storage);
        AccountBE girokonto = new AccountBE("Girokonto");
        BudgetAccountBE haushalt = new BudgetAccountBE("Haushalt", 500f, 6000f);
        BudgetAccountBE lebensmittel = new BudgetAccountBE("Lebensmittel", 200f, 2400f);
        haushalt.addSubBudget(lebensmittel);
        controller.getModel().asset_accounts.add(girokonto);
        controller.getModel().budget_accounts.add(haushalt);
        girokonto.addTx(new TxBE(-30f, "Markt", when));
        lebensmittel.addTx(new TxBE(30f, "Markt", when));

        assertTrue(controller.updateTxPair(when, "Markt", girokonto, when, "Markt", -45f));
        assertEquals(45f, lebensmittel.getSum(), DELTA);
    }

    @Test
    public void withNoCounterpart_changesNothingAndSaysSo() throws JSONException, IOException {
        // An income or an opening balance has no counterpart. The caller falls back to a one-sided
        // edit, which is only correct if this method really did leave everything alone.
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Date when = date(2026, 8, 10);
        Controller controller = controllerWith(storage);
        AccountBE girokonto = new AccountBE("Girokonto");
        controller.getModel().asset_accounts.add(girokonto);
        girokonto.addTx(new TxBE(2000f, "Gehalt", when));

        boolean paired = controller.updateTxPair(when, "Gehalt", girokonto,
                date(2026, 8, 12), "Lohn", 2500f);

        assertFalse(paired);
        TxBE untouched = girokonto.getTxList().get(0);
        assertEquals("nothing may be applied when the pair was not found",
                2000f, untouched.getAmount(), DELTA);
        assertEquals("Gehalt", untouched.getDescription());
        assertEquals(when, untouched.getDate());
        assertEquals("and nothing may be written either", 0, storage.writeCount);
    }

    @Test
    public void withNoSuchEntryOnTheSourceAccount_returnsFalse()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Date when = date(2026, 8, 10);
        Controller controller = deepPair(storage, when);
        AccountBE girokonto = controller.getModel().getAssetAccountByName("Girokonto");

        assertFalse(controller.updateTxPair(when, "GibtEsNicht", girokonto,
                when, "Neu", -45f));
    }

    @Test
    public void bothSidesSurviveASaveAndReload() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Date when = date(2026, 8, 10);
        Controller controller = deepPair(storage, when);
        AccountBE girokonto = controller.getModel().getAssetAccountByName("Girokonto");

        controller.updateTxPair(when, "Markt", girokonto,
                date(2026, 8, 12), "Wochenmarkt", -45f);

        Controller reader = new Controller(storage);
        reader.readAccountsFromInternal(FILE);
        assertEquals(-45f, reader.getModel().getAssetAccountByName("Girokonto").getSum(), DELTA);
        assertEquals(45f,
                reader.getModel().getBudgetAccountByName("Wocheneinkauf").getSum(), DELTA);
        assertEquals("Wochenmarkt", reader.getModel()
                .getBudgetAccountByName("Wocheneinkauf").getTxList().get(0).getDescription());
    }

    @Test
    public void aFailedSaveRevertsBothSides() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Date when = date(2026, 8, 10);
        Controller controller = deepPair(storage, when);
        AccountBE girokonto = controller.getModel().getAssetAccountByName("Girokonto");
        BudgetAccountBE wocheneinkauf =
                controller.getModel().getBudgetAccountByName("Wocheneinkauf");
        storage.failWrites = true;

        try {
            controller.updateTxPair(when, "Markt", girokonto,
                    date(2026, 8, 12), "Wochenmarkt", -45f);
        } catch (IOException expected) {
            // saveOrRevert rethrows after undoing the mutation
        }

        assertEquals("a half-applied edit is worse than none", -30f, girokonto.getSum(), DELTA);
        assertEquals(30f, wocheneinkauf.getSum(), DELTA);
        assertEquals("Markt", girokonto.getTxList().get(0).getDescription());
        assertEquals("Markt", wocheneinkauf.getTxList().get(0).getDescription());
        assertEquals(when, wocheneinkauf.getTxList().get(0).getDate());
    }
}
