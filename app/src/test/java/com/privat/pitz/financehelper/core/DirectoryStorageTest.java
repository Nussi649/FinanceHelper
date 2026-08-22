package com.privat.pitz.financehelper.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

public class DirectoryStorageTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private DirectoryStorage newStorage() {
        return new DirectoryStorage(tempFolder.getRoot());
    }

    @Test
    public void writeThenRead_roundTrips() throws IOException {
        DirectoryStorage storage = newStorage();
        storage.write("2026-08-User.jso", "hello world");

        String result = storage.read("2026-08-User.jso");

        assertEquals("hello world\n", result);
    }

    @Test
    public void read_missingFile_throwsFileNotFoundException() {
        DirectoryStorage storage = newStorage();
        try {
            storage.read("does-not-exist.jso");
            fail("expected FileNotFoundException");
        } catch (FileNotFoundException expected) {
            // expected
        } catch (IOException e) {
            fail("expected FileNotFoundException specifically, got: " + e);
        }
    }

    @Test
    public void list_emptyDirectory_returnsEmptyList() {
        DirectoryStorage storage = newStorage();

        List<String> names = storage.list();

        assertTrue(names.isEmpty());
    }

    @Test
    public void delete_removesFile() throws IOException {
        DirectoryStorage storage = newStorage();
        storage.write("2026-08-User.jso", "data");
        assertTrue(storage.exists("2026-08-User.jso"));

        boolean deleted = storage.delete("2026-08-User.jso");

        assertTrue(deleted);
        assertFalse(storage.exists("2026-08-User.jso"));
    }

    @Test
    public void exists_beforeAndAfterWrite() throws IOException {
        DirectoryStorage storage = newStorage();

        assertFalse(storage.exists("2026-08-User.jso"));

        storage.write("2026-08-User.jso", "data");

        assertTrue(storage.exists("2026-08-User.jso"));
    }
}
