package View.Dialogs;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.privat.pitz.financehelper.R;

public abstract class CreateBudgetAccountDialog {
    private final Context context;
    private final boolean isProjectBudget;

    // View objects
    EditText subBudgetNameInput;
    EditText yearlyBudgetInput;
    EditText currentMonthBudgetInput;

    public CreateBudgetAccountDialog(Context context, boolean projectBudget) {
        this.context = context;
        this.isProjectBudget = projectBudget;
    }

    public CreateBudgetAccountDialog(Context context) {
        this(context, false);
    }

    public abstract void onConfirm(String subBudgetName, float currentMonthBudget, float yearlyBudget);

    @SuppressLint("InflateParams")
    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);

        View view = inflater.inflate(R.layout.dialog_new_budget_account, null);
        subBudgetNameInput = view.findViewById(R.id.budget_name_input);
        yearlyBudgetInput = view.findViewById(R.id.yearly_budget_input);
        currentMonthBudgetInput = view.findViewById(R.id.current_month_budget_input);
        if (isProjectBudget) {
            view.findViewById(R.id.euro_sign).setVisibility(View.GONE);
            currentMonthBudgetInput.setVisibility(View.GONE);
            subBudgetNameInput.setHint(R.string.label_project_name);
            yearlyBudgetInput.setHint(R.string.label_project_budget);
        }

        builder.setTitle(isProjectBudget ? R.string.label_project_budget_new : R.string.label_budget_account_new);

        builder.setView(view)
                .setPositiveButton(context.getString(R.string.confirm), null)
                .setNegativeButton(context.getString(R.string.cancel), null);

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialogInterface -> {
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(view1 -> {
                String subBudgetNameString = subBudgetNameInput.getText().toString();
                String yearlyBudgetString = yearlyBudgetInput.getText().toString();
                String currentMonthBudgetString = currentMonthBudgetInput.getText().toString();

                if (subBudgetNameString.isEmpty())
                    Toast.makeText(context, context.getString(R.string.toast_error_empty_name), Toast.LENGTH_LONG).show();
                else if (yearlyBudgetString.isEmpty())
                    Toast.makeText(context, context.getString(R.string.toast_error_empty_amount), Toast.LENGTH_LONG).show();
                else {
                    float yearlyBudget = (float) Double.parseDouble(yearlyBudgetString);
                    float currentMonthBudget = currentMonthBudgetString.isEmpty() ? yearlyBudget / 12 : (float) Double.parseDouble(currentMonthBudgetString);

                    onConfirm(subBudgetNameString, currentMonthBudget, yearlyBudget);
                    dialog.dismiss();
                }
            });
        });

        dialog.show();
    }
}
