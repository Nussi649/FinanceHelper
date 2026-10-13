package com.privat.pitz.financehelper.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.privat.pitz.financehelper.core.InMemorySavefileStorage;

public class StorageLocalFilesTest {

    private static final String FILE = "2026-08-User.jso";

    private InMemorySavefileStorage files;
    private InMemorySavefileStorage trash;
    private List<String> replaced;
    private StorageLocalFiles local;

    @Before
    public void setUp() {
        files = new InMemorySavefileStorage();
        trash = new InMemorySavefileStorage();
        replaced = new ArrayList<>();
        local = new StorageLocalFiles(files, trash, Runnable::run, replaced::add);
    }

    @Test
    public void read_missingFile_isNull() throws Exception {
        assertNull(local.read(FILE));
    }

    @Test
    public void read_returnsWhatTheStorageReturns() throws Exception {
        files.write(FILE, "abc");
        assertEquals("abc", local.read(FILE));
    }

    @Test
    public void replace_whenUnchanged_writesAndNotifiesTheListener() throws Exception {
        files.write(FILE, "old");

        assertTrue(local.replaceIfUnchanged(FILE, "old", "new"));

        assertEquals("new", files.read(FILE));
        assertEquals(1, replaced.size());
        assertEquals(FILE, replaced.get(0));
    }

    @Test
    public void replace_whenTheFileChangedMeanwhile_changesNothing() throws Exception {
        files.write(FILE, "changed by the app");

        assertFalse(local.replaceIfUnchanged(FILE, "old", "new"));

        assertEquals("changed by the app", files.read(FILE));
        assertTrue(replaced.isEmpty());
    }

    @Test
    public void replace_ofAFileThatShouldNotExist_failsIfItDoes() throws Exception {
        files.write(FILE, "already there");
        assertFalse(local.replaceIfUnchanged(FILE, null, "new"));
        assertEquals("already there", files.read(FILE));
    }

    @Test
    public void replace_creatingANewFile_works() throws Exception {
        assertTrue(local.replaceIfUnchanged(FILE, null, "new"));
        assertEquals("new", files.read(FILE));
    }

    @Test
    public void aFailingListener_surfacesAsIOException() throws Exception {
        local = new StorageLocalFiles(files, trash, Runnable::run, name -> {
            throw new IOException("reload failed");
        });
        files.write(FILE, "old");
        try {
            local.replaceIfUnchanged(FILE, "old", "new");
            fail("expected IOException");
        } catch (IOException e) {
            assertEquals("reload failed", e.getMessage());
        }
    }

    @Test
    public void theReplaceRunsOnTheApplierThread_andTheCallerWaitsForIt() throws Exception {
        ExecutorService applierThread = Executors.newSingleThreadExecutor();
        try {
            final Thread[] ranOn = new Thread[1];
            local = new StorageLocalFiles(files, trash, applierThread,
                    name -> ranOn[0] = Thread.currentThread());
            files.write(FILE, "old");

            assertTrue(local.replaceIfUnchanged(FILE, "old", "new"));

            assertEquals("new", files.read(FILE));
            assertTrue(ranOn[0] != Thread.currentThread());
        } finally {
            applierThread.shutdownNow();
        }
    }

    @Test
    public void putTrash_goesToTheTrashStorageNotTheSaveFiles() throws Exception {
        local.putTrash(FILE + ".20261013T140500Z", "old content");
        assertEquals("old content", trash.read(FILE + ".20261013T140500Z"));
        assertTrue(files.list().isEmpty());
    }
}
