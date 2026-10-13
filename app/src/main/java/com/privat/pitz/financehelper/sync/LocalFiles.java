package com.privat.pitz.financehelper.sync;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/** The device's own save files, as seen by the sync engine. */
public interface LocalFiles {

    List<String> listFiles();

    /** @return null if the file does not exist */
    String read(String name) throws IOException;

    Date lastModified(String name);

    /**
     * Replaces the file only if it still has the content the caller merged from, so a save made by
     * the app while the sync was running is never overwritten with an older merge.
     *
     * @param expected the content that was read, or null to demand that the file not exist yet
     * @return false if the file changed in the meantime - the caller re-reads and merges again
     */
    boolean replaceIfUnchanged(String name, String expected, String content) throws IOException;

    /** Stores a copy of a file that is about to be overwritten, in the local trash. */
    void putTrash(String trashName, String content) throws IOException;
}
