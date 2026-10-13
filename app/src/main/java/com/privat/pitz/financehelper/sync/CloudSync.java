package com.privat.pitz.financehelper.sync;

import android.content.Context;

import java.io.IOException;

import com.privat.pitz.financehelper.core.Controller;
import com.privat.pitz.financehelper.sync.graph.GraphRemoteStore;
import com.privat.pitz.financehelper.sync.graph.MsalTokenProvider;
import com.privat.pitz.financehelper.sync.graph.UrlConnectionTransport;

/** Builds the OneDrive sync from its parts; the one place that knows how they fit together. */
public final class CloudSync {
    private static MsalTokenProvider tokens;

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

    public static SyncEngine engine(Context context, Controller controller) throws IOException {
        return new SyncEngine(SyncSetup.localFiles(context, controller), remote(context),
                SyncSetup.baseStore(context));
    }
}
