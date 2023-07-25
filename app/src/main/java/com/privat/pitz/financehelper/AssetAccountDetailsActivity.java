package com.privat.pitz.financehelper;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import Backend.TxListAdapter;
import Backend.Const;
import Backend.Util;
import Logic.AccountBE;
import Logic.TxBE;

public class AssetAccountDetailsActivity extends AbstractActivity {
    AccountBE mAccount;
    TxListAdapter listAdapter;

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
        setContentView(R.layout.activity_asset_account_details);
        populateUI();
        // set Activity title
        try {
            Util.FileNameParts parts = Util.parseFileName(getModel().currentFileName);
            String monthName = Const.getMonthNameById(parts.month - 1);
            setTitle(String.format("%s (%s) - %s", parts.entityName, monthName, mAccount.toString()));
        } catch (IllegalArgumentException e) {
            Log.println(Log.ERROR, "parse_file_name", e.toString());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_account_details, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.item_delete_account:
                deleteAccount();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    protected boolean hasEntries() {
        return mAccount.getTxList().size() > 0;
    }

    protected List<TxBE> getEntries() {
        return mAccount.getTxList();
    }

    protected void populateUI() {
        if (!hasEntries()) {
            showToastLong(R.string.toast_error_no_entries);
        }
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        listAdapter = new TxListAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(listAdapter);

        filterEntries(null);
        SearchView searchView = findViewById(R.id.search_filter);
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
        TextView textSum = findViewById(R.id.display_sum);
        textSum.setText(Util.formatLargeFloatDisplay(sum));

        // Update the RecyclerView adapter's data set and refresh the display
        listAdapter.setEntries(entries);
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

    protected void deleteAccount() {
        Dialog.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                try {
                    boolean result = controller.deleteAccount(mAccount);
                    if (result) {
                        startActivity(MainActivity.class);
                        showToastLong(R.string.toast_success_delete_account);
                    } else {
                        showToastLong(R.string.toast_error_account_not_found);
                    }
                } catch (JSONException e) {
                    showToastLong(R.string.toast_error_JSONError);
                } catch (IOException e) {
                    showToastLong(R.string.toast_error_IOError);
                }
            }
        };
        showConfirmDialog(R.string.question_delete_account, listener);
    }

    @Override
    public void onRefresh() {

    }
}
