package View.Dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.privat.pitz.financehelper.R;

import java.util.List;

public abstract class TransactionRedirectionDialog {
    private final Context context;
    private final String desc;
    private final float amount;
    private final List<String> allAccounts;

    public TransactionRedirectionDialog(Context context, String desc, float amount, List<String> allAccounts) {
        this.context = context;
        this.desc = desc;
        this.amount = amount;
        this.allAccounts = allAccounts;
    }

    public abstract void onConfirm(String selectedAccountName);

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);

        View view = inflater.inflate(R.layout.dialog_tx_redirection_input, null);
        TextView descriptionTextView = view.findViewById(R.id.descriptionTextView);
        TextView amountTextView = view.findViewById(R.id.amountTextView);
        Spinner accountSpinner = view.findViewById(R.id.accountSpinner);

        descriptionTextView.setText(desc);
        amountTextView.setText(String.valueOf(amount));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, allAccounts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        accountSpinner.setAdapter(adapter);

        builder.setView(view)
                .setNegativeButton(context.getString(R.string.cancel), null)
                .setPositiveButton(context.getString(R.string.confirm), (dialog, id) -> {
                    String selectedAccountName = accountSpinner.getSelectedItem().toString();
                    onConfirm(selectedAccountName);
                });
        builder.setTitle(R.string.label_transaction_redirection_select_target);

        builder.create().show();
    }
}