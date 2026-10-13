package com.privat.pitz.financehelper.sync;

import static com.privat.pitz.financehelper.sync.Docs.account;
import static com.privat.pitz.financehelper.sync.Docs.doc;
import static com.privat.pitz.financehelper.sync.Docs.find;
import static com.privat.pitz.financehelper.sync.Docs.tx;
import static com.privat.pitz.financehelper.sync.Docs.txOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import com.privat.pitz.financehelper.sync.SyncEngine.FileOutcome;
import com.privat.pitz.financehelper.sync.SyncEngine.Status;

public class SyncEngineTest {

    private static final String FILE = "2026-08-User.jso";
    private static final String M1 = "2026-08-01T10:00:00Z";
    private static final String M2 = "2026-08-02T10:00:00Z";
    private static final String M3 = "2026-08-03T10:00:00Z";
    // 2026-10-13 14:05:00 UTC
    private static final long NOW = 1_791_900_300_000L;

    private Fakes.Local local;
    private Fakes.Remote remote;
    private Fakes.Base base;
    private SyncEngine engine;

    @Before
    public void setUp() {
        local = new Fakes.Local();
        remote = new Fakes.Remote();
        base = new Fakes.Base();
        engine = new SyncEngine(local, remote, base, () -> NOW);
    }

    private static String text(JSONObject o) {
        try {
            return o.toString(4);
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private JSONObject localDoc() throws Exception {
        return new JSONObject(local.files.get(FILE));
    }

    private JSONObject remoteDoc() throws Exception {
        return new JSONObject(remote.files.get(FILE));
    }

    /** Puts the same agreed version on both sides and records it as base. */
    private void agreeOn(JSONObject d) throws Exception {
        String t = text(d);
        local.files.put(FILE, t);
        remote.write(FILE, t);
        base.setBase(FILE, t, remote.etag(FILE));
    }

    // region basic flows

    @Test
    public void nothingAnywhere_isUnchanged() {
        assertEquals(Status.UNCHANGED, engine.syncFile(FILE).status);
    }

    @Test
    public void firstSync_localOnly_pushesAndGivesIdsToLegacyEntries() throws Exception {
        local.files.put(FILE, text(doc(account("Giro", tx(null, "Miete", "-850.00", null)))));

        FileOutcome o = engine.syncFile(FILE);

        assertEquals(o.toString(), Status.MERGED, o.status);   // ids were added, so local is rewritten too
        String id = txOf(remoteDoc(), "Giro").getJSONObject(0).getString("i");
        assertFalse(id.isEmpty());
        assertEquals(id, txOf(localDoc(), "Giro").getJSONObject(0).getString("i"));
        assertNotNull(base.getBase(FILE));
    }

    @Test
    public void firstSync_remoteOnly_pullsIt() throws Exception {
        remote.write(FILE, text(doc(account("Giro", tx("a", "Miete", "-850.00", M1)))));

        FileOutcome o = engine.syncFile(FILE);

        assertEquals(Status.PULLED, o.status);
        assertEquals(1, txOf(localDoc(), "Giro").length());
    }

    @Test
    public void localEdit_isPushed() throws Exception {
        agreeOn(doc(account("Giro", tx("a", "Miete", "-850.00", M1))));
        local.files.put(FILE, text(doc(account("Giro", tx("a", "Miete", "-860.00", M2)))));

        FileOutcome o = engine.syncFile(FILE);

        assertEquals(Status.PUSHED, o.status);
        assertEquals("-860.00", find(txOf(remoteDoc(), "Giro"), "a").getString("a"));
    }

    @Test
    public void agentEdit_appearsLocally() throws Exception {
        agreeOn(doc(account("Giro", tx("a", "Miete", "-850.00", M1))));
        remote.write(FILE, text(doc(account("Giro", tx("a", "Miete", "-850.00", M1), tx("b", "Agent", "-9.00", M2)))));

        FileOutcome o = engine.syncFile(FILE);

        assertEquals(Status.PULLED, o.status);
        assertEquals(2, txOf(localDoc(), "Giro").length());
    }

    @Test
    public void inSync_secondRunDoesNothing() throws Exception {
        agreeOn(doc(account("Giro", tx("a", "Miete", "-850.00", M1))));
        int puts = remote.puts;
        int writes = local.writes;

        FileOutcome o = engine.syncFile(FILE);

        assertEquals(Status.UNCHANGED, o.status);
        assertEquals(puts, remote.puts);
        assertEquals(writes, local.writes);
    }

    @Test
    public void bothSidesChange_areMerged() throws Exception {
        agreeOn(doc(account("Giro", tx("a", "x", "1.00", M1))));
        local.files.put(FILE, text(doc(account("Giro", tx("a", "x", "1.00", M1), tx("p", "Phone", "-1.00", M2)))));
        remote.write(FILE, text(doc(account("Giro", tx("a", "x", "1.00", M1), tx("g", "Agent", "-2.00", M2)))));

        FileOutcome o = engine.syncFile(FILE);

        assertEquals(Status.MERGED, o.status);
        assertEquals(3, txOf(localDoc(), "Giro").length());
        assertEquals(3, txOf(remoteDoc(), "Giro").length());
        assertEquals(local.files.get(FILE), remote.files.get(FILE));
    }

    @Test
    public void afterASync_theMergedTextIsTheNewBase() throws Exception {
        local.files.put(FILE, text(doc(account("Giro", tx("a", "x", "1.00", M1)))));
        engine.syncFile(FILE);
        assertEquals(remote.files.get(FILE), base.getBase(FILE));
    }

    // endregion

    // region concurrency

    @Test
    public void remoteChangedDuringTheSync_isReadAndMergedAgain_nothingIsOverwritten() throws Exception {
        agreeOn(doc(account("Giro", tx("a", "x", "1.00", M1))));
        local.files.put(FILE, text(doc(account("Giro", tx("a", "x", "1.00", M1), tx("p", "Phone", "-1.00", M2)))));
        // the agent writes between our read and our put
        remote.beforePut = () -> remote.write(FILE,
                text(doc(account("Giro", tx("a", "x", "1.00", M1), tx("g", "Agent", "-2.00", M2)))));

        FileOutcome o = engine.syncFile(FILE);

        assertEquals(o.toString(), Status.MERGED, o.status);
        assertNotNull(find(txOf(remoteDoc(), "Giro"), "g"));
        assertNotNull(find(txOf(remoteDoc(), "Giro"), "p"));
        assertEquals(local.files.get(FILE), remote.files.get(FILE));
    }

    @Test
    public void appSavedDuringTheSync_isNotOverwrittenWithTheOlderMerge() throws Exception {
        agreeOn(doc(account("Giro", tx("a", "x", "1.00", M1))));
        remote.write(FILE, text(doc(account("Giro", tx("a", "x", "1.00", M1), tx("g", "Agent", "-2.00", M2)))));
        local.beforeReplace = () -> local.files.put(FILE,
                text(doc(account("Giro", tx("a", "x", "1.00", M1), tx("p", "Phone", "-1.00", M3)))));

        FileOutcome o = engine.syncFile(FILE);

        assertTrue(o.toString(), o.status != Status.ERROR);
        assertNotNull(find(txOf(localDoc(), "Giro"), "p"));
        assertNotNull(find(txOf(localDoc(), "Giro"), "g"));
        assertEquals(local.files.get(FILE), remote.files.get(FILE));
    }

    @Test
    public void aWriterThatNeverStops_endsInAnErrorNotAnEndlessLoop() throws Exception {
        agreeOn(doc(account("Giro", tx("a", "x", "1.00", M1))));
        local.files.put(FILE, text(doc(account("Giro", tx("a", "x", "1.00", M1), tx("p", "Phone", "-1.00", M2)))));
        final int[] n = {0};
        Runnable keepWriting = new Runnable() {
            @Override
            public void run() {
                n[0]++;
                remote.write(FILE, "{\"assets\":[],\"budgets\":[],\"recur_tx\":[],\"income\":[],\"n\":" + n[0] + "}");
                remote.beforePut = this;
            }
        };
        remote.beforePut = keepWriting;

        FileOutcome o = engine.syncFile(FILE);

        assertEquals(Status.ERROR, o.status);
        assertEquals(SyncEngine.MAX_ATTEMPTS, n[0]);
    }

    // endregion

    // region trash

    @Test
    public void aLocalDeletion_keepsTheOldRemoteFileInTheTrash() throws Exception {
        JSONObject before = doc(account("Giro", tx("a", "x", "1.00", M1), tx("b", "y", "2.00", M1)));
        agreeOn(before);
        local.files.put(FILE, text(doc(account("Giro", tx("a", "x", "1.00", M1)))));

        engine.syncFile(FILE);

        assertNull(find(txOf(remoteDoc(), "Giro"), "b"));
        assertEquals(1, remote.trash.size());
        String trashName = remote.trash.keySet().iterator().next();
        assertEquals(FILE + ".20261013T140500Z", trashName);
        assertNotNull(find(txOf(new JSONObject(remote.trash.get(trashName)), "Giro"), "b"));
        assertTrue(local.trash.isEmpty());
    }

    @Test
    public void anAgentDeletion_keepsTheOldLocalFileInTheLocalTrash() throws Exception {
        agreeOn(doc(account("Giro", tx("a", "x", "1.00", M1), tx("b", "y", "2.00", M1))));
        remote.write(FILE, text(doc(account("Giro", tx("a", "x", "1.00", M1)))));

        engine.syncFile(FILE);

        assertNull(find(txOf(localDoc(), "Giro"), "b"));
        assertEquals(1, local.trash.size());
        assertTrue(remote.trash.isEmpty());
    }

    @Test
    public void aLosingConflictSide_isKeptInTheTrash() throws Exception {
        agreeOn(doc(account("Giro", tx("a", "x", "1.00", M1))));
        local.files.put(FILE, text(doc(account("Giro", tx("a", "x", "2.00", M3)))));
        remote.write(FILE, text(doc(account("Giro", tx("a", "x", "3.00", M2)))));

        FileOutcome o = engine.syncFile(FILE);

        assertEquals("2.00", find(txOf(remoteDoc(), "Giro"), "a").getString("a"));
        assertEquals(1, remote.trash.size());
        assertTrue(o.dropped > 0);
        assertFalse(o.notes.isEmpty());
    }

    @Test
    public void anOrdinarySync_writesNothingToTheTrash() throws Exception {
        agreeOn(doc(account("Giro", tx("a", "x", "1.00", M1))));
        local.files.put(FILE, text(doc(account("Giro", tx("a", "x", "1.00", M1), tx("p", "Phone", "-1.00", M2)))));

        engine.syncFile(FILE);

        assertTrue(remote.trash.isEmpty());
        assertTrue(local.trash.isEmpty());
    }

    @Test
    public void ifTheTrashCopyCannotBeStored_theFileIsLeftAlone() throws Exception {
        agreeOn(doc(account("Giro", tx("a", "x", "1.00", M1), tx("b", "y", "2.00", M1))));
        String before = remote.files.get(FILE);
        local.files.put(FILE, text(doc(account("Giro", tx("a", "x", "1.00", M1)))));
        remote.failTrash = true;

        FileOutcome o = engine.syncFile(FILE);

        assertEquals(Status.ERROR, o.status);
        assertEquals(before, remote.files.get(FILE));
    }

    // endregion

    // region bad input

    @Test
    public void invalidJsonInTheCloud_isNeverOverwritten() throws Exception {
        agreeOn(doc(account("Giro", tx("a", "x", "1.00", M1))));
        remote.write(FILE, "{ das ist kein json");
        local.files.put(FILE, text(doc(account("Giro", tx("a", "x", "1.00", M1), tx("p", "Phone", "-1.00", M2)))));

        FileOutcome o = engine.syncFile(FILE);

        assertEquals(Status.ERROR, o.status);
        assertEquals("{ das ist kein json", remote.files.get(FILE));
    }

    @Test
    public void invalidLocalJson_isNeverOverwritten() throws Exception {
        agreeOn(doc(account("Giro", tx("a", "x", "1.00", M1))));
        local.files.put(FILE, "kaputt");
        remote.write(FILE, text(doc(account("Giro", tx("a", "x", "1.00", M1), tx("g", "Agent", "-2.00", M2)))));

        FileOutcome o = engine.syncFile(FILE);

        assertEquals(Status.ERROR, o.status);
        assertEquals("kaputt", local.files.get(FILE));
    }

    @Test
    public void aMissingBase_neverLosesData() throws Exception {
        local.files.put(FILE, text(doc(account("Giro", tx("a", "x", "1.00", M1)))));
        remote.write(FILE, text(doc(account("Giro", tx("a", "x", "1.00", M1), tx("g", "Agent", "-2.00", M2)))));
        // base store is empty: as after a reinstall

        engine.syncFile(FILE);

        assertEquals(2, txOf(localDoc(), "Giro").length());
        assertEquals(2, txOf(remoteDoc(), "Giro").length());
    }

    // endregion

    // region syncAll

    @Test
    public void syncAll_coversFilesFromBothSides_andIgnoresOtherNames() throws Exception {
        local.files.put("2026-08-User.jso", text(doc(account("Giro", tx("a", "x", "1.00", M1)))));
        remote.write("2026-09-User.jso", text(doc(account("Giro", tx("b", "y", "2.00", M1)))));
        remote.write("notes.txt", "hallo");

        Map<String, FileOutcome> out = engine.syncAll();

        assertEquals(2, out.size());
        assertTrue(local.files.containsKey("2026-09-User.jso"));
        assertTrue(remote.files.containsKey("2026-08-User.jso"));
        assertFalse(local.files.containsKey("notes.txt"));
    }

    @Test
    public void syncAll_aBrokenFileDoesNotStopTheOthers() throws Exception {
        local.files.put("2026-07-User.jso", "kaputt");
        remote.write("2026-07-User.jso", text(doc(account("Giro"))));
        local.files.put("2026-08-User.jso", text(doc(account("Giro", tx("a", "x", "1.00", M1)))));

        Map<String, FileOutcome> out = engine.syncAll();

        assertEquals(Status.ERROR, out.get("2026-07-User.jso").status);
        assertTrue(out.get("2026-08-User.jso").status != Status.ERROR);
        assertTrue(remote.files.containsKey("2026-08-User.jso"));
    }

    // endregion
}
