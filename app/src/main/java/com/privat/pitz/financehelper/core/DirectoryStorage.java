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
import java.util.ArrayList;
import java.util.Arrays;
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

    @Override
    public void write(String name, String data) throws IOException {
        File file = new File(dir, name);
        if (!file.exists()) {
            file.createNewFile();
        }
        FileWriter writer = new FileWriter(file);
        writer.append(data);
        writer.flush();
        writer.close();
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
        String[] names = dir.list();
        return names == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(names));
    }
}
