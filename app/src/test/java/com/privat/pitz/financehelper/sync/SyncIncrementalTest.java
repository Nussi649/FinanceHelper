package com.privat.pitz.financehelper.sync;

import static com.privat.pitz.financehelper.sync.Docs.account;
import static com.privat.pitz.financehelper.sync.Docs.doc;
import static com.privat.pitz.financehelper.sync.Docs.find;
import static com.privat.pitz.financehelper.sync.Docs.tx;
import static com.privat.pitz.financehelper.sync.Docs.txOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import com.privat.pitz.financehelper.sync.SyncEngine.FileOutcome;
import com.privat.pitz.financehelper.sync.SyncEngine.Status;

/** Only what changed costs network traffic. */
public class SyncIncrementalTest {

    private static final String FILE = "2026-10-User.jso";
    private static final String OLD = "2026-03-User.jso";
    private static final String M1 = "2026-10-01T10:00:00Z";
    private static final String M2 = "2026-10-02T10:00:00Z";

    private Fakes.Local local;
    private Fakes.Remote remote;
    private Fakes.Base base;
    private SyncEngine engine;

    @Before
    public void setUp() {
        local = new Fakes.Local();
        remote = new Fakes.Remote();
        base = new Fakes.Base();
        engine = new SyncEngine(local, remote, base);
    }

