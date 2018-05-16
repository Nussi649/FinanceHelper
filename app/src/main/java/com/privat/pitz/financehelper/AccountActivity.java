package com.privat.pitz.financehelper;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import Backend.Util;
import Logic.AccountBE;
import Logic.EntryBE;

public class AccountActivity extends AbstractActivity {
    AccountBE mAccount;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
    }

    @Override
    protected void workingThread() {
        mAccount = getModel().currentInspectedAccount;
    }

    @Override
    protected void endWorkingThread() {
        setTitle(mAccount.getName());
        populateUI();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_account, menu);
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

    private void populateUI() {
        if (mAccount.getEntries().size() == 0) {
            findViewById(R.id.root_layout).setVisibility(View.GONE);
            showToastLong(R.string.toast_error_no_entries);
            return;
        }
        TableLayout tabLay = findViewById(R.id.contentTable);
        for (EntryBE entr : mAccount.getEntries()) {
            TableRow row = (TableRow) getLayoutInflater().inflate(R.layout.table_row_account, tabLay, false);
            TextView date = row.findViewById(R.id.label_time);
            TextView description = row.findViewById(R.id.label_description);
            TextView amount = row.findViewById(R.id.label_amount);
            date.setText(Util.formatDate(entr.getDate()));
            description.setText(entr.getDescription());
            amount.setText(Util.formatFloat(entr.getAmount()));
            tabLay.addView(row);
        }
        TextView sum = findViewById(R.id.display_sum);
        sum.setText(Util.formatFloat(mAccount.getSum()));
    }

    private void deleteAccount() {
        // TODO: ask for confirmation
        if (getModel().payAccounts.contains(mAccount))
            getModel().payAccounts.remove(mAccount);
        if (getModel().investAccounts.contains(mAccount))
            getModel().investAccounts.remove(mAccount);

        startActivity(MainActivity.class);
    }
}
