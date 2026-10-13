package com.privat.pitz.financehelper.sync;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * What the user chose (sync on or off) and how the last run went, kept in the app's preferences so
 * it is there for the background worker and the screen alike.
 */
public final class CloudSyncStatus {
    private static final String PREFS = "cloud_sync";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_TIME = "last_time";
    private static final String KEY_OK = "last_ok";
    private static final String KEY_TEXT = "last_text";

    private CloudSyncStatus() {
    }

    public static final class Last {
        public final long time;
        public final boolean ok;
        public final String text;

        Last(long time, boolean ok, String text) {
            this.time = time;
            this.ok = ok;
            this.text = text;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** True once the user signed in; automatic syncs are only scheduled while this is true. */
    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static void record(Context context, boolean ok, String text) {
        prefs(context).edit()
                .putLong(KEY_TIME, System.currentTimeMillis())
                .putBoolean(KEY_OK, ok)
                .putString(KEY_TEXT, text)
                .apply();
    }

    /** @return null if no run has been recorded yet */
    public static Last last(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.contains(KEY_TIME)) return null;
        return new Last(p.getLong(KEY_TIME, 0), p.getBoolean(KEY_OK, false), p.getString(KEY_TEXT, ""));
    }
}
