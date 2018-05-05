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

import java.io.IOException;
import java.util.List;

import Backend.Util;
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
    protected void workingThread() {
        try {
            controller.readAccountsFromInternal();
        } catch (JSONException jsone) {
            jsone.printStackTrace();
            getController().initAccountLists();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            getController().initAccountLists();
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
            case R.id.item_save_accounts:
                showSaveAccountsDialog();
                break;
            case R.id.item_load_accounts:
                showLoadAccountsDialog();
                break;
            case R.id.item_settings:
                startActivity(SettingsActivity.class);
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadAccountLists();
    }

    @Override
    protected void onStop() {
        try {
            controller.saveAccountsToInternal();
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onStop();
    }

    private void reloadAccountLists() {
        LinearLayout payAccounts = findViewById(R.id.linLayPayAccounts);
        LinearLayout investAccounts = findViewById(R.id.linLayInvestAccounts);

        Util.populatePayAccountsList(this, payAccounts);
        Util.populateInvestAccountsList(this, investAccounts);
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

        Util.populatePayAccountsList(this, payAccounts);
        Util.populateInvestAccountsList(this, investAccounts);
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
                createInvestAccount(newAccountName.getText().toString());
            }
        });
        builder.show();
        newAccountName.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(newAccountName, InputMethodManager.SHOW_IMPLICIT);
    }

    private void showSaveAccountsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.label_save_accounts);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_save_accounts, null);
        final EditText saveName = dialogView.findViewById(R.id.edit_save_name);
        final Button exportButton = dialogView.findViewById(R.id.button_export);
        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportAccounts();
            }
        });
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, getDoNothingClickListener());
        builder.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                saveAccountsByName(saveName.getText().toString());
            }
        });
        builder.show();
        saveName.requestFocus();
    }

    private void showLoadAccountsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.label_load_accounts);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_load_accounts, null);
        final LinearLayout availableFilesLayout = dialogView.findViewById(R.id.layout_available_files);
        final EditText filenameEdit = dialogView.findViewById(R.id.edit_load_name);
        List<String> availableFiles = getController().getAvailableSaveFiles();
        for (String s : availableFiles) {
            TextView tv = (TextView) getLayoutInflater().inflate(R.layout.textview_accountselect, availableFilesLayout, false);
            tv.setText(s);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    filenameEdit.setText(((TextView)v).getText());
                }
            });
            availableFilesLayout.addView(tv);
        }
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, getDoNothingClickListener());
        builder.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    getController().readAccountsFromInternal(filenameEdit.getText().toString());
                } catch (JSONException jsone) {
                    dialog.dismiss();
                    showToastLong(R.string.toast_error_unknown);
                } catch (IOException ioe) {
                    dialog.dismiss();
                    showToastLong(R.string.toast_error_invalid_filename);
                }
            }
        });
        builder.show();
    }

    private void exportAccounts() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.label_export);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_show_export, null);
        final EditText showExport = dialogView.findViewById(R.id.edit_show_export);
        try {
            showExport.setText(getController().exportAccounts());
        } catch (JSONException jsone) {
            showToast(R.string.toast_error_unknown);
            return;
        }
        builder.setView(dialogView);
        builder.setPositiveButton(R.string.accept, getDoNothingClickListener());
        builder.show();
    }

    private void saveAccountsByName(String name) {
        try {
            getController().saveAccountsToInternal(name);
        } catch (JSONException json) {
            showToastLong(R.string.toast_error_unknown);
            return;
        } catch (IOException ioe) {
            showToastLong(R.string.toast_error_unknown);
            return;
        }
        showToastLong(R.string.toast_success_accounts_saved);
    }

    private void createInvestAccount(String name) {
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
