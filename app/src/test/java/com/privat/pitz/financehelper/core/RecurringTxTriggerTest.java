package com.privat.pitz.financehelper.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.json.JSONException;
import org.junit.Test;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.RecurringTxBE;

/**
 * Covers {@link TxService#triggerRecurringTx()}, which runs on every month rollover.
 *
 * <p>Its three guards were written as {@code try { assert x; } catch (AssertionError e) { ... }}.
 * Java assertions are disabled at runtime unless the JVM is started with {@code -ea}, and Android
 * never enables them, so on a device none of those catch blocks could ever run and the guarded
 * cases fell through into a NullPointerException instead.
 */
public class RecurringTxTriggerTest {

    private static final String FILE = "2026-08-User.jso";

    private Date date(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day, 12, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private Controller controllerWith(InMemorySavefileStorage storage, String... assetAccounts) {
        Controller controller = new Controller(storage);
        controller.getModel().currentFileName = FILE;
        for (String name : assetAccounts) {
            controller.getModel().asset_accounts.add(new AccountBE(name));
        }
        return controller;
    }

    @Test
    public void triggerRecurringTx_normalOrder_movesFundsBothWays() throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = controllerWith(storage, "Girokonto", "Sparkonto");
        controller.getModel().recurringTx.add(new RecurringTxBE(
                100f, "Sparrate", date(2026, 8, 1), "Girokonto", "Sparkonto"));

        controller.txService.triggerRecurringTx();

        Model model = controller.getModel();
        assertEquals(-100f, model.getAssetAccountByName("Girokonto").getSum(), 0.001f);
        assertEquals(100f, model.getAssetAccountByName("Sparkonto").getSum(), 0.001f);
    }

    @Test
    public void triggerRecurringTx_receiverAccountNoLongerExists_skipsTheOrder()
            throws JSONException, IOException {
        // e.g. the receiving account was renamed or deleted after the order was set up
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = controllerWith(storage, "Girokonto");
        controller.getModel().recurringTx.add(new RecurringTxBE(
                100f, "Sparrate", date(2026, 8, 1), "Girokonto", "GibtEsNichtMehr"));

        controller.txService.triggerRecurringTx();

        // the order is skipped; the surviving account must be left alone
        assertEquals(0f, controller.getModel().getAssetAccountByName("Girokonto").getSum(), 0.001f);
    }

    @Test
    public void triggerRecurringTx_emptySender_isBookedAsRecurringIncome()
            throws JSONException, IOException {
        // an order with no sender is a recurring income: it credits the receiver and is recorded
        // in the income list, with no counter-booking anywhere
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = controllerWith(storage, "Girokonto");
        controller.getModel().recurringTx.add(new RecurringTxBE(
                2000f, "Gehalt", date(2026, 8, 1), "", "Girokonto"));

        controller.txService.triggerRecurringTx();

        Model model = controller.getModel();
        assertEquals(2000f, model.getAssetAccountByName("Girokonto").getSum(), 0.001f);
        assertEquals(1, model.currentIncome.size());
        assertEquals("Gehalt", model.currentIncome.get(0).getDescription());
        assertEquals(2000f, model.currentIncome.get(0).getAmount(), 0.001f);
    }

    @Test
    public void triggerRecurringTx_senderAccountNoLongerExists_skipsTheOrder()
            throws JSONException, IOException {
        InMemorySavefileStorage storage = new InMemorySavefileStorage();
        Controller controller = controllerWith(storage, "Girokonto");
        controller.getModel().recurringTx.add(new RecurringTxBE(
                100f, "Sparrate", date(2026, 8, 1), "GibtEsNichtMehr", "Girokonto"));

        controller.txService.triggerRecurringTx();

        Model model = controller.getModel();
        assertNotNull(model.getAssetAccountByName("Girokonto"));
        // nothing booked: the counter-account is gone, so the pair cannot be completed
        assertEquals(0f, model.getAssetAccountByName("Girokonto").getSum(), 0.001f);
        assertEquals(0, model.currentIncome.size());
    }
}
