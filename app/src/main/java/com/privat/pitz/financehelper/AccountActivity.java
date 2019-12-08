package com.privat.pitz.financehelper;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import org.json.JSONException;

import Backend.Util;
import Logic.AccountBE;
import Logic.EntryBE;

public class AccountActivity extends AbstractActivity {
    AccountBE mAccount;
    boolean isPayAccount;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
    }

    @Override
    protected void workingThread() {
        mAccount = getModel().currentInspectedAccount;
    }

    @Override
    protected void endWorkingThread() {
        setTitle(mAccount.getName());
        isPayAccount = model.payAccounts.contains(mAccount);
        populateUI();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_account, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.item_delete_account:
                deleteAccount();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void populateUI() {
        if (mAccount.getEntries().size() == 0) {
            findViewById(R.id.root_layout).setVisibility(View.GONE);
            showToastLong(R.string.toast_error_no_entries);
            return;
        }
        filterEntries(null);
        EditText filter = findViewById(R.id.edit_filter);
        filter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                removeAllEntries();
                filterEntries(charSequence);
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    private void deleteAccount() {
        Dialog.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (getModel().payAccounts.contains(mAccount))
                    getModel().payAccounts.remove(mAccount);
                if (getModel().investAccounts.contains(mAccount))
                    getModel().investAccounts.remove(mAccount);
                startActivity(MainActivity.class);
            }
        };
        showConfirmDialog(R.string.question_delete_account, listener);
    }

    private void deleteEntry(final EntryBE toDelete) {
        Dialog.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (mAccount.getEntries().contains(toDelete)) {
                    mAccount.removeEntry(toDelete);
                    showToastLong(R.string.toast_success_delete_entry);
                    try {
                        controller.saveAccountsToInternal();
                    } catch (Exception ex) { }
                    refreshView();
                }
            }
        };
        showConfirmDialog(R.string.question_delete_entry, listener);
    }

    protected void removeAllEntries() {
        TableLayout tabLay = findViewById(R.id.contentTable);
        while (tabLay.getChildCount() > 2) {
            tabLay.removeViewAt(2);
        }
    }

    protected void refreshView() {
        removeAllEntries();
        filterEntries(null);
    }

    protected void filterEntries(CharSequence filter) {
        float sum = 0.0f;
        TableLayout tabLay = findViewById(R.id.contentTable);
        if (filter == null) {
            for (final EntryBE entr : mAccount.getEntries()) {
                TableRow row = (TableRow) getLayoutInflater().inflate(R.layout.table_row_account, tabLay, false);
                TextView date = row.findViewById(R.id.label_time);
                final TextView labelDescription = row.findViewById(R.id.label_description);
                final EditText editDescription = row.findViewById(R.id.edit_text_description);
                final ViewSwitcher switcherDesc = row.findViewById(R.id.viewSwitcher_description);
                final TextView labelAmount = row.findViewById(R.id.label_amount);
                final EditText editAmount = row.findViewById(R.id.edit_text_amount);
                final ViewSwitcher switcherAmount = row.findViewById(R.id.viewSwitcher_amount);
                date.setText(Util.formatDateDisplay(entr.getDate()));
                date.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        deleteEntry(entr);
                        return true;
                    }
                });
                labelDescription.setText(entr.getDescription());
                labelDescription.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        String desc = labelDescription.getText().toString();
                        switcherDesc.showNext();
                        editDescription.setText(desc);
                        return true;
                    }
                });
                editDescription.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        String desc = editDescription.getText().toString();
                        if (controller.updateEntry(entr.getDate(), entr.getDescription(), mAccount, isPayAccount, desc)) {
                            labelDescription.setText(desc);
                            showToast(R.string.toast_success_update_entries);
                        } else {
                            showToastLong(R.string.toast_error_update_entries);
                        }
                        switcherDesc.showNext();
                        return true;
                    }
                });
                labelAmount.setText(Util.formatFloat(entr.getAmount()));
                labelAmount.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        float am = Float.parseFloat(labelAmount.getText().toString());
                        switcherAmount.showNext();
                        editAmount.setText(String.valueOf(am));
                        return true;
                    }
                });
                editAmount.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        float am = Float.parseFloat(editAmount.getText().toString());
                        if (controller.updateEntry(entr.getDate(), entr.getDescription(), mAccount, isPayAccount, am)) {
                            labelAmount.setText(String.valueOf(am));
                            showToast(R.string.toast_success_update_entries);
                        } else {
                            showToastLong(R.string.toast_error_update_entries);
                        }
                        switcherAmount.showNext();
                        return true;
                    }
                });
                sum += entr.getAmount();
                tabLay.addView(row);
            }
        } else {
            for (final EntryBE entr : mAccount.getEntries()) {
                if (entr.getDescription().contains(filter)) {
                    TableRow row = (TableRow) getLayoutInflater().inflate(R.layout.table_row_account, tabLay, false);
                    TextView date = row.findViewById(R.id.label_time);
                    final TextView labelDescription = row.findViewById(R.id.label_description);
                    final EditText editDescription = row.findViewById(R.id.edit_text_description);
                    final ViewSwitcher switcherDesc = row.findViewById(R.id.viewSwitcher_description);
                    final TextView labelAmount = row.findViewById(R.id.label_amount);
                    final EditText editAmount = row.findViewById(R.id.edit_text_amount);
                    final ViewSwitcher switcherAmount = row.findViewById(R.id.viewSwitcher_amount);
                    date.setText(Util.formatDateDisplay(entr.getDate()));
                    labelDescription.setText(entr.getDescription());
                    labelDescription.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            String desc = labelDescription.getText().toString();
                            switcherDesc.showNext();
                            editDescription.setText(desc);
                            return true;
                        }
                    });
                    editDescription.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            String desc = editDescription.getText().toString();
                            if (controller.updateEntry(entr.getDate(), entr.getDescription(), mAccount, isPayAccount, desc)) {
                                labelDescription.setText(desc);
                                showToast(R.string.toast_success_update_entries);
                            } else {
                                showToastLong(R.string.toast_error_update_entries);
                            }
                            switcherDesc.showNext();
                            return true;
                        }
                    });
                    labelAmount.setText(Util.formatFloat(entr.getAmount()));
                    labelAmount.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            float am = Float.parseFloat(labelAmount.getText().toString());
                            switcherAmount.showNext();
                            editAmount.setText(String.valueOf(am));
                            return true;
                        }
                    });
                    editAmount.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            float am = Float.parseFloat(editAmount.getText().toString());
                            if (controller.updateEntry(entr.getDate(), entr.getDescription(), mAccount, isPayAccount, am)) {
                                labelAmount.setText(String.valueOf(am));
                                showToast(R.string.toast_success_update_entries);
                            } else {
                                showToastLong(R.string.toast_error_update_entries);
                            }
                            switcherAmount.showNext();
                            return true;
                        }
                    });
                    sum += entr.getAmount();
                    tabLay.addView(row);
                }
            }
        }
        TextView textSum = findViewById(R.id.display_sum);
        textSum.setText(Util.formatFloat(sum));
    }
}
