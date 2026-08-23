package com.privat.pitz.financehelper.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Abstraction over the plain-file save storage used by {@link Controller}, so Controller can be
 * unit-tested on a plain JVM (via an in-memory fake) without depending on Android's file APIs.
 * The Android-backed implementation is {@link DirectoryStorage}.
 */
public interface SavefileStorage {
    /** @throws java.io.FileNotFoundException if absent - callers rely on this specific type */
    String read(String name) throws IOException;
    void write(String name, String data) throws IOException;
    InputStream openRead(String name) throws IOException;
    OutputStream openWrite(String name) throws IOException;
    boolean exists(String name);
    boolean delete(String name);
    /** Names only, never null; empty list when the directory is empty or unreadable. */
    List<String> list();
}
