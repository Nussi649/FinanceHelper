package View.Dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.privat.pitz.financehelper.R;

public abstract class AddIncomeDialog {
    private final Context context;

    public AddIncomeDialog(Context context) {
        this.context = context;
    }

    public abstract void onConfirm(float amount, String description);

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);

        View view = inflater.inflate(R.layout.dialog_add_funds, null);
        EditText addFundsAmount = view.findViewById(R.id.edit_amount);
        EditText descriptionText = view.findViewById(R.id.edit_new_description);

        builder.setView(view)
                .setTitle(context.getString(R.string.label_add_funds))
                .setNegativeButton(context.getString(R.string.cancel), null)
                .setPositiveButton(context.getString(R.string.confirm), null); // Set an empty listener, to be overridden later
        builder.setTitle(R.string.label_add_funds);

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String amountString = addFundsAmount.getText().toString().trim();
                        String description = descriptionText.getText().toString().trim();
                        if (amountString.isEmpty()) {
                            Toast.makeText(context, R.string.toast_error_empty_amount, Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (description.isEmpty()) {
                            Toast.makeText(context, R.string.toast_error_empty_description, Toast.LENGTH_LONG).show();
                            return;
                        }
                        try {
                            float amount = Float.parseFloat(amountString);
                            onConfirm(amount, description);
                            dialog.dismiss();
                        } catch (NumberFormatException e) {
                            Toast.makeText(context, R.string.toast_error_invalid_amount, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });

        dialog.show();
    }
}