package com.privat.pitz.financehelper.sync.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import com.privat.pitz.financehelper.sync.RemoteStore;
import com.privat.pitz.financehelper.sync.RemoteStore.EtagMismatchException;

public class GraphRemoteStoreTest {

    private static final String FILE = "2026-08-User.jso";

    private FakeGraph graph;
    private int invalidated;
    private GraphRemoteStore store;

    @Before
    public void setUp() {
        graph = new FakeGraph();
        invalidated = 0;
        TokenProvider tokens = new TokenProvider() {
            @Override
            public String getAccessToken() {
                return FakeGraph.TOKEN;
            }

            @Override
            public void invalidate() {
                invalidated++;
            }
        };
        store = new GraphRemoteStore(graph, tokens);
    }

    // region listing

    @Test
    public void listFiles_returnsTheFolderFilesOnly_notTheTrash() throws Exception {
        graph.put(FILE, "{}");
        graph.put("2026-09-User.jso", "{}");
        graph.put("trash/" + FILE + ".20261013T140500Z", "{}");

        List<String> names = store.listFiles();

        assertEquals(new HashSet<>(java.util.Arrays.asList(FILE, "2026-09-User.jso")), new HashSet<>(names));
    }

    @Test
    public void listFiles_followsPaging() throws Exception {
        for (int i = 1; i <= 5; i++) graph.put("2026-0" + i + "-User.jso", "{}");
        graph.pageSize = 2;

        assertEquals(5, store.listFiles().size());
    }

    @Test
    public void listFiles_ofAnEmptyFolder_isEmpty() throws Exception {
        assertEquals(Collections.emptyList(), store.listFiles());
    }

    // endregion

    // region get

    @Test
    public void get_missingFile_isNull() throws Exception {
        assertNull(store.get(FILE));
    }

    @Test
    public void get_returnsContentEtagAndModifiedTime() throws Exception {
        graph.put(FILE, "{\"a\":1}");

        RemoteStore.RemoteFile f = store.get(FILE);

        assertEquals("{\"a\":1}", f.content);
        assertEquals(FakeGraph.etag(graph.items.get(FILE)), f.etag);
        assertEquals(1_791_900_300_000L, f.modified.getTime());   // 2026-10-13T14:05:00Z
    }

    @Test
    public void get_downloadsFromTheRedirectWithoutTheBearerToken() throws Exception {
        graph.put(FILE, "x");

        assertEquals("x", store.get(FILE).content);

        assertEquals(1, graph.downloads.size());
        assertFalse(graph.downloads.get(0).headers.containsKey("Authorization"));
    }

