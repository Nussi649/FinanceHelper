package com.privat.pitz.financehelper.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.privat.pitz.financehelper.R;
import com.privat.pitz.financehelper.data.TxBE;
import com.privat.pitz.financehelper.ui.adapter.TxListAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Reusable wiring for the component_tx_recycler_view.xml component: a SearchView-filtered
 * RecyclerView plus a summary card. Operates on a plain {@link View} (never an Activity) so it
 * can be reused from both activities (via their root/content view) and from dialogs that only
 * have an inflated View available.
 */
public class TxListSection {

    /** Implemented by TxListAdapter and RecurringTxAdapter so this class can stay non-generic. */
    public interface EntryListAdapter {
        void setEntries(List<? extends TxBE> entries);
    }

    /** Supplied only when swipe gestures are wanted. */
    public interface TxActions {
        void onEditRequested(int position, TxBE tx);
        void onDeleteRequested(int position, TxBE tx);
    }

    /**
     * Supplied by screens whose entries can be selected for bulk actions. Each callback receives
     * {@code done}, which ends selection mode; call it once the action is carried out, and not if
     * the user backs out of a dialog, so the selection survives a cancel.
     */
    public interface SelectionActions {
        /** {@code anchor} is the entry selected first, which the others are merged into. */
        void onMergeRequested(TxBE anchor, List<TxBE> others, Runnable done);
        void onMoveRequested(List<TxBE> entries, Runnable done);
    }

    public static final BiPredicate<TxBE, String> MATCH_DESCRIPTION =
            (tx, query) -> tx.getDescription().toUpperCase().contains(query.toUpperCase());

    private final View root;
    private final RecyclerView.Adapter<?> recyclerAdapter;
    private final EntryListAdapter entryAdapter;
    private final Supplier<List<? extends TxBE>> entriesSupplier;
    private final BiPredicate<TxBE, String> filter;
    private final Consumer<Float> sumRenderer;

    private final RecyclerView recyclerView;

    private String lastQuery;
    private List<? extends TxBE> lastVisibleEntries = new ArrayList<>();

    private static final int MENU_MERGE = 1;
    private static final int MENU_MOVE = 2;
    // non-null exactly while selection mode is active
    private ActionMode actionMode;

