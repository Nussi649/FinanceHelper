package com.privat.pitz.financehelper;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.privat.pitz.financehelper.sync.CloudSync;
import com.privat.pitz.financehelper.sync.CloudSyncScheduler;
import com.privat.pitz.financehelper.sync.CloudSyncStatus;
import com.privat.pitz.financehelper.sync.SyncEngine;
import com.privat.pitz.financehelper.sync.graph.MsalTokenProvider;
import com.privat.pitz.financehelper.sync.graph.TokenProvider;

/**
 * Sign in to OneDrive, see how the automatic sync is doing, check the connection, sync by hand.
 * Everything slow runs on a single worker thread; the buttons are disabled while it works.
 */
public class CloudSyncActivity extends AbstractActivity {
    private static final String TAG = "cloud_sync";
    private static final long STATUS_TICK_MS = 5000;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable statusTick = new Runnable() {
        @Override
        public void run() {
            showLastRun();
            handler.postDelayed(this, STATUS_TICK_MS);
        }
    };
    private TextView tvStatus;
    private TextView tvLast;
    private TextView tvLog;
    private Button btnSignIn;
    private Button btnCheck;
    private Button btnSync;
    private Button btnSignOut;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud_sync);
    }

    @Override
    protected void endWorkingThread() {
        tvStatus = findViewById(R.id.tv_cloud_status);
        tvLast = findViewById(R.id.tv_cloud_last);
        tvLog = findViewById(R.id.tv_cloud_log);
        btnSignIn = findViewById(R.id.btn_cloud_sign_in);
        btnCheck = findViewById(R.id.btn_cloud_check);
        btnSync = findViewById(R.id.btn_cloud_sync);
        btnSignOut = findViewById(R.id.btn_cloud_sign_out);

        btnSignIn.setOnClickListener(v -> signIn());
        btnCheck.setOnClickListener(v -> check());
        btnSync.setOnClickListener(v -> syncNow());
        btnSignOut.setOnClickListener(v -> signOut());
        setCustomTitle();
        refreshStatus();
        handler.post(statusTick);
    }

    private interface Job {
        void run() throws Exception;
    }

    /** Runs a job on the worker with the buttons disabled; failures end up in the log. */
    private void runBusy(Job job) {
        setBusy(true);
        worker.execute(() -> {
            try {
                job.run();
            } catch (TokenProvider.SignInRequiredException e) {
                log("Bitte zuerst bei Microsoft anmelden.");
            } catch (Exception e) {
                log("Fehler: " + e.getMessage());
            } finally {
                runOnUiThread(() -> setBusy(false));
            }
        });
    }

    private void setBusy(boolean busy) {
        btnSignIn.setEnabled(!busy);
        btnCheck.setEnabled(!busy);
        btnSync.setEnabled(!busy);
        btnSignOut.setEnabled(!busy);
    }

    private void refreshStatus() {
        worker.execute(() -> {
            String status;
            try {
                boolean signedIn = CloudSync.tokens(this).isSignedIn();
                // signed in before automatic sync existed (or the choice was lost): switch it on
                if (signedIn && !CloudSyncStatus.isEnabled(this)) {
                    CloudSyncStatus.setEnabled(this, true);
                    CloudSyncScheduler.requestSync(this, 0);
                }
                status = signedIn
                        ? getString(R.string.cloud_status_signed_in)
                        : getString(R.string.cloud_status_signed_out);
            } catch (IOException e) {
                status = getString(R.string.cloud_status_unavailable, e.getMessage());
            }
            final String shown = status;
            runOnUiThread(() -> tvStatus.setText(shown));
        });
        showLastRun();
    }

    /** The outcome of the latest sync, whoever ran it (this screen or the background worker). */
    private void showLastRun() {
        CloudSyncStatus.Last last = CloudSyncStatus.last(this);
        if (last == null) {
            tvLast.setText(R.string.cloud_last_none);
            return;
        }
        String time = new SimpleDateFormat("dd.MM. HH:mm:ss", Locale.ROOT).format(new Date(last.time));
        tvLast.setText(last.ok
                ? getString(R.string.cloud_last_ok, time, last.text)
                : getString(R.string.cloud_last_failed, time, last.text));
    }

    private void signIn() {
        runBusy(() -> {
            MsalTokenProvider tokens = CloudSync.tokens(this);
            if (tokens.isSignedIn()) {
                log("Bereits angemeldet. Automatischer Sync ist aktiv.");
                CloudSyncStatus.setEnabled(this, true);
                refreshStatus();
                return;
            }
            runOnUiThread(() -> tokens.signIn(this, new MsalTokenProvider.SignInCallback() {
                @Override
                public void onSignedIn() {
                    log("Angemeldet. Automatischer Sync ist jetzt aktiv.");
                    CloudSyncStatus.setEnabled(CloudSyncActivity.this, true);
                    CloudSyncScheduler.requestSync(CloudSyncActivity.this, 0);
                    refreshStatus();
                }

                @Override
                public void onCancelled() {
                    log("Anmeldung abgebrochen.");
                }

                @Override
                public void onError(String message) {
                    log("Anmeldung fehlgeschlagen: " + message);
                }
            }));
        });
    }

    private void signOut() {
        runBusy(() -> {
            CloudSyncStatus.setEnabled(this, false);
            CloudSyncScheduler.cancel(this);
            CloudSync.tokens(this).signOut();
            log("Abgemeldet. Der automatische Sync ist aus; die Dateien in OneDrive bleiben unverändert.");
            refreshStatus();
        });
    }

    private void check() {
        runBusy(() -> {
            log("Prüfe Verbindung und bedingtes Schreiben (If-Match) …");
            CloudSync.remote(this).verifyConditionalWrites();
            log("In Ordnung: OneDrive lehnt Schreiben mit veraltetem eTag ab, und eine "
                    + "vorhandene Datei wird beim Anlegen nicht überschrieben.");
        });
    }

    private void syncNow() {
        runBusy(() -> {
            log("Synchronisiere …");
            // a background run may be in progress; wait for it rather than run alongside it
            CloudSync.RUN_LOCK.lock();
            try {
                Map<String, SyncEngine.FileOutcome> result = CloudSync.engine(this).syncAll();
                int pushed = 0, pulled = 0, merged = 0, unchanged = 0, errors = 0;
                for (SyncEngine.FileOutcome o : result.values()) {
                    switch (o.status) {
                        case PUSHED: pushed++; break;
                        case PULLED: pulled++; break;
                        case MERGED: merged++; break;
                        case UNCHANGED: unchanged++; break;
                        default: errors++; break;
                    }
                    if (o.status != SyncEngine.Status.UNCHANGED) {
                        log(o.toString());
                    }
                    for (String note : o.notes) {
                        log("  " + note);
                    }
                    if (o.dropped > 0) {
                        log("  " + o.dropped + " Eintrag/Einträge im Papierkorb gesichert");
                    }
                }
                String summary = String.format(Locale.ROOT, "%d hochgeladen, %d geladen, %d zusammengeführt, "
                        + "%d unverändert, %d Fehler", pushed, pulled, merged, unchanged, errors);
                log("Fertig: " + summary + ".");
                CloudSyncStatus.record(this, errors == 0, summary);
            } finally {
                CloudSync.RUN_LOCK.unlock();
            }
            runOnUiThread(this::showLastRun);
        });
    }

    private void log(String message) {
        String line = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date()) + "  " + message;
        Log.i(TAG, message);
        runOnUiThread(() -> {
            CharSequence old = tvLog.getText();
            tvLog.setText(old.length() == 0 ? line : old + "\n" + line);
        });
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(statusTick);
        worker.shutdown();
        super.onDestroy();
    }

    @Override
    public void onRefresh() {
    }

    @Override
    public void setCustomTitle() {
        setCustomTitle(getString(R.string.label_cloud_sync));
    }
}
