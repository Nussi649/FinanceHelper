package com.privat.pitz.financehelper;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
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

    @Override
    protected void endWorkingThread() {
        populateUI();
    }

    abstract void populateUI();

    protected void addAccountToUI(AccountBE acc) {
        AccountView newAccountView = new AccountView(this, acc);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            TableLayout table = findViewById(R.id.accountContainer);
            newAccountView.setLayoutWidth(getWindowManager().getDefaultDisplay().getWidth()/2-60);
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
            showToast(R.string.toast_error_unknown);
        }
    }

    protected void reloadContent() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            TableLayout table = findViewById(R.id.accountContainer);
            table.removeAllViews();
        } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LinearLayout linLay = findViewById(R.id.accountContainer);
            linLay.removeAllViews();
        } else {
            showToast(R.string.toast_error_unknown);
        }
        for (AccountBE a : model.payAccounts) {
            addAccountToUI(a);
        }
    }

    protected void showNewAccountDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.label_new_account);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_new_account, null);
        final EditText newAccountName = dialogView.findViewById(R.id.edit_new_account_name);
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, getDoNothingClickListener());
        builder.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                createAccount(newAccountName.getText().toString());
            }
        });
        builder.show();
        newAccountName.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(newAccountName, InputMethodManager.SHOW_IMPLICIT);
    }

    abstract void createAccount(String name);
}
