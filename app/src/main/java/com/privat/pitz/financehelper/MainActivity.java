package com.privat.pitz.financehelper;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;

import java.util.ArrayList;

import Backend.AccountView;
import Backend.Controller;
import Logic.AccountBE;

public class MainActivity extends AbstractActivity {


    public EditText newAccountName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        onAppStartup();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        populateUI();
    }

    private void populateUI() {
        Button pay = findViewById(R.id.button_pay);
        Button invest = findViewById(R.id.button_invest);

        pay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(PayActivity.class);
            }
        });

        invest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(InvestActivity.class);
            }
        });
    }

    @Override
    protected void workingThread() {
        if (model.payAccounts.size() == 0) {
            model.payAccounts.add(new AccountBE(getString(R.string.account_bargeld)));
            model.payAccounts.add(new AccountBE(getString(R.string.account_bank)));
            model.payAccounts.add(new AccountBE(getString(R.string.account_credit_card)));
            model.payAccounts.add(new AccountBE(getString(R.string.account_savings)));
        }
        if (model.investAccounts.size() == 0) {
            model.investAccounts.add(new AccountBE(getString(R.string.account_investments)));
            model.investAccounts.add(new AccountBE(getString(R.string.account_groceries)));
            model.investAccounts.add(new AccountBE(getString(R.string.account_cosmetics)));
            model.investAccounts.add(new AccountBE(getString(R.string.account_go_out)));
            model.investAccounts.add(new AccountBE(getString(R.string.account_drugs)));
            model.investAccounts.add(new AccountBE(getString(R.string.account_necessary)));
        }

//        controller.readAccountsFromInternal();
    }

    @Override
    protected void endWorkingThread() {

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.item_new_account:
                showNewAccountDialog();
                break;
            case R.id.item_save_accounts:
                controller.saveAccountsToInternal();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showNewAccountDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.text_new_account);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_new_account, null);
        newAccountName = dialogView.findViewById(R.id.edit_new_account_name);
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, getDoNothingClickListener());
        builder.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                // TODO: check for correct input
                createAccount(newAccountName.getText().toString());
            }
        });
        builder.show();
        newAccountName.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(newAccountName, InputMethodManager.SHOW_IMPLICIT);
    }

    private void createAccount(String name) {
        AccountBE newAccount = new AccountBE(name);
        model.investAccounts.add(newAccount);
        addAccountToUI(newAccount);
    }

    private void addAccountToUI(AccountBE acc) {
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
        if (newAccountView != null) {
            newAccountView.adjustWidth();
        }
    }
}