    @Test
    public void get_ofAFolder_isAnError() throws Exception {
        // a folder answers the metadata call without a "file" facet
        graph = new FakeGraph() {
            @Override
            public Response execute(Request request) throws IOException {
                if (request.url.contains("$select=id,eTag")) {
                    return new Response(200, new java.util.LinkedHashMap<>(),
                            "{\"id\":\"1\",\"eTag\":\"e\",\"folder\":{}}".getBytes());
                }
                return super.execute(request);
            }
        };
        store = new GraphRemoteStore(graph, new TokenProvider() {
            @Override
            public String getAccessToken() {
                return FakeGraph.TOKEN;
            }

            @Override
            public void invalidate() {
            }
        });
        try {
            store.get(FILE);
            fail("expected an IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("keine Datei"));
        }
    }

    @Test
    public void namesWithSpacesAndUmlauts_roundTrip() throws Exception {
        String name = "2026-08-Jörg Müller.jso";
        store.put(name, "hallo", null);

        assertEquals("hallo", store.get(name).content);
        assertTrue(graph.items.containsKey(name));
    }

    // endregion

    // region conditional writes

    @Test
    public void put_createsAFile_andReturnsItsEtag() throws Exception {
        String etag = store.put(FILE, "one", null);

        assertEquals(FakeGraph.etag(graph.items.get(FILE)), etag);
        assertEquals("one", graph.items.get(FILE).content);
    }

    @Test
    public void put_createOnly_failsIfTheFileAppearedMeanwhile() throws Exception {
        graph.put(FILE, "someone else");
        try {
            store.put(FILE, "mine", null);
            fail("expected EtagMismatchException");
        } catch (EtagMismatchException expected) {
            assertEquals("someone else", graph.items.get(FILE).content);
        }
    }

    @Test
    public void put_withTheCurrentEtag_succeeds_andGivesANewEtag() throws Exception {
        String first = store.put(FILE, "one", null);

        String second = store.put(FILE, "two", first);

        assertNotEquals(first, second);
        assertEquals("two", graph.items.get(FILE).content);
    }

    @Test
    public void put_withAStaleEtag_isRefused_andChangesNothing() throws Exception {
        String first = store.put(FILE, "one", null);
        graph.put(FILE, "agent was here");      // the other writer

        try {
            store.put(FILE, "two", first);
            fail("expected EtagMismatchException");
        } catch (EtagMismatchException expected) {
            assertEquals("agent was here", graph.items.get(FILE).content);
        }
    }

    @Test
    public void put_expectingAFileThatWasDeleted_isAMismatch() throws Exception {
        try {
            store.put(FILE, "two", "\"{AB12},1\"");
            fail("expected EtagMismatchException");
        } catch (EtagMismatchException expected) {
            assertTrue(graph.items.isEmpty());
        }
    }

    @Test
    public void put_refusesFilesAboveTheSimpleUploadLimit() {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 4 * 1024 * 1024 + 1; i++) big.append('x');
        try {
            store.put(FILE, big.toString(), null);
            fail("expected an IOException");
        } catch (IOException expected) {
            assertTrue(graph.items.isEmpty());
        }
    }

    // endregion

    // region trash

    @Test
    public void putTrash_storesInTheTrashFolder() throws Exception {
        store.putTrash(FILE + ".20261013T140500Z", "old");

        assertEquals("old", graph.items.get("trash/" + FILE + ".20261013T140500Z").content);
        assertFalse(graph.items.containsKey(FILE + ".20261013T140500Z"));
    }

    // endregion

    // region auth and errors

    @Test
    public void aRejectedToken_isInvalidatedAndTheRequestRepeatedOnce() throws Exception {
        graph.put(FILE, "x");
        graph.reject401Times = 1;

        assertNotNull(store.get(FILE));

        assertEquals(1, invalidated);
    }

    @Test
    public void aTokenThatKeepsBeingRejected_endsInAnError() throws Exception {
        graph.reject401Times = 99;
        try {
            store.get(FILE);
            fail("expected an IOException");
        } catch (GraphRemoteStore.GraphException e) {
            assertEquals(401, e.status);
            assertEquals(1, invalidated);
        }
    }

    @Test
    public void throttling_isReportedWithTheRetryAfterHint() throws Exception {
        graph.forceStatus = 429;
        graph.forceRetryAfter = "17";
        try {
            store.put(FILE, "x", null);
            fail("expected an IOException");
        } catch (GraphRemoteStore.GraphException e) {
            assertEquals(429, e.status);
            assertTrue(e.getMessage(), e.getMessage().contains("17"));
        }
    }

    // endregion

    // region self test

    @Test
    public void verifyConditionalWrites_passesOnAFaithfulBackend_andLeavesNothingBehind() throws Exception {
        graph.put(FILE, "real data");

        store.verifyConditionalWrites();

        assertEquals(1, graph.items.size());
        assertEquals("real data", graph.items.get(FILE).content);
    }

    @Test
    public void verifyConditionalWrites_failsIfIfMatchIsIgnored() throws Exception {
        graph.enforceIfMatch = false;
        try {
            store.verifyConditionalWrites();
            fail("expected an IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("If-Match"));
            assertTrue(graph.items.isEmpty());
        }
    }

    @Test
    public void verifyConditionalWrites_failsIfCreateOnlyIsIgnored() throws Exception {
        graph.enforceCreateOnly = false;
        try {
            store.verifyConditionalWrites();
            fail("expected an IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("conflictBehavior"));
            assertTrue(graph.items.isEmpty());
        }
    }

    // endregion
}
