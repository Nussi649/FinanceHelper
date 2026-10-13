package com.privat.pitz.financehelper.sync;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

/**
 * The shared copy of the save files (OneDrive, later). The sync engine only ever talks to this
 * interface, so everything above it can be tested against an in-memory fake.
 */
public interface RemoteStore {

    final class RemoteFile {
        public final String content;
        /** Opaque version tag; changes whenever the file's content does. */
        public final String etag;
        public final Date modified;

        public RemoteFile(String content, String etag, Date modified) {
            this.content = content;
            this.etag = etag;
            this.modified = modified;
        }
    }

    /** Thrown by {@link #put} when the file is no longer the version the caller based its write on. */
    class EtagMismatchException extends IOException {
        public EtagMismatchException(String message) {
            super(message);
        }
    }

    /**
     * The save files in the folder (excluding the trash) with their current etags, in one request.
     * Comparing these with the etags remembered from the last sync is how unchanged files are
     * skipped without being downloaded.
     */
    Map<String, String> listEtags() throws IOException;

    /** @return null if the file does not exist */
    RemoteFile get(String name) throws IOException;

    /**
     * Writes the file only if it is still the version {@code expectedEtag} names, so two writers
     * cannot silently overwrite each other.
     *
     * @param expectedEtag the etag read before merging, or null to demand that the file not exist yet
     * @return the etag of the file as just written
     * @throws EtagMismatchException if someone else wrote in between - re-read, re-merge, retry
     */
    String put(String name, String content, String expectedEtag) throws IOException;

    /** Stores a copy of a file that is about to be overwritten, in the trash folder. */
    void putTrash(String trashName, String content) throws IOException;
}
