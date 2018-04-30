package com.privat.pitz.financehelper;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Bundle;
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
import android.widget.TextView;

import org.json.JSONException;

import View.AccountView;
import Logic.AccountBE;

public class MainActivity extends AbstractActivity {


    public EditText newAccountName;
    public EditText newDescription;
    public EditText newAmount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        onAppStartup();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onDestroy() {
        try {
            controller.saveAccountsToInternal();
        } catch (JSONException jsone) {
            jsone.printStackTrace();
        }
        super.onDestroy();
    }

    private void populateUI() {
        Button pay = findViewById(R.id.button_pay);
        Button invest = findViewById(R.id.button_invest);
        Button addEntry = findViewById(R.id.button_add_entry);
        LinearLayout payAccounts = findViewById(R.id.linLayPayAccounts);
        LinearLayout investAccounts = findViewById(R.id.linLayInvestAccounts);
        newDescription = findViewById(R.id.edit_new_description);
        newAmount = findViewById(R.id.edit_new_amount);

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

        addEntry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String des = newDescription.getText().toString();
                String am = newAmount.getText().toString();

                if (des.equals("")) {
                    showToastLong(R.string.toast_error_empty_description);
                    return;
                }
                if (am.equals("")) {
                    showToastLong(R.string.toast_error_empty_amount);
                    return;
                }
                controller.addEntry(des, Float.valueOf(am), model.currentPayAcc, model.currentInvestAcc);
                newDescription.setText("");
                newAmount.setText("");
                showToastLong(R.string.toast_success_new_entry);
            }
        });

        //region populate pay Account list
        for (AccountBE a : model.payAccounts) {
            TextView tv = (TextView) getLayoutInflater().inflate(R.layout.textview_accountselect, null, false);
            tv.setText(a.getName());
            tv.setTag(a.getName());
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    findViewById(R.id.root_layout).findViewWithTag(model.currentPayAcc.getName()).setBackgroundColor(getResources().getColor(R.color.colorPrimary));
                    model.currentPayAcc = controller.getPayAccountByName(v.getTag().toString());
                    findViewById(R.id.root_layout).findViewWithTag(model.currentPayAcc.getName()).setBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));
                }
            });
            payAccounts.addView(tv);
        }
        if (model.currentPayAcc == null)
            model.currentPayAcc = model.payAccounts.get(0);
        findViewById(R.id.root_layout).findViewWithTag(model.currentPayAcc.getName()).setBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));
        //endregion

        //region populate invest Account list
        for (AccountBE a : model.investAccounts) {
            TextView tv = (TextView) getLayoutInflater().inflate(R.layout.textview_accountselect, null, false);
            tv.setText(a.getName());
            tv.setTag(a.getName());
            tv.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    findViewById(R.id.root_layout).findViewWithTag(model.currentInvestAcc.getName()).setBackgroundColor(getResources().getColor(R.color.colorPrimary));
                    model.currentInvestAcc = controller.getInvestAccountByName(v.getTag().toString());
                    findViewById(R.id.root_layout).findViewWithTag(model.currentInvestAcc.getName()).setBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));
                }
            });
            investAccounts.addView(tv);
        }
        if (model.currentInvestAcc == null)
            model.currentInvestAcc = model.investAccounts.get(0);
        findViewById(R.id.root_layout).findViewWithTag(model.currentInvestAcc.getName()).setBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));
        //endregion
    }

    @Override
    protected void workingThread() {
        try {
            controller.readAccountsFromInternal();
        } catch (JSONException jsone) {
            jsone.printStackTrace();
        }

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
    }

    @Override
    protected void endWorkingThread() {
        populateUI();
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
            showToast(R.string.toast_error_unknown);
        }
    }
}
