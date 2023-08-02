package com.privat.pitz.financehelper;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import Backend.TxListAdapter;
import Backend.Util;
import Logic.AccountBE;
import Logic.TxBE;
import View.Dialogs.EditTxDialog;

public class AssetAccountDetailsActivity extends AbstractActivity {
    AccountBE mAccount;
    TxListAdapter listAdapter;
    RecyclerView recyclerView;
    SearchView searchView;
    TextView indivValue;
    TextView indivPercentage;
    TextView indivYearly;

    // region AbstractActivity & Activity Overrides
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void workingThread() {
        mAccount = getModel().currentInspectedAccount;
    }

    @Override
    protected void endWorkingThread() {
        setContentLayout();
        initViews();
        initListAdapter();
        initListGestures();
        populateUI();
        setTitle();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_account_details, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.item_delete_account)
            deleteAccount();
        return super.onOptionsItemSelected(item);
    }
    // endregion

    // region Relevant for Compatibility (Overridden in inheriting classes)
    protected void setContentLayout() {
        setContentView(R.layout.activity_asset_account_details);
    }

    protected void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        searchView = findViewById(R.id.search_filter);
        View containerTx = findViewById(R.id.container_tx_sum);
        indivValue= containerTx.findViewById(R.id.total_current_value);
        indivPercentage = containerTx.findViewById(R.id.total_current_percentage);
        indivYearly = containerTx.findViewById(R.id.total_yearly_budget);
        indivYearly.setVisibility(View.GONE);
        indivPercentage.setVisibility(View.GONE);
    }

    protected void initListAdapter() {
        listAdapter = new TxListAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(listAdapter);
    }

    protected void initListGestures() {
        Context self = this;
        ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                TxBE tx = listAdapter.getTxAtPosition(position);

                if (direction == ItemTouchHelper.LEFT) {
                    // Swipe left to delete
                    deleteTx(position, tx);
                } else if (direction == ItemTouchHelper.RIGHT) {
                    // Swipe right to edit
                    showEditTxDialog(position, tx);
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
                    icon = ContextCompat.getDrawable(self, R.drawable.ic_edit_black_24); // replace with your own drawable
                    background = new ColorDrawable(getColor(R.color.colorNeutral)); // replace with your own color

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
                    icon = ContextCompat.getDrawable(self, R.drawable.ic_delete_24); // replace with your own drawable
                    background = new ColorDrawable(getColor(R.color.colorNegative)); // replace with your own color

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

    protected void redirectAfterAccountDelete() {
        startActivity(MainActivity.class);
    }

    protected void setTxSum(float newValue) {
        String newString = Util.formatLargeFloatDisplay(newValue) + "x";
        indivValue.setText(newString.replace("x", getString(R.string.label_currency)));
    }

    protected void setTitle() {
        setCustomTitle();
        setCustomTitleDetails(mAccount.toString());
    }

    protected boolean hasEntries() {
        return mAccount.getTxList().size() > 0;
    }

    protected void populateUI() {
        if (!hasEntries()) {
            showToastLong(R.string.toast_error_no_entries);
        }
        TextView labelSigma = findViewById(R.id.container_tx_sum).findViewById(R.id.label_sigma);
        labelSigma.setText(R.string.label_sum_tx);

        filterEntries(null);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextChange(String newText) {
                // filter entries
                filterEntries(newText);
                return true;
            }

            @Override
            public boolean onQueryTextSubmit(String query) {
                // you can leave this empty if you don't need to do anything when the user submits the search
                return false;
            }
        });

    }

    protected void filterEntries(CharSequence filter) {
        float sum = 0.0f;
        List<TxBE> entries;

        List<TxBE> rawEntries = getEntries();
        // Filter the entries based on the filter string
        if (filter == null) {
            entries = rawEntries;
        } else {
            entries = new ArrayList<>();
            for (TxBE entr : rawEntries) {
                if (entr.getDescription().toUpperCase().contains(filter.toString().toUpperCase())) {
                    entries.add(entr);
                }
            }
        }

        // Calculate the sum of the amounts
        for (TxBE entr : entries) {
            sum += entr.getAmount();
        }

        // Update the sum display
        setTxSum(sum);

        // Update the RecyclerView adapter's data set and refresh the display
        listAdapter.setEntries(entries);
    }

    @Override
    public void onRefresh() {
        setTxSum(getReference().getSum());
    }

    protected List<TxBE> getEntries() {
        return mAccount.getTxList();
    }

    protected AccountBE getReference() {
        return mAccount;
    }
    // endregion

    protected void showEditTxDialog(int position, TxBE tx) {
        EditTxDialog dialog = new EditTxDialog(this, tx) {
            @Override
            public void onConfirm(TxBE tx) {
                try {
                    controller.saveAccountsToInternal();
                    onRefresh();
                } catch (JSONException | IOException e) {
                    if (e instanceof JSONException)
                        Log.println(Log.ERROR, "edit_tx",
                                String.format("Error serializing safe file after editing a transaction (%s): %s", tx, e));
                    else
                        Log.println(Log.ERROR, "edit_tx",
                                String.format("Error writing safe file after editing a transaction (%s): %s", tx, e));
                }
                // Update the list
                listAdapter.notifyItemChanged(position);
            }
        };

        // Show the dialog
        dialog.show();
    }

    protected void deleteTx(int position, TxBE tx) {
        // Temporarily remove the transaction from the list
        listAdapter.removeEntry(tx);

        // Show a Snackbar with an "Undo" action
        Snackbar snackbar = Snackbar.make(recyclerView, R.string.snackbar_tx_deleted, Snackbar.LENGTH_LONG);
        snackbar.setAction("Undo", view -> {
            // User clicked the "Undo" action, so put the transaction back into the list
            listAdapter.addEntry(position, tx);
        });
        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar snackbar, int event) {
                if (event != Snackbar.Callback.DISMISS_EVENT_ACTION) {
                    try {
                        controller.deleteTx(getReference(), tx);
                        onRefresh();
                    } catch (JSONException | IOException e) {
                        showErrorToast(e);
                    }
                }
            }
        });
        snackbar.show();
    }

    protected void deleteAccount() {
        Dialog.OnClickListener listener = (dialogInterface, i) -> {
            try {
                boolean result = controller.deleteAccount(getReference());
                if (result) {
                    redirectAfterAccountDelete();
                    showToastLong(R.string.toast_success_delete_account);
                } else {
                    showToastLong(R.string.toast_error_account_not_found);
                }
            } catch (JSONException e) {
                showToastLong(R.string.toast_error_JSONError);
            } catch (IOException e) {
                showToastLong(R.string.toast_error_IOError);
            }
        };
        showConfirmDialog(R.string.question_delete_account, listener);
    }

    public boolean updateEntryDescription(TxBE reference, String newDescription) {
        Date referenceDate = reference.getDate();
        String referenceDescription = reference.getDescription();
        boolean result = false;
        try {
            result = controller.updateTx(referenceDate, referenceDescription, mAccount, newDescription);
            if (result)
                showToastLong(R.string.toast_success_update_entries);
            else
                showToastLong(R.string.toast_error_update_entries_no_partner);
        } catch (JSONException e) {
            showToastLong(R.string.toast_error_JSONError);
        } catch (IOException e) {
            showToastLong(R.string.toast_error_IOError);
        }
        return result;
    }

    public boolean updateEntryAmount(TxBE reference, String newAmount) {
        Date referenceDate = reference.getDate();
        String referenceDescription = reference.getDescription();
        boolean result = false;
        try {
            float newAmountFloat = Float.parseFloat(newAmount);
            result = controller.updateTx(referenceDate, referenceDescription, mAccount, newAmountFloat);
            if (result)
                showToastLong(R.string.toast_success_update_entries);
            else
                showToastLong(R.string.toast_error_update_entries_no_partner);
        } catch (JSONException e) {
            showToastLong(R.string.toast_error_JSONError);
        } catch (IOException e) {
            showToastLong(R.string.toast_error_IOError);
        }
        return result;
    }
}
