package com.privat.pitz.financehelper.sync;

import org.json.JSONException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;

import com.privat.pitz.financehelper.core.SavefileStorage;

/**
 * {@link LocalFiles} on top of the app's own save-file storage.
 *
 * <p>Replacing a file is the delicate part: the app keeps its accounts in memory and rewrites the
 * whole file on every change, so a file replaced behind its back would be overwritten again by the
 * next change, silently throwing away what the sync just brought in. Two things prevent that.
 * The compare-and-replace runs on {@code applier} - on Android the main thread, where the app
 * performs its own changes - so no change can slip in between the check and the write; and
 * afterwards {@code listener} gets to reload whatever the app holds in memory from the new file.
 *
 * <p>Because of that hop, {@link #replaceIfUnchanged} blocks until the applier has run it and must
 * not be called from the applier's own thread unless that thread is allowed to run inline.
 */
public class StorageLocalFiles implements LocalFiles {

    /** Told after a file was replaced, on the applier's thread. */
    public interface ReplaceListener {
        void onReplaced(String name) throws IOException, JSONException;
    }

    private final SavefileStorage files;
    private final SavefileStorage trash;
    private final Executor applier;
    private final ReplaceListener listener;

    public StorageLocalFiles(SavefileStorage files, SavefileStorage trash, Executor applier,
                             ReplaceListener listener) {
        this.files = files;
        this.trash = trash;
        this.applier = applier;
        this.listener = listener;
    }

    @Override
    public List<String> listFiles() {
        return files.list();
    }

    @Override
    public String read(String name) throws IOException {
        if (!files.exists(name)) return null;
        try {
            return files.read(name);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public Date lastModified(String name) {
        long t = files.lastModified(name);
        return t < 0 ? new Date() : new Date(t);
    }

    @Override
    public boolean replaceIfUnchanged(String name, String expected, String content) throws IOException {
        FutureTask<Boolean> task = new FutureTask<>(() -> {
            // same monitor the storage takes for its own writes: nothing else can write in between
            synchronized (files) {
                String current = read(name);
                boolean unchanged = expected == null ? current == null : expected.equals(current);
                if (!unchanged) return false;
                files.write(name, content);
            }
            listener.onReplaced(name);
            return true;
        });
        applier.execute(task);
        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while replacing " + name);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException("Could not replace " + name + ": " + cause, cause);
        }
    }

    @Override
    public void putTrash(String trashName, String content) throws IOException {
        trash.write(trashName, content);
    }
}
