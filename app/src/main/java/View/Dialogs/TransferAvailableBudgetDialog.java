package View.Dialogs;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.privat.pitz.financehelper.R;

import java.util.List;

import Logic.BudgetAccountBE;

public abstract class TransferAvailableBudgetDialog {
    private final Context context;
    private final List<BudgetAccountBE> allBudgetAccounts;

    // View objects
    EditText amountInput;
    Spinner recipientSpinner;

    public TransferAvailableBudgetDialog(Context context, List<BudgetAccountBE> allBudgetAccounts, BudgetAccountBE self) {
        this.context = context;
        this.allBudgetAccounts = allBudgetAccounts;
        this.allBudgetAccounts.remove(self);
    }

    public abstract void onConfirm(float amount, BudgetAccountBE selectedAccount);

    @SuppressLint("InflateParams")
    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);

        View view = inflater.inflate(R.layout.dialog_transfer_budget, null);
        amountInput = view.findViewById(R.id.amount_input);
        recipientSpinner = view.findViewById(R.id.recipient_spinner);

        // Set up the spinner
        ArrayAdapter<BudgetAccountBE> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, allBudgetAccounts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        recipientSpinner.setAdapter(adapter);
        builder.setTitle(R.string.label_available_budget_transfer);

        builder.setView(view)
                .setPositiveButton(context.getString(R.string.confirm), null)
                .setNegativeButton(context.getString(R.string.cancel), null);

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialogInterface -> {
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(view1 -> {
                String amountString = amountInput.getText().toString();
                if (amountString.isEmpty())
                    Toast.makeText(context, context.getString(R.string.toast_error_empty_amount), Toast.LENGTH_LONG).show();
                else if (recipientSpinner.getSelectedItem() == null)
                    Toast.makeText(context, context.getString(R.string.toast_error_no_receiver_selected), Toast.LENGTH_LONG).show();
                else {
                    float amount = (float) Double.parseDouble(amountString);
                    BudgetAccountBE selectedAccount = (BudgetAccountBE) recipientSpinner.getSelectedItem();

                    onConfirm(amount, selectedAccount);
                    dialog.dismiss();
                }
            });
        });

        dialog.show();
    }
}