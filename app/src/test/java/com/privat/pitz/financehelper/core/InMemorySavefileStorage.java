package com.privat.pitz.financehelper.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory {@link SavefileStorage} fake for JVM tests, so {@link Controller} can be exercised
 * without touching the real filesystem or Android APIs.
 */
public class InMemorySavefileStorage implements SavefileStorage {
    public final Map<String, byte[]> files = new LinkedHashMap<>();
    /** When true every write/openWrite throws IOException - exercises the revert paths. */
    public boolean failWrites = false;
    public int writeCount = 0;

    @Override
    public String read(String name) throws IOException {
        byte[] data = files.get(name);
        if (data == null)
            throw new FileNotFoundException("No such file: " + name);
        return new String(data, StandardCharsets.UTF_8);
    }

    @Override
    public void write(String name, String data) throws IOException {
        writeCount++;
        if (failWrites)
            throw new IOException("Simulated write failure");
        files.put(name, data.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public InputStream openRead(String name) throws IOException {
        byte[] data = files.get(name);
        if (data == null)
            throw new FileNotFoundException("No such file: " + name);
        return new ByteArrayInputStream(data);
    }

    @Override
    public OutputStream openWrite(String name) throws IOException {
        writeCount++;
        if (failWrites)
            throw new IOException("Simulated write failure");
        return new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                super.close();
                files.put(name, this.toByteArray());
            }
        };
    }

    @Override
    public boolean exists(String name) {
        return files.containsKey(name);
    }

    @Override
    public boolean delete(String name) {
        return files.remove(name) != null;
    }

    @Override
    public List<String> list() {
        return new ArrayList<>(files.keySet());
    }
}
