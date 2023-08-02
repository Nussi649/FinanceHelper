package com.privat.pitz.financehelper;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Looper;
import android.app.AlertDialog;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import Backend.Const;
import Backend.Controller;
import Backend.RbAccountManager;
import Backend.Util;
import View.Dialogs.AddIncomeDialog;
import View.Dialogs.EditSourceCodeDialog;
import View.Dialogs.CurrentIncomeDialog;
import View.Dialogs.LoadFileDialog;
import View.Dialogs.SaveFileDialog;
import View.Dialogs.TransactionRedirectionDialog;

public class MainActivity extends AbstractActivity {


    public EditText newDescription;
    public EditText newAmount;
    RbAccountManager rbSender;
    RbAccountManager rbReceiver;

    //region overridden activity methods
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        onAppStartup();
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void workingThread() {
        // Call Looper.prepare() to be able to send Toasts
        Looper.prepare();
        initiateAccounts();
    }

    @SuppressLint("DefaultLocale")
    @Override
    protected void endWorkingThread() {
        setContentView(R.layout.activity_main);
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
        int itemId = item.getItemId();
        if (itemId == R.id.item_show_current_income) {
            showIncomeListDialog();
        } else if (itemId == R.id.item_add_funds) {
            showAddIncomeDialog();
        } else if (itemId == R.id.item_save_accounts) {
            showSaveFileDialog();
        } else if (itemId == R.id.item_load_accounts) {
            showLoadFileDialog();
        } else if (itemId == R.id.item_edit) {
            showEditSavefileDialog();
        } else if (itemId == R.id.item_settings) {
            startActivity(SettingsActivity.class);
        } else if (itemId == R.id.item_display_recurring_orders) {
            startActivity(RecurringTxActivity.class);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // only act, if activity has already been visited before
        if (!passedOnCreate) {
            if (getModel().currentFileName != null)
                runOnUiThread(this::onRefresh);
        }
    }

    @Override
    protected void onStop() {
        try {
            controller.saveAccountsToInternal();
            controller.saveAppSettings();
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onStop();
    }
    //endregion

    @SuppressLint("DefaultLocale")
    private void reloadAccountLists() {
        LinearLayout container_assets = findViewById(R.id.overview_asset_accounts);
        LinearLayout container_budgets = findViewById(R.id.overview_budget_accounts);

        Util.populateAssetAccountsPreview(model.asset_accounts,
                this,
                container_assets,
                rbReceiver,
                rbSender);
        Util.populateBudgetAccountsPreview(model.budget_accounts,
                this,
                container_budgets,
                rbReceiver);
        RadioButton currentSender = rbSender.getRadioButtonForAccount(model.currentSender);
        if (currentSender != null)
            currentSender.setChecked(true);
        RadioButton currentReceiver = rbReceiver.getRadioButtonForAccount(model.currentReceiver);
        if (currentReceiver != null)
            currentReceiver.setChecked(true);
    }

    private void populateUI() {
        TextView open_assets_detailed = findViewById(R.id.button_assets_overview);
        TextView open_budgets_detailed = findViewById(R.id.button_budgets_overview);
        Button addTx = findViewById(R.id.button_add_tx);
        Button addRecurringOrder = findViewById(R.id.button_add_recurring_order);
        newDescription = findViewById(R.id.edit_new_description);
        newAmount = findViewById(R.id.edit_new_amount);
        newAmount.setFilters(new InputFilter[] {
                (source, start, end, dest, dstart, dend) -> {
                    for (int i = start; i < end; i++) {
                        if (!Character.isDigit(source.charAt(i)) && source.charAt(i) != ',' && source.charAt(i) != '.') {
                            return "";
                        }
                    }
                    return null;
                }
        });

        open_assets_detailed.setOnClickListener(view -> startActivity(AssetsActivity.class));

        open_budgets_detailed.setOnClickListener(view -> startActivity(BudgetsActivity.class));

        MainActivity parent = this;
        addTx.setOnClickListener(v -> {
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
            float amount = 0.0f;
            try {
                amount = Float.parseFloat(am.replace(",", "."));
            } catch (NumberFormatException e) {
                showToastLong(R.string.toast_error_invalid_amount);
                return;
            }
            // try creating a transaction and wait for result
            boolean result = false;
            try {
                result = controller.createTx(parent, des, amount);
            } catch (JSONException | IOException e) {
                showErrorToast(e);
            }
            // if transaction was created successfully, clear input fields and show toast
            if (result) {
                newDescription.setText("");
                newAmount.setText("");
                onRefresh();
                showToastLong(R.string.toast_success_new_entry);
            }
        });

        addRecurringOrder.setOnClickListener(view -> {
            String amountString = newAmount.getText().toString().trim();
            String description = newDescription.getText().toString().trim();
            if (amountString.isEmpty()) {
                showToastLong(R.string.toast_error_empty_amount);
                return;
            }
            if (description.isEmpty()) {
                showToastLong(R.string.toast_error_empty_description);
                return;
            }

            try {
                float amount = Float.parseFloat(amountString.replace(",", "."));
                boolean result = controller.addRecurringTx(parent, description, amount);
                if (result) {
                    showToast(R.string.toast_success_new_recurring_tx);
                    newDescription.setText("");
                    newAmount.setText("");
                } else {
                    showToastLong(R.string.toast_error_unknown);
                }
            } catch (JSONException | IOException | NumberFormatException e) {
                showErrorToast(e);
            }
        });

        onRefresh();
    }

    //region show Dialog
    private void showAddIncomeDialog() {
        AddIncomeDialog dialog = new AddIncomeDialog(this) {
            @Override
            public void onConfirm(float amount, String description) {
                boolean result = false;
                try {
                    result = controller.addFunds(amount, description);
                } catch (JSONException | IOException e) {
                    showErrorToast(e);
                }
                if (result) {
                    showToast(R.string.toast_success_new_income);
                    onRefresh();
                } else {
                    showToastLong(R.string.toast_error_no_receiver_selected);
                }
            }
        };
        dialog.show();
    }

    public void getTransactionRedirectionInput(String targetFileName, JSONObject fileContent, String desc, float amount, JSONArray allAccounts) {
        // calculate list of account names as strings
        List<String> accountNames = new ArrayList<>();
        for (int i = 0; i < allAccounts.length(); i++) {
            try {
                JSONObject account = allAccounts.getJSONObject(i);
                if (account.getBoolean(Const.JSON_TAG_ISACTIVE))
                    accountNames.add(account.getString(Const.JSON_TAG_NAME));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        TransactionRedirectionDialog dialog = new TransactionRedirectionDialog(this, desc, amount, accountNames) {
            @Override
            public void onConfirm(String selectedAccountName) {
                boolean success = false;
                try {
                    success = controller.completeTxRedirection(targetFileName, model.currentFileAttributes.entityName, desc, amount, selectedAccountName, fileContent);
                } catch (JSONException | IOException e) {
                    showErrorToast(e);
                }
                if (success) {
                    showToastLong(R.string.toast_success_transaction_redirection);
                } else {
                    showToastLong(R.string.toast_error_transaction_redirection);
                }
            }
        };
        dialog.show();
    }

    private void showIncomeListDialog() {
        CurrentIncomeDialog dialog = new CurrentIncomeDialog(this, model.currentIncome);
        dialog.show();
    }

    private void showSaveFileDialog() {
        SaveFileDialog dialog = new SaveFileDialog(this, model.currentFileName) {
            @Override
            public void onConfirm(String saveName) {
                try {
                    getController().saveAccountsToInternal(saveName + Const.ACCOUNTS_FILE_TYPE);
                    showToastLong(R.string.toast_success_write_save_file);
                } catch (JSONException | IOException e) {
                    showErrorToast(e);
                }
            }

            @Override
            public void onExport() {
                showExportDialog();
            }
        };
        dialog.show();
    }

    private void showLoadFileDialog() {
        List<File> availableFiles = Util.getValidFiles(getFilesDir());
        LoadFileDialog dialog = new LoadFileDialog(this, availableFiles) {
            @Override
            public void onConfirm(String filename) {
                try {
                    controller.saveAppSettings();
                    controller.readAccountsFromInternal(filename + Const.ACCOUNTS_FILE_TYPE);
                    onRefresh();
                } catch (JSONException | IOException e) {
                    showErrorToast(e);
                }
            }

            @Override
            public void onImport() {
                showImportDialog();
            }

            @Override
            public void onDelete(String filename) {
                AlertDialog.OnClickListener listener = (dialog1, which) -> {
                    if (getController().deleteSavefile(filename)) {
                        showToastLong(R.string.toast_success_file_deleted);
                    } else {
                        showToastLong(R.string.toast_error_unknown);
                    }
                };
                showConfirmDialog(R.string.question_delete_savefile, listener);
            }
        };
        dialog.show();
    }

    private void showExportDialog() {
        AlertDialog dialog = getBasicEditDialog();
        dialog.setTitle(R.string.label_export_code);
        dialog.show();
        EditText showExport = dialog.findViewById(R.id.edit_text);
        try {
            showExport.setText(getController().exportAccounts());
        } catch (JSONException e) {
            showErrorToast(e);
            dialog.dismiss();
            return;
        }
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.confirm), (dialog1, which) -> dialog1.dismiss());
    }

    private void showImportDialog() {
        AlertDialog dialog = getBasicEditDialog();
        dialog.setTitle(R.string.label_import_code);
        dialog.show();
        EditText showImport = dialog.findViewById(R.id.edit_text);
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.label_import_code), (dialogInterface, i) -> {
            try {
                getController().importAccounts(showImport.getText().toString());
                showToast(R.string.toast_success_accounts_imported);
            } catch (JSONException e) {
                showErrorToast(e);
            }
            dialog.dismiss();
        });
    }

