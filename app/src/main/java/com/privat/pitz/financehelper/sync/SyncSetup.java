package com.privat.pitz.financehelper.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.concurrent.Executor;

import com.privat.pitz.financehelper.core.Controller;
import com.privat.pitz.financehelper.core.DirectoryStorage;

/**
 * Wires the sync engine's storage interfaces to the app: the save files are read and written
 * through a {@link DirectoryStorage} on the app's files folder (its lock is shared process-wide, so
 * the sync and the app's own saves exclude each other), the sync bookkeeping lives in two folders
 * next to them that the save-file code never lists.
 *
 * <p>The sync also runs when the app's screens are not (WorkManager starts the process on its own),
 * so nothing here may depend on a {@link Controller} existing. Whether one exists is looked up at
 * the moment a file is replaced, on the main thread - the same thread that builds it - so a
 * Controller that appears while a sync is running still gets to reload what the sync replaced.
 */
public final class SyncSetup {
    private static final String BASE_DIR = "sync-base";
    private static final String TRASH_DIR = "sync-trash";

    private SyncSetup() {
    }

    public static LocalFiles localFiles(Context context) {
        Context app = context.getApplicationContext();
        return new StorageLocalFiles(new DirectoryStorage(app.getFilesDir()), storageIn(app, TRASH_DIR),
                mainThread(), name -> {
                    Controller controller = Controller.instance;
                    if (controller != null) controller.onSavefileReplacedBySync(name);
                });
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
