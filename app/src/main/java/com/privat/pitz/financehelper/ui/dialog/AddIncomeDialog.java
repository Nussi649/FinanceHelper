package com.privat.pitz.financehelper.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.EditText;

import com.privat.pitz.financehelper.R;

import com.privat.pitz.financehelper.core.Util;

public abstract class AddIncomeDialog extends BaseInputDialog {
    EditText addFundsAmount;
    EditText descriptionText;

    public AddIncomeDialog(Context context) {
        super(context);
    }

    public abstract void onConfirm(float amount, String description);

    @Override
    protected int getLayoutRes() {
        return R.layout.dialog_add_funds;
    }

    @Override
    protected int getTitleRes() {
        return R.string.label_add_funds;
    }

    @Override
    protected void bindViews(View view) {
        addFundsAmount = view.findViewById(R.id.edit_amount);
        descriptionText = view.findViewById(R.id.edit_new_description);
    }

    @Override
    protected boolean onConfirmClicked() {
        String amountString = addFundsAmount.getText().toString().trim();
        String description = descriptionText.getText().toString().trim();
        if (amountString.isEmpty()) {
            toastLong(R.string.toast_error_empty_amount);
            return false;
        }
        if (description.isEmpty()) {
            toastLong(R.string.toast_error_empty_description);
            return false;
        }
        try {
            float amount = Util.parseAmount(amountString);
            onConfirm(amount, description);
            return true;
        } catch (NumberFormatException e) {
            toastLong(R.string.toast_error_invalid_amount);
            return false;
        }
    }
}