    private void showEditSavefileDialog() {
        EditSourceCodeDialog dialog = new EditSourceCodeDialog(this, getBasicEditDialog()) {
            @Override
            public void onConfirm(String newContent) {
                try {
                    getController().importAccounts(newContent);
                    showToast(R.string.toast_success_accounts_imported);
                } catch (JSONException e) {
                    showErrorToast(e);
                }
            }
        };

        try {
            dialog.show(getController().exportAccounts());
        } catch (JSONException e) {
            showErrorToast(e);
        }
    }
    //endregion

    public void initiateAccounts() {
        int response = getController().setupAccounts(false);
        if (response == Controller.LOADED_NEW_MONTH) {
            showToastLong("New Sheet for Month " + Const.getDisplayableCurrentMonthName() + " created.");
        }
        if (response == Controller.CREATED_BLANK)
            showToastLong(getString(R.string.toast_info_blank_accounts));
        rbSender = new RbAccountManager(Const.GROUP_SENDER, controller);
        rbReceiver = new RbAccountManager(Const.GROUP_RECEIVER, controller);
    }

    @Override
    public void onRefresh() {
        reloadAccountLists();
        setupActionBar();
        setCustomTitle();
        float delta = model.sumAllIncome() - model.sumAllExpenses();
        String firstOrder = String.format("%sx",
                Util.formatLargeFloatShort(delta >= 0 ? delta : -delta)).replace("x", getString(R.string.label_currency));
        String titleDetails = getString(R.string.label_delta) + String.format(delta >= 0 ? " %s" : " (%s)", firstOrder);
        setCustomTitleDetails(titleDetails);
    }
}
