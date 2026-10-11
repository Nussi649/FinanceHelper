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
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.OverScroller;

import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
    // finger down on the text (not the gutter)
    private boolean textTouch;
    // The user panned by hand and has not tapped or typed since. TextView pulls the view back to
    // the caret whenever it can (on finger-up and on later redraws), which snapped every
    // sideways pan straight back to x = 0 the moment the finger lifted. While this is set,
    // bringPointIntoView is a no-op; a tap or any typing clears it, so typing still follows
    // the caret.
    private boolean keepUserScroll;
    // the current text touch scrolled the view, i.e. it was a pan rather than a tap
    private boolean pannedThisTouch;
    // this touch moved past the slop and is being handled as a pan by this class, not TextView
    private boolean panning;
    // this touch turned into a long-press selection, which TextView keeps handling
    private boolean longPressed;
    private float downX, downY, lastX, lastY;
    private VelocityTracker velocity;
    private final OverScroller flinger;
    private final int minFlingVelocity;
    private final int maxFlingVelocity;
    // right edge of the widest line, -1 = needs measuring (reset whenever the text changes)
    private int widestLine = -1;
    private boolean imeVisible;

    public FoldEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        gutterWidth = Math.round(24 * density);
        ViewConfiguration viewConfig = ViewConfiguration.get(context);
        touchSlop = viewConfig.getScaledTouchSlop();
        minFlingVelocity = viewConfig.getScaledMinimumFlingVelocity();
        maxFlingVelocity = viewConfig.getScaledMaximumFlingVelocity();
        flinger = new OverScroller(context);
        placeholderTextColor = context.getColor(R.color.jsonEditorPlaceholderText);
        placeholderBackgroundColor = context.getColor(R.color.jsonEditorPlaceholderBackground);
        searchHitColor = context.getColor(R.color.jsonEditorSearchHit);
        gutterPaint.setColor(context.getColor(R.color.jsonEditorGutter));
        dividerPaint.setColor(context.getColor(R.color.jsonEditorGutterDivider));
        markerPaint.setColor(context.getColor(R.color.jsonEditorMarker));
        markerPaint.setStyle(Paint.Style.FILL);

        // reserve the gutter inside the left padding; onDraw paints into it
        setPadding(gutterWidth + Math.round(4 * density), getPaddingTop(), getPaddingRight(), getPaddingBottom());
        // Lines never wrap: the EditText itself pans in both axes (its movement method turns a
        // one-finger drag into scrollTo(x, y)); the gutter is repainted at getScrollX() so it
        // stays put on screen.
        setHorizontallyScrolling(true);

        // Closing the keyboard (Back, or swiping it away) ends editing: drop the cursor so the
        // editor is back in its neutral, read-and-scroll state. The layout root is focusable in
        // touch mode, so the focus has somewhere to go.
        ViewCompat.setOnApplyWindowInsetsListener(this, (view, insets) -> {
            boolean visible = insets.isVisible(WindowInsetsCompat.Type.ime());
            if (imeVisible && !visible && hasFocus())
                clearFocus();
            imeVisible = visible;
            return ViewCompat.onApplyWindowInsets(view, insets);
        });

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
                if (!structuralChange)
                    keepUserScroll = false;
                widestLine = -1;
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
        int x = 0;
        int visibleWidth = getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        if (visibleWidth > 0) {
            // keep the offset a quarter of the way in from the left once it is off screen
            int maxX = Math.max(0, (int) Math.ceil(layout.getLineRight(line)) - visibleWidth);
            int at = (int) layout.getPrimaryHorizontal(offset);
            if (at >= visibleWidth)
                x = Math.max(0, Math.min(at - visibleWidth / 4, maxX));
        }
        scrollTo(x, Math.max(0, Math.min(target, max)));
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
        super.onDraw(canvas);
        Layout layout = getLayout();
        if (layout == null)
            return;
        // The canvas is translated by the scroll offsets, so the gutter is painted at
        // (getScrollX(), getScrollY()): it covers whatever text has scrolled under it and the
        // markers stay at the same screen position at any horizontal scroll.
        int left = getScrollX();
        int top = getScrollY();
        canvas.drawRect(left, top, left + gutterWidth, top + getHeight(), gutterPaint);
        canvas.drawRect(left + gutterWidth - 1, top, left + gutterWidth, top + getHeight(), dividerPaint);
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
                textTouch = true;
                pannedThisTouch = false;
                panning = false;
                longPressed = false;
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                flinger.forceFinished(true);
                if (velocity != null)
                    velocity.recycle();
                velocity = VelocityTracker.obtain();
                velocity.addMovement(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (gutterTouch)
                    return true;
                if (velocity != null)
                    velocity.addMovement(event);
                if (!panning && !longPressed && textTouch
                        && Math.hypot(event.getX() - downX, event.getY() - downY) > touchSlop) {
                    // From here on this touch is a pan, never a caret gesture. TextView would
                    // otherwise read a mostly-horizontal drag as "drag the cursor" (Android 12+
                    // does that anywhere in focused text), so once a cursor existed every sideways
                    // swipe moved it instead of scrolling. Cancelling the TextView's half of the
                    // gesture also drops its pending long-press.
                    panning = true;
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.onTouchEvent(cancel);
                    cancel.recycle();
                    lastX = event.getX();
                    lastY = event.getY();
                }
                if (panning) {
                    panTo(getScrollX() + Math.round(lastX - event.getX()),
                            getScrollY() + Math.round(lastY - event.getY()));
                    lastX = event.getX();
                    lastY = event.getY();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (gutterTouch) {
                    gutterTouch = false;
                    if (Math.abs(event.getY() - gutterDownY) < touchSlop)
                        onGutterTap(event.getY());
                    return true;
                }
                if (panning) {
                    panning = false;
                    textTouch = false;
                    if (velocity != null) {
                        velocity.addMovement(event);
                        velocity.computeCurrentVelocity(1000, maxFlingVelocity);
                        fling(-velocity.getXVelocity(), -velocity.getYVelocity());
                    }
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                textTouch = false;
                if (gutterTouch) {
                    gutterTouch = false;
                    return true;
                }
                if (panning) {
                    panning = false;
                    return true;
                }
                break;
            default:
                if (gutterTouch || panning)
                    return true;
                break;
        }
        boolean handled = super.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP && textTouch) {
            textTouch = false;
            // a tap places the caret, so following it is right again; a pan keeps its position
            if (!pannedThisTouch)
                keepUserScroll = false;
        }
        return handled;
    }

    @Override
    public boolean performLongClick() {
        // the TextView's long-press (word selection) owns the rest of this touch: dragging then
        // extends the selection instead of panning
        longPressed = true;
        return super.performLongClick();
    }

    /** Scrolls to (x, y), clamped to the text's full extent, not just the lines on screen. */
    private void panTo(int x, int y) {
        scrollTo(Math.max(0, Math.min(x, maxScrollX())), Math.max(0, Math.min(y, maxScrollY())));
    }

    private void fling(float velocityX, float velocityY) {
        if (Math.abs(velocityX) < minFlingVelocity && Math.abs(velocityY) < minFlingVelocity)
            return;
        flinger.fling(getScrollX(), getScrollY(), Math.round(velocityX), Math.round(velocityY),
                0, maxScrollX(), 0, maxScrollY());
        postInvalidateOnAnimation();
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (flinger.computeScrollOffset()) {
            keepUserScroll = true; // a fling is the tail of a pan
            panTo(flinger.getCurrX(), flinger.getCurrY());
            postInvalidateOnAnimation();
        }
    }

    private int maxScrollX() {
        Layout layout = getLayout();
        if (layout == null)
            return 0;
        if (widestLine < 0) {
            // getWidth() of a horizontally scrolling layout is a huge sentinel, so measure lines
            float widest = 0;
            for (int i = 0; i < layout.getLineCount(); i++)
                widest = Math.max(widest, layout.getLineRight(i));
            widestLine = (int) Math.ceil(widest);
        }
        return Math.max(0, widestLine + getCompoundPaddingLeft() + getCompoundPaddingRight() - getWidth());
    }

    private int maxScrollY() {
        Layout layout = getLayout();
        if (layout == null)
            return 0;
        return Math.max(0, layout.getHeight() + getCompoundPaddingTop() + getCompoundPaddingBottom() - getHeight());
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (textTouch) {
            pannedThisTouch = true;
            keepUserScroll = true;
        }
    }

    // Deliberately not cleared on selection changes: lifting the finger after a pan moves the
    // selection to the touch point and straight back, which would undo the pan on the next
    // frame. Taps and typing are the signals that the caret should be followed again.
    @Override
    public boolean bringPointIntoView(int offset) {
        return !keepUserScroll && super.bringPointIntoView(offset);
    }

    @Override
    public boolean bringPointIntoView(int offset, boolean requestRectWithoutFocus) {
        return !keepUserScroll && super.bringPointIntoView(offset, requestRectWithoutFocus);
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
