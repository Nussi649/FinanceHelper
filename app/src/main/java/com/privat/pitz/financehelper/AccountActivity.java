package com.privat.pitz.financehelper;

import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;

import View.AccountView;
import Logic.AccountBE;

public abstract class AccountActivity extends AbstractActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    protected void addAccountToUI(AccountBE acc) {
        AccountView newAccountView = new AccountView(this, acc);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            TableLayout table = findViewById(R.id.accountContainer);
            if (table.getChildCount() == 0) {
                TableRow row = new TableRow(this);
                row.addView(newAccountView);
                table.addView(row);
            } else {
                TableRow lastRow = (TableRow) table.getChildAt(table.getChildCount() - 1);
                if (lastRow.getChildCount() == 2) {
                    TableRow newRow = new TableRow(this);
                    newRow.addView(newAccountView);
                    table.addView(newRow);
                } else {
                    lastRow.addView(newAccountView);
                }
            }
        } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LinearLayout linLay = findViewById(R.id.accountContainer);
            linLay.addView(newAccountView);
        } else {
            showToast(R.string.toast_unknown_error);
        }
    }
}
