package com.privat.pitz.financehelper.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

import com.privat.pitz.financehelper.core.InMemorySavefileStorage;

public class StorageSyncBaseStoreTest {

    private static final String FILE = "2026-10-User.jso";

    private InMemorySavefileStorage storage;
    private StorageSyncBaseStore store;

    @Before
    public void setUp() {
        storage = new InMemorySavefileStorage();
        store = new StorageSyncBaseStore(storage);
    }

    @Test
    public void nothingStoredYet_isNull() throws Exception {
        assertNull(store.getBase(FILE));
        assertNull(store.getEtag(FILE));
    }

    @Test
    public void baseAndEtag_roundTrip() throws Exception {
        store.setBase(FILE, "{\"a\":1}", "\"{AB},3\"");

        assertEquals("{\"a\":1}", store.getBase(FILE));
        assertEquals("\"{AB},3\"", store.getEtag(FILE));
    }

    @Test
    public void storingWithoutAnEtag_forgetsTheOldOne() throws Exception {
        store.setBase(FILE, "one", "etag-1");
        store.setBase(FILE, "two", null);

        assertEquals("two", store.getBase(FILE));
        assertNull(store.getEtag(FILE));
    }

    @Test
    public void theEtagLivesNextToTheBase_notInsteadOfIt() throws Exception {
        store.setBase(FILE, "content", "etag-1");

        assertEquals(2, storage.list().size());
        assertEquals("content", storage.read(FILE));
    }
}
