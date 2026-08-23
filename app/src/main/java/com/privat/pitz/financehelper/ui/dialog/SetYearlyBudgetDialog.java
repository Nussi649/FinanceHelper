package com.privat.pitz.financehelper.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import com.privat.pitz.financehelper.R;

import com.privat.pitz.financehelper.core.Util;

public abstract class SetYearlyBudgetDialog extends BaseInputDialog {
    private final float currentBudget;

    // View objects
    EditText budgetInput;
    CheckBox adjustCheck;

    public SetYearlyBudgetDialog(Context context, float currentBudget) {
        super(context);
        this.currentBudget = currentBudget;
    }

    public abstract void onConfirm(float newBudget, boolean adjustAvailable);

    @Override
    protected int getLayoutRes() {
        return R.layout.dialog_set_yearly_budget;
    }

    @Override
    protected int getTitleRes() {
        return R.string.label_yearly_budget_edit;
    }

    @Override
    protected void bindViews(View view) {
        budgetInput = view.findViewById(R.id.budget_input);
        adjustCheck = view.findViewById(R.id.apply_to_available_budget);
        budgetInput.setText(Util.formatFloatSave(currentBudget));
    }

    @Override
    protected boolean onConfirmClicked() {
        String budgetString = budgetInput.getText().toString();
        if (!budgetString.isEmpty()) {
            try {
                float budget = Util.parseAmount(budgetString);
                onConfirm(budget, adjustCheck.isChecked());
                return true;
            } catch (NumberFormatException e) {
                toastLong(R.string.toast_error_invalid_amount);
                return false;
            }
        } else {
            toastLong(R.string.toast_error_empty_amount);
            return false;
        }
    }
}
