package com.privat.pitz.financehelper;

import android.view.Menu;
import android.view.MenuItem;

import androidx.recyclerview.widget.LinearLayoutManager;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import Backend.RecurringTxAdapter;
import Logic.RecurringTxBE;

public class RecurringTxActivity extends AssetAccountDetailsActivity {
    RecurringTxAdapter listAdapter;

    // region AbstractActivity & Activity Overrides
    @Override
    protected void workingThread() {

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) { return true; }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) { return true; }
    // endregion

    // region AssetAccountDetailsActivity Overrides
    @Override
    protected void filterEntries(CharSequence filter) {
        float sum = 0.0f;
        List<RecurringTxBE> entries;

        // Filter the entries based on the filter string
        if (filter == null) {
            entries = getModel().recurringTx;
        } else {
            entries = new ArrayList<>();
            for (RecurringTxBE entr : getModel().recurringTx) {
                if (entr.getDescription().contains(filter) ||
                        entr.getSenderStr().contains(filter) ||
                        entr.getReceiverStr().contains(filter)) {
                    entries.add(entr);
                }
            }
        }

        // Calculate the sum of the amounts
        for (RecurringTxBE entr : entries) {
            sum += entr.getAmount();
        }

        // Update the sum display
        setTxSum(sum);

        // Update the RecyclerView adapter's data set and refresh the display
        listAdapter.setEntries(entries);
    }

    @Override
    protected void setContentLayout() {
        setContentView(R.layout.activity_asset_account_details);
    }

    @Override
    protected void initListAdapter() {
        listAdapter = new RecurringTxAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(listAdapter);
    }

    @Override
    protected boolean hasEntries() {
        return model.currentIncome.size() > 0;
    }
    // endregion

    public void deleteOrder(RecurringTxBE recurringTx) {
        try {
            boolean result = controller.deleteRecurringTx(recurringTx);
            if (result) {
                showToastLong(R.string.toast_success_delete_recurring_tx);
                onRefresh();
            } else {
                showToastLong(R.string.toast_error_recurring_tx_not_found);
            }
        } catch (JSONException e) {
            showToastLong(R.string.toast_error_JSONError);
        } catch (IOException e) {
            showToastLong(R.string.toast_error_IOError);
        }
    }

    protected void setTitle() {
        setCustomTitle();
        setCustomTitleDetails(getString(R.string.label_recurring_orders));
    }

    @Override
    public void onRefresh() {
        filterEntries(null);
    }
}
