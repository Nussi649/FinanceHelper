package com.privat.pitz.financehelper.sync;

import android.content.Context;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import com.privat.pitz.financehelper.sync.graph.GraphRemoteStore;
import com.privat.pitz.financehelper.sync.graph.MsalTokenProvider;
import com.privat.pitz.financehelper.sync.graph.UrlConnectionTransport;

/** Builds the OneDrive sync from its parts; the one place that knows how they fit together. */
public final class CloudSync {
    private static MsalTokenProvider tokens;

    /** Only one sync runs at a time, whoever started it (the button, a worker). */
    public static final ReentrantLock RUN_LOCK = new ReentrantLock();

    private static final AtomicInteger pauses = new AtomicInteger();

    private CloudSync() {
    }

    /** One sign-in object per process. Blocking on first use (reads MSAL configuration). */
    public static synchronized MsalTokenProvider tokens(Context context) throws IOException {
        if (tokens == null) {
            tokens = MsalTokenProvider.create(context);
        }
        return tokens;
    }

    public static GraphRemoteStore remote(Context context) throws IOException {
        return new GraphRemoteStore(new UrlConnectionTransport(), tokens(context));
    }

    public static SyncEngine engine(Context context) throws IOException {
        return new SyncEngine(SyncSetup.localFiles(context), remote(context), SyncSetup.baseStore(context));
    }

    /**
     * Holds automatic syncs back while a screen is open that works on a copy of the save file the
     * sync would replace (the raw JSON editor): replacing the file under it would silently discard
     * what it later saves. Calls nest; every {@code pause()} needs its {@code resume()}.
     */
    public static void pause() {
        pauses.incrementAndGet();
    }

    public static void resume() {
        if (pauses.decrementAndGet() < 0) pauses.set(0);
    }

    public static boolean isPaused() {
        return pauses.get() > 0;
    }
}
