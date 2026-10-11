package com.privat.pitz.financehelper.jsoneditor;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * The text arithmetic behind code folding, free of any Android type so it is unit-testable.
 *
 * <p>The model: the document has a <em>full text</em> and a set of <em>folded openers</em>, the
 * offsets (in the full text) of the {@code {} / {@code [} whose block is folded. The
 * <em>display text</em> is derived from those two: for each folded block the content after the
 * opener, up to and including the matching closer, is replaced by a short placeholder such as
 * {@code " … 42 ]"}. The hidden text is kept verbatim inside the {@link Placeholder}.
 *
 * <p>{@link #render} turns (full, folded) into display text plus placeholders; {@link #extract}
 * is the exact inverse, so saving can always rebuild the complete text no matter what is folded.
 * Folds nested inside a folded block are remembered ({@link Placeholder#dormant}) and come back
 * when the outer block is unfolded, like in Notepad++.
 *
 * <p>The view layer attaches each Placeholder as a span to the placeholder characters, so the
 * Android text machinery keeps its position correct while the user edits around it.
 */
public final class FoldMath {

    private FoldMath() { }

    /** One folded block as it appears in the display text. */
    public static final class Placeholder {
        /** Range of the placeholder characters in the display text. */
        public int displayStart;
        public int displayEnd;
        /** Range of the hidden text in the full text (see {@link Rendered#displayToFull}). */
        public int fullStart;
        public int fullEnd;
        /** The hidden text: everything after the opener up to and including the closer. */
        public final String hidden;
        /** Folded openers inside {@link #hidden}, as offsets relative to its start. */
        public final int[] dormant;
        /** The characters shown in place of the hidden text. */
        public final String text;

        public Placeholder(String hidden, int[] dormant) {
            this.hidden = hidden;
            this.dormant = dormant;
            this.text = placeholderText(hidden);
        }

        Placeholder copy() {
            Placeholder p = new Placeholder(hidden, dormant);
            p.displayStart = displayStart;
            p.displayEnd = displayEnd;
            p.fullStart = fullStart;
            p.fullEnd = fullEnd;
            return p;
        }
    }

    /** Display text with the placeholders it contains, ordered by position. */
    public static final class Rendered {
        public final String display;
        public final List<Placeholder> placeholders;

        Rendered(String display, List<Placeholder> placeholders) {
            this.display = display;
            this.placeholders = placeholders;
        }

        public int displayToFull(int displayOffset) {
            return FoldMath.displayToFull(placeholders, 0, displayOffset);
        }

        public int fullToDisplay(int fullOffset) {
            return FoldMath.fullToDisplay(placeholders, 0, fullOffset);
        }
    }

    /** The full text recovered from (a range of) display text. */
    public static final class Extracted {
        public final String full;
        /** Folded openers, as offsets in {@link #full}. */
        public final TreeSet<Integer> folded;
        private final List<Placeholder> placeholders;
        private final int origin;

        Extracted(String full, TreeSet<Integer> folded, List<Placeholder> placeholders, int origin) {
            this.full = full;
            this.folded = folded;
            this.placeholders = placeholders;
            this.origin = origin;
        }

        /** Maps an absolute display offset to an offset in {@link #full}. */
        public int displayToFull(int displayOffset) {
            return FoldMath.displayToFull(placeholders, origin, displayOffset);
        }

        /** Maps an offset in {@link #full} to an absolute display offset. */
        public int fullToDisplay(int fullOffset) {
            return FoldMath.fullToDisplay(placeholders, origin, fullOffset);
        }
    }

    /**
     * Builds the display text for {@code full} with the given blocks folded.
     *
     * <p>Offsets in {@code folded} that are not the opener of a multi-line block are ignored. A
     * folded block inside another folded block is not shown but stays recorded as dormant.
     */
    public static Rendered render(String full, Set<Integer> folded) {
        NavigableSet<Integer> foldedSorted = new TreeSet<>(folded);
        StringBuilder out = new StringBuilder(full.length());
        List<Placeholder> placeholders = new ArrayList<>();
        int pos = 0;
        for (JsonFoldRegions.Region r : JsonFoldRegions.scan(full).regions) {
            if (!r.isMultiline() || r.openOffset < pos || !foldedSorted.contains(r.openOffset))
                continue; // single-line, hidden inside an earlier fold, or not folded
            int hiddenStart = r.openOffset + 1;
            int hiddenEnd = r.closeOffset + 1;
            out.append(full, pos, hiddenStart);

            NavigableSet<Integer> inner = foldedSorted.subSet(hiddenStart, true, hiddenEnd, false);
            int[] dormant = new int[inner.size()];
            int k = 0;
            for (int o : inner)
                dormant[k++] = o - hiddenStart;

            Placeholder p = new Placeholder(full.substring(hiddenStart, hiddenEnd), dormant);
            p.displayStart = out.length();
            out.append(p.text);
            p.displayEnd = out.length();
            p.fullStart = hiddenStart;
            p.fullEnd = hiddenEnd;
            placeholders.add(p);
            pos = hiddenEnd;
        }
        out.append(full, pos, full.length());
        return new Rendered(out.toString(), placeholders);
    }

    /**
     * Rebuilds the full text of {@code display[from, to)} by substituting every placeholder in it
     * with the text it hides. Placeholders must lie wholly inside the range, sorted by position.
     *
     * <p>Also reports which openers are folded, so the result can go straight back into
     * {@link #render}.
     */
    public static Extracted extract(CharSequence display, List<Placeholder> placeholders, int from, int to) {
        StringBuilder full = new StringBuilder(to - from);
        TreeSet<Integer> folded = new TreeSet<>();
        List<Placeholder> inRange = new ArrayList<>();
        int pos = from;
        for (Placeholder p : placeholders) {
            if (p.displayEnd <= from)
                continue;
            if (p.displayStart >= to)
                break;
            if (p.displayStart < from || p.displayEnd > to || p.displayStart < pos)
                throw new IllegalArgumentException("placeholder straddles the extracted range");
            full.append(display, pos, p.displayStart);
            int hiddenStart = full.length();
            // the opener is the character right before the placeholder
            if (p.displayStart > from)
                folded.add(hiddenStart - 1);
            for (int d : p.dormant)
                folded.add(hiddenStart + d);
            full.append(p.hidden);
            Placeholder q = p.copy();
            q.fullStart = hiddenStart;
            q.fullEnd = full.length();
            inRange.add(q);
            pos = p.displayEnd;
        }
        full.append(display, pos, to);
        return new Extracted(full.toString(), folded, inRange, from);
    }

    private static int displayToFull(List<Placeholder> phs, int origin, int displayOffset) {
        int shift = 0;
        for (Placeholder p : phs) {
            if (displayOffset < p.displayStart)
                break;
            if (displayOffset >= p.displayEnd)
                shift += (p.fullEnd - p.fullStart) - (p.displayEnd - p.displayStart);
            else
                return p.fullStart; // inside a placeholder: the start of what it hides
        }
        return displayOffset - origin + shift;
    }

    private static int fullToDisplay(List<Placeholder> phs, int origin, int fullOffset) {
        int shift = 0;
        for (Placeholder p : phs) {
            if (fullOffset < p.fullStart)
                break;
            if (fullOffset >= p.fullEnd)
                shift += (p.fullEnd - p.fullStart) - (p.displayEnd - p.displayStart);
            else
                return p.displayStart; // hidden: the nearest visible spot is the placeholder
        }
        return fullOffset + origin - shift;
    }

    /**
     * The folds that remain after unfolding every block whose hidden part overlaps
     * {@code [start, end)} of the full text. Used to reveal a search hit.
     */
    public static TreeSet<Integer> unfoldOverlapping(String full, Set<Integer> folded, int start, int end) {
        TreeSet<Integer> result = new TreeSet<>(folded);
        for (JsonFoldRegions.Region r : JsonFoldRegions.scan(full).regions) {
            if (!result.contains(r.openOffset))
                continue;
            if (r.openOffset + 1 < end && r.closeOffset + 1 > start)
                result.remove(r.openOffset);
        }
        return result;
    }

    /** {@code folded} plus every multi-line block at exactly the given nesting depth. */
    public static TreeSet<Integer> foldAtDepth(String full, Set<Integer> folded, int depth) {
        TreeSet<Integer> result = new TreeSet<>(folded);
        for (JsonFoldRegions.Region r : JsonFoldRegions.scan(full).regions) {
            if (r.depth == depth && r.isMultiline())
                result.add(r.openOffset);
        }
        return result;
    }

    /** What stands in for a hidden block, e.g. {@code " … 42 ]"}; the count is its direct entries. */
    public static String placeholderText(String hidden) {
        char closer = hidden.isEmpty() ? '?' : hidden.charAt(hidden.length() - 1);
        int entries = countEntries(hidden.isEmpty() ? hidden : hidden.substring(0, hidden.length() - 1));
        return " … " + entries + " " + closer;
    }

    /** Number of comma-separated top-level entries in the inside of a block (string aware). */
    static int countEntries(String inner) {
        int depth = 0;
        int commas = 0;
        boolean inString = false;
        boolean escaped = false;
        boolean any = false;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (inString) {
                if (escaped)
                    escaped = false;
                else if (c == '\\')
                    escaped = true;
                else if (c == '"')
                    inString = false;
                continue;
            }
            if (!Character.isWhitespace(c))
                any = true;
            if (c == '"')
                inString = true;
            else if (c == '{' || c == '[')
                depth++;
            else if (c == '}' || c == ']')
                depth--;
            else if (c == ',' && depth == 0)
                commas++;
        }
        return any ? commas + 1 : 0;
    }
}
