package com.privat.pitz.financehelper.sync.graph;

import static com.privat.pitz.financehelper.sync.Docs.account;
import static com.privat.pitz.financehelper.sync.Docs.doc;
import static com.privat.pitz.financehelper.sync.Docs.find;
import static com.privat.pitz.financehelper.sync.Docs.tx;
import static com.privat.pitz.financehelper.sync.Docs.txOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import com.privat.pitz.financehelper.sync.Fakes;
import com.privat.pitz.financehelper.sync.SyncEngine;

/** The real engine over the real Graph store, with only the network faked. */
public class SyncOverGraphTest {

    private static final String FILE = "2026-08-User.jso";
    private static final String M1 = "2026-08-01T10:00:00Z";
    private static final String M2 = "2026-08-02T10:00:00Z";

    private FakeGraph graph;
    private Fakes.Local local;
    private SyncEngine engine;

    @Before
    public void setUp() {
        graph = new FakeGraph();
        local = new Fakes.Local();
        GraphRemoteStore store = new GraphRemoteStore(graph, new TokenProvider() {
            @Override
            public String getAccessToken() {
                return FakeGraph.TOKEN;
            }

            @Override
            public void invalidate() {
            }
        });
        engine = new SyncEngine(local, store, new Fakes.Base());
    }

    private static String text(JSONObject o) {
        try {
            return o.toString(4);
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void firstSync_uploadsTheLocalFile() throws Exception {
        local.files.put(FILE, text(doc(account("Giro", tx("a", "Miete", "-850.00", M1)))));

        SyncEngine.FileOutcome o = engine.syncFile(FILE);

        assertTrue(o.toString(), o.status != SyncEngine.Status.ERROR);
        assertEquals(1, txOf(new JSONObject(graph.items.get(FILE).content), "Giro").length());
    }

    @Test
    public void anAgentWritingBetweenOurReadAndOurWrite_losesNothingOnEitherSide() throws Exception {
        local.files.put(FILE, text(doc(account("Giro", tx("a", "x", "1.00", M1)))));
        engine.syncFile(FILE);

        // the phone adds an entry; while we upload, the agent saves a different one
        local.files.put(FILE, text(doc(account("Giro", tx("a", "x", "1.00", M1), tx("p", "Phone", "-1.00", M2)))));
        graph.beforePut = () -> graph.put(FILE, text(doc(account("Giro",
                tx("a", "x", "1.00", M1), tx("g", "Agent", "-2.00", M2)))));

        SyncEngine.FileOutcome o = engine.syncFile(FILE);

        assertEquals(o.toString(), SyncEngine.Status.MERGED, o.status);
        JSONObject remote = new JSONObject(graph.items.get(FILE).content);
        assertNotNull(find(txOf(remote, "Giro"), "p"));
        assertNotNull(find(txOf(remote, "Giro"), "g"));
        JSONObject onPhone = new JSONObject(local.files.get(FILE));
        assertNotNull(find(txOf(onPhone, "Giro"), "g"));
    }

    @Test
    public void aDeletion_leavesTheOldCloudFileInTheTrashFolder() throws Exception {
        local.files.put(FILE, text(doc(account("Giro", tx("a", "x", "1.00", M1), tx("b", "y", "2.00", M1)))));
        engine.syncFile(FILE);
        local.files.put(FILE, text(doc(account("Giro", tx("a", "x", "1.00", M1)))));

        engine.syncFile(FILE);

        boolean inTrash = false;
        for (String path : graph.items.keySet()) {
            if (path.startsWith("trash/" + FILE + ".")) inTrash = true;
        }
        assertTrue(inTrash);
    }
}
