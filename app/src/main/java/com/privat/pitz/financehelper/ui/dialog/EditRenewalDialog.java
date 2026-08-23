package com.privat.pitz.financehelper.ui.dialog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.widget.EditText;

import com.privat.pitz.financehelper.R;

import com.privat.pitz.financehelper.data.BudgetAccountBE;

@SuppressLint("DefaultLocale")
public abstract class EditRenewalDialog extends BaseInputDialog {
    private final BudgetAccountBE account;

    // View objects
    EditText etRenewalPeriod;
    EditText etNextRenewal;

    public EditRenewalDialog(Context context, BudgetAccountBE account) {
        super(context);
        this.account = account;
    }

    public abstract void onConfirm(int renewalPeriod, String nextRenewal);

    @Override
    protected int getLayoutRes() {
        return R.layout.dialog_edit_renewal;
    }

    @Override
    protected int getTitleRes() {
        return R.string.label_account_renewal;
    }

    @Override
    protected void bindViews(View view) {
        etRenewalPeriod = view.findViewById(R.id.et_renewal_period);
        etNextRenewal = view.findViewById(R.id.et_next_renewal);

        // Pre-fill the fields with the current values
        etRenewalPeriod.setText(String.valueOf(account.getRenewalPeriod()));
        etNextRenewal.setText(account.getNextRenewal());
    }

    @Override
    protected boolean onConfirmClicked() {
        String renewalPeriodString = etRenewalPeriod.getText().toString();
        String nextRenewal = etNextRenewal.getText().toString();

        if (renewalPeriodString.isEmpty()) {
            toastLong(R.string.toast_error_empty_renewal_period);
            return false;
        } else if (nextRenewal.isEmpty()) {
            toastLong(R.string.toast_error_empty_next_renewal);
            return false;
        } else {
            try {
                int renewalPeriod = Integer.parseInt(renewalPeriodString);
                onConfirm(renewalPeriod, nextRenewal);
                return true;
            } catch (NumberFormatException e) {
                toastLong(R.string.toast_error_invalid_renewal_period);
                return false;
            }
        }
    }
}
