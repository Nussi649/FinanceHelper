package com.privat.pitz.financehelper.core;

import android.content.Context;
import android.net.Uri;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * SAF (Storage Access Framework) transfer cluster extracted from {@link Controller}: bundling all
 * save files into/out of a zip at a user-picked Uri. This is deliberately not unit-tested: it is the
 * one class that keeps Android dependencies ({@link Context}, {@link Uri}, {@code ContentResolver}) -
 * the pure parts ({@link Util#copyStream}, {@link Util#isSyncableName}) were already extracted, and
 * what remains here is thin SAF plumbing where a fake ContentResolver would only test the fake.
 */
public class SavefileTransferService {
    private final Context context;
    private final SavefileStorage storage;

    SavefileTransferService(Context context, SavefileStorage storage) {
        this.context = context;
        this.storage = storage;
    }

    // bundles all save files (and the app settings) into a single zip file at the given SAF Uri,
    // so it can be picked up by a file manager, cloud sync folder, or copied off the device over USB
    public int exportAllSavefilesToUri(Uri targetUri) throws IOException {
        List<String> names = storage.list();
        int count = 0;
        OutputStream os = context.getContentResolver().openOutputStream(targetUri);
        if (os == null)
            throw new IOException("Could not open output stream for target Uri");
        try (ZipOutputStream zos = new ZipOutputStream(os)) {
            for (String name : names) {
                if (!Util.isSyncableName(name))
                    continue;
                zos.putNextEntry(new ZipEntry(name));
                try (InputStream is = storage.openRead(name)) {
                    Util.copyStream(is, zos);
                }
                zos.closeEntry();
                count++;
            }
        }
        return count;
    }

    // extracts all save files (and app settings, if present) from a previously exported zip
    // file at the given SAF Uri back into internal storage
    public int importSavefilesFromZipUri(Uri sourceUri) throws IOException {
        int count = 0;
        InputStream is = context.getContentResolver().openInputStream(sourceUri);
        if (is == null)
            throw new IOException("Could not open input stream for source Uri");
        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // use only the plain file name to prevent path traversal ("zip slip") via entry names
                String name = new File(entry.getName()).getName();
                if (!Util.isSyncableName(name)) {
                    zis.closeEntry();
                    continue;
                }
                try (OutputStream os = storage.openWrite(name)) {
                    Util.copyStream(zis, os);
                }
                zis.closeEntry();
                count++;
            }
        }
        return count;
    }
}
