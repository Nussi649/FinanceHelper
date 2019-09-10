package com.privat.pitz.financehelper;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import Backend.Const;
import Backend.Util;
import Logic.AccountBE;

public class InvestActivity extends AccountListActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_list);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_invest, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.item_new_account:
                showNewAccountDialog();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void createAccount(String name) {
        AccountBE newAccount = new AccountBE(name);
        model.investAccounts.add(newAccount);
        addAccountToUI(newAccount);
    }

    @Override
    protected void populateUI() {
        String cut = Util.cutFileNameIfNecessary(getModel().currentFileName);
        String title = Const.getMonthNameById(Integer.valueOf(cut.substring(0, cut.length() - 1)) - 1) + " - " + getModel().sumAllExpenses() + "€";
        setTitle(title);
        for (AccountBE a : model.investAccounts) {
            if (a.getIsActive())
                addAccountToUI(a);
        }
    }
}
