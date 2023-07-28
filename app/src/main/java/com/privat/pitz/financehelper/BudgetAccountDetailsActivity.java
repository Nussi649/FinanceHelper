package com.privat.pitz.financehelper;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import Backend.BudgetAccountListHandler;
import Backend.Const;
import Backend.TxListAdapter;
import Backend.Util;
import Logic.AccountBE;
import Logic.BudgetAccountBE;
import Logic.TxBE;
import View.BudgetAccountTableRow;
import View.Dialogs.CreateBudgetAccountDialog;
import View.Dialogs.SetYearlyBudgetDialog;
import View.Dialogs.TransferAvailableBudgetDialog;
import View.Dialogs.TransferSubBudgetDialog;

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
            reloadUI();
        }
    }

    @Override
    protected void workingThread() {
        AccountBE acc = getModel().currentInspectedAccount;
        if (acc instanceof BudgetAccountBE) {
            mAccount = (BudgetAccountBE) acc;
            loadSubBudgets();
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
            openTransferAvailableBudgetDialog();
        } else if (itemId == R.id.item_new_sub_budget) {
            showCreateSubBudgetDialog();
        } else if (itemId == R.id.item_transfer_sub_budget) {
            showTransferSubBudgetDialog();
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void endWorkingThread() {
        setContentView(R.layout.activity_budget_account_details);
        recyclerView = findViewById(R.id.recyclerView);
        searchView = findViewById(R.id.search_filter);
        listAdapter = new TxListAdapter();
        View containerTotal = findViewById(R.id.container_total_sum);
        totalValue = containerTotal.findViewById(R.id.total_current_value);
        totalPercentage = containerTotal.findViewById(R.id.total_current_percentage);
        totalYearly = containerTotal.findViewById(R.id.total_yearly_budget);
        View containerTx = findViewById(R.id.container_tx_sum);
        indivValue= containerTx.findViewById(R.id.total_current_value);
        indivPercentage = containerTx.findViewById(R.id.total_current_percentage);
        indivYearly = containerTx.findViewById(R.id.total_yearly_budget);

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

    @SuppressLint("DefaultLocale")
    @Override
    protected void setTxSum(float newValue) {
        String currentBudgetString = Util.formatToFixedLength(Util.formatLargeFloatDisplay(mAccount.indivAvailableBudget),5);
        String currentSumString = String.format("%sx / %sx",
                Util.formatLargeFloatDisplay(newValue),
                currentBudgetString);
        String currentPercentageString = String.format("%.0f%%",
                (newValue / mAccount.indivAvailableBudget) * 100);
        String yearly_budget_string = Util.formatLargeFloatShort(mAccount.indivYearlyBudget) + "x";
        // get currency character
        String currency = getString(R.string.label_currency);
        // set values of total sum text views
        indivValue.setText(currentSumString.replace("x", currency));
        indivPercentage.setText(currentPercentageString);
        indivYearly.setText(yearly_budget_string.replace("x", currency));
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
        updateUITotalSums();
        updateTitle();
    }

    @SuppressLint("DefaultLocale")
    private void updateUITotalSums() {
        float totalSum = mAccount.getTotalSum();
        float current_budget = mAccount.getTotalAvailableBudget();
        float current_percentage = totalSum / current_budget;
        // set values of total sum text views
        String currentBudgetString = Util.formatToFixedLength(Util.formatLargeFloatShort(current_budget),5);
        String currentSumString = String.format("%s / %s",
                Util.formatLargeFloatShort(totalSum),
                currentBudgetString);
        String currentPercentageString = String.format("%.0f%%",
                current_percentage * 100);
        String yearly_budget_string = Util.formatLargeFloatShort(mAccount.getTotalYearlyBudget());
        totalValue.setText(currentSumString);
        totalPercentage.setText(currentPercentageString);
        totalYearly.setText(yearly_budget_string);
        char percentageEval = Util.evaluatePercentage(current_percentage);
        if (percentageEval == '+')
            totalPercentage.setBackground(Util.createBackground(getColor(R.color.colorNegative)));
        if (percentageEval == '-')
            totalPercentage.setBackground(Util.createBackground(getColor(R.color.colorPositive)));
        if (percentageEval == 'O')
            totalPercentage.setBackground(Util.createBackground(getColor(R.color.colorNeutral)));
    }

    private void loadSubBudgets() {
        budgetViews = new ArrayList<>();
        List<BudgetAccountBE> firstLevelBudgets = mAccount.getDirectSubBudgets();
        int count = firstLevelBudgets.size();
        for (int index = 0; index < count; index++) {
            BudgetAccountBE currentAccount = firstLevelBudgets.get(index);
            BudgetAccountTableRow newRow = BudgetAccountTableRow.getInstance(this);
            newRow.init(this, currentAccount, index == count - 1);
        }
    }

    private void reloadUI() {
        budgetViews = new ArrayList<>();
        loadSubBudgets();
        // This will ensure your UI is up-to-date with the current state of budgetViews
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                populateUI();
            }
        });
    }

    // region Dialogs
    public void openSetYearlyBudgetDialog() {
        SetYearlyBudgetDialog dialog = new SetYearlyBudgetDialog(this, mAccount.indivYearlyBudget) {
            @Override
            public void onConfirm(float newBudget, boolean adjustAvailable) {
                setYearlyBudget(newBudget, adjustAvailable);
            }
        };
        dialog.show();
    }

    private void setYearlyBudget(float newBudget, boolean adjustAvailable) {
        try {
            controller.updateYearlyBudget(newBudget, mAccount, adjustAvailable);
            onRefresh();
            showToastLong(R.string.toast_success_yearly_budget_adjusted);
        } catch (JSONException | IOException e) {
            showErrorToast(e);
        }
    }

    public void openTransferAvailableBudgetDialog() {
        TransferAvailableBudgetDialog dialog = new TransferAvailableBudgetDialog(this, model.getAllBudgetAccounts(), mAccount) {
            @Override
            public void onConfirm(float amount, BudgetAccountBE selectedAccount, boolean applyToYearly) {
                transferAvailableBudget(amount, selectedAccount, applyToYearly);
            }
        };
        dialog.show();
    }

    private void transferAvailableBudget(float amount, BudgetAccountBE recipient, boolean adjustYearly) {
        try {
            controller.transferAvailableBudget(amount, mAccount, recipient, adjustYearly);
            onRefresh();
            showToastLong(R.string.toast_success_available_budget_transferred);
        } catch (JSONException | IOException e) {
            showErrorToast(e);
        }
    }

    public void showCreateSubBudgetDialog() {
        CreateBudgetAccountDialog dialog = new CreateBudgetAccountDialog(this) {
            @Override
            public void onConfirm(String subBudgetName, float currentMonthBudget, float yearlyBudget) {
                createSubBudget(subBudgetName, currentMonthBudget, yearlyBudget);
            }
        };
        dialog.show();
    }

    @SuppressLint("InflateParams")
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
                BudgetAccountTableRow newRow = BudgetAccountTableRow.getInstance(this);
                newRow.init(this, newAccount, true);
                TableLayout rootLayout = findViewById(R.id.sub_budget_overview_table_layout);
                newRow.populateUI();
                rootLayout.addView(newRow);
                updateUITotalSums();
                showToastLong(R.string.toast_success_sub_budget_created);
            }
        } catch (JSONException | IOException e) {
            showErrorToast(e);
        }
    }

    public void showTransferSubBudgetDialog() {
        TransferSubBudgetDialog transferDialog = new TransferSubBudgetDialog(this,
                mAccount, model.getAllBudgetAccounts()) {
            @Override
            public void onConfirm(BudgetAccountBE subBudget, BudgetAccountBE target) {
                transferSubBudget(subBudget, target);
            }
        };
        transferDialog.show();
    }

    private void transferSubBudget(BudgetAccountBE subBudget, BudgetAccountBE targetAccount) {
        try {
            boolean result = controller.transferSubBudget(mAccount, subBudget, targetAccount);
            if (result)
                showToastLong(R.string.toast_success_sub_budget_transferred);
            else {
                showToastLong(R.string.toast_error_invalid_request);
                return;
            }
            reloadUI();
        } catch (JSONException | IOException e) {
            showErrorToast(e);
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
                } catch (JSONException | IOException e) {
                    showErrorToast(e);
                }
            }
        };
        showConfirmDialog(R.string.question_delete_account, listener);
    }
    // endregion

    @SuppressLint("DefaultLocale")
    private void updateTitle() {
        // set Activity title
        try {
            Util.FileNameParts parts = Util.parseFileName(getModel().currentFileName);
            String monthName = Const.getMonthNameById(parts.month - 1);
            setCustomTitle(monthName + ":");
            setCustomTitleDetails(mAccount.getName());
        } catch (IllegalArgumentException e) {
            Log.println(Log.ERROR, "parse_file_name", e.toString());
        }
    }

    @Override
    public void onRefresh() {
        updateTitle();
        updateUITotalSums();
    }
}