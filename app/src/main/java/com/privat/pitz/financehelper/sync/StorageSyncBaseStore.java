package com.privat.pitz.financehelper.sync;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.privat.pitz.financehelper.core.SavefileStorage;

/** {@link SyncBaseStore} keeping one file per save file in a storage of its own. */
public class StorageSyncBaseStore implements SyncBaseStore {
    private final SavefileStorage storage;

    public StorageSyncBaseStore(SavefileStorage storage) {
        this.storage = storage;
    }

    @Override
    public String getBase(String name) throws IOException {
        if (!storage.exists(name)) return null;
        try {
            return storage.read(name);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public void setBase(String name, String content) throws IOException {
        storage.write(name, content);
    }
}
