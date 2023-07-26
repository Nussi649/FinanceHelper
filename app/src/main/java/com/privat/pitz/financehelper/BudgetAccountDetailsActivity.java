package com.privat.pitz.financehelper;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TextView;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import Backend.BudgetAccountListHandler;
import Backend.Util;
import Logic.AccountBE;
import Logic.BudgetAccountBE;
import Logic.TxBE;
import View.BudgetAccountTableRow;

public class BudgetAccountDetailsActivity extends AssetAccountDetailsActivity implements BudgetAccountListHandler {
    BudgetAccountBE mAccount;
    List<BudgetAccountTableRow> budgetViews = new ArrayList<>();
    TextView totalValue;
    TextView totalPercentage;
    TextView totalYearly;

    @Override
    public void onStart() {
        super.onStart();
        if (!passedOnCreate) {
            List<BudgetAccountBE> currentAccounts = mAccount.getDirectSubBudgets();
            Iterator<BudgetAccountTableRow> iterator = budgetViews.iterator();

            while (iterator.hasNext()) {
                BudgetAccountTableRow row = iterator.next();
                if (!currentAccounts.contains(row.getReferenceAccount())) {
                    iterator.remove();
                }
            }

            // This will ensure your UI is up-to-date with the current state of budgetViews
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    populateUI();
                }
            });
        }
    }

    @Override
    protected void workingThread() {
        AccountBE acc = getModel().currentInspectedAccount;
        if (acc instanceof BudgetAccountBE) {
            mAccount = (BudgetAccountBE) acc;
            List<BudgetAccountBE> firstLevelBudgets = mAccount.getDirectSubBudgets();
            int count = firstLevelBudgets.size();
            for (int index = 0; index < count; index++) {
                BudgetAccountBE currentAccount = firstLevelBudgets.get(index);
                BudgetAccountTableRow newRow = (BudgetAccountTableRow) LayoutInflater.from(this).inflate(R.layout.table_row_budget_account_overview, null);
                newRow.init(this, this, currentAccount, index == count - 1);
            }
        } else {
            // handle unexpected behaviour
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_budgetaccount_details, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.item_set_yearly_budget) {
            openSetYearlyBudgetDialog();
        } else if (itemId == R.id.item_transfer_budget) {
            openTransferBudgetDialog();
        } else if (itemId == R.id.item_new_sub_budget) {
            openNewSubBudgetDialog();
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void endWorkingThread() {
        setContentView(R.layout.activity_budget_account_details);
        totalValue = findViewById(R.id.total_current_value);
        totalPercentage = findViewById(R.id.total_current_percentage);
        totalYearly = findViewById(R.id.total_yearly_budget);
        populateUI();
    }

    @Override
    public void showAccountDetails(BudgetAccountBE referenceAccount) {
        model.currentInspectedAccount = referenceAccount;
        startActivity(BudgetAccountDetailsActivity.class);
    }

    @Override
    public void addBudgetViewToBackend(BudgetAccountTableRow newItem) {
        budgetViews.add(newItem);
    }

    @Override
    public boolean removeBudgetViewFromBackend(BudgetAccountTableRow removeItem) {
        return budgetViews.remove(removeItem);
    }

    @Override
    protected boolean hasEntries() {
        return mAccount.getTxList().size() != 0;
    }

    protected List<TxBE> getEntries() {
        return mAccount.getTxList();
    }

    @SuppressLint("DefaultLocale")
    protected void populateUI() {
        // populate tx entries
        super.populateUI();
        // populate sub budget entries
        // remove all table rows (there is no header)
        TableLayout rootLayout = findViewById(R.id.sub_budget_overview_table_layout);
        rootLayout.removeAllViews();
        // add the up-to-date BudgetAccountTableRow objects and increase total sum
        for (BudgetAccountTableRow view : budgetViews) {
            view.populateUI();
            rootLayout.addView(view);
        }
        updateUISums();
        updateTitle();
    }

    @SuppressLint("DefaultLocale")
    private void updateUISums() {
        float totalSum = mAccount.getTotalSum();
        float current_budget = mAccount.getTotalAvailableBudget();
        // set values of total sum text views
        String currentBudgetString = Util.formatToFixedLength(Util.formatLargeFloatShort(current_budget),5);
        String currentSumString = String.format("%s / %s",
                Util.formatLargeFloatShort(totalSum),
                currentBudgetString);
        String currentPercentageString = String.format("%.0f%%",
                (totalSum / current_budget) * 100);
        String yearly_budget_string = Util.formatLargeFloatShort(mAccount.getTotalYearlyBudget());
        totalValue.setText(currentSumString);
        totalPercentage.setText(currentPercentageString);
        totalYearly.setText(yearly_budget_string);
    }

    // region Dialogs
    public void openSetYearlyBudgetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_set_yearly_budget, null);

        EditText budgetInput = view.findViewById(R.id.budget_input);

        budgetInput.setText(Util.formatFloatSave(mAccount.indivYearlyBudget));

        builder.setView(view)
                .setPositiveButton("Confirm", (dialog, id) -> {
                    String budgetString = budgetInput.getText().toString();
                    if (!budgetString.isEmpty()) {
                        float budget = (float) Double.parseDouble(budgetString);
                        setYearlyBudget(budget);
                        dialog.dismiss();
                    } else {
                        showToastLong(R.string.toast_error_empty_amount);
                    }
                })
                .setNegativeButton("Cancel", null);

        builder.create().show();
    }

    private void setYearlyBudget(float newBudget) {
        try {
            controller.updateYearlyBudget(newBudget, mAccount);
            onRefresh();
        } catch (JSONException e) {
            showToastLong(R.string.toast_error_JSONError);
        } catch (IOException e) {
            showToastLong(R.string.toast_error_IOError);
        }
    }

    public void openTransferBudgetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_transfer_budget, null);

        EditText amountInput = view.findViewById(R.id.amount_input);
        Spinner recipientSpinner = view.findViewById(R.id.recipient_spinner);
        CheckBox applyToYearlyBudget = view.findViewById(R.id.apply_to_yearly_budget);

        // Get budget accounts from controller and set up the spinner
        List<BudgetAccountBE> budgetAccounts = model.getAllBudgetAccounts();
        ArrayAdapter<BudgetAccountBE> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, budgetAccounts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        recipientSpinner.setAdapter(adapter);
        builder.setTitle(R.string.label_available_budget_transfer);

        builder.setView(view)
                .setPositiveButton("Confirm", (dialog, id) -> {
                    String amountString = amountInput.getText().toString();
                    if (!amountString.isEmpty()) {
                        float amount = (float) Double.parseDouble(amountString);
                        BudgetAccountBE selectedAccount = (BudgetAccountBE) recipientSpinner.getSelectedItem();
                        boolean applyToYearly = applyToYearlyBudget.isChecked();

                        transferBudget(amount, selectedAccount, applyToYearly);
                        dialog.dismiss();
                    } else {
                        showToastLong(R.string.toast_error_empty_amount);
                    }
                })
                .setNegativeButton("Cancel", null);

        builder.create().show();
    }

    private void transferBudget(float amount, BudgetAccountBE recipient, boolean adjustYearly) {
        try {
            controller.transferCurrentBudget(amount, mAccount, recipient, adjustYearly);
            onRefresh();
        } catch (JSONException e) {
            showToastLong(R.string.toast_error_JSONError);
        } catch (IOException e) {
            showToastLong(R.string.toast_error_IOError);
        }
    }

    public void openNewSubBudgetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_new_budget_account, null);

        EditText subBudgetNameInput = view.findViewById(R.id.budget_name_input);
        EditText yearlyBudgetInput = view.findViewById(R.id.yearly_budget_input);
        EditText currentMonthBudgetInput = view.findViewById(R.id.current_month_budget_input);

        builder.setView(view)
                .setPositiveButton("Confirm", (dialog, id) -> {
                    String subBudgetNameString = subBudgetNameInput.getText().toString();
                    String yearlyBudgetString = yearlyBudgetInput.getText().toString();
                    String currentMonthBudgetString = currentMonthBudgetInput.getText().toString();

                    if (!subBudgetNameString.isEmpty() && !yearlyBudgetString.isEmpty()) {
                        float yearlyBudget = (float) Double.parseDouble(yearlyBudgetString);
                        float currentMonthBudget = currentMonthBudgetString.isEmpty() ? yearlyBudget / 12 : (float) Double.parseDouble(currentMonthBudgetString);

                        createSubBudget(subBudgetNameString, currentMonthBudget, yearlyBudget);
                        dialog.dismiss();
                    } else {
                        showToastLong(R.string.toast_error_empty_amount);
                    }
                })
                .setNegativeButton("Cancel", null);

        builder.create().show();
    }

    private void createSubBudget(String name, float currentBudget, float yearlyBudget) {
        try {
            BudgetAccountBE newAccount = controller.createSubBudget(mAccount, name, currentBudget, yearlyBudget);
            if (newAccount == null)
                showToastLong(R.string.toast_error_account_name_taken);
            else {
                int count = budgetViews.size();
                if (count > 0) {
                    // get previously last subBudget and set it to isLast = false
                    BudgetAccountTableRow last = budgetViews.get(count - 1);
                    last.setIsLast(false);
                    last.updateUI();
                }
                // create new BudgetAccountTableRow object and add it to the rootLayout
                BudgetAccountTableRow newRow = (BudgetAccountTableRow) LayoutInflater.from(this).inflate(R.layout.table_row_budget_account_overview, null);
                newRow.init(this, this, newAccount, true);
                TableLayout rootLayout = findViewById(R.id.sub_budget_overview_table_layout);
                newRow.populateUI();
                rootLayout.addView(newRow);
                updateUISums();
            }
        } catch (JSONException | IOException e) {
            if (e instanceof JSONException)
                showToastLong(R.string.toast_error_JSONError);
            else
                showToastLong(R.string.toast_error_IOError);
        }
    }

    @Override
    protected void deleteAccount() {
        Dialog.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                try {
                    boolean result = controller.deleteAccount(mAccount);
                    if (result) {
                        startActivity(BudgetsActivity.class);
                        showToastLong(R.string.toast_success_delete_account);
                    } else {
                        showToastLong(R.string.toast_error_account_not_found);
                    }
                } catch (JSONException e) {
                    showToastLong(R.string.toast_error_JSONError);
                } catch (IOException e) {
                    showToastLong(R.string.toast_error_IOError);
                }
            }
        };
        showConfirmDialog(R.string.question_delete_account, listener);
    }
    // endregion

    @SuppressLint("DefaultLocale")
    private void updateTitle() {

        setTitle(String.format("%s: %s / %s   (%s)",
                mAccount.getName(),
                Util.formatLargeFloatShort(mAccount.getSum()),
                Util.formatLargeFloatShort(mAccount.indivAvailableBudget),
                Util.formatLargeFloatShort(mAccount.indivYearlyBudget)));
    }

    @Override
    public void onRefresh() {
        updateTitle();
    }
}
