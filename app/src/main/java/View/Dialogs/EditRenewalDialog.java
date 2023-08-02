package View.Dialogs;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.privat.pitz.financehelper.R;

import Logic.BudgetAccountBE;

@SuppressLint("DefaultLocale")
public abstract class EditRenewalDialog {
    private final Context context;
    private final BudgetAccountBE account;

    // View objects
    EditText etRenewalPeriod;
    EditText etNextRenewal;

    public EditRenewalDialog(Context context, BudgetAccountBE account) {
        this.context = context;
        this.account = account;
    }

    public abstract void onConfirm(int renewalPeriod, String nextRenewal);

    @SuppressLint("InflateParams")
    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);

        View view = inflater.inflate(R.layout.dialog_edit_renewal, null);
        etRenewalPeriod = view.findViewById(R.id.et_renewal_period);
        etNextRenewal = view.findViewById(R.id.et_next_renewal);

        // Pre-fill the fields with the current values
        etRenewalPeriod.setText(String.valueOf(account.getRenewalPeriod()));
        etNextRenewal.setText(account.getNextRenewal());

        builder.setView(view)
                .setPositiveButton(context.getString(R.string.confirm), null)
                .setNegativeButton(context.getString(R.string.cancel), null)
                .setTitle(R.string.label_account_renewal);

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view1 -> {
            String renewalPeriodString = etRenewalPeriod.getText().toString();
            String nextRenewal = etNextRenewal.getText().toString();

            if (renewalPeriodString.isEmpty())
                Toast.makeText(context, context.getString(R.string.toast_error_empty_renewal_period), Toast.LENGTH_LONG).show();
            else if (nextRenewal.isEmpty())
                Toast.makeText(context, context.getString(R.string.toast_error_empty_next_renewal), Toast.LENGTH_LONG).show();
            else {
                int renewalPeriod = Integer.parseInt(renewalPeriodString);
                onConfirm(renewalPeriod, nextRenewal);
                dialog.dismiss();
            }
        }));

        dialog.show();
    }
}