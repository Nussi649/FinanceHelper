package com.privat.pitz.financehelper.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

import java.io.IOException;

import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;

/**
 * A transaction must land on both accounts or on neither.
 *
 * <p>Reported from the field: "a new transaction is entered in the main screen, but the entry is
 * only made to one account - as if the other account was never selected". The cause is an orphaned
 * selection. {@code model.currentSender}/{@code currentReceiver} hold account <em>objects</em>, and
 * every load replaces every account object, so a selection made before a load can keep pointing at
 * the old object: right name, right transactions, but no list in the model contains it. {@code
 * addTx} on it mutates something the serialiser never visits, so that side of the transfer is
 * silently dropped while the other side is written normally.
 *
 * <p>Two defences, and both are needed. The selection is re-pointed or cleared whenever the account
 * lists are rebuilt, so orphans stop being created; and createTx refuses to book against one
 * anyway, so a route nobody has thought of still cannot half-write a transfer.
 */
public class TxSelectionTest {

    private static final float DELTA = 0.001f;
    private static final String FILE = "2026-08-User.jso";

    private Controller controllerWith(InMemorySavefileStorage storage) {
        Controller controller = new Controller(storage);
        controller.getModel().currentFileName = FILE;
        return controller;
    }

    private Controller populated(InMemorySavefileStorage storage)
            throws JSONException, IOException {
        Controller controller = controllerWith(storage);
        AccountBE giro = controller.createAssetAccount("Girokonto");
        controller.createRootBudget("Lebensmittel", 100f, 1200f);
        controller.getModel().currentSender = giro;
        controller.getModel().currentReceiver =
                controller.getModel().getBudgetAccountByName("Lebensmittel");
        return controller;
    }

    // region createTx refuses what it cannot book on both sides

    @Test
    public void createTx_withAnOrphanedSender_bookNothingAtAll()
            throws JSONException, IOException {
        // the reported bug, reproduced: an account that is not in the model but is still selected
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = populated(storage);
        AccountBE orphan = new AccountBE("Girokonto");   // same name, different object
        controller.getModel().currentSender = orphan;
        BudgetAccountBE receiver = controller.getModel().getBudgetAccountByName("Lebensmittel");

        boolean booked = controller.createTx("Markt", 30f, null);

        assertFalse("a transfer that can only be half-written must be refused", booked);
        assertTrue("the orphan must not be mutated either", orphan.getTxList().isEmpty());
        assertTrue("and the live account must not be left holding a one-sided entry",
                receiver.getTxList().isEmpty());
    }

    @Test
    public void createTx_withAnOrphanedReceiver_booksNothingAtAll()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = populated(storage);
        AccountBE sender = controller.getModel().getAssetAccountByName("Girokonto");
        controller.getModel().currentReceiver = new BudgetAccountBE("Lebensmittel", 100f, 1200f);

