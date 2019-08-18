package com.privat.pitz.financehelper;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Looper;
import android.support.v7.app.AlertDialog;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.json.JSONException;

import java.io.IOException;
import java.text.ParseException;
import java.time.Month;
import java.util.Calendar;
import java.util.List;

import Backend.Const;
import Backend.Util;
import View.IncomeListDialog;

public class MainActivity extends AbstractActivity {


    public EditText passwordInput;
    public EditText newDescription;
    public EditText newAmount;

    //region overridden activity methods
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        onAppStartup();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void workingThread() {
        Looper.prepare();
        setupNewAccounts();
    }

    @Override
    protected void endWorkingThread() {
        populateUI();
        setTitle(getResources().getString(R.string.app_name) + " - " + Util.cutFileNameIfNecessary(getModel().currentFileName));
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
            case R.id.item_show_income_list:
                showIncomeListDialog();
                break;
            case R.id.item_save_accounts:
                showSaveAccountsDialog();
                break;
            case R.id.item_load_accounts:
                showLoadAccountsDialog();
                break;
            case R.id.item_settings:
                startActivity(SettingsActivity.class);
                break;
            case R.id.item_display_recurring_orders:
                startActivity(RecurringOrderActivity.class);
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
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
    //endregion

    private void reloadAccountLists() {
        LinearLayout payAccounts = findViewById(R.id.linLayPayAccounts);
        LinearLayout investAccounts = findViewById(R.id.linLayInvestAccounts);

        Util.populatePayAccountsList(this, payAccounts);
        Util.populateInvestAccountsList(this, investAccounts);
        setTitle(getResources().getString(R.string.app_name) + " - " + Util.cutFileNameIfNecessary(getModel().currentFileName));
    }

    private void populateUI() {
        Button pay = findViewById(R.id.button_pay);
        Button invest = findViewById(R.id.button_invest);
        Button addEntry = findViewById(R.id.button_add_entry);
        Button addRecurringOrder = findViewById(R.id.button_add_recurring_order);
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

        addRecurringOrder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
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
                controller.addRecurringOrder(model.currentPayAcc, model.currentInvestAcc, des, Float.valueOf(am));
                newDescription.setText("");
                newAmount.setText("");
                showToastLong(R.string.toast_success_new_recurring_order);
            }
        });

        Util.populatePayAccountsList(this, payAccounts);
        Util.populateInvestAccountsList(this, investAccounts);
    }

    //region show Dialog
    private void showIncomeListDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.label_income_list);
        ScrollView dialogView = new IncomeListDialog(this, model.incomeList);
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
        final CheckBox encryptedCheck = dialogView.findViewById(R.id.check_encrypt);
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
                if (encryptedCheck.isChecked()) {
                    getModel().nextFileName = saveName.getText().toString();
                    getModel().nextFileHidden = hiddenCheck.isChecked();
                    showSavePasswordDialog();
                    dialogInterface.dismiss();
                } else {
                    saveAccountsByName(saveName.getText().toString(), hiddenCheck.isChecked());
                }
            }
        });
        builder.show();
        saveName.requestFocus();
    }

    private void showSavePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.label_enter_password);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_enter_password, null);
        final EditText password = dialogView.findViewById(R.id.edit_password);
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, getDoNothingClickListener());
        builder.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                saveEncryptedAccountsByName(getModel().nextFileName, password.getText().toString(), getModel().nextFileHidden);
            }
        });
        builder.show();
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
        final List<String> availableFiles = getController().getAvailableSaveFiles();
        for (String s : availableFiles) {
            TableRow row = (TableRow) getLayoutInflater().inflate(R.layout.tablerow_file_list, availableFilesLayout, false);
            row.setTag(s);
            final TextView name = row.findViewById(R.id.text_filename);
            TextView delete = row.findViewById(R.id.text_delete_sign);
            name.setText(s);
            name.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    filenameEdit.setText(((TextView)v).getText());
                }
            });
            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    AlertDialog.OnClickListener listener = new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (getController().deleteSavefile(name.getText().toString())) {
                                ((TableLayout) v.getParent().getParent()).removeView((View)v.getParent());
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
                    if (availableFiles.contains(filenameEdit.getText().toString())) {
                        getController().readAccountsFromInternal(filenameEdit.getText().toString());
                        reloadAccountLists();
                    } else {
                        getController().readAccountsFromInternal(Const.ACCOUNTS_HIDDEN_DIRECTORY + "/" + filenameEdit.getText().toString());
                        reloadAccountLists();
                    }
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

    private void showPasswordInputDialog() {

    }

    private void showExportDialog() {
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

    private void showImportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.label_import);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_show_import, null);
        final EditText showImport = dialogView.findViewById(R.id.edit_show_import);
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, getDoNothingClickListener());
        builder.setPositiveButton(R.string.label_import, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    getController().importAccounts(showImport.getText().toString());
                    showToast(R.string.toast_success_accounts_imported);
                } catch (JSONException jsone) {
                    showToastLong(R.string.toast_error_invalid_import);
                } catch (ParseException pe) {
                    showToastLong(R.string.toast_error_parsing);
                }
            }
        });
        builder.show();
    }
    //endregion

    //region react to dialog
    private void saveEncryptedAccountsByName(String name, String password, boolean hidden) {
        try {
            if (hidden) {
                getController().saveEncryptedAccountsToInternal(Const.ACCOUNTS_HIDDEN_DIRECTORY + "/" + name, password);
            } else {
                getController().saveEncryptedAccountsToInternal(name, password);
            }
        } catch (JSONException json) {
            showToastLong(R.string.toast_error_encryption);
            return;
        } catch (IOException ioe) {
            showToastLong(R.string.toast_error_unknown);
            return;
        }
        showToastLong(R.string.toast_success_accounts_saved);
    }

    private void saveAccountsByName(String name, boolean hidden) {
        try {
            if (hidden) {
                getController().saveAccountsToInternal(Const.ACCOUNTS_HIDDEN_DIRECTORY + "/" + name);
            } else {
                getController().saveAccountsToInternal(name);
            }
        } catch (JSONException json) {
            showToastLong(R.string.toast_error_unknown);
            return;
        } catch (IOException ioe) {
            showToastLong(R.string.toast_error_unknown);
            return;
        }
        showToastLong(R.string.toast_success_accounts_saved);
    }
    //endregion

    public void setupNewAccounts() {
        if (!getController().setupAccounts(false))
            showToastLong("New Sheet for Month " + Const.getDisplayableCurrentMonthName() + " created.");
    }
}
