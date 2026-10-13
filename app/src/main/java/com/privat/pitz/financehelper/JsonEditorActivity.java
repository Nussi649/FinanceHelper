package com.privat.pitz.financehelper;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBar;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import com.privat.pitz.financehelper.core.ParseReport;
import com.privat.pitz.financehelper.sync.CloudSync;
import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;
import com.privat.pitz.financehelper.jsoneditor.AccountFocus;
import com.privat.pitz.financehelper.jsoneditor.FoldMath;
import com.privat.pitz.financehelper.jsoneditor.JsonCompactPrinter;
import com.privat.pitz.financehelper.jsoneditor.TextSearch;
import com.privat.pitz.financehelper.ui.FoldEditText;

/**
 * Full-screen editor for the raw save file, with one transaction per line, search and
 * Notepad++-style folding of blocks.
 *
 * <p>Replaces the old single-dialog editor, which showed the whole file as one unstructured
 * block of {@code toString(4)} text and had to be scrolled through line by line.
 *
 * <p>Optionally opened "on" one account (see {@link #focusIntent}): every other account block is
 * folded and the view is scrolled to that account.
 */
public class JsonEditorActivity extends AbstractActivity {
    private static final String EXTRA_FOCUS_NAME = "focus_name";
    private static final String EXTRA_FOCUS_BUDGET = "focus_budget";
    /** At depth 2 sit the individual accounts: root (0) > "assets"/"budgets" (1) > account (2). */
    private static final int ACCOUNT_DEPTH = 2;
    private static final long REJECT_TOAST_INTERVAL_MS = 2500;

    private FoldEditText editor;
    private View searchBar;
    private EditText searchField;
    private TextView searchCounter;

    private String formatted;
    private JSONException loadError;
    // The full text as first shown. Whether there are unsaved changes is judged by comparing the
    // restored full text against this, so folding and unfolding alone never count as a change.
    private String initialFull;
    // start (in the full text) of the search hit that is currently selected, or -1
    private int currentMatchFull = -1;
    private long lastRejectToast;
    private boolean focused;

    /** Intent that opens the editor with only {@code account} (and its ancestors) unfolded. */
    public static Intent focusIntent(Context context, AccountBE account, boolean budget) {
        Intent intent = new Intent(context, JsonEditorActivity.class);
        intent.putExtra(EXTRA_FOCUS_NAME, account.getName());
        intent.putExtra(EXTRA_FOCUS_BUDGET, budget);
        return intent;
    }

    //region AbstractActivity overrides
    @Override
    protected void setupActionBar() {
        // not the entity-spinner bar of the other screens: this one is just a title
        ActionBar bar = getSupportActionBar();
        if (bar == null)
            return;
        bar.setDisplayShowCustomEnabled(false);
        bar.setDisplayShowTitleEnabled(true);
        bar.setDisplayHomeAsUpEnabled(true);
        bar.setTitle(R.string.label_json_editor_title);
    }

    @Override
    protected void workingThread() {
        // formatting a large file is the slow part, so it stays off the UI thread
        try {
            formatted = JsonCompactPrinter.format(controller.exportAccounts());
        } catch (JSONException e) {
            loadError = e;
        }
    }