        assertFalse(controller.createTx("Markt", 30f, null));
        assertTrue(sender.getTxList().isEmpty());
    }

    @Test
    public void createTx_withSenderAndReceiverTheSameAccount_isRefused()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = populated(storage);
        AccountBE giro = controller.getModel().getAssetAccountByName("Girokonto");
        controller.getModel().currentReceiver = giro;

        assertFalse("a transfer to itself nets to zero and is always a misclick",
                controller.createTx("Markt", 30f, null));
        assertTrue(giro.getTxList().isEmpty());
    }

    @Test
    public void createTx_withBothSidesLive_booksBothAndPersistsBoth()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = populated(storage);

        assertTrue(controller.createTx("Markt", 30f, null));

        Controller reader = new Controller(storage);
        reader.readAccountsFromInternal(FILE);
        assertEquals("the paying account", -30f,
                reader.getModel().getAssetAccountByName("Girokonto").getSum(), DELTA);
        assertEquals("and the budget it was spent on", 30f,
                reader.getModel().getBudgetAccountByName("Lebensmittel").getSum(), DELTA);
    }

    // endregion

    // region selections stop going stale in the first place

    @Test
    public void reload_repointsTheSelectionAtTheNewlyParsedAccount()
            throws JSONException, IOException {
        // Without this, the selection still refers to the pre-load object and the very next
        // transaction is booked on one side only.
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = populated(storage);
        controller.saveAccountsToInternal();
        AccountBE beforeReload = controller.getModel().currentSender;

        controller.readAccountsFromInternal(FILE);

        AccountBE afterReload = controller.getModel().currentSender;
        assertNotNull(afterReload);
        assertEquals("Girokonto", afterReload.getName());
        assertTrue("the selection must be an account the model actually holds",
                controller.getModel().containsAccount(afterReload));
        assertFalse("and therefore not the object from before the load",
                beforeReload == afterReload);
    }

    @Test
    public void reload_clearsASelectionWhoseAccountIsGone() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = populated(storage);
        controller.saveAccountsToInternal();
        // select something that exists now but will not exist in the file being loaded
        controller.getModel().currentSender = new AccountBE("SeitdemGelöscht");

        controller.readAccountsFromInternal(FILE);

        assertNull("an unresolvable selection must be cleared, not left dangling",
                controller.getModel().currentSender);
    }

    @Test
    public void reloadedSelection_canImmediatelyBookATransactionOnBothSides()
            throws JSONException, IOException {
        // the end-to-end version of the reported bug
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller writer = populated(storage);
        writer.saveAccountsToInternal();

        Controller reader = controllerWith(storage);
        reader.readAccountsFromInternal(FILE);
        reader.getModel().currentSender = reader.getModel().getAssetAccountByName("Girokonto");
        reader.getModel().currentReceiver =
                reader.getModel().getBudgetAccountByName("Lebensmittel");

        assertTrue(reader.createTx("Markt", 30f, null));

        Controller verifier = new Controller(storage);
        verifier.readAccountsFromInternal(FILE);
        assertEquals(-30f,
                verifier.getModel().getAssetAccountByName("Girokonto").getSum(), DELTA);
        assertEquals(30f,
                verifier.getModel().getBudgetAccountByName("Lebensmittel").getSum(), DELTA);
    }

    @Test
    public void deletingTheSelectedAccount_clearsTheSelection() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = populated(storage);
        AccountBE giro = controller.getModel().getAssetAccountByName("Girokonto");

        assertTrue(controller.deleteAccount(giro));

        assertNull("a deleted account must not stay selected as the sender",
                controller.getModel().currentSender);
    }

    @Test
    public void aFailedDeleteRestoresTheSelectionAlongWithTheAccount()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = populated(storage);
        AccountBE giro = controller.getModel().getAssetAccountByName("Girokonto");
        storage.failWrites = true;

        try {
            controller.deleteAccount(giro);
        } catch (IOException expected) {
            // saveOrRevert rethrows after undoing the removal
        }

        assertSame("the revert must put the selection back too, not just the account",
                giro, controller.getModel().currentSender);
        assertTrue(controller.getModel().containsAccount(giro));
    }

    @Test
    public void resettingTheAccountLists_clearsTheSelections() {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = controllerWith(storage);
        controller.getModel().currentEntity = "User";
        AccountBE giro = new AccountBE("Girokonto");
        controller.getModel().asset_accounts.add(giro);
        controller.getModel().currentSender = giro;
        controller.getModel().currentReceiver = giro;

        controller.resetAccountLists();

        assertNull(controller.getModel().currentSender);
        assertNull(controller.getModel().currentReceiver);
    }

    // endregion

    // region the liveness predicate itself

    @Test
    public void containsAccount_comparesByIdentityNotByName() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = populated(storage);
        Model model = controller.getModel();

        assertTrue(model.containsAccount(model.getAssetAccountByName("Girokonto")));
        assertFalse("a same-named impostor is exactly the case that used to slip through",
                model.containsAccount(new AccountBE("Girokonto")));
        assertFalse(model.containsAccount(null));
    }

    @Test
    public void containsAccount_findsSubBudgetsAtEveryDepth() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = controllerWith(storage);
        BudgetAccountBE root = controller.createRootBudget("Haushalt", 500f, 6000f);
        BudgetAccountBE level1 = controller.createSubBudget(root, "Lebensmittel", 200f, 2400f);
        BudgetAccountBE level2 = controller.createSubBudget(level1, "Wocheneinkauf", 50f, 600f);

        assertTrue(controller.getModel().containsAccount(level2));
    }

    @Test
    public void hasLiveTxSelection_requiresTwoDifferentLiveAccounts()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = populated(storage);
        Model model = controller.getModel();

        assertTrue(model.hasLiveTxSelection());

        model.currentReceiver = model.currentSender;
        assertFalse("same account on both sides", model.hasLiveTxSelection());

        model.currentReceiver = model.getBudgetAccountByName("Lebensmittel");
        model.currentSender = new AccountBE("Girokonto");
        assertFalse("orphaned sender", model.hasLiveTxSelection());

        model.currentSender = null;
        assertFalse("nothing selected", model.hasLiveTxSelection());
    }

    // endregion
}