    public <A extends RecyclerView.Adapter<?> & EntryListAdapter> TxListSection(
            View root,
            A adapter,
            Supplier<List<? extends TxBE>> entries,
            BiPredicate<TxBE, String> filter,
            Consumer<Float> sumRenderer,
            TxActions swipeActions) {

        this.root = root;
        this.recyclerAdapter = adapter;
        this.entryAdapter = adapter;
        this.entriesSupplier = entries;
        this.filter = filter;
        this.sumRenderer = sumRenderer;

        recyclerView = root.findViewById(R.id.recyclerView);
        SearchView searchView = root.findViewById(R.id.search_filter);
        View containerTx = root.findViewById(R.id.container_tx_sum);
        TextView indivValue = containerTx.findViewById(R.id.total_current_value);
        TextView indivPercentage = containerTx.findViewById(R.id.total_current_percentage);
        TextView indivYearly = containerTx.findViewById(R.id.total_yearly_budget);
        indivYearly.setVisibility(View.GONE);
        indivPercentage.setVisibility(View.GONE);

        recyclerView.setLayoutManager(new LinearLayoutManager(root.getContext()));
        recyclerView.setAdapter(recyclerAdapter);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextChange(String newText) {
                applyFilter(newText);
                return true;
            }

            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }
        });

        if (swipeActions != null) {
            attachSwipeGestures(swipeActions);
        }

        applyFilter(null);
    }

    private void attachSwipeGestures(TxActions actions) {
        Context context = root.getContext();

        ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                // a swipe-delete in the middle of selecting would pull a row out from under the
                // selection; while selecting, a horizontal drag does nothing
                return actionMode != null ? 0 : super.getSwipeDirs(recyclerView, viewHolder);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                TxBE tx = lastVisibleEntries.get(position);

                if (direction == ItemTouchHelper.LEFT) {
                    // Swipe left to delete
                    actions.onDeleteRequested(position, tx);
                } else if (direction == ItemTouchHelper.RIGHT) {
                    // Swipe right to edit
                    actions.onEditRequested(position, tx);

                    // ItemTouchHelper assumes onSwiped() means the item is gone and leaves the
                    // ViewHolder translated off-screen. That is correct for LEFT/delete, where
                    // onDeleteRequested actually removes the row from the adapter. RIGHT/edit is
                    // not a removal - it is "reveal a dialog" - so the row must always come back
                    // once the gesture finishes, regardless of whether the dialog is confirmed,
                    // cancelled, or dismissed. Relying on the action implementation to do this
                    // (e.g. only on a confirmed edit) leaves the row missing on cancel, which is
                    // exactly the bug this restores. This is the swipe gesture's own callback, so
                    // it - not any TxActions implementation - is the right place to guarantee it,
                    // unconditionally and every time. Do not move this into onConfirm/onCancel.
                    recyclerAdapter.notifyItemChanged(position);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

                Drawable icon;
                ColorDrawable background;

                View itemView = viewHolder.itemView;
                int backgroundCornerOffset = 20; // to cover the rounded corners of the item

                if (dX > 0) { // Swiping to the right
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_edit_black_24); // replace with your own drawable
                    background = new ColorDrawable(context.getColor(R.color.colorNeutral)); // replace with your own color

                    int iconMargin = (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
                    int iconTop = itemView.getTop() + (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
                    int iconBottom = iconTop + icon.getIntrinsicHeight();

                    int iconLeft = itemView.getLeft() + iconMargin;
                    int iconRight = itemView.getLeft() + iconMargin + icon.getIntrinsicWidth();

                    icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                    background.setBounds(itemView.getLeft(), itemView.getTop(),
                            itemView.getLeft() + ((int) dX) + backgroundCornerOffset, itemView.getBottom());
                    background.draw(c);
                    icon.draw(c);
                } else if (dX < 0) { // Swiping to the left
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_delete_24); // replace with your own drawable
                    background = new ColorDrawable(context.getColor(R.color.colorNegative)); // replace with your own color

                    int iconMargin = (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
                    int iconTop = itemView.getTop() + (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
                    int iconBottom = iconTop + icon.getIntrinsicHeight();

                    int iconRight = itemView.getRight() - iconMargin;
                    int iconLeft = itemView.getRight() - iconMargin - icon.getIntrinsicWidth();

                    icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                    background.setBounds(itemView.getRight() + ((int) dX) - backgroundCornerOffset,
                            itemView.getTop(), itemView.getRight(), itemView.getBottom());
                    background.draw(c);
                    icon.draw(c);
                }
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    /**
     * Turns on selecting entries: a long-press selects the first one and enters selection mode,
     * taps then add or remove entries, and the contextual action bar offers merge and move.
     */
    public void enableSelection(TxListAdapter adapter, SelectionActions actions) {
        AppCompatActivity activity = (AppCompatActivity) root.getContext();
        ActionMode.Callback callback = new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                menu.add(Menu.NONE, MENU_MERGE, Menu.NONE, R.string.label_merge)
                        .setIcon(R.drawable.ic_merge_24)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                menu.add(Menu.NONE, MENU_MOVE, Menu.NONE, R.string.label_move)
                        .setIcon(R.drawable.ic_transfer_arrow_24)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                List<TxBE> selected = adapter.getSelected();
                if (item.getItemId() == MENU_MERGE) {
                    if (selected.size() < 2) {
                        Toast.makeText(activity, R.string.toast_info_merge_needs_two,
                                Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    actions.onMergeRequested(selected.get(0),
                            selected.subList(1, selected.size()), mode::finish);
                    return true;
                } else if (item.getItemId() == MENU_MOVE) {
                    actions.onMoveRequested(selected, mode::finish);
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                actionMode = null;
                adapter.clearSelection();
            }
        };

        adapter.setEntryInteraction(new TxListAdapter.EntryInteraction() {
            @Override
            public void onEntryClicked(TxBE tx) {
                if (actionMode == null)
                    return;
                toggle(tx);
            }

            @Override
            public void onEntryLongClicked(TxBE tx) {
                if (actionMode == null)
                    actionMode = activity.startSupportActionMode(callback);
                toggle(tx);
            }

            private void toggle(TxBE tx) {
                adapter.toggleSelected(tx);
                int count = adapter.getSelected().size();
                if (count == 0)
                    actionMode.finish();
                else
                    actionMode.setTitle(activity.getString(R.string.label_selection_count, count));
            }
        });
    }

    /**
     * Re-reads the live entries, filters them against {@code query} (when non-null and
     * non-empty), sums the visible entries, and pushes both the sum and the filtered list to
     * the caller-supplied renderer/adapter.
     */
    public void applyFilter(String query) {
        lastQuery = query;

        List<? extends TxBE> rawEntries = entriesSupplier.get();
        List<? extends TxBE> visible;

        // Always a fresh list, never the caller's own. TxListAdapter stores what it is given by
        // reference and mutates it in place on swipe-delete, so handing it the live model list
        // (which is what the unfiltered path used to do) let the optimistic removal delete the
        // transaction from the model before Controller.deleteTx ever looked for it.
        List<TxBE> selected = new ArrayList<>();
        for (TxBE entry : rawEntries) {
            if (query == null || filter.test(entry, query)) {
                selected.add(entry);
            }
        }
        visible = selected;

        float sum = 0.0f;
        for (TxBE entry : visible) {
            sum += entry.getAmount();
        }

        sumRenderer.accept(sum);
        entryAdapter.setEntries(visible);
        lastVisibleEntries = visible;
    }

    /** Re-applies the currently active filter against freshly read live entries. */
    public void refresh() {
        applyFilter(lastQuery);
    }

    public RecyclerView getRecyclerView() {
        return recyclerView;
    }

    public boolean isEmpty() {
        return recyclerAdapter.getItemCount() == 0;
    }
}
