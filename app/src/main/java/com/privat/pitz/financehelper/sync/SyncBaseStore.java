package com.privat.pitz.financehelper.sync;

import java.io.IOException;

/**
 * Remembers, per file, the version both sides last agreed on - the "base" of the next three-way
 * merge. Must live outside the save files' own storage and survive app restarts; losing it only
 * costs precision (the next sync falls back to matching by content), never data.
 */
public interface SyncBaseStore {

    /** @return null if this file has never been synced */
    String getBase(String name) throws IOException;

    void setBase(String name, String content) throws IOException;
}
