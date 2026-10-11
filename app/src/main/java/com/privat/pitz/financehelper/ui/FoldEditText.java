package com.privat.pitz.financehelper.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.appcompat.widget.AppCompatEditText;

import com.privat.pitz.financehelper.R;
import com.privat.pitz.financehelper.jsoneditor.FoldMath;
import com.privat.pitz.financehelper.jsoneditor.JsonFoldRegions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A plain-text editor with Notepad++-style code folding for JSON.
 *
 * <p>A left gutter shows a triangle on every line that opens a multi-line block: pointing down if
 * the block is open, right if it is folded. Tapping it folds the block down to its opening line
 * plus a short placeholder such as {@code [ … 42 ]}, or unfolds it again.
 *
 * <p><b>Why folded content cannot get lost.</b> The text that a fold hides is not deleted - it is
 * stored verbatim in a {@link FoldMath.Placeholder} that is attached to the placeholder
 * characters as a span. The span travels with those characters as the user edits around them, and
 * {@link #extractAll()} swaps every placeholder back for its hidden text, so the complete
 * document can be rebuilt no matter what is folded. To keep the placeholder and its opening
 * bracket from being edited into something that no longer matches the hidden text, the editor's
 * {@code Editable} rejects any change that touches them (see {@link ProtectedEditable}); the user
 * has to unfold first. The one place text is replaced on purpose, fold/unfold, bypasses that guard.
 *
 * <p>All the actual text arithmetic lives in the pure-Java {@link FoldMath} so it can be tested
 * on the JVM; this class only wires it to Android's text and drawing machinery.
 */
public class FoldEditText extends AppCompatEditText {

    /** Receives notifications the host activity cares about. */
    public interface Listener {
        /** The user tried to edit a folded block or its opening bracket; nothing was changed. */
        void onEditRejected();

        /** The text was changed by the user (not by folding/unfolding). */
        void onUserEdit();
    }

    private static final long RESCAN_DELAY_MS = 250;

    private final int gutterWidth;
    private final int touchSlop;
    private final Paint gutterPaint = new Paint();
    private final Paint dividerPaint = new Paint();
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path triangle = new Path();
    private final int placeholderTextColor;
    private final int placeholderBackgroundColor;
    private final int searchHitColor;

    private Listener listener;
    private BackgroundColorSpan searchHit;

    // Fold regions of the text as it is displayed. Recomputed lazily, debounced after edits, so
    // typing in a long document does not rescan it on every keystroke.
    private JsonFoldRegions.Scan scan;
    private boolean scanDirty = true;
    private final SparseArray<JsonFoldRegions.Region> openersByLine = new SparseArray<>();
    private final SparseBooleanArray foldedLines = new SparseBooleanArray();
    private final Runnable rescan = () -> {
        ensureScan();
        invalidate();
    };

    // true while fold()/unfold() replace text on purpose
    private boolean structuralChange;
    private boolean gutterTouch;
    private float gutterDownY;

    public FoldEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        gutterWidth = Math.round(24 * density);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        placeholderTextColor = context.getColor(R.color.jsonEditorPlaceholderText);
        placeholderBackgroundColor = context.getColor(R.color.jsonEditorPlaceholderBackground);
        searchHitColor = context.getColor(R.color.jsonEditorSearchHit);
        gutterPaint.setColor(context.getColor(R.color.jsonEditorGutter));
        dividerPaint.setColor(context.getColor(R.color.jsonEditorGutterDivider));
        markerPaint.setColor(context.getColor(R.color.jsonEditorMarker));
        markerPaint.setStyle(Paint.Style.FILL);

        // reserve the gutter inside the left padding; onDraw paints into it
        setPadding(gutterWidth + Math.round(4 * density), getPaddingTop(), getPaddingRight(), getPaddingBottom());
        setHorizontallyScrolling(false);

        // The TextView constructor already built an Editable with the default factory; swap the
        // factory and rebuild so every Editable from now on is a ProtectedEditable.
        setEditableFactory(new Editable.Factory() {
            @Override
            public Editable newEditable(CharSequence source) {
                return new ProtectedEditable(source);
            }
        });
        setText("", BufferType.EDITABLE);

        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                scanDirty = true;
                removeCallbacks(rescan);
                postDelayed(rescan, RESCAN_DELAY_MS);
                if (!structuralChange && listener != null)
                    listener.onUserEdit();
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    //region document API
    /** Replaces the whole content with {@code full}, folded as given. Resets caret and scroll. */
    public FoldMath.Rendered setDocument(String full, Set<Integer> folded) {
        FoldMath.Rendered rendered = FoldMath.render(full, folded);
        SpannableStringBuilder text = new SpannableStringBuilder(rendered.display);
        for (FoldMath.Placeholder p : rendered.placeholders)
            decorate(text, p, 0);
        structuralChange = true;
        try {
            setText(text, BufferType.EDITABLE);
        } finally {
            structuralChange = false;
        }
        searchHit = null;
        scanDirty = true;
        ensureScan();
        invalidate();
        return rendered;
    }

    /** The complete document with every folded block restored, plus the current fold state. */
    public FoldMath.Extracted extractAll() {
        Editable text = getText();
        return FoldMath.extract(text, placeholdersOf(text), 0, text.length());
    }

    private static List<FoldMath.Placeholder> placeholdersOf(Editable text) {
        FoldMath.Placeholder[] spans = text.getSpans(0, text.length(), FoldMath.Placeholder.class);
        List<FoldMath.Placeholder> list = new ArrayList<>(spans.length);
        for (FoldMath.Placeholder p : spans) {
            p.displayStart = text.getSpanStart(p);
            p.displayEnd = text.getSpanEnd(p);
            list.add(p);
        }
        Collections.sort(list, (a, b) -> Integer.compare(a.displayStart, b.displayStart));
        return list;
    }

    /** Adds the placeholder span (and its styling) to {@code text}; offsets shifted by {@code shift}. */
    private void decorate(Spannable text, FoldMath.Placeholder p, int shift) {
        int start = p.displayStart + shift;
        int end = p.displayEnd + shift;
        text.setSpan(p, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(placeholderTextColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new BackgroundColorSpan(placeholderBackgroundColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** Selects the range, highlights it and scrolls it into view. Range must be visible text. */
    public void showMatch(int start, int end) {
        Editable text = getText();
        if (searchHit != null)
            text.removeSpan(searchHit);
        searchHit = new BackgroundColorSpan(searchHitColor);
        text.setSpan(searchHit, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        setSelection(start, end);
        scrollOffsetIntoView(start);
        // once more after the next layout pass, in case the text was only just replaced
        post(() -> scrollOffsetIntoView(start));
    }

    public void clearMatch() {
        if (searchHit != null) {
            getText().removeSpan(searchHit);
            searchHit = null;
        }
    }

    public void scrollOffsetIntoView(int offset) {
        Layout layout = getLayout();
        if (layout == null)
            return;
        offset = Math.max(0, Math.min(offset, getText().length()));
        int line = layout.getLineForOffset(offset);
        int max = Math.max(0, layout.getHeight() + getCompoundPaddingTop() + getCompoundPaddingBottom() - getHeight());
        // a third of the way down leaves context above the target
        int target = layout.getLineTop(line) - getHeight() / 3;
        scrollTo(0, Math.max(0, Math.min(target, max)));
    }
    //endregion

    //region folding
    private void ensureScan() {
        if (!scanDirty && scan != null)
            return;
        Editable text = getText();
        scan = JsonFoldRegions.scan(text);
        openersByLine.clear();
        foldedLines.clear();
        for (JsonFoldRegions.Region r : scan.regions) {
            // outer blocks come first, so a line that opens several keeps its outermost
            if (r.isMultiline() && openersByLine.get(r.startLine) == null)
                openersByLine.put(r.startLine, r);
        }
        for (FoldMath.Placeholder p : placeholdersOf(text))
            foldedLines.put(scan.lineOf(p.displayStart), true);
        scanDirty = false;
    }

    private void toggleLine(int line) {
        ensureScan();
        Editable text = getText();
        for (FoldMath.Placeholder p : placeholdersOf(text)) {
            if (scan.lineOf(p.displayStart) == line) {
                unfold(p);
                return;
            }
        }
        JsonFoldRegions.Region region = openersByLine.get(line);
        if (region != null)
            fold(region);
    }

    private void fold(JsonFoldRegions.Region region) {
        Editable text = getText();
        int from = region.openOffset + 1;
        int to = region.closeOffset + 1;
        // the hidden text, with any folds already inside it resolved back to their full text
        FoldMath.Extracted hidden = FoldMath.extract(text, placeholdersOf(text), from, to);
        int[] dormant = new int[hidden.folded.size()];
        int k = 0;
        for (int o : hidden.folded)
            dormant[k++] = o;
        FoldMath.Placeholder p = new FoldMath.Placeholder(hidden.full, dormant, region.key);
        p.displayStart = 0;
        p.displayEnd = p.text.length();
        SpannableStringBuilder replacement = new SpannableStringBuilder(p.text);
        decorate(replacement, p, 0);
        int caret = getSelectionStart();
        replaceStructurally(from, to, replacement);
        if (caret >= from)
            setSelection(Math.min(caret < to ? from : caret - (to - from) + p.text.length(), getText().length()));
    }

    private void unfold(FoldMath.Placeholder p) {
        Editable text = getText();
        int start = text.getSpanStart(p);
        int end = text.getSpanEnd(p);
        TreeSet<Integer> inner = new TreeSet<>();
        for (int d : p.dormant)
            inner.add(d);
        // folds that were nested inside this block come back folded, as in Notepad++
        FoldMath.Rendered rendered = FoldMath.render(p.hidden, inner);
        SpannableStringBuilder replacement = new SpannableStringBuilder(rendered.display);
        for (FoldMath.Placeholder q : rendered.placeholders)
            decorate(replacement, q, 0);
        replaceStructurally(start, end, replacement);
    }

    private void replaceStructurally(int start, int end, CharSequence replacement) {
        ProtectedEditable text = (ProtectedEditable) getText();
        structuralChange = true;
        text.bypass = true;
        try {
            text.replace(start, end, replacement);
        } finally {
            text.bypass = false;
            structuralChange = false;
        }
        scanDirty = true;
        ensureScan();
        invalidate();
    }
    //endregion

    //region gutter
    @Override
    protected void onDraw(Canvas canvas) {
        Layout layout = getLayout();
        if (layout != null) {
            int left = getScrollX();
            int top = getScrollY();
            canvas.drawRect(left, top, left + gutterWidth, top + getHeight(), gutterPaint);
            canvas.drawRect(left + gutterWidth - 1, top, left + gutterWidth, top + getHeight(), dividerPaint);
        }
        super.onDraw(canvas);
        if (layout == null)
            return;
        // Never rescan from here: drawing happens per keystroke. A scan that is a few hundred
        // milliseconds stale only misplaces a marker briefly; the debounced rescan fixes it.
        if (scan == null)
            ensureScan();
        Editable text = getText();
        int topLine = layout.getLineForVertical(Math.max(0, getScrollY() - getExtendedPaddingTop()));
        int bottomLine = layout.getLineForVertical(Math.max(0, getScrollY() + getHeight() - getExtendedPaddingTop()));
        float cx = getScrollX() + gutterWidth / 2f;
        float size = gutterWidth * 0.22f;
        for (int v = topLine; v <= bottomLine; v++) {
            int start = layout.getLineStart(v);
            if (start > 0 && text.charAt(start - 1) != '\n')
                continue; // wrapped continuation of a logical line already marked
            int logical = scan.lineOf(start);
            boolean folded = foldedLines.get(logical);
            if (!folded && openersByLine.get(logical) == null)
                continue;
            float cy = getExtendedPaddingTop() + (layout.getLineTop(v) + layout.getLineBottom(v)) / 2f;
            drawMarker(canvas, cx, cy, size, folded);
        }
    }

    private void drawMarker(Canvas canvas, float cx, float cy, float size, boolean folded) {
        triangle.reset();
        if (folded) { // pointing right
            triangle.moveTo(cx - size * 0.6f, cy - size);
            triangle.lineTo(cx + size, cy);
            triangle.lineTo(cx - size * 0.6f, cy + size);
        } else { // pointing down
            triangle.moveTo(cx - size, cy - size * 0.6f);
            triangle.lineTo(cx + size, cy - size * 0.6f);
            triangle.lineTo(cx, cy + size);
        }
        triangle.close();
        canvas.drawPath(triangle, markerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gutterTouch = event.getX() < gutterWidth;
                gutterDownY = event.getY();
                if (gutterTouch)
                    return true; // keep the caret where it is
                break;
            case MotionEvent.ACTION_UP:
                if (gutterTouch) {
                    gutterTouch = false;
                    if (Math.abs(event.getY() - gutterDownY) < touchSlop)
                        onGutterTap(event.getY());
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (gutterTouch) {
                    gutterTouch = false;
                    return true;
                }
                break;
            default:
                if (gutterTouch)
                    return true;
                break;
        }
        return super.onTouchEvent(event);
    }

    private void onGutterTap(float y) {
        Layout layout = getLayout();
        if (layout == null)
            return;
        int line = layout.getLineForVertical(Math.max(0, (int) (y + getScrollY() - getExtendedPaddingTop())));
        ensureScan(); // a tap must act on the current text, not a stale scan
        toggleLine(scan.lineOf(layout.getLineStart(line)));
    }
    //endregion

    /**
     * The editor's text buffer. It rejects every change that touches a folded placeholder or the
     * opening bracket right before it; the single funnel for all edits (typing, IME, paste, cut,
     * delete) is {@code replace(int, int, CharSequence, int, int)}.
     *
     * <p>Rejecting here, rather than in an InputFilter, matters: a filter can only substitute
     * replacement text, and substituting "the original text" for a deletion that covers a
     * placeholder would re-insert it without its span - losing the hidden content.
     */
    private final class ProtectedEditable extends SpannableStringBuilder {
        boolean bypass;

        ProtectedEditable(CharSequence source) {
            super(source);
        }

        @Override
        public SpannableStringBuilder replace(int st, int en, CharSequence source, int start, int end) {
            if (!bypass && touchesPlaceholder(Math.min(st, en), Math.max(st, en))) {
                if (listener != null)
                    listener.onEditRejected();
                return this;
            }
            return super.replace(st, en, source, start, end);
        }

        private boolean touchesPlaceholder(int st, int en) {
            FoldMath.Placeholder[] spans = getSpans(0, length(), FoldMath.Placeholder.class);
            for (FoldMath.Placeholder p : spans) {
                int ps = getSpanStart(p);
                int pe = getSpanEnd(p);
                // protected zone is [opener, placeholder end); editing right before the opener
                // or right after the placeholder is fine
                if (ps >= 0 && en > ps - 1 && st < pe)
                    return true;
            }
            return false;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(rescan);
        super.onDetachedFromWindow();
    }
}
