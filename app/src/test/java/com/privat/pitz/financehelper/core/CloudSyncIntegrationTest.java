package com.privat.pitz.financehelper.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;

import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.TxBE;
import com.privat.pitz.financehelper.sync.Fakes;
import com.privat.pitz.financehelper.sync.StorageLocalFiles;
import com.privat.pitz.financehelper.sync.SyncEngine;

/**
 * The real Controller, its real save format and the real sync engine, with only the cloud and the
 * disk faked: what the app does after a sync brings in changes the agent made.
 */
public class CloudSyncIntegrationTest {

    private static final String FILE = "2026-08-User.jso";

    private InMemorySavefileStorage disk;
    private Controller controller;
    private Fakes.Remote cloud;
    private SyncEngine engine;
    private int reloads;

    @Before
    public void setUp() throws Exception {
        disk = new InMemorySavefileStorage();
        controller = new Controller(disk);
        Model model = controller.getModel();
        AccountBE giro = new AccountBE("Girokonto");
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.AUGUST, 3, 9, 0, 0);
        giro.addTx(new TxBE(100f, "Eroeffnung", cal.getTime()));
        model.asset_accounts.add(giro);
        controller.setCurrentFileName(FILE);
        controller.saveAccountsToInternal();

        cloud = new Fakes.Remote();
        reloads = 0;
        controller.addSavefileReloadListener(() -> reloads++);
        StorageLocalFiles local = new StorageLocalFiles(disk, new InMemorySavefileStorage(), Runnable::run,
                controller::onSavefileReplacedBySync);
        engine = new SyncEngine(local, cloud, new Fakes.Base());
    }

    private int txCount() {
        return controller.getModel().getAssetAccountByName("Girokonto").getTxList().size();
    }

    private void agentAddsATransaction(String description) throws Exception {
        JSONObject doc = new JSONObject(cloud.files.get(FILE));
        JSONObject tx = new JSONObject();
        tx.put("d", description);
        tx.put("a", "-9.00");
        tx.put("t", "04.08.2026 12:00");
        JSONArray txs = doc.getJSONArray("assets").getJSONObject(0).getJSONArray("tx");
        txs.put(tx);
        cloud.write(FILE, doc.toString(4));
    }

    @Test
    public void theAppsFirstSave_isPushedToTheCloud() {
        SyncEngine.FileOutcome o = engine.syncFile(FILE);
        assertTrue(o.toString(), o.status != SyncEngine.Status.ERROR);
        assertTrue(cloud.files.containsKey(FILE));
    }

    @Test
    public void anAgentsTransaction_appearsInTheOpenModel_andListenersAreTold() throws Exception {
        engine.syncFile(FILE);
        assertEquals(1, txCount());

        agentAddsATransaction("Agent kauft ein");
        SyncEngine.FileOutcome o = engine.syncFile(FILE);

        // MERGED, not PULLED: the agent's entry had no id, so the cloud copy is rewritten with one too
        assertEquals(o.toString(), SyncEngine.Status.MERGED, o.status);
        assertEquals(2, txCount());
        assertEquals(1, reloads);
    }

    @Test
    public void afterThePull_anAppChange_doesNotWipeOutTheAgentsTransaction() throws Exception {
        engine.syncFile(FILE);
        agentAddsATransaction("Agent kauft ein");
        engine.syncFile(FILE);

        // the app now changes something and saves, as it does after every edit
        AccountBE giro = controller.getModel().getAssetAccountByName("Girokonto");
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.AUGUST, 5, 10, 0, 0);
        giro.addTx(new TxBE(-3f, "Kaffee", cal.getTime()));
        controller.saveAccountsToInternal();
        engine.syncFile(FILE);

        JSONArray remoteTx = new JSONObject(cloud.files.get(FILE))
                .getJSONArray("assets").getJSONObject(0).getJSONArray("tx");
        assertEquals(3, remoteTx.length());
        assertEquals(3, txCount());
    }

    @Test
    public void aTransactionTheAgentAddedGetsAnIdAndModifiedStampOnTheWayThrough() throws Exception {
        engine.syncFile(FILE);
        agentAddsATransaction("Agent kauft ein");
        engine.syncFile(FILE);

        TxBE added = null;
        for (TxBE tx : controller.getModel().getAssetAccountByName("Girokonto").getTxList()) {
            if (tx.getDescription().equals("Agent kauft ein")) added = tx;
        }
        assertNotNull(added);
        assertFalse(added.getId().isEmpty());
        assertTrue(added.getModified().getTime() > 0);
        // and the cloud copy carries the same id
        JSONArray remoteTx = new JSONObject(cloud.files.get(FILE))
                .getJSONArray("assets").getJSONObject(0).getJSONArray("tx");
        assertEquals(added.getId(), remoteTx.getJSONObject(1).getString("i"));
    }

    @Test
    public void aChangeToAFileThatIsNotOpen_doesNotTouchTheModel() throws Exception {
        engine.syncFile(FILE);
        String other = "2026-07-User.jso";
        cloud.write(other, cloud.files.get(FILE));

        engine.syncFile(other);

        assertTrue(disk.files.containsKey(other));
        assertEquals(0, reloads);
    }
}
