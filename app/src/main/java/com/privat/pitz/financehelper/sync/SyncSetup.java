package com.privat.pitz.financehelper.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.concurrent.Executor;

import com.privat.pitz.financehelper.core.Controller;
import com.privat.pitz.financehelper.core.DirectoryStorage;

/**
 * Wires the sync engine's storage interfaces to the app: the save files are the Controller's own
 * storage (the same object, so writes exclude each other), the sync bookkeeping lives in two
 * folders next to them that the save-file code never lists.
 */
public final class SyncSetup {
    private static final String BASE_DIR = "sync-base";
    private static final String TRASH_DIR = "sync-trash";

    private SyncSetup() {
    }

    public static LocalFiles localFiles(Context context, Controller controller) {
        return new StorageLocalFiles(controller.getStorage(), storageIn(context, TRASH_DIR),
                mainThread(), controller::onSavefileReplacedBySync);
    }

    public static SyncBaseStore baseStore(Context context) {
        return new StorageSyncBaseStore(storageIn(context, BASE_DIR));
    }

    private static DirectoryStorage storageIn(Context context, String folder) {
        File dir = new File(context.getApplicationContext().getFilesDir(), folder);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new DirectoryStorage(dir);
    }

    /** Runs inline when already on the main thread, so a caller there cannot deadlock itself. */
    private static Executor mainThread() {
        final Handler handler = new Handler(Looper.getMainLooper());
        return task -> {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                task.run();
            } else {
                handler.post(task);
            }
        };
    }
}
