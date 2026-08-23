package com.privat.pitz.financehelper.core;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

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
 * save files into/out of a zip at a user-picked Uri, and pushing/pulling them unzipped to/from a
 * user-picked folder. This is deliberately not unit-tested: it is the one class that keeps Android
 * dependencies ({@link Context}, {@link Uri}, {@code ContentResolver}, {@link DocumentFile}) - the
 * pure parts ({@link Util#copyStream}, {@link Util#isSyncableName}) were already extracted, and
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

    // pushes all save files (and app settings) as individual, unzipped files into a SAF tree Uri
    // (e.g. a folder inside a cloud-sync app like Google Drive), overwriting any same-named file
    // already there. This is the "no zip" counterpart to exportAllSavefilesToUri, meant to let a
    // remote tool/agent read and edit the plain JSON files directly.
    public int pushSavefilesToFolder(Uri treeUri) throws IOException {
        DocumentFile treeDir = DocumentFile.fromTreeUri(context, treeUri);
        if (treeDir == null || !treeDir.canWrite())
            throw new IOException("Cannot write to the selected sync folder");
        List<String> names = storage.list();
        int count = 0;
        for (String name : names) {
            if (!Util.isSyncableName(name))
                continue;
            // remove any existing file with the same name so createFile doesn't produce a duplicate
            DocumentFile existing = treeDir.findFile(name);
            if (existing != null)
                existing.delete();
            DocumentFile target = treeDir.createFile("application/octet-stream", name);
            if (target == null)
                continue;
            OutputStream os = context.getContentResolver().openOutputStream(target.getUri());
            if (os == null)
                continue;
            try (OutputStream out = os; InputStream is = storage.openRead(name)) {
                Util.copyStream(is, out);
            }
            count++;
        }
        return count;
    }

    // pulls all save files (and app settings) from a SAF tree Uri back into internal storage,
    // overwriting local files of the same name. Counterpart to pushSavefilesToFolder.
    public int pullSavefilesFromFolder(Uri treeUri) throws IOException {
        DocumentFile treeDir = DocumentFile.fromTreeUri(context, treeUri);
        if (treeDir == null || !treeDir.canRead())
            throw new IOException("Cannot read from the selected sync folder");
        int count = 0;
        for (DocumentFile child : treeDir.listFiles()) {
            if (child.isDirectory())
                continue;
            String name = child.getName();
            if (!Util.isSyncableName(name))
                continue;
            InputStream is = context.getContentResolver().openInputStream(child.getUri());
            if (is == null)
                continue;
            try (InputStream in = is; OutputStream out = storage.openWrite(name)) {
                Util.copyStream(in, out);
            }
            count++;
        }
        return count;
    }
}
