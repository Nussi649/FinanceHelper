package com.privat.pitz.financehelper.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import com.privat.pitz.financehelper.sync.graph.TokenProvider;

/**
 * One background sync: everything that changed on either side, repeated while the app keeps saving
 * during the run. Failures are recorded for the status line and retried by WorkManager, except a
 * missing sign-in, which only the user can fix.
 */
public class CloudSyncWorker extends Worker {
    private static final String TAG = "cloud_sync";
    /** The rounds a run may take to catch up with saves that happened while it was syncing. */
    private static final int MAX_ROUNDS = 3;
    private static final long PAUSED_RETRY_S = 60;

    public CloudSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (!CloudSyncStatus.isEnabled(context)) return Result.success();
        if (CloudSync.isPaused()) {
            // a screen is editing the file the sync would replace; try again shortly
            CloudSyncScheduler.requestSync(context, PAUSED_RETRY_S);
            return Result.success();
        }

        CloudSync.RUN_LOCK.lock();
        try {
            SyncEngine engine = CloudSync.engine(context);
            int pushed = 0, pulled = 0, merged = 0, errors = 0;
            String firstError = null;
            for (int round = 0; round < MAX_ROUNDS; round++) {
                Map<String, SyncEngine.FileOutcome> result = engine.syncAll();
                for (SyncEngine.FileOutcome o : result.values()) {
                    switch (o.status) {
                        case PUSHED: pushed++; break;
                        case PULLED: pulled++; break;
                        case MERGED: merged++; break;
                        case ERROR:
                            errors++;
                            if (firstError == null) firstError = o.toString();
                            break;
                        default: break;
                    }
                    for (String note : o.notes) Log.i(TAG, o.name + ": " + note);
                }
                if (!engine.hasPendingLocalChanges()) break;
            }
            String summary = String.format(Locale.ROOT, "%d hochgeladen, %d geladen, %d zusammengeführt",
                    pushed, pulled, merged);
            if (errors > 0) {
                CloudSyncStatus.record(context, false, errors + " Datei(en) mit Fehler: " + firstError);
                return Result.retry();
            }
            CloudSyncStatus.record(context, true, summary);
            Log.i(TAG, "Hintergrund-Sync: " + summary);
            return Result.success();
        } catch (TokenProvider.SignInRequiredException e) {
            CloudSyncStatus.record(context, false, "Anmeldung bei Microsoft nötig");
            return Result.failure();
        } catch (IOException e) {
            CloudSyncStatus.record(context, false, String.valueOf(e.getMessage()));
            Log.w(TAG, "Hintergrund-Sync fehlgeschlagen: " + e.getMessage());
            return Result.retry();
        } finally {
            CloudSync.RUN_LOCK.unlock();
        }
    }
}