    private static String text(JSONObject o) {
        try {
            return o.toString(4);
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A file that has been through one full sync, so ids, base and etag exist. */
    private void syncedFile(String name, JSONObject content) {
        local.files.put(name, text(content));
        FileOutcome o = engine.syncFile(name);
        assertTrue(o.toString(), o.status != Status.ERROR);
        resetCounters();
    }

    private void resetCounters() {
        remote.gets = 0;
        remote.puts = 0;
        remote.lists = 0;
        local.writes = 0;
    }

    private JSONObject localDoc(String name) throws Exception {
        return new JSONObject(local.files.get(name));
    }

    private JSONObject remoteDoc(String name) throws Exception {
        return new JSONObject(remote.files.get(name));
    }

    private static String idOfFirstTx(JSONObject d, String account) throws Exception {
        return txOf(d, account).getJSONObject(0).getString("i");
    }

    @Test
    public void nothingChanged_costsOneListRequest_andNothingElse() throws Exception {
        syncedFile(FILE, doc(account("Giro", tx("a", "x", "1.00", M1))));
        syncedFile(OLD, doc(account("Giro", tx("b", "y", "2.00", M1))));

        Map<String, FileOutcome> out = engine.syncAll();

        assertEquals(2, out.size());
        for (FileOutcome o : out.values()) assertEquals(o.toString(), Status.UNCHANGED, o.status);
        assertEquals(1, remote.lists);
        assertEquals(0, remote.gets);
        assertEquals(0, remote.puts);
        assertEquals(0, local.writes);
    }

    @Test
    public void aLocalChange_isPushedWithoutDownloadingFirst() throws Exception {
        syncedFile(FILE, doc(account("Giro", tx("a", "x", "1.00", M1))));
        String id = idOfFirstTx(localDoc(FILE), "Giro");
        local.files.put(FILE, text(doc(account("Giro", tx(id, "x", "1.00", M1), tx("n", "neu", "-5.00", M2)))));

        FileOutcome o = engine.syncAll().get(FILE);

        assertEquals(o.toString(), Status.PUSHED, o.status);
        assertEquals(0, remote.gets);
        assertEquals(1, remote.puts);
        assertNotNull(find(txOf(remoteDoc(FILE), "Giro"), "n"));
    }

    @Test
    public void afterAPush_theNextRunIsFreeAgain() throws Exception {
        syncedFile(FILE, doc(account("Giro", tx("a", "x", "1.00", M1))));
        String id = idOfFirstTx(localDoc(FILE), "Giro");
        local.files.put(FILE, text(doc(account("Giro", tx(id, "x", "1.00", M1), tx("n", "neu", "-5.00", M2)))));
        engine.syncAll();
        resetCounters();

        FileOutcome o = engine.syncAll().get(FILE);

        assertEquals(Status.UNCHANGED, o.status);
        assertEquals(0, remote.gets);
        assertEquals(0, remote.puts);
    }

    @Test
    public void aRemoteChange_isDownloadedOnce_andNothingIsWritten() throws Exception {
        syncedFile(FILE, doc(account("Giro", tx("a", "x", "1.00", M1))));
        String id = idOfFirstTx(localDoc(FILE), "Giro");
        remote.write(FILE, text(doc(account("Giro", tx(id, "x", "1.00", M1), tx("g", "Agent", "-9.00", M2)))));

        FileOutcome o = engine.syncAll().get(FILE);

        assertEquals(o.toString(), Status.PULLED, o.status);
        assertEquals(1, remote.gets);
        assertEquals(0, remote.puts);
        assertNotNull(find(txOf(localDoc(FILE), "Giro"), "g"));
    }

    @Test
    public void onlyTheChangedFileIsTouched() throws Exception {
        syncedFile(FILE, doc(account("Giro", tx("a", "x", "1.00", M1))));
        syncedFile(OLD, doc(account("Giro", tx("b", "y", "2.00", M1))));
        String id = idOfFirstTx(localDoc(FILE), "Giro");
        remote.write(FILE, text(doc(account("Giro", tx(id, "x", "1.00", M1), tx("g", "Agent", "-9.00", M2)))));

        engine.syncAll();

        assertEquals(1, remote.gets);   // not 2
    }

    @Test
    public void reformattedButEqualLocalFile_isNotPushed_andSettlesAfterOneRun() throws Exception {
        syncedFile(FILE, doc(account("Giro", tx("a", "x", "1.00", M1))));
        // the app saved the same content again, laid out differently
        local.files.put(FILE, localDoc(FILE).toString(1));

        FileOutcome first = engine.syncAll().get(FILE);
        assertEquals(first.toString(), Status.UNCHANGED, first.status);
        assertEquals(0, remote.puts);
        assertEquals(0, remote.gets);

        resetCounters();
        engine.syncAll();
        assertEquals(0, remote.gets);
        assertEquals(0, remote.puts);
    }

    @Test
    public void anUnknownEtag_forcesOneFullReadButLosesNothing() throws Exception {
        syncedFile(FILE, doc(account("Giro", tx("a", "x", "1.00", M1))));
        base.etags.clear();

        FileOutcome o = engine.syncAll().get(FILE);

        assertEquals(o.toString(), Status.UNCHANGED, o.status);
        assertEquals(1, remote.gets);
        assertEquals(1, txOf(localDoc(FILE), "Giro").length());
        resetCounters();
        engine.syncAll();
        assertEquals(0, remote.gets);   // and from here on it is remembered again
    }

    @Test
    public void aFileDeletedInTheCloud_isUploadedAgain() throws Exception {
        syncedFile(FILE, doc(account("Giro", tx("a", "x", "1.00", M1))));
        remote.files.remove(FILE);
        remote.versions.remove(FILE);

        FileOutcome o = engine.syncAll().get(FILE);

        assertEquals(o.toString(), Status.PUSHED, o.status);
        assertEquals(1, txOf(remoteDoc(FILE), "Giro").length());
    }

    // region syncLocalChanges: "the app just saved"

    @Test
    public void localChangesSync_withNothingChanged_makesNoRequestAtAll() throws Exception {
        syncedFile(FILE, doc(account("Giro", tx("a", "x", "1.00", M1))));

        Map<String, FileOutcome> out = engine.syncLocalChanges(Collections.singletonList(FILE));

        assertEquals(Status.UNCHANGED, out.get(FILE).status);
        assertEquals(0, remote.lists);
        assertEquals(0, remote.gets);
        assertEquals(0, remote.puts);
    }

    @Test
    public void localChangesSync_pushesWithASingleConditionalWrite() throws Exception {
        syncedFile(FILE, doc(account("Giro", tx("a", "x", "1.00", M1))));
        String id = idOfFirstTx(localDoc(FILE), "Giro");
        local.files.put(FILE, text(doc(account("Giro", tx(id, "x", "1.00", M1), tx("n", "neu", "-5.00", M2)))));

        FileOutcome o = engine.syncLocalChanges(Collections.singletonList(FILE)).get(FILE);

        assertEquals(o.toString(), Status.PUSHED, o.status);
        assertEquals(0, remote.lists);
        assertEquals(0, remote.gets);
        assertEquals(1, remote.puts);
    }

    @Test
    public void localChangesSync_whenTheAgentWroteMeanwhile_fallsBackToAProperMerge() throws Exception {
        syncedFile(FILE, doc(account("Giro", tx("a", "x", "1.00", M1))));
        String id = idOfFirstTx(localDoc(FILE), "Giro");
        // the phone adds an entry; the agent saved a different one that this device has not seen
        local.files.put(FILE, text(doc(account("Giro", tx(id, "x", "1.00", M1), tx("p", "Phone", "-1.00", M2)))));
        remote.write(FILE, text(doc(account("Giro", tx(id, "x", "1.00", M1), tx("g", "Agent", "-2.00", M2)))));

        FileOutcome o = engine.syncLocalChanges(Collections.singletonList(FILE)).get(FILE);

        assertEquals(o.toString(), Status.MERGED, o.status);
        assertEquals(1, remote.gets);   // read only because the conditional write was refused
        assertNotNull(find(txOf(remoteDoc(FILE), "Giro"), "p"));
        assertNotNull(find(txOf(remoteDoc(FILE), "Giro"), "g"));
        assertNotNull(find(txOf(localDoc(FILE), "Giro"), "g"));
    }

    @Test
    public void localChangesSync_aLocalDeletion_stillLeavesATrashCopyOfTheCloudFile() throws Exception {
        syncedFile(FILE, doc(account("Giro", tx("a", "x", "1.00", M1), tx("b", "y", "2.00", M1))));
        String idA = idOfFirstTx(localDoc(FILE), "Giro");
        local.files.put(FILE, text(doc(account("Giro", tx(idA, "x", "1.00", M1)))));

        engine.syncLocalChanges(Collections.singletonList(FILE));

        assertEquals(1, remote.trash.size());
        assertNull(find(txOf(remoteDoc(FILE), "Giro"), "b"));
    }

    @Test
    public void localChangesSync_ignoresNamesThatAreNotSaveFiles() {
        Map<String, FileOutcome> out = engine.syncLocalChanges(Collections.singletonList("settings.json"));
        assertTrue(out.isEmpty());
    }

    @Test
    public void aFileThatWasNeverSynced_isPushedByALocalChangesSyncToo() throws Exception {
        local.files.put(FILE, text(doc(account("Giro", tx("a", "x", "1.00", M1)))));

        FileOutcome o = engine.syncLocalChanges(Collections.singletonList(FILE)).get(FILE);

        assertTrue(o.toString(), o.status != Status.ERROR);
        assertEquals(1, txOf(remoteDoc(FILE), "Giro").length());
    }

    // endregion
}
