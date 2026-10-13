package com.privat.pitz.financehelper.sync;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Asks for a background sync. There is only ever one pending request: a new one replaces it, which
 * is what turns a burst of saves into a single sync a few seconds after the last of them. The
 * request waits for a network and is retried with growing delays if it fails, also after the app
 * has been closed.
 *
 * <p>Replacing a request that is already running does not stop it (WorkManager cannot interrupt
 * the thread), but the runs are serialised by {@link CloudSync#RUN_LOCK}, so at worst the next one
 * starts when the first has finished.
 */
public final class CloudSyncScheduler {
    private static final String WORK_NAME = "cloud-sync";
    /** After a save: long enough to bundle several, short enough to feel immediate. */
    public static final long AFTER_SAVE_DELAY_S = 5;

    private CloudSyncScheduler() {
    }

    /** Does nothing until the user has signed in. */
    public static void requestSync(Context context, long delaySeconds) {
        if (!CloudSyncStatus.isEnabled(context)) return;
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(CloudSyncWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request);
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(WORK_NAME);
    }
}
