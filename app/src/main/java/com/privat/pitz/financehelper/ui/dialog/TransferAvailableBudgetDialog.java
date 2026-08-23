package com.privat.pitz.financehelper.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import com.privat.pitz.financehelper.R;

import java.util.List;

import com.privat.pitz.financehelper.core.Util;
import com.privat.pitz.financehelper.data.BudgetAccountBE;

public abstract class TransferAvailableBudgetDialog extends BaseInputDialog {
    private final List<BudgetAccountBE> allBudgetAccounts;

    // View objects
    EditText amountInput;
    Spinner recipientSpinner;

    public TransferAvailableBudgetDialog(Context context, List<BudgetAccountBE> allBudgetAccounts, BudgetAccountBE self) {
        super(context);
        this.allBudgetAccounts = allBudgetAccounts;
        this.allBudgetAccounts.remove(self);
    }

    public abstract void onConfirm(float amount, BudgetAccountBE selectedAccount);

    @Override
    protected int getLayoutRes() {
        return R.layout.dialog_transfer_budget;
    }

    @Override
    protected int getTitleRes() {
        return R.string.label_available_budget_transfer;
    }

    @Override
    protected void bindViews(View view) {
        amountInput = view.findViewById(R.id.amount_input);
        recipientSpinner = view.findViewById(R.id.recipient_spinner);

        // Set up the spinner
        ArrayAdapter<BudgetAccountBE> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, allBudgetAccounts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        recipientSpinner.setAdapter(adapter);
    }

    @Override
    protected boolean onConfirmClicked() {
        String amountString = amountInput.getText().toString();
        if (amountString.isEmpty()) {
            toastLong(R.string.toast_error_empty_amount);
            return false;
        } else if (recipientSpinner.getSelectedItem() == null) {
            toastLong(R.string.toast_error_no_receiver_selected);
            return false;
        } else {
            try {
                float amount = Util.parseAmount(amountString);
                BudgetAccountBE selectedAccount = (BudgetAccountBE) recipientSpinner.getSelectedItem();

                onConfirm(amount, selectedAccount);
                return true;
            } catch (NumberFormatException e) {
                toastLong(R.string.toast_error_invalid_amount);
                return false;
            }
        }
    }
}