    @Override
    protected void endWorkingThread() {
        if (loadError != null) {
            showErrorToast(loadError);
            finish();
            return;
        }
        setContentView(R.layout.activity_json_editor);
        editor = findViewById(R.id.json_editor);
        searchBar = findViewById(R.id.search_bar);
        searchField = findViewById(R.id.search_query);
        searchCounter = findViewById(R.id.search_counter);
        initialFull = formatted;

        editor.setListener(new FoldEditText.Listener() {
            @Override
            public void onEditRejected() {
                long now = SystemClock.elapsedRealtime();
                if (now - lastRejectToast > REJECT_TOAST_INTERVAL_MS) {
                    lastRejectToast = now;
                    showToast(R.string.toast_info_folded_block_protected);
                }
            }

            @Override
            public void onUserEdit() {
                // the remembered hit position no longer refers to the same text
                currentMatchFull = -1;
            }
        });

        setUpSearch();
        showInitialDocument();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                requestExit();
            }
        });
    }

    @Override
    public void onRefresh() {
        // nothing to refresh: this screen only changes through its own save
    }

    @Override
    protected void onSavefileReloaded() {
        // automatic syncs are held back while this screen is open (see onStart), so a reload that
        // still arrives was already under way; the text being edited is not touched
    }

    @Override
    protected void onStart() {
        super.onStart();
        CloudSync.pause();
    }

    @Override
    protected void onStop() {
        CloudSync.resume();
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_json_editor, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (editor == null)
            return super.onOptionsItemSelected(item);
        // handled items return true: letting AppCompat see "home" would navigate up and skip
        // the unsaved-changes question
        if (id == android.R.id.home) {
            requestExit();
        } else if (id == R.id.item_json_save) {
            save();
        } else if (id == R.id.item_json_search) {
            toggleSearchBar();
        } else if (id == R.id.item_json_fold_all) {
            foldAll();
        } else if (id == R.id.item_json_unfold_all) {
            applyFolds(editor.extractAll(), new TreeSet<>());
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }
    //endregion

    //region document
    private void showInitialDocument() {
        AccountFocus.Plan plan = null;
        String name = getIntent().getStringExtra(EXTRA_FOCUS_NAME);
        if (name != null) {
            boolean budget = getIntent().getBooleanExtra(EXTRA_FOCUS_BUDGET, false);
            plan = AccountFocus.plan(formatted, budget, pathOf(name, budget));
            focused = plan != null;
        }
        if (plan == null) {
            editor.setDocument(formatted, new TreeSet<>());
            return;
        }
        FoldMath.Rendered rendered = editor.setDocument(formatted, plan.fold);
        int at = rendered.fullToDisplay(plan.target.openOffset);
        editor.setSelection(at);
        editor.scrollOffsetIntoView(at);
        editor.post(() -> editor.scrollOffsetIntoView(at));
    }

    /**
     * Account names from the top down to the inspected account. A sub-budget is told apart from
     * a same-named account elsewhere by its parents, so the path is worked out by identity from
     * the model rather than trusting the name alone.
     */
    private List<String> pathOf(String name, boolean budget) {
        if (budget && model.currentInspectedAccount instanceof BudgetAccountBE
                && name.equals(model.currentInspectedAccount.getName())) {
            List<String> path = findBudgetPath(model.budget_accounts,
                    (BudgetAccountBE) model.currentInspectedAccount, new ArrayList<>());
            if (path != null)
                return path;
        }
        return Collections.singletonList(name);
    }

    private static List<String> findBudgetPath(List<BudgetAccountBE> level, BudgetAccountBE target,
                                               List<String> prefix) {
        for (BudgetAccountBE candidate : level) {
            List<String> path = new ArrayList<>(prefix);
            path.add(candidate.getName());
            if (candidate == target)
                return path;
            List<String> deeper = findBudgetPath(candidate.getDirectSubBudgets(), target, path);
            if (deeper != null)
                return deeper;
        }
        return null;
    }

    private void foldAll() {
        FoldMath.Extracted current = editor.extractAll();
        applyFolds(current, FoldMath.foldAtDepth(current.full, current.folded, ACCOUNT_DEPTH));
    }

    /** Re-renders the document with a new fold state, keeping the caret on the same text. */
    private void applyFolds(FoldMath.Extracted current, TreeSet<Integer> folded) {
        int caretFull = current.displayToFull(editor.getSelectionStart());
        FoldMath.Rendered rendered = editor.setDocument(current.full, folded);
        int caret = rendered.fullToDisplay(caretFull);
        editor.setSelection(caret);
        editor.scrollOffsetIntoView(caret);
    }

    private boolean hasUnsavedChanges() {
        return !editor.extractAll().full.equals(initialFull);
    }

    private void requestExit() {
        if (editor == null || !hasUnsavedChanges()) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.question_discard_changes)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.label_discard, (dialog, which) -> finish())
                .show();
    }
    //endregion

    //region save
    private void save() {
        // the text with every folded block restored, regardless of what is folded
        String text = editor.extractAll().full;

        // importAccounts replaces the model piece by piece and can fail half way through, so keep
        // a copy to put the model back with. Without it a typo would leave the app running on a
        // half-imported model that the next autosave then writes to disk.
        String backup;
        try {
            backup = controller.exportAccounts();
        } catch (JSONException e) {
            showErrorToast(e);
            return;
        }

        try {
            controller.importAccounts(text);
        } catch (JSONException | RuntimeException e) {
            restoreModel(backup);
            showToastLong(getString(R.string.toast_error_json_invalid, String.valueOf(e.getMessage())));
            return; // stay in the editor so no edits are lost
        }
        controller.sortAllTransactions();

        try {
            controller.saveAccountsToInternal();
        } catch (JSONException | IOException e) {
            showErrorToast(e);
            return;
        }
        initialFull = text;
        showToast(R.string.toast_success_accounts_imported);

        ParseReport report = model.takeLoadReport();
        if (report.hasDiscards()) {
            // an edit made the parser drop records: say so before leaving, the user can still
            // reload the old file from a backup
            StringBuilder sb = new StringBuilder();
            for (ParseReport.Note note : report.getNotes())
                sb.append("• ").append(note.message).append("\n\n");
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.label_load_problems_title, report.countDiscards()))
                    .setMessage(sb.toString().trim())
                    .setPositiveButton(R.string.ok, null)
                    .setOnDismissListener(dialog -> leaveAfterSave())
                    .show();
            return;
        }
        leaveAfterSave();
    }

    private void restoreModel(String backup) {
        try {
            controller.importAccounts(backup);
            model.takeLoadReport(); // an unchanged re-import reports nothing worth showing
        } catch (JSONException | RuntimeException e) {
            android.util.Log.println(android.util.Log.ERROR, "json_editor",
                    "Could not restore the model after a failed import: " + e);
        }
    }

    private void leaveAfterSave() {
        if (focused) {
            // The details screen underneath holds the old account objects, which importAccounts
            // just replaced. Return to the main screen and drop everything above it.
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
        // MainActivity refreshes itself in onStart when it comes back to the front
        finish();
    }
    //endregion

    //region search
    private void setUpSearch() {
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                currentMatchFull = -1; // a new query starts from the caret again
                search(true);
            }
        });
        searchField.setOnEditorActionListener((v, actionId, event) -> {
            search(true);
            return true;
        });
        findViewById(R.id.search_next).setOnClickListener(v -> search(true));
        findViewById(R.id.search_previous).setOnClickListener(v -> search(false));
    }

    private void toggleSearchBar() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (searchBar.getVisibility() == View.VISIBLE) {
            searchBar.setVisibility(View.GONE);
            editor.clearMatch();
            currentMatchFull = -1;
            imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
        } else {
            searchBar.setVisibility(View.VISIBLE);
            searchField.requestFocus();
            imm.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT);
            if (searchField.length() > 0)
                search(true);
        }
    }

    /**
     * Jumps to the next/previous hit. Searches the full text, folded parts included, and unfolds
     * whatever hides the hit, so a hit is never present-but-invisible.
     */
    private void search(boolean forward) {
        String query = searchField.getText().toString();
        if (query.isEmpty()) {
            searchCounter.setText("");
            editor.clearMatch();
            return;
        }
        FoldMath.Extracted current = editor.extractAll();
        int[] starts = TextSearch.findAll(current.full, query);
        if (starts.length == 0) {
            searchCounter.setText(R.string.label_search_no_hits);
            editor.clearMatch();
            currentMatchFull = -1;
            return;
        }

        int ref;
        boolean inclusive;
        if (currentMatchFull >= 0) {
            ref = currentMatchFull;
            inclusive = false;
        } else {
            ref = current.displayToFull(editor.getSelectionStart());
            inclusive = true;
        }
        int index = TextSearch.pick(starts, ref, forward, inclusive);
        int start = starts[index];
        int end = start + query.length();

        TreeSet<Integer> remaining = FoldMath.unfoldOverlapping(current.full, current.folded, start, end);
        int displayStart;
        if (remaining.equals(current.folded)) {
            displayStart = current.fullToDisplay(start);
        } else {
            displayStart = editor.setDocument(current.full, remaining).fullToDisplay(start);
        }
        editor.showMatch(displayStart, displayStart + query.length());
        currentMatchFull = start;
        searchCounter.setText(getString(R.string.label_search_counter, index + 1, starts.length));
    }
    //endregion
}
