package com.privat.pitz.financehelper.ui;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;

import java.io.IOException;

import com.privat.pitz.financehelper.R;
import com.privat.pitz.financehelper.core.Controller;
import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.TxBE;
import com.privat.pitz.financehelper.ui.adapter.TxListAdapter;
import com.privat.pitz.financehelper.ui.dialog.EditTxDialog;

/**
 * Shared implementation of {@link TxListSection.TxActions}: swipe RIGHT opens {@link EditTxDialog}
 * (on confirm: sort the account's tx list by date, persist, refresh), swipe LEFT removes the row
 * from the adapter immediately and shows an Undo {@link Snackbar} - the tx is only actually
 * deleted via {@link Controller#deleteTx} if the Snackbar is dismissed without the Undo action
 * being pressed.
 *
 * Extracted from what used to be near-identical copies of these two methods in
 * {@code AssetAccountDetailsActivity} and {@code BudgetAccountDetailsActivity}.
 */
public class TxSwipeActions implements TxListSection.TxActions {
    private final Context context;
    private final Controller controller;
    private final AccountBE account;
    private final TxListAdapter adapter;
    private final View snackbarAnchor;
    private final RefreshListener onChanged;

    /**
     * @param snackbarAnchor the View the Undo Snackbar is anchored to - pass
     *                       {@link TxListSection#getRecyclerView()}. Taken as a View rather than
     *                       the TxListSection itself because TxSwipeActions must be constructed
     *                       (to be handed to the TxListSection constructor as its swipeActions
     *                       argument) before the TxListSection instance exists.
     */
    public TxSwipeActions(Context context, Controller controller, AccountBE account,
                           TxListAdapter adapter, View snackbarAnchor, RefreshListener onChanged) {
        this.context = context;
        this.controller = controller;
        this.account = account;
        this.adapter = adapter;
        this.snackbarAnchor = snackbarAnchor;
        this.onChanged = onChanged;
    }

    @Override
    public void onEditRequested(int position, TxBE tx) {
        EditTxDialog dialog = new EditTxDialog(context, tx) {
            @Override
            public void onConfirm(TxBE tx) {
                try {
                    account.sortTxByDate();
                    controller.saveAccountsToInternal();
                    onChanged.onRefresh();
                } catch (JSONException | IOException e) {
                    if (e instanceof JSONException)
                        Log.println(Log.ERROR, "edit_tx",
                                String.format("Error serializing safe file after editing a transaction (%s): %s", tx, e));
                    else
                        Log.println(Log.ERROR, "edit_tx",
                                String.format("Error writing safe file after editing a transaction (%s): %s", tx, e));
                }
                // Update the list
                adapter.notifyItemChanged(position);
            }
        };

        // Show the dialog
        dialog.show();
    }

    @Override
    public void onDeleteRequested(int position, TxBE tx) {
        // Temporarily remove the transaction from the list
        adapter.removeEntry(tx);

        // Show a Snackbar with an "Undo" action
        Snackbar snackbar = Snackbar.make(snackbarAnchor, R.string.snackbar_tx_deleted, Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.label_undo, view -> {
            // User clicked the "Undo" action, so put the transaction back into the list
            adapter.addEntry(position, tx);
        });
        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar snackbar, int event) {
                if (event != Snackbar.Callback.DISMISS_EVENT_ACTION) {
                    try {
                        controller.deleteTx(account, tx);
                        onChanged.onRefresh();
                    } catch (JSONException | IOException e) {
                        showErrorToast(e);
                    }
                }
            }
        });
        snackbar.show();
    }

    // Mirrors AbstractActivity.showErrorToast(Exception) for the two exception types this class's
    // callers can actually throw (JSONException/IOException from Controller), since TxSwipeActions
    // is not itself an AbstractActivity and has only a Context to work with.
    private void showErrorToast(Exception e) {
        if (e instanceof JSONException)
            Toast.makeText(context, R.string.toast_error_JSONError, Toast.LENGTH_LONG).show();
        if (e instanceof IOException)
            Toast.makeText(context, R.string.toast_error_IOError, Toast.LENGTH_LONG).show();
    }
}
