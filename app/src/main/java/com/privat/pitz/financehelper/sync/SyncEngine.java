package com.privat.pitz.financehelper.sync;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collection;
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
 * new base together with the remote etag it was written under. Three things keep that safe:
 * <ul>
 *   <li>The remote write is conditional on the etag that was read ({@link RemoteStore#put}); if
 *       the other writer got in first, the whole file is read and merged again.</li>
 *   <li>The local write is conditional on the local content that was read
 *       ({@link LocalFiles#replaceIfUnchanged}); if the app saved meanwhile, same again.</li>
 *   <li>Before a file is overwritten while the merge is dropping something it holds, a copy goes
 *       to the trash. If that copy cannot be stored, the file is left alone.</li>
 * </ul>
 * A file that is not valid JSON on either side is never touched: it is reported and skipped.
 *
 * <p>Only what changed costs anything. A remote file whose etag is the remembered one still holds
 * the base, so it is not downloaded; a local file whose text equals the base has not been changed
 * by the app, so there is nothing to push.
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

    /**
     * Syncs the save files that changed, on either side. One request lists the remote files with
     * their etags; a file whose etag is the one remembered from the last sync and whose local copy
     * is still the remembered base costs nothing further. One file failing does not stop the others.
     */
    public Map<String, FileOutcome> syncAll() throws IOException {
        Map<String, String> remoteEtags = remote.listEtags();
        Set<String> names = new LinkedHashSet<>();
        for (String n : local.listFiles()) {
            if (SAVEFILE_NAME.matcher(n).matches()) names.add(n);
        }
        for (String n : remoteEtags.keySet()) {
            if (SAVEFILE_NAME.matcher(n).matches()) names.add(n);
        }
        Map<String, FileOutcome> out = new LinkedHashMap<>();
        for (String name : names) {
            String known = remoteEtags.get(name);
            boolean remoteUnchanged = known != null && known.equals(lastEtag(name));
            out.put(name, sync(name, remoteUnchanged));
        }
        return out;
    }

    /**
     * Pushes local changes of the named files without asking the cloud first: the remote is
     * assumed to be as it was at the last sync, and the conditional write turns out whether that
     * was true - if not, the file is read and merged properly. A file that is unchanged locally
     * causes no network traffic at all. Meant for "the app just saved this file".
     */
    public Map<String, FileOutcome> syncLocalChanges(Collection<String> names) {
        Map<String, FileOutcome> out = new LinkedHashMap<>();
        for (String name : names) {
            if (SAVEFILE_NAME.matcher(name).matches()) out.put(name, sync(name, true));
        }
        return out;
    }

    /**
     * True if any local save file differs from what was last synced - i.e. the app saved since.
     * Local reads only, no network. A worker uses it to notice saves that happened while it ran.
     */
    public boolean hasPendingLocalChanges() {
        for (String name : local.listFiles()) {
            if (!SAVEFILE_NAME.matcher(name).matches()) continue;
            try {
                String base = baseStore.getBase(name);
                if (base == null || baseStore.getEtag(name) == null || !base.equals(local.read(name))) {
                    return true;
                }
            } catch (IOException e) {
                return true;
            }
        }
        return false;
    }

    /** Syncs one file completely, reading both sides. */
    public FileOutcome syncFile(String name) {
        return sync(name, false);
    }

    private String lastEtag(String name) {
        try {
            return baseStore.getEtag(name);
        } catch (IOException e) {
            return null;
        }
    }

    private FileOutcome sync(String name, boolean assumeRemoteUnchanged) {
        try {
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                // the assumption is only ever tried once: if it was wrong, read the real thing
                FileOutcome outcome = attemptSync(name, assumeRemoteUnchanged && attempt == 0);
                if (outcome != null) return outcome;
            }
            return error(name, "Zu viele gleichzeitige Änderungen - später erneut versuchen");
        } catch (IOException | JSONException e) {
            return error(name, String.valueOf(e.getMessage()));
        }
    }

    /** @return null if a writer got in between and the file has to be read and merged again */
    private FileOutcome attemptSync(String name, boolean assumeRemoteUnchanged) throws IOException, JSONException {
        String localText = local.read(name);
        String baseText = baseStore.getBase(name);
        String baseEtag = baseStore.getEtag(name);

        RemoteStore.RemoteFile remoteFile;
        if (assumeRemoteUnchanged && baseText != null && baseEtag != null && localText != null) {
            if (localText.equals(baseText)) {
                return new FileOutcome(name, Status.UNCHANGED, "", Collections.emptyList(), 0);
            }
            // trusted only as far as the conditional write below confirms it
            remoteFile = new RemoteStore.RemoteFile(baseText, baseEtag, new Date(clock.getAsLong()));
        } else {
            remoteFile = remote.get(name);
        }
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
        JSONObject base = parse(baseText);

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

        String newEtag = remoteFile == null ? null : remoteFile.etag;
        if (writeRemote) {
            try {
                newEtag = remote.put(name, mergedText, newEtag);
            } catch (RemoteStore.EtagMismatchException e) {
                return null;
            }
        }
        if (writeLocal && !local.replaceIfUnchanged(name, localText, mergedText)) {
            return null;
        }
        // base = what the local file now contains, so that the next run can tell "unchanged" by
        // comparing text; when the local file was left alone that is its own text, not the merge's
        baseStore.setBase(name, writeLocal ? mergedText : localText, newEtag);

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
