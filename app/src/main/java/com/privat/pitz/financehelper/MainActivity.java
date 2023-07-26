package com.privat.pitz.financehelper;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Looper;
import android.app.AlertDialog;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import Backend.Const;
import Backend.Controller;
import Backend.RbAccountManager;
import Backend.Util;
import View.IncomeListDialog;

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
            showAddFundsDialog();
        } else if (itemId == R.id.item_save_accounts) {
            showSaveAccountsDialog();
        } else if (itemId == R.id.item_load_accounts) {
            showLoadAccountsDialog();
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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onRefresh();
                    }
                });
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
        TableLayout tv_AssetAccounts = findViewById(R.id.treeView_Assets);
        TableLayout tv_BudgetAccounts = findViewById(R.id.treeView_Budgets);

        Util.populateAssetAccountsPreview(model.asset_accounts,
                this,
                tv_AssetAccounts,
                rbReceiver,
                rbSender);
        Util.populateBudgetAccountsPreview(model.budget_accounts,
                this,
                tv_BudgetAccounts,
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

        open_assets_detailed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(AssetsActivity.class);
            }
        });

        open_budgets_detailed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(BudgetsActivity.class);
            }
        });

        MainActivity parent = this;
        addTx.setOnClickListener(new View.OnClickListener() {
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
                float amount = 0.0f;
                try {
                    amount = Float.parseFloat(am);
                } catch (NumberFormatException e) {
                    showToastLong(R.string.toast_error_invalid_amount);
                    return;
                }
                // try creating a transaction and wait for result
                boolean result = false;
                try {
                    result = controller.createTx(parent, des, amount);
                } catch (JSONException e) {
                    showToastLong(R.string.toast_error_JSONError);
                } catch (IOException e) {
                    showToastLong(R.string.toast_error_IOError);
                }
                // if transaction was created successfully, clear input fields and show toast
                if (result) {
                    newDescription.setText("");
                    newAmount.setText("");
                    onRefresh();
                    showToastLong(R.string.toast_success_new_entry);
                }
            }
        });

        addRecurringOrder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
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
                    float amount = Float.parseFloat(amountString);
                    boolean result = controller.addRecurringTx(parent, description, amount);
                    if (result) {
                        showToast(R.string.toast_success_new_recurring_tx);
                        newDescription.setText("");
                        newAmount.setText("");
                    } else {
                        showToastLong(R.string.toast_error_unknown);
                    }
                } catch (NumberFormatException e) {
                    showToast(R.string.toast_error_NaN);
                } catch (JSONException e) {
                    showToastLong(R.string.toast_error_JSONError);
                } catch (IOException e) {
                    showToastLong(R.string.toast_error_IOError);
                }
            }
        });

        onRefresh();
    }

    //region show Dialog
    private void showAddFundsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.label_add_funds);

        // Inflate the dialog_add_funds layout
        final View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_funds, null);
        builder.setView(dialogView);

        // Get references to EditText views
        final EditText addFundsAmount = dialogView.findViewById(R.id.edit_amount);
        final EditText descriptionText = dialogView.findViewById(R.id.edit_new_description);

        // Set negative button with cancel action
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        // Set positive button with add funds action
        builder.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String amountString = addFundsAmount.getText().toString().trim();
                String description = descriptionText.getText().toString().trim();
                if (amountString.isEmpty()) {
                    showToastLong(R.string.toast_error_empty_amount);
                    return;
                }
                if (description.isEmpty()) {
                    showToastLong(R.string.toast_error_empty_description);
                    return;
                }

                try {
                    float amount = Float.parseFloat(amountString);
                    boolean result = controller.addFunds(amount, description);
                    if (result) {
                        showToast(R.string.toast_success_new_income);
                        onRefresh();
                        dialog.dismiss();
                    } else {
                        showToastLong(R.string.toast_error_no_receiver_selected);
                    }
                } catch (NumberFormatException e) {
                    showToast(R.string.toast_error_NaN);
                } catch (JSONException e) {
                    showToastLong(R.string.toast_error_JSONError);
                } catch (IOException e) {
                    showToastLong(R.string.toast_error_IOError);
                }
            }
        });

        // Show the dialog
        builder.show();
    }

    public void getTransactionRedirectionInput(String targetFileName, JSONObject fileContent, String desc, float amount, JSONArray allAccounts) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select an account for transaction redirection");

        // Layout for the dialog
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_tx_redirection_input, null);

        // Get the TextViews and Spinner from the layout
        TextView descriptionTextView = dialogView.findViewById(R.id.descriptionTextView);
        TextView amountTextView = dialogView.findViewById(R.id.amountTextView);
        Spinner accountSpinner = dialogView.findViewById(R.id.accountSpinner);

        // Set the description and amount
        descriptionTextView.setText(desc);
        amountTextView.setText(String.valueOf(amount));

        // Create an ArrayAdapter using the string array and a default spinner layout
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
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, accountNames);

        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Apply the adapter to the spinner
        accountSpinner.setAdapter(adapter);

        builder.setView(dialogView);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String selectedAccountName = accountSpinner.getSelectedItem().toString();
                boolean success = false;
                try {
                    success = controller.completeTxRedirection(targetFileName, model.currentFileAttributes.entityName, desc, amount, selectedAccountName, fileContent);
                } catch (JSONException e) {
                    showToastLong(R.string.toast_error_JSONError);
                } catch (IOException e) {
                    showToastLong(R.string.toast_error_IOError);
                }
                if (success) {
                    showToastLong("Transaction redirection completed successfully.");
                } else {
                    showToastLong("Error while completing transaction redirection.");
                }
            }
        });

        builder.show();
    }

    private void showIncomeListDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.label_income_list);
        LinearLayout dialogView = new IncomeListDialog(this, model.currentIncome);
        builder.setView(dialogView);
        builder.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        builder.show();
    }

    private void showSaveAccountsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.label_save_accounts);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_save_accounts, null);
        final EditText saveName = dialogView.findViewById(R.id.edit_save_name);
        final Button exportButton = dialogView.findViewById(R.id.button_export);
        final CheckBox hiddenCheck = dialogView.findViewById(R.id.check_hidden);
        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showExportDialog();
            }
        });
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, getDoNothingClickListener());
        builder.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                saveStateToFile(saveName.getText().toString(), hiddenCheck.isChecked());{
                }
            }
        });
        builder.show();
        saveName.requestFocus();
    }

    private void showLoadAccountsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.label_load_accounts);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_load_accounts, null);
        final TableLayout availableFilesLayout = dialogView.findViewById(R.id.layout_available_files);
        final EditText filenameEdit = dialogView.findViewById(R.id.edit_load_name);
        final Button importButton = dialogView.findViewById(R.id.button_import);
        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImportDialog();
            }
        });
        final List<String> availableFiles = Util.getFileNames(Util.getValidFiles(getFilesDir()));
        if (availableFiles == null || availableFiles.size() == 0)
            showToastLong(R.string.toast_error_no_valid_files);
        else
            for (String s : availableFiles) {
                TableRow row = (TableRow) getLayoutInflater().inflate(R.layout.tablerow_file_list, availableFilesLayout, false);
                row.setTag(s);
                final TextView name = row.findViewById(R.id.text_filename);
                TextView delete = row.findViewById(R.id.text_delete_sign);
                name.setText(s);
                name.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        filenameEdit.setText(((TextView) v).getText());
                    }
                });
                delete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        AlertDialog.OnClickListener listener = new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (getController().deleteSavefile(name.getText().toString())) {
                                    ((TableLayout) v.getParent().getParent()).removeView((View) v.getParent());
                                    showToastLong(R.string.toast_success_file_deleted);
                                } else {
                                    showToastLong(R.string.toast_error_unknown);
                                }
                            }
                        };
                        showConfirmDialog(R.string.question_delete_savefile, listener);
                    }
                });
                availableFilesLayout.addView(row);
            }
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, getDoNothingClickListener());
        builder.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    getController().readAccountsFromInternal(filenameEdit.getText().toString());
                    onRefresh();
                } catch (JSONException jsone) {
                    dialog.dismiss();
                    showToastLong(R.string.toast_error_unknown);
                } catch (IOException ioe) {
                    dialog.dismiss();
                    showToastLong(R.string.toast_error_invalid_filename);
                } catch (ParseException pe) {
                    dialog.dismiss();
                    showToastLong(R.string.toast_error_parsing);
                }
            }
        });
        builder.show();
    }

    private void showExportDialog() {
        AlertDialog dialog = getBasicEditDialog();
        dialog.setTitle(R.string.label_export);
        dialog.show();
        EditText showExport = dialog.findViewById(R.id.edit_text);
        try {
            showExport.setText(getController().exportAccounts());
        } catch (JSONException jsone) {
            showToast(R.string.toast_error_unknown);
            return;
        }
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.accept), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
    }

    private void showImportDialog() {
        AlertDialog dialog = getBasicEditDialog();
        dialog.setTitle(R.string.label_import);
        dialog.show();
        EditText showImport = dialog.findViewById(R.id.edit_text);
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.label_import), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                try {
                    getController().importAccounts(showImport.getText().toString());
                    showToast(R.string.toast_success_accounts_imported);
                } catch (JSONException jsone) {
                    showToastLong(R.string.toast_error_invalid_import);
                } catch (ParseException pe) {
                    showToastLong(R.string.toast_error_parsing);
                }
                dialog.dismiss();
            }
        });
    }

    private void showEditSavefileDialog() {
        AlertDialog dialog = getBasicEditDialog();
        dialog.setTitle(R.string.label_edit_savefile);
        dialog.show();
        EditText showSavefile = dialog.findViewById(R.id.edit_text);
        try {
            showSavefile.setText(getController().exportAccounts());
        } catch (JSONException jsone) {
            showToast(R.string.toast_error_unknown);
            return;
        }
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.accept), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                try {
                    getController().importAccounts(showSavefile.getText().toString());
                    showToast(R.string.toast_success_accounts_imported);
                } catch (JSONException jsone) {
                    showToastLong(R.string.toast_error_invalid_import);
                } catch (ParseException pe) {
                    showToastLong(R.string.toast_error_parsing);
                }
                dialog.dismiss();
            }
        });
    }
    //endregion

    //region react to dialog
    private void saveStateToFile(String name, boolean hidden) {
        try {
            if (hidden) {
                getController().saveAccountsToInternal(Const.ACCOUNTS_HIDDEN_DIRECTORY + "/" + name);
            } else {
                getController().saveAccountsToInternal(name);
            }
        } catch (JSONException | IOException ex) {
            showToastLong(R.string.toast_error_unknown);
            return;
        }
        showToastLong(R.string.toast_success_write_save_file);
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
        try {
            Util.FileNameParts parts = Util.parseFileName(getModel().currentFileName);
            String monthName = Const.getMonthNameById(parts.month - 1);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setTitle(String.format("%s (%s)", parts.entityName, monthName));
                }
            });
        } catch (IllegalArgumentException e) {
            Log.println(Log.ERROR, "parse_file_name", e.toString());
        }
    }
}
