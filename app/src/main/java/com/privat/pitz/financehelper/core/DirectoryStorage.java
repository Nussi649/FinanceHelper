package com.privat.pitz.financehelper.core;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link SavefileStorage} backed by a plain directory on disk. Android constructs this from
 * {@code getFilesDir()}; a test constructs it from a temp folder - it is the same class both ways.
 */
public class DirectoryStorage implements SavefileStorage {
    private final File dir;

    public DirectoryStorage(File dir) {
        this.dir = dir;
    }

    /**
     * Writes to a scratch file and renames it over the target, so a reader (the cloud sync runs on
     * its own thread) sees either the old or the new content, never half of the new. The scratch
     * name deliberately matches no save-file pattern.
     */
    @Override
    public synchronized void write(String name, String data) throws IOException {
        File file = new File(dir, name);
        File scratch = new File(dir, "write-" + System.nanoTime() + ".part");
        try {
            try (FileWriter writer = new FileWriter(scratch)) {
                writer.append(data);
                writer.flush();
            }
            Files.move(scratch.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (scratch.exists()) {
                scratch.delete();
            }
        }
    }

    @Override
    public String read(String name) throws IOException {
        StringBuilder data = new StringBuilder();

        File file = new File(dir, name);
        FileReader fReader;
        try {
            fReader = new FileReader(file);
        } catch (FileNotFoundException e) {
            Log.println(Log.ERROR, "load_file",
                    String.format("Error reading file: File not found: %s", e));
            throw e;
        }
        BufferedReader reader = new BufferedReader(fReader);
        String line;
        while ((line = reader.readLine()) != null) {
            data.append(line).append("\n");
        }
        reader.close();
        fReader.close();
        return data.toString();
    }

    @Override
    public InputStream openRead(String name) throws IOException {
        return new FileInputStream(new File(dir, name));
    }

    @Override
    public OutputStream openWrite(String name) throws IOException {
        return new FileOutputStream(new File(dir, name));
    }

    @Override
    public boolean exists(String name) {
        return new File(dir, name).exists();
    }

    @Override
    public boolean delete(String name) {
        return new File(dir, name).delete();
    }

    @Override
    public List<String> list() {
        File[] files = dir.listFiles(File::isFile);
        List<String> names = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (!f.getName().endsWith(".part")) names.add(f.getName());
            }
        }
        return names;
    }

    @Override
    public long lastModified(String name) {
        File file = new File(dir, name);
        return file.exists() ? file.lastModified() : -1L;
    }
}
