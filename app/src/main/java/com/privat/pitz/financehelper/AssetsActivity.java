package com.privat.pitz.financehelper;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import Backend.Const;
import Backend.Util;
import Logic.AccountBE;
import View.AccountWidget;

public class AssetsActivity extends AbstractActivity {
    List<AccountWidget> loadedWidgets = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void endWorkingThread() {
        setContentView(R.layout.activity_asset_overview);
        populateUI();
    }

    public void onStart() {
        super.onStart();
        // only act, if activity has already been visited before
        if (!passedOnCreate) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onRefresh();
                    }
                });
        }
    }

    protected void populateUI() {
        try {
            Util.FileNameParts parts = Util.parseFileName(getModel().currentFileName);
            String monthName = Const.getMonthNameById(parts.month - 1);
            setTitle(String.format("%s (%s) - %s", parts.entityName, monthName, getString(R.string.label_assets)));
        } catch (IllegalArgumentException e) {
            Log.println(Log.ERROR, "parse_file_name", e.toString());
        }
        for (AccountBE a : model.asset_accounts) {
            if (a.getIsActive())
                addAccountToUI(a);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_account_overview, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.item_new_account) {
            showNewAccountDialog();
        }
        return super.onOptionsItemSelected(item);
    }

    protected void addAccountToUI(final AccountBE acc) {
        AccountWidget newAccountWidget = new AccountWidget(this, acc);
        newAccountWidget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getModel().currentInspectedAccount = acc;
                startActivity(AssetAccountDetailsActivity.class);
            }
        });
        TableLayout table = findViewById(R.id.accountContainer);
        newAccountWidget.setLayoutWidth(getWindowManager().getDefaultDisplay().getWidth()/2-60);
        if (table.getChildCount() == 0) {
            TableRow row = new TableRow(this);
            row.addView(newAccountWidget);
            table.addView(row);
        } else {
            TableRow lastRow = (TableRow) table.getChildAt(table.getChildCount() - 1);
            if (lastRow.getChildCount() == 2) {
                TableRow newRow = new TableRow(this);
                newRow.addView(newAccountWidget);
                table.addView(newRow);
            } else {
                lastRow.addView(newAccountWidget);
            }
        }
        loadedWidgets.add(newAccountWidget);
    }

    // drops all views, loads them from scratch
    protected void reloadContent() {
        TableLayout table = findViewById(R.id.accountContainer);
        table.removeAllViews();
        loadedWidgets = new ArrayList<>();
        for (AccountBE a : model.asset_accounts) {
            addAccountToUI(a);
        }
    }

    // triggers a refresh in all widgets
    protected void refreshContent() {
        for (AccountWidget widget : loadedWidgets)
            widget.refreshTx();
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

    protected void createAccount(String name) {
        try {
            AccountBE newAccount = controller.createAssetAccount(name);
            if (newAccount == null) {
                showToastLong(R.string.toast_error_account_name_in_use);
            } else {
                showToastLong(R.string.toast_success_new_account);
                addAccountToUI(newAccount);
            }
        } catch (JSONException e) {
            showToastLong(R.string.toast_error_JSONError);
        } catch (IOException e) {
            showToastLong(R.string.toast_error_IOError);
        }
    }

    @Override
    public void onRefresh() {
        refreshContent();
    }
}
