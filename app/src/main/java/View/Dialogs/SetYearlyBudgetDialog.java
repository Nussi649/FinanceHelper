package View.Dialogs;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.privat.pitz.financehelper.R;

import Backend.Util;

public abstract class SetYearlyBudgetDialog {
    private final Context context;
    private final float currentBudget;

    // View objects
    EditText budgetInput;
    CheckBox adjustCheck;

    public SetYearlyBudgetDialog(Context context, float currentBudget) {
        this.context = context;
        this.currentBudget = currentBudget;
    }

    public abstract void onConfirm(float newBudget, boolean adjustAvailable);

    @SuppressLint("InflateParams")
    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);

        View view = inflater.inflate(R.layout.dialog_set_yearly_budget, null);
        budgetInput = view.findViewById(R.id.budget_input);
        adjustCheck = view.findViewById(R.id.apply_to_available_budget);
        budgetInput.setText(Util.formatFloatSave(currentBudget));

        builder.setView(view)
                .setPositiveButton(context.getString(R.string.confirm), null)
                .setNegativeButton(context.getString(R.string.cancel), null);

        builder.setTitle(R.string.label_yearly_budget_edit);

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialogInterface -> {
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(view1 -> {
                String budgetString = budgetInput.getText().toString();
                if (!budgetString.isEmpty()) {
                    float budget = (float) Double.parseDouble(budgetString);
                    onConfirm(budget, adjustCheck.isChecked());
                    dialog.dismiss();
                } else {
                    Toast.makeText(context, context.getString(R.string.toast_error_empty_amount), Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }
}