package com.privat.pitz.financehelper;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.app.AlertDialog;
import android.text.InputFilter;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.privat.pitz.financehelper.core.Const;
import com.privat.pitz.financehelper.core.Controller;
import com.privat.pitz.financehelper.core.IntegrityChecker;
import com.privat.pitz.financehelper.core.RedirectionPrompt;
import com.privat.pitz.financehelper.ui.AccountPreviewList;
import com.privat.pitz.financehelper.ui.RbAccountManager;
import com.privat.pitz.financehelper.core.Util;
import com.privat.pitz.financehelper.ui.dialog.AddIncomeDialog;
import com.privat.pitz.financehelper.ui.dialog.EditSourceCodeDialog;
import com.privat.pitz.financehelper.ui.dialog.CurrentIncomeDialog;
import com.privat.pitz.financehelper.ui.dialog.LoadFileDialog;
import com.privat.pitz.financehelper.ui.dialog.SaveFileDialog;
import com.privat.pitz.financehelper.ui.dialog.TransactionRedirectionDialog;

public class MainActivity extends AbstractActivity implements RedirectionPrompt {


    public EditText newDescription;
    public EditText newAmount;
    RbAccountManager rbSender;
    RbAccountManager rbReceiver;

    private final ActivityResultLauncher<String> createBackupLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"), this::onBackupTargetSelected);
    private final ActivityResultLauncher<String[]> openRestoreLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onRestoreSourceSelected);
    private final ActivityResultLauncher<Uri> pickSyncFolderLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), this::onSyncFolderPicked);
    private enum PendingSyncAction { NONE, PUSH, PULL }
    // set right before launching pickSyncFolderLauncher, so its callback knows which action (if any) to run afterwards
    private PendingSyncAction pendingSyncAction = PendingSyncAction.NONE;

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
        } else if (itemId == R.id.item_backup_all) {
            startBackupAll();
        } else if (itemId == R.id.item_restore_all) {
            startRestoreAll();
        } else if (itemId == R.id.item_sync_push) {
            triggerSync(PendingSyncAction.PUSH);
        } else if (itemId == R.id.item_sync_pull) {
            triggerSync(PendingSyncAction.PULL);
        } else if (itemId == R.id.item_sync_change_folder) {
            pendingSyncAction = PendingSyncAction.NONE;
            pickSyncFolderLauncher.launch(null);
        } else if (itemId == R.id.item_integrity_check) {
            showIntegrityCheckDialog();
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

        AccountPreviewList.populateAssetAccountsPreview(model.asset_accounts,
                this,
                container_assets,
                rbReceiver,
                rbSender);
        AccountPreviewList.populateBudgetAccountsPreview(model.budget_accounts,
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

        addTx.setOnClickListener(v -> {
            String des = newDescription.getText().toString();
            String am = newAmount.getText().toString();

            if (des.isEmpty()) {
                showToastLong(R.string.toast_error_empty_description);
                return;
            }
            if (am.isEmpty()) {
                showToastLong(R.string.toast_error_empty_amount);
                return;
            }
            float amount;
            try {
                amount = Util.parseAmount(am);
            } catch (NumberFormatException e) {
                showToastLong(R.string.toast_error_invalid_amount);
                return;
            }
            // try creating a transaction and wait for result
            boolean result = false;
            try {
                result = controller.createTx(des, amount, this);
            } catch (JSONException | IOException e) {
                showErrorToast(e);
            }
            // if transaction was created successfully, clear input fields and show toast
            if (result) {
                newDescription.setText("");
                newAmount.setText("");
                onRefresh();
                showToastLong(R.string.toast_success_new_entry);
            } else if (getModel().currentSender == null || getModel().currentReceiver == null) {
                showToastLong(R.string.toast_error_no_sender_or_receiver);
            } else {
                // createTx returned false for some other reason. Silence here would leave the
                // input fields populated and the user unsure whether anything happened.
                showToastLong(R.string.toast_error_tx_not_created);
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
                float amount = Util.parseAmount(amountString);
                boolean result = controller.addRecurringTx(description, amount);
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

    @Override
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
        LoadFileDialog dialog = new LoadFileDialog(this, controller.getValidSavefileNames()) {
            @Override
            public void onConfirm(String filename) {
                try {
                    controller.saveAppSettings();
                    controller.readAccountsFromInternal(filename + Const.ACCOUNTS_FILE_TYPE);
                    reportLoadProblems();
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
                    getController().sortAllTransactions();
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

    private void startBackupAll() {
        try {
            controller.saveAccountsToInternal();
        } catch (JSONException | IOException e) {
            showErrorToast(e);
            return;
        }
        String defaultName = "FinanceHelper_Backup_" +
                new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(new Date()) + ".zip";
        createBackupLauncher.launch(defaultName);
    }

    private void onBackupTargetSelected(Uri targetUri) {
        // user cancelled the file picker
        if (targetUri == null)
            return;
        try {
            int count = getController().exportAllSavefilesToUri(targetUri);
            showToastLong(getString(R.string.toast_success_backup_written, count));
        } catch (IOException e) {
            showToastLong(R.string.toast_error_backup_failed);
        }
    }

    private void startRestoreAll() {
        openRestoreLauncher.launch(new String[] {"application/zip", "application/x-zip-compressed", "*/*"});
    }

    private void onRestoreSourceSelected(Uri sourceUri) {
        // user cancelled the file picker
        if (sourceUri == null)
            return;
        int count;
        try {
            count = getController().importSavefilesFromZipUri(sourceUri);
        } catch (IOException e) {
            showToastLong(R.string.toast_error_restore_failed);
            return;
        }
        if (count == 0) {
            showToastLong(R.string.toast_error_restore_empty);
            return;
        }
        showToastLong(getString(R.string.toast_success_backup_restored, count));
        // reload settings and available entities, then restart the activity to pick up the restored state
        controller.loadAppSettings();
        List<String> availableEntities = controller.getAllAvailableEntities();
        if (!availableEntities.isEmpty())
            model.availableEntities = availableEntities;
        recreate();
    }

    private void triggerSync(PendingSyncAction action) {
        String stored = model.settings.syncFolderUri;
        if (stored == null || stored.isEmpty()) {
            pendingSyncAction = action;
            pickSyncFolderLauncher.launch(null);
        } else {
            performSync(Uri.parse(stored), action);
        }
    }

    private void onSyncFolderPicked(Uri treeUri) {
        // user cancelled the folder picker
        if (treeUri == null)
            return;
        // persist access to this folder across app/device restarts
        getContentResolver().takePersistableUriPermission(treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        model.settings.syncFolderUri = treeUri.toString();
        try {
            controller.saveAppSettings();
        } catch (JSONException | IOException e) {
            showErrorToast(e);
        }
        if (pendingSyncAction != PendingSyncAction.NONE)
            performSync(treeUri, pendingSyncAction);
        else
            showToastLong(R.string.label_sync_change_folder);
    }

    private void performSync(Uri treeUri, PendingSyncAction action) {
        try {
            if (action == PendingSyncAction.PUSH) {
                controller.saveAppSettings();
                controller.saveAccountsToInternal();
                int count = controller.pushSavefilesToFolder(treeUri);
                showToastLong(getString(R.string.toast_success_sync_push, count));
            } else if (action == PendingSyncAction.PULL) {
                int count = controller.pullSavefilesFromFolder(treeUri);
                if (count == 0) {
                    showToastLong(R.string.toast_error_sync_folder_empty);
                    return;
                }
                // reload whatever settings came down, then re-assert the sync folder link in case
                // the pulled settings.json didn't carry it (e.g. an older or hand-edited copy)
                controller.loadAppSettings();
                model.settings.syncFolderUri = treeUri.toString();
                controller.saveAppSettings();
                List<String> availableEntities = controller.getAllAvailableEntities();
                if (!availableEntities.isEmpty())
                    model.availableEntities = availableEntities;
                showToastLong(getString(R.string.toast_success_sync_pull, count));
                recreate();
            }
        } catch (SecurityException e) {
            // the persisted grant became invalid (folder removed/unshared, permission revoked, etc.)
            model.settings.syncFolderUri = null;
            showToastLong(R.string.toast_error_sync_folder_invalid);
        } catch (IOException e) {
            showToastLong(R.string.toast_error_sync_failed);
        } catch (JSONException e) {
            showErrorToast(e);
        }
    }

    private void showIntegrityCheckDialog() {
        List<String> names = controller.getValidSavefileNames();
        if (names.isEmpty()) {
            showToastLong(R.string.toast_error_no_valid_files);
            return;
        }
        String[] namesArray = names.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.label_integrity_check_title_before)
                .setItems(namesArray, (dialog, which) -> showIntegrityCheckAfterDialog(namesArray[which], namesArray))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showIntegrityCheckAfterDialog(String beforeFileName, String[] namesArray) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.label_integrity_check_title_after, Util.reduceFileTypeEnding(beforeFileName)))
                .setItems(namesArray, (dialog, which) -> runIntegrityCheck(beforeFileName, namesArray[which]))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void runIntegrityCheck(String beforeFileName, String afterFileName) {
        IntegrityChecker.Result result = new IntegrityChecker(getController()).compare(beforeFileName, afterFileName);
        AlertDialog dialog = getBasicEditDialog();
        dialog.setTitle(getString(R.string.label_integrity_check_result, Util.reduceFileTypeEnding(afterFileName)));
        dialog.show();
        EditText resultText = dialog.findViewById(R.id.edit_text);
        resultText.setText(formatIntegrityResult(result));
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.confirm), (d, w) -> d.dismiss());
    }

    /**
     * Runs the structural integrity checks against the file that was just loaded.
     *
     * <p>IntegrityChecker already detected the exact "entry silently discarded" class that hid the
     * budget-account bug for years - its own test fixture used that very shape as the example. The
     * check was correct; nobody had ever run it against a real save file, because it was buried in
     * the overflow menu. A check nobody runs is not a check.
     *
     * <p>Deliberately a toast rather than a dialog: this runs on every start, so it must not
     * become something to dismiss reflexively. It says how many findings there are and points at
     * the menu action, which does the full before/after comparison and shows the detail. The
     * findings are logged in full either way.
     *
     * <p>Only the single-file structural checks run here. The total-sum conservation check - the
     * primary one - needs a "before" file to compare against and so stays a manual action.
     */
    private void runIntegrityCheckOnLoad() {
        String fileName = getModel().currentFileName;
        if (fileName == null)
            return;
        IntegrityChecker.Result result = new IntegrityChecker(getController()).check(fileName);
        if (result.isClean())
            return;
        for (IntegrityChecker.Finding finding : result.findings)
            Log.println(Log.ERROR, "integrity_check_on_load", finding.message);
        showToastLong(getString(R.string.toast_warn_integrity_findings, result.findings.size()));
    }

    private String formatIntegrityResult(IntegrityChecker.Result result) {
        if (result.isClean())
            return getString(R.string.label_integrity_check_clean);
        StringBuilder sb = new StringBuilder();
        for (IntegrityChecker.Finding finding : result.findings) {
            sb.append("• ").append(finding.message).append("\n\n");
        }
        return sb.toString().trim();
    }
    //endregion

    public void initiateAccounts() {
        int response = getController().setupAccounts(false);
        if (response == Controller.LOADED_NEW_MONTH) {
            showToastLong(getString(R.string.toast_info_new_month_created,
                    Const.getDisplayableCurrentMonthName()));
        }
        if (response == Controller.CREATED_BLANK)
            showToastLong(getString(R.string.toast_info_blank_accounts));
        // startup parses the settings file and then the save file; both land in the same report
        reportLoadProblems();
        if (response == Controller.LOADED_ACCOUNTS || response == Controller.LOADED_NEW_MONTH)
            runIntegrityCheckOnLoad();
        rbSender = new RbAccountManager(Const.GROUP_SENDER, controller);
        rbReceiver = new RbAccountManager(Const.GROUP_RECEIVER, controller);
    }

    @Override
    public void onRefresh() {
        reloadAccountLists();
        setupActionBar();
        setTitle();
    }

    public void setTitle() {
        setCustomTitle();
        float delta = model.sumAllIncome() - model.sumLoadedPeriodExpenses();
        String firstOrder = String.format("%sx",
                Util.formatLargeFloatShort(delta >= 0 ? delta : -delta)).replace("x", getString(R.string.label_currency));
        String titleDetails = getString(R.string.label_delta) + String.format(delta >= 0 ? " %s" : " (%s)", firstOrder);
        setCustomTitleDetails(titleDetails);
    }
}
