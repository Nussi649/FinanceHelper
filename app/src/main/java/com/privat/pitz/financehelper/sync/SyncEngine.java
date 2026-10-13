package com.privat.pitz.financehelper.sync;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;


/**
 * Brings the local and the remote copy of each save file into agreement.
 *
 * <p>Per file: read both, three-way merge them against the last agreed version
 * ({@link SaveFileMerger}), write the result to whichever side differs from it, remember it as the
 * new base. Three things keep that safe:
 * <ul>
 *   <li>The remote write is conditional on the etag that was read ({@link RemoteStore#put}); if
 *       the other writer got in first, the whole file is read and merged again.</li>
 *   <li>The local write is conditional on the local content that was read
 *       ({@link LocalFiles#replaceIfUnchanged}); if the app saved meanwhile, same again.</li>
 *   <li>Before a file is overwritten while the merge is dropping something it holds, a copy goes
 *       to the trash. If that copy cannot be stored, the file is left alone.</li>
 * </ul>
 * A file that is not valid JSON on either side is never touched: it is reported and skipped.
 */
public class SyncEngine {

    public enum Status {
        /** Both sides already agreed. */
        UNCHANGED,
        /** Only the remote side was written. */
        PUSHED,
        /** Only the local side was written. */
        PULLED,
        /** Both sides were written. */
        MERGED,
        /** The file was left alone; see {@link FileOutcome#message}. */
        ERROR
    }

    public static final class FileOutcome {
        public final String name;
        public final Status status;
        public final String message;
        /** Conflicts and resurrections the merge resolved; worth showing in a sync log. */
        public final List<String> notes;
        /** How many things the merge dropped (each is recoverable from the trash). */
        public final int dropped;

        FileOutcome(String name, Status status, String message, List<String> notes, int dropped) {
            this.name = name;
            this.status = status;
            this.message = message;
            this.notes = notes;
            this.dropped = dropped;
        }

        @Override
        public String toString() {
            return name + ": " + status + (message.isEmpty() ? "" : " - " + message);
        }
    }

    /** A save file name exactly - the app-wide check is unanchored and would also take "x.jso.bak". */
    private static final Pattern SAVEFILE_NAME = Pattern.compile("\\d{4}-\\d{2}-[^\\s/]+\\.jso");

    static final int MAX_ATTEMPTS = 5;
    static final String TRASH_STAMP = "yyyyMMdd'T'HHmmss'Z'";

    private final LocalFiles local;
    private final RemoteStore remote;
    private final SyncBaseStore baseStore;
    private final LongSupplier clock;

    public SyncEngine(LocalFiles local, RemoteStore remote, SyncBaseStore baseStore) {
        this(local, remote, baseStore, System::currentTimeMillis);
    }

    SyncEngine(LocalFiles local, RemoteStore remote, SyncBaseStore baseStore, LongSupplier clock) {
        this.local = local;
        this.remote = remote;
        this.baseStore = baseStore;
        this.clock = clock;
    }

    /** Syncs every save file either side has. One file failing does not stop the others. */
    public Map<String, FileOutcome> syncAll() throws IOException {
        Set<String> names = new LinkedHashSet<>();
        for (String n : local.listFiles()) {
            if (SAVEFILE_NAME.matcher(n).matches()) names.add(n);
        }
        for (String n : remote.listFiles()) {
            if (SAVEFILE_NAME.matcher(n).matches()) names.add(n);
        }
        Map<String, FileOutcome> out = new LinkedHashMap<>();
        for (String name : names) {
            out.put(name, syncFile(name));
        }
        return out;
    }

    public FileOutcome syncFile(String name) {
        try {
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                FileOutcome outcome = attemptSync(name);
                if (outcome != null) return outcome;
            }
            return error(name, "Zu viele gleichzeitige Änderungen - später erneut versuchen");
        } catch (IOException | JSONException e) {
            return error(name, String.valueOf(e.getMessage()));
        }
    }

    /** @return null if a writer got in between and the file has to be read and merged again */
    private FileOutcome attemptSync(String name) throws IOException, JSONException {
        RemoteStore.RemoteFile remoteFile = remote.get(name);
        String localText = local.read(name);
        if (remoteFile == null && localText == null) {
            return new FileOutcome(name, Status.UNCHANGED, "", Collections.emptyList(), 0);
        }

        final JSONObject localDoc = parse(localText);
        final JSONObject remoteDoc = parse(remoteFile == null ? null : remoteFile.content);
        JSONObject l = localDoc;
        JSONObject r = remoteDoc;
        if (localText != null && l == null) {
            return error(name, "Lokale Datei ist kein gültiges JSON - nicht synchronisiert");
        }
        if (remoteFile != null && r == null) {
            return error(name, "Datei in der Cloud ist kein gültiges JSON - nicht synchronisiert");
        }
        JSONObject base = parse(baseStore.getBase(name));

        // a side that does not have the file yet is a copy of the other, so the merge is a plain copy
        // (ids are assigned once, here, so that both "sides" carry the same ones)
        if (l == null) l = r = SaveFileMerger.withIds(r);
        else if (r == null) l = r = SaveFileMerger.withIds(l);

        Date localTime = localText == null ? new Date(clock.getAsLong()) : local.lastModified(name);
        Date remoteTime = remoteFile == null ? new Date(clock.getAsLong()) : remoteFile.modified;
        SaveFileMerger.Result result = SaveFileMerger.merge(base, l, r, localTime, remoteTime);

        String mergedCanon = SaveFileMerger.canonical(result.merged);
        boolean writeRemote = remoteDoc == null || !mergedCanon.equals(SaveFileMerger.canonical(remoteDoc));
        boolean writeLocal = localDoc == null || !mergedCanon.equals(SaveFileMerger.canonical(localDoc));
        String mergedText = result.merged.toString(4);

        // keep what the merge is about to drop before the file holding it is overwritten
        String stamp = trashStamp();
        for (SaveFileMerger.Loss loss : result.losses) {
            if (loss.heldBy == SaveFileMerger.Side.REMOTE && writeRemote && remoteFile != null) {
                remote.putTrash(trashName(name, stamp), remoteFile.content);
                break;
            }
        }
        for (SaveFileMerger.Loss loss : result.losses) {
            if (loss.heldBy == SaveFileMerger.Side.LOCAL && writeLocal && localText != null) {
                local.putTrash(trashName(name, stamp), localText);
                break;
            }
        }

        if (writeRemote) {
            try {
                remote.put(name, mergedText, remoteFile == null ? null : remoteFile.etag);
            } catch (RemoteStore.EtagMismatchException e) {
                return null;
            }
        }
        if (writeLocal && !local.replaceIfUnchanged(name, localText, mergedText)) {
            return null;
        }
        baseStore.setBase(name, mergedText);

        Status status = writeRemote && writeLocal ? Status.MERGED
                : writeRemote ? Status.PUSHED
                : writeLocal ? Status.PULLED
                : Status.UNCHANGED;
        return new FileOutcome(name, status, "", result.notes, result.losses.size());
    }

    private static JSONObject parse(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return new JSONObject(text);
        } catch (JSONException e) {
            return null;
        }
    }

    private static FileOutcome error(String name, String message) {
        return new FileOutcome(name, Status.ERROR, message, Collections.emptyList(), 0);
    }

    private String trashStamp() {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat(TRASH_STAMP, java.util.Locale.ROOT);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return f.format(new Date(clock.getAsLong()));
    }

    static String trashName(String name, String stamp) {
        return name + "." + stamp;
    }
}
