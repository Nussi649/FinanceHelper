package com.privat.pitz.financehelper.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.EditText;

import com.privat.pitz.financehelper.R;

import com.privat.pitz.financehelper.core.Util;

public abstract class CreateBudgetAccountDialog extends BaseInputDialog {
    private final boolean isProjectBudget;

    // View objects
    EditText subBudgetNameInput;
    EditText yearlyBudgetInput;
    EditText currentMonthBudgetInput;

    public CreateBudgetAccountDialog(Context context, boolean projectBudget) {
        super(context);
        this.isProjectBudget = projectBudget;
    }

    public CreateBudgetAccountDialog(Context context) {
        this(context, false);
    }

    public abstract void onConfirm(String subBudgetName, float currentMonthBudget, float yearlyBudget);

    @Override
    protected int getLayoutRes() {
        return R.layout.dialog_new_budget_account;
    }

    @Override
    protected int getTitleRes() {
        return isProjectBudget ? R.string.label_project_budget_new : R.string.label_budget_account_new;
    }

    @Override
    protected void bindViews(View view) {
        subBudgetNameInput = view.findViewById(R.id.budget_name_input);
        yearlyBudgetInput = view.findViewById(R.id.yearly_budget_input);
        currentMonthBudgetInput = view.findViewById(R.id.current_month_budget_input);
        if (isProjectBudget) {
            view.findViewById(R.id.euro_sign).setVisibility(View.GONE);
            currentMonthBudgetInput.setVisibility(View.GONE);
            subBudgetNameInput.setHint(R.string.label_project_name);
            yearlyBudgetInput.setHint(R.string.label_project_budget);
        }
    }

    @Override
    protected boolean onConfirmClicked() {
        String subBudgetNameString = subBudgetNameInput.getText().toString();
        String yearlyBudgetString = yearlyBudgetInput.getText().toString();
        String currentMonthBudgetString = currentMonthBudgetInput.getText().toString();

        if (subBudgetNameString.isEmpty()) {
            toastLong(R.string.toast_error_empty_name);
            return false;
        } else if (yearlyBudgetString.isEmpty()) {
            toastLong(R.string.toast_error_empty_amount);
            return false;
        } else {
            try {
                float yearlyBudget = Util.parseAmount(yearlyBudgetString);
                float currentMonthBudget = currentMonthBudgetString.isEmpty() ? yearlyBudget / 12 : Util.parseAmount(currentMonthBudgetString);

                onConfirm(subBudgetNameString, currentMonthBudget, yearlyBudget);
                return true;
            } catch (NumberFormatException e) {
                toastLong(R.string.toast_error_invalid_amount);
                return false;
            }
        }
    }
}
