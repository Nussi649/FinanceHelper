package com.privat.pitz.financehelper.sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory stand-ins for the three things the sync engine talks to. */
public final class Fakes {
    private Fakes() {
    }

    public static final class Remote implements RemoteStore {
        public final Map<String, String> files = new LinkedHashMap<>();
        public final Map<String, Integer> versions = new HashMap<>();
        public final Map<String, String> trash = new LinkedHashMap<>();
        public Date modified = new Date(1_786_000_000_000L);
        public int puts = 0;
        public boolean failTrash = false;
        /** Runs just before every put - the place to simulate the other writer getting in first. */
        public Runnable beforePut = null;

        public void write(String name, String content) {
            files.put(name, content);
            versions.merge(name, 1, Integer::sum);
        }

        public String etag(String name) {
            return versions.containsKey(name) ? "v" + versions.get(name) : null;
        }

        @Override
        public List<String> listFiles() {
            return new ArrayList<>(files.keySet());
        }

        @Override
        public RemoteFile get(String name) {
            return files.containsKey(name) ? new RemoteFile(files.get(name), etag(name), modified) : null;
        }

        @Override
        public String put(String name, String content, String expectedEtag) throws IOException {
            if (beforePut != null) {
                Runnable r = beforePut;
                beforePut = null;
                r.run();
            }
            String current = etag(name);
            boolean ok = expectedEtag == null ? current == null : expectedEtag.equals(current);
            if (!ok) throw new EtagMismatchException("etag " + expectedEtag + " != " + current);
            puts++;
            write(name, content);
            return etag(name);
        }

        @Override
        public void putTrash(String trashName, String content) throws IOException {
            if (failTrash) throw new IOException("trash unavailable");
            trash.put(trashName, content);
        }
    }

    public static final class Local implements LocalFiles {
        public final Map<String, String> files = new LinkedHashMap<>();
        public final Map<String, String> trash = new LinkedHashMap<>();
        public Date modified = new Date(1_786_000_000_000L);
        public int writes = 0;
        /** Runs just before every replaceIfUnchanged - simulates the app saving mid-sync. */
        public Runnable beforeReplace = null;

        @Override
        public List<String> listFiles() {
            return new ArrayList<>(files.keySet());
        }

        @Override
        public String read(String name) {
            return files.get(name);
        }

        @Override
        public Date lastModified(String name) {
            return modified;
        }

        @Override
        public boolean replaceIfUnchanged(String name, String expected, String content) {
            if (beforeReplace != null) {
                Runnable r = beforeReplace;
                beforeReplace = null;
                r.run();
            }
            String current = files.get(name);
            boolean ok = expected == null ? current == null : expected.equals(current);
            if (!ok) return false;
            writes++;
            files.put(name, content);
            return true;
        }

        @Override
        public void putTrash(String trashName, String content) {
            trash.put(trashName, content);
        }
    }

    public static final class Base implements SyncBaseStore {
        public final Map<String, String> bases = new HashMap<>();

        @Override
        public String getBase(String name) {
            return bases.get(name);
        }

        @Override
        public void setBase(String name, String content) {
            bases.put(name, content);
        }
    }
}
