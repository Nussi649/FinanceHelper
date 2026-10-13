package com.privat.pitz.financehelper;

import android.os.Bundle;
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
import com.privat.pitz.financehelper.sync.SyncEngine;
import com.privat.pitz.financehelper.sync.graph.MsalTokenProvider;
import com.privat.pitz.financehelper.sync.graph.TokenProvider;

/**
 * Sign in to OneDrive, check that the connection behaves, and sync by hand. Everything slow runs
 * on a single worker thread; the buttons are disabled while it works.
 */
public class CloudSyncActivity extends AbstractActivity {
    private static final String TAG = "cloud_sync";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView tvStatus;
    private TextView tvLog;
    private Button btnSignIn;
    private Button btnCheck;
    private Button btnSync;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud_sync);
    }

    @Override
    protected void endWorkingThread() {
        tvStatus = findViewById(R.id.tv_cloud_status);
        tvLog = findViewById(R.id.tv_cloud_log);
        btnSignIn = findViewById(R.id.btn_cloud_sign_in);
        btnCheck = findViewById(R.id.btn_cloud_check);
        btnSync = findViewById(R.id.btn_cloud_sync);

        btnSignIn.setOnClickListener(v -> signIn());
        btnCheck.setOnClickListener(v -> check());
        btnSync.setOnClickListener(v -> syncNow());
        setCustomTitle();
        refreshStatus();
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
    }

    private void refreshStatus() {
        worker.execute(() -> {
            String status;
            try {
                status = CloudSync.tokens(this).isSignedIn()
                        ? getString(R.string.cloud_status_signed_in)
                        : getString(R.string.cloud_status_signed_out);
            } catch (IOException e) {
                status = getString(R.string.cloud_status_unavailable, e.getMessage());
            }
            final String shown = status;
            runOnUiThread(() -> tvStatus.setText(shown));
        });
    }

    private void signIn() {
        runBusy(() -> {
            MsalTokenProvider tokens = CloudSync.tokens(this);
            runOnUiThread(() -> tokens.signIn(this, new MsalTokenProvider.SignInCallback() {
                @Override
                public void onSignedIn() {
                    log("Angemeldet.");
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
            Map<String, SyncEngine.FileOutcome> result =
                    CloudSync.engine(this, getController()).syncAll();
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
            log(String.format(Locale.ROOT, "Fertig: %d hochgeladen, %d geladen, %d zusammengeführt, "
                            + "%d unverändert, %d Fehler.", pushed, pulled, merged, unchanged, errors));
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
