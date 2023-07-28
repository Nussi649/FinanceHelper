package View.Dialogs;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import com.privat.pitz.financehelper.R;

import java.util.ArrayList;
import java.util.List;

import Logic.BudgetAccountBE;

public abstract class TransferSubBudgetDialog {
    private final Context context;
    private final List<BudgetAccountBE> directSubBudgets;
    private final List<BudgetAccountBE> allBudgetAccounts;

    // View objects
    Spinner subBudgetSpinner;
    Spinner targetSpinner;

    public TransferSubBudgetDialog(Context context, BudgetAccountBE currentAccount, List<BudgetAccountBE> allBudgetAccounts) {
        this.context = context;
        this.directSubBudgets = currentAccount.getDirectSubBudgets();
        this.allBudgetAccounts = allBudgetAccounts;
        this.allBudgetAccounts.remove(currentAccount);
    }

    public abstract void onConfirm(BudgetAccountBE subBudget, BudgetAccountBE target);

    @SuppressLint("InflateParams")
    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);

        View view = inflater.inflate(R.layout.dialog_transfer_sub_budget, null);
        subBudgetSpinner = view.findViewById(R.id.direct_sub_budgets_spinner);
        targetSpinner = view.findViewById(R.id.target_account_spinner);

        // Populate the direct sub budgets spinner
        ArrayAdapter<BudgetAccountBE> subBudgetAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, directSubBudgets);
        subBudgetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        subBudgetSpinner.setAdapter(subBudgetAdapter);

        // Initially, the second spinner is disabled
        targetSpinner.setEnabled(false);

        // When an item is selected in the first spinner, enable the second spinner and populate it
        subBudgetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // First calculate list of available targets (exclude the selected account)
                BudgetAccountBE selection = (BudgetAccountBE) subBudgetSpinner.getSelectedItem();
                List<BudgetAccountBE> availableTargets = new ArrayList<>(allBudgetAccounts);
                availableTargets.remove(selection);
                availableTargets.removeAll(selection.getAllSubBudgets());
                // Populate the all budget accounts spinner
                ArrayAdapter<BudgetAccountBE> targetAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, availableTargets);
                targetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                targetSpinner.setAdapter(targetAdapter);

                // Enable the second spinner
                targetSpinner.setEnabled(true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // If nothing is selected, clear the second spinner and disable it
                targetSpinner.setAdapter(null);
                targetSpinner.setEnabled(false);
            }
        });
        builder.setTitle(R.string.label_sub_budget_transfer);

        // set positive button with null onClickListener for now
        builder.setView(view)
                .setPositiveButton(context.getString(R.string.confirm), null)
                .setNegativeButton(context.getString(R.string.cancel), null);

        // set the dialog's view
        builder.setView(view);

        // create dialog
        AlertDialog dialog = builder.create();

        // set onShowListener to add an confirm onClickListener that doesn't by default dismiss the dialog
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                Button button = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Object selectedDirectSubBudgetObj = subBudgetSpinner.getSelectedItem();
                        Object selectedTargetAccountObj = targetSpinner.getSelectedItem();

                        if (selectedDirectSubBudgetObj == null || selectedTargetAccountObj == null) {
                            Toast.makeText(context, context.getString(R.string.toast_error_select_both_accounts), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        BudgetAccountBE selectedDirectSubBudget = (BudgetAccountBE) selectedDirectSubBudgetObj;
                        BudgetAccountBE selectedTargetAccount = (BudgetAccountBE) selectedTargetAccountObj;

                        onConfirm(selectedDirectSubBudget, selectedTargetAccount);
                        dialog.dismiss(); // manually dismiss dialog
                    }
                });
            }
        });

        // show it
        dialog.show();
    }
}
