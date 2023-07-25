package com.privat.pitz.financehelper;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import Backend.RecurringTxAdapter;
import Backend.TxListAdapter;
import Backend.Util;
import Logic.RecurringTxBE;

public class RecurringTxActivity extends AssetAccountDetailsActivity {
    RecurringTxAdapter listAdapter;

    @Override
    protected void workingThread() {

    }

    @Override
    protected void endWorkingThread() {
        setContentView(R.layout.activity_asset_account_details);
        setTitle(getString(R.string.label_recurring_orders));
        populateUI();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) { return true; }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) { return true; }

    @Override
    protected boolean hasEntries() {
        return model.currentIncome.size() > 0;
    }

    @Override
    protected void populateUI() {
        if (!hasEntries()) {
            showToastLong(R.string.toast_error_no_entries);
        }
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        listAdapter = new RecurringTxAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(listAdapter);

        filterEntries(null);
        androidx.appcompat.widget.SearchView searchView = findViewById(R.id.search_filter);
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
        TextView textSum = findViewById(R.id.display_sum);
        textSum.setText(Util.formatFloatSave(sum));

        // Update the RecyclerView adapter's data set and refresh the display
        listAdapter.setEntries(entries);
    }

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
}
