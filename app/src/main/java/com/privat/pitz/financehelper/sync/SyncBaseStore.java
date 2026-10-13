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

    /**
     * The etag the remote file had when {@link #getBase} was current. While the remote still has
     * this etag, the remote content is known to be the base, without downloading it.
     *
     * @return null if unknown
     */
    String getEtag(String name) throws IOException;

    /** Stores base and etag together; implementations must write the base first. */
    void setBase(String name, String content, String etag) throws IOException;
}
