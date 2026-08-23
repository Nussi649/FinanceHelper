package com.privat.pitz.financehelper.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects what a save-file parse had to repair or throw away, so a load can tell the user
 * instead of failing in silence.
 *
 * <p>This exists because of a specific failure: a budget account was written to the save file
 * without one key, {@link Util#parseJSON_BudgetAccount} treated the missing key as fatal and
 * returned null, and the caller dropped that null without a word. The account was gone, and the
 * next save - triggered by any navigation - wrote the model back without it, making the loss
 * permanent. Nobody was ever told.
 *
 * <p>The rule this class enforces is procedural rather than technical: a parse may default a
 * field or discard a record, but it may not do either quietly. Every such decision lands here,
 * and {@link SaveFileRepository} hands the finished report to the caller so the UI can surface
 * it.
 *
 * <p>Messages are German and user-facing, matching {@link IntegrityChecker}. Both classes are
 * Android-free and JVM-tested, so reaching strings.xml would mean handing core a Context and
 * undoing the layering work; there is one values/ folder and no second locale.
 */
public class ParseReport {

    public enum Severity {
        /** A record was thrown away entirely. The user has lost data and must be told. */
        DISCARDED,
        /** A field was missing or unusable and a default was substituted. The record survived. */
        DEFAULTED
    }

    public static class Note {
        public final Severity severity;
        public final String message;

        Note(Severity severity, String message) {
            this.severity = severity;
            this.message = message;
        }

        @Override
        public String toString() {
            return message;
        }
    }

    private final List<Note> notes = new ArrayList<>();

    /** Records that {@code what} was thrown away, and why. */
    public void discarded(String what, String why) {
        notes.add(new Note(Severity.DISCARDED,
                String.format("%s wurde beim Laden verworfen: %s", what, why)));
    }

    /** Records that a field of {@code what} was missing or unusable and was defaulted. */
    public void defaulted(String what, String field, String fallback) {
        notes.add(new Note(Severity.DEFAULTED, String.format(
                "%s: Feld \"%s\" fehlt oder ist ungültig, ersetzt durch \"%s\".",
                what, field, fallback)));
    }

    public List<Note> getNotes() {
        return Collections.unmodifiableList(notes);
    }

    public boolean isEmpty() {
        return notes.isEmpty();
    }

    /** True if anything was thrown away, as opposed to merely repaired. */
    public boolean hasDiscards() {
        return countDiscards() > 0;
    }

    public int countDiscards() {
        int count = 0;
        for (Note note : notes)
            if (note.severity == Severity.DISCARDED)
                count++;
        return count;
    }

    public List<String> messagesOf(Severity severity) {
        List<String> result = new ArrayList<>();
        for (Note note : notes)
            if (note.severity == severity)
                result.add(note.message);
        return result;
    }

    /** Folds {@code other} into this report, so a multi-file load produces one list. */
    public void addAll(ParseReport other) {
        if (other != null)
            notes.addAll(other.notes);
    }
}
