package com.privat.pitz.financehelper.sync;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.privat.pitz.financehelper.core.SavefileStorage;

/** {@link SyncBaseStore} keeping one file per save file (plus one for its etag) in a storage of its own. */
public class StorageSyncBaseStore implements SyncBaseStore {
    private final SavefileStorage storage;

    public StorageSyncBaseStore(SavefileStorage storage) {
        this.storage = storage;
    }

    @Override
    public String getBase(String name) throws IOException {
        return readOrNull(name);
    }

    @Override
    public String getEtag(String name) throws IOException {
        String etag = readOrNull(etagFile(name));
        etag = etag == null ? null : etag.trim();
        return etag == null || etag.isEmpty() ? null : etag;
    }

    /**
     * Base first, etag second: a crash in between leaves a new base with an old etag, which only
     * makes the next sync download the file once more. The other order would leave an etag that
     * claims the remote holds a base it does not hold.
     */
    @Override
    public void setBase(String name, String content, String etag) throws IOException {
        storage.write(name, content);
        if (etag == null) {
            storage.delete(etagFile(name));
        } else {
            storage.write(etagFile(name), etag);
        }
    }

    private String readOrNull(String file) throws IOException {
        if (!storage.exists(file)) return null;
        try {
            return storage.read(file);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    private static String etagFile(String name) {
        return name + ".etag";
    }
}
