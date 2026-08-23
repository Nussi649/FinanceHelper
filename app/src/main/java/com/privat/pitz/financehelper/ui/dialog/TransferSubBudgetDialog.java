package com.privat.pitz.financehelper.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.privat.pitz.financehelper.R;

import java.util.ArrayList;
import java.util.List;

import com.privat.pitz.financehelper.data.BudgetAccountBE;

public abstract class TransferSubBudgetDialog extends BaseInputDialog {
    private final List<BudgetAccountBE> directSubBudgets;
    private final List<BudgetAccountBE> allBudgetAccounts;

    // View objects
    Spinner subBudgetSpinner;
    Spinner targetSpinner;

    public TransferSubBudgetDialog(Context context, BudgetAccountBE currentAccount, List<BudgetAccountBE> allBudgetAccounts) {
        super(context);
        this.directSubBudgets = currentAccount.getDirectSubBudgets();
        this.allBudgetAccounts = allBudgetAccounts;
        this.allBudgetAccounts.remove(currentAccount);
    }

    public abstract void onConfirm(BudgetAccountBE subBudget, BudgetAccountBE target);

    @Override
    protected int getLayoutRes() {
        return R.layout.dialog_transfer_sub_budget;
    }

    @Override
    protected int getTitleRes() {
        return R.string.label_sub_budget_transfer;
    }

    @Override
    protected void bindViews(View view) {
        subBudgetSpinner = view.findViewById(R.id.direct_sub_budgets_spinner);
        targetSpinner = view.findViewById(R.id.target_account_spinner);

        // Populate the direct sub budgets spinner
        ArrayAdapter<BudgetAccountBE> subBudgetAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, directSubBudgets);
        subBudgetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        subBudgetSpinner.setAdapter(subBudgetAdapter);

        // Initially, the second spinner is disabled
        targetSpinner.setEnabled(false);

        // When an item is selected in the first spinner, enable the second spinner and populate it
        subBudgetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // First calculate list of available targets (exclude the selected account)
                BudgetAccountBE selection = (BudgetAccountBE) subBudgetSpinner.getSelectedItem();
                List<BudgetAccountBE> availableTargets = new ArrayList<>(allBudgetAccounts);
                availableTargets.remove(selection);
                availableTargets.removeAll(selection.getAllSubBudgets());
                // Populate the all budget accounts spinner
                ArrayAdapter<BudgetAccountBE> targetAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, availableTargets);
                targetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                targetSpinner.setAdapter(targetAdapter);

                // Enable the second spinner
                targetSpinner.setEnabled(true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // If nothing is selected, clear the second spinner and disable it
                targetSpinner.setAdapter(null);
                targetSpinner.setEnabled(false);
            }
        });
    }

    @Override
    protected boolean onConfirmClicked() {
        Object selectedDirectSubBudgetObj = subBudgetSpinner.getSelectedItem();
        Object selectedTargetAccountObj = targetSpinner.getSelectedItem();

        if (selectedDirectSubBudgetObj == null || selectedTargetAccountObj == null) {
            toastShort(R.string.toast_error_select_both_accounts);
            return false;
        }

        BudgetAccountBE selectedDirectSubBudget = (BudgetAccountBE) selectedDirectSubBudgetObj;
        BudgetAccountBE selectedTargetAccount = (BudgetAccountBE) selectedTargetAccountObj;

        onConfirm(selectedDirectSubBudget, selectedTargetAccount);
        return true;
    }
}
