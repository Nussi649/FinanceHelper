package com.privat.pitz.financehelper;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import Backend.Util;
import Logic.AccountBE;
import View.AccountWidget;

public class AssetsActivity extends AbstractActivity {
    List<AccountWidget> loadedWidgets = new ArrayList<>();
    TableLayout container;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void endWorkingThread() {
        setContentView(R.layout.activity_asset_overview);
        container = findViewById(R.id.accountContainer);
        populateUI();
    }

    public void onStart() {
        super.onStart();
        // only act, if activity has already been visited before
        if (!passedOnCreate) {
            // check if loaded widgets still equal model.asset_accounts
//            boolean canRefresh = checkLists();
//            runOnUiThread(canRefresh ? new Runnable() {
//                @Override
//                public void run() {
//                    onRefresh();
//                }
//            } : new Runnable() {
//                @Override
//                public void run() {
//                    reloadContent();
//                }
//            });
            // for now no easy way identified to check, if reload is necessary or refresh suffices
            // reload could be required not only if not all accounts are loaded but also if transactions within account differ
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    reloadContent();
                }
            });
        }
    }

    protected void populateUI() {
        for (AccountBE a : model.asset_accounts) {
            if (a.getIsActive())
                addAccountToUI(a);
        }
        setCustomTitle();
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
        // create new account widget with AccountBE object and initiate it
        AccountWidget newAccountWidget = AccountWidget.getInstance(this);
        newAccountWidget.init(acc);
        newAccountWidget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getModel().currentInspectedAccount = acc;
                startActivity(AssetAccountDetailsActivity.class);
            }
        });

        // Set the layout parameters for the AccountWidget, with margins
        TableRow.LayoutParams params = new TableRow.LayoutParams(
                0,
                TableRow.LayoutParams.WRAP_CONTENT,
                1f // This is the weight
        );
        params.setMargins(30, 30, 30, 30);
        newAccountWidget.setLayoutParams(params);

        if (container.getChildCount() == 0) {
            TableRow row = new TableRow(this);
            row.addView(newAccountWidget);
            // Add an empty View as well
            View emptyView = new View(this);
            emptyView.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(emptyView);
            container.addView(row);
        } else {
            TableRow lastRow = (TableRow) container.getChildAt(container.getChildCount() - 1);
            if (lastRow.getChildCount() == 2 && lastRow.getChildAt(1) instanceof AccountWidget) {
                TableRow newRow = new TableRow(this);
                newRow.addView(newAccountWidget);
                // Add an empty View as well
                View emptyView = new View(this);
                emptyView.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f));
                newRow.addView(emptyView);
                container.addView(newRow);
            } else {
                // Replace the empty View with the new AccountWidget
                lastRow.removeViewAt(1);
                lastRow.addView(newAccountWidget);
            }
        }
        loadedWidgets.add(newAccountWidget);
        newAccountWidget.refreshUI();

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
            widget.refreshUI();
    }

    protected void showNewAccountDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.label_account_new);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_new_account, null);
        final EditText newAccountName = dialogView.findViewById(R.id.edit_new_account_name);
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, getDoNothingClickListener());
        builder.setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
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
                showToastLong(R.string.toast_error_account_name_taken);
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
    protected void setCustomTitle() {
        super.setCustomTitle();
        String titleDetails = getString(R.string.label_assets) + String.format("  %sx",
                Util.formatLargeFloatShort(model.sumAllAssets())).replace("x", getString(R.string.label_currency));
        setCustomTitleDetails(titleDetails);
    }

    @Override
    public void onRefresh() {
        refreshContent();
        setCustomTitle();
    }

    // region util
    private boolean checkLists() {
        if (loadedWidgets.size() != model.asset_accounts.size()) {
            return false;
        }
        for (int i = 0; i < loadedWidgets.size(); i++) {
            if (!loadedWidgets.get(i).getAccount().equals(model.asset_accounts.get(i))) {
                return false;
            }
        }
        return true;
    }
    // endregion
}
