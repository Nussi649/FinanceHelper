package com.privat.pitz.financehelper;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import Backend.BudgetAccountListHandler;
import Backend.Const;
import Backend.Util;
import Logic.BudgetAccountBE;
import View.BudgetAccountTableRow;

public class BudgetsActivity extends AbstractActivity implements BudgetAccountListHandler {
    List<BudgetAccountTableRow> budgetViews = new ArrayList<>();
    TableLayout container;
    TextView expandCollapse;
    TextView totalValue;
    TextView totalPercentage;
    TextView totalYearly;
    float totalSpent;
    float totalAvailableBudget;
    float totalYearlyBudget;
    float totalAllottedBudget;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!passedOnCreate) {
            reloadUI();
        }
    }

    @Override
    protected void workingThread() {
        loadBudgets();
    }

    @Override
    protected void endWorkingThread() {
        setContentView(R.layout.activity_budget_overview);
        Context context = this;
        container = findViewById(R.id.budget_overview_table_layout);
        totalValue = findViewById(R.id.total_current_value);
        totalPercentage = findViewById(R.id.total_current_percentage);
        totalYearly = findViewById(R.id.total_yearly_budget);
        expandCollapse = findViewById(R.id.label_expand_contract);
        expandCollapse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String currentState = expandCollapse.getText().toString();
                // initiate icon with value for collapse to please compiler
                Drawable icon = ContextCompat.getDrawable(context, R.drawable.ic_collapse_24);
                if (currentState.equals(getString(R.string.label_expand_all))) {
                    expandAll();
                    expandCollapse.setText(R.string.label_collapse_all);
                } else if (currentState.equals(getString(R.string.label_collapse_all))) {
                    collapseAll();
                    expandCollapse.setText(R.string.label_expand_all);
                    // overwrite with value for expand if necessary
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_expand_24);
                } else {
                    Log.println(Log.INFO, "budgets_overview", "Unexpected Text of Expand/Reduce button! Did not perform any action.");
                }
                expandCollapse.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
            }
        });
        populateUI();
        setCustomTitle();
    }

    @Override
    protected void setCustomTitle() {
        super.setCustomTitle();
        String titleDetails = getString(R.string.label_budgets) + String.format("  %sx",
                Util.formatLargeFloatShort(model.sumAllExpenses())).replace("x", getString(R.string.label_currency));
        setCustomTitleDetails(titleDetails);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_account_overview, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.item_new_account) {
            openNewBudgetDialog();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRefresh() {
        for (BudgetAccountTableRow row : budgetViews)
            row.updateUI();
        updateUISums();
    }

    public void addBudgetViewToBackend(BudgetAccountTableRow newItem) {
        budgetViews.add(newItem);
    }

    public boolean removeBudgetViewFromBackend(BudgetAccountTableRow removeItem) {
        return budgetViews.remove(removeItem);
    }

    private void reloadUI() {
        budgetViews = new ArrayList<>();
        loadBudgets();
        // This will ensure your UI is up-to-date with the current state of budgetViews
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                clearTable();
                populateUI();
            }
        });
        setCustomTitle();
    }

    private void loadBudgets() {
        // get budget accounts and prepare BudgetAccountTableRow objects
        List<BudgetAccountBE> firstLevelBudgets = model.budget_accounts;
        int count = firstLevelBudgets.size();
        totalSpent = 0.0f;
        totalAvailableBudget = 0.0f;
        totalYearlyBudget = 0.0f;
        totalAllottedBudget = 0.0f;
        for (int index = 0; index < count; index++) {
            BudgetAccountBE currentAccount = firstLevelBudgets.get(index);
            BudgetAccountTableRow newRow = BudgetAccountTableRow.getInstance(this);
            newRow.init(this, currentAccount, index == count - 1);
            totalSpent += currentAccount.getTotalSum();
            totalAvailableBudget += currentAccount.getTotalAvailableBudget();
            totalYearlyBudget += currentAccount.getTotalYearlyBudget();
            totalAllottedBudget += currentAccount.getMeanAllottedTotalBudget();
        }
    }

    private void populateUI() {
        // add the up-to-date BudgetAccountTableRow objects
        for (BudgetAccountTableRow view : budgetViews) {
            container.addView(view);
            view.populateUI();
        }
        updateUISums();
    }

    private void clearTable() {
        // remove any BudgetAccountTableRow objects, leaving other views (like the header) intact
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof BudgetAccountTableRow) {
                container.removeViewAt(i);
                i--; // adjust the index to account for the removal
            }
        }
    }

    public void showAccountDetails(BudgetAccountBE target) {
        model.currentInspectedAccount = target;
        startActivity(BudgetAccountDetailsActivity.class);
    }

    public void expandAll() {
        for (BudgetAccountTableRow row: budgetViews) {
            row.expand(true, true);
        }
    }

    public void collapseAll() {
        for (BudgetAccountTableRow row: budgetViews) {
            row.reduce(true, true);
        }
    }

    // region Dialogs
    public void openNewBudgetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_new_budget_account, null);

        EditText budgetNameInput = view.findViewById(R.id.budget_name_input);
        EditText yearlyBudgetInput = view.findViewById(R.id.yearly_budget_input);
        EditText currentMonthBudgetInput = view.findViewById(R.id.current_month_budget_input);

        builder.setView(view)
                .setPositiveButton("Confirm", (dialog, id) -> {
                    String budgetNameString = budgetNameInput.getText().toString();
                    String yearlyBudgetString = yearlyBudgetInput.getText().toString();
                    String currentMonthBudgetString = currentMonthBudgetInput.getText().toString();

                    if (!budgetNameString.isEmpty() && !yearlyBudgetString.isEmpty()) {
                        float yearlyBudget = (float) Double.parseDouble(yearlyBudgetString);
                        float currentMonthBudget = currentMonthBudgetString.isEmpty() ? yearlyBudget / 12 : (float) Double.parseDouble(currentMonthBudgetString);

                        createBudgetAccount(budgetNameString, currentMonthBudget, yearlyBudget);
                        dialog.dismiss();
                    } else {
                        showToastLong(R.string.toast_error_empty_amount);
                    }
                })
                .setNegativeButton("Cancel", null);

        builder.create().show();
    }


    private void createBudgetAccount(String name, float currentBudget, float yearlyBudget) {
        try {
            BudgetAccountBE newAccount = controller.createRootBudget(name, currentBudget, yearlyBudget);
            if (newAccount == null)
                showToastLong(R.string.toast_error_account_name_taken);
            else {
                int count = budgetViews.size();
                if (count > 0) {
                    // get previously last subBudget and set it to isLast = false
                    BudgetAccountTableRow last = budgetViews.get(count - 1);
                    last.setIsLast(false);
                    last.updateUI();
                }
                // create new BudgetAccountTableRow object and add it to the rootLayout
                BudgetAccountTableRow newRow = BudgetAccountTableRow.getInstance(this);
                newRow.init(this, newAccount, true);
                newRow.populateUI();
                container.addView(newRow);
                totalAvailableBudget += newAccount.indivAvailableBudget;
                totalYearlyBudget += newAccount.indivYearlyBudget;
                updateUISums();
            }
        } catch (JSONException | IOException e) {
            if (e instanceof JSONException)
                showToastLong(R.string.toast_error_JSONError);
            else
                showToastLong(R.string.toast_error_IOError);
        }
    }
    // endregion

    // set values of total sum text views
    @SuppressLint("DefaultLocale")
    private void updateUISums() {
        float current_percentage = Util.calculateAdvancedPercentage(totalAvailableBudget, totalSpent, totalAllottedBudget);

        String currentBudgetString = Util.formatToFixedLength(Util.formatLargeFloatShort(totalAvailableBudget),5);
        String currentSumString = String.format("%s / %s",
                Util.formatLargeFloatShort(totalSpent),
                currentBudgetString);
        String currentPercentageString = String.format("%.0f%%",
                (current_percentage) * 100);
        String yearly_budget_string = Util.formatLargeFloatShort(totalYearlyBudget);
        totalValue.setText(currentSumString);
        totalPercentage.setText(currentPercentageString);
        totalYearly.setText(yearly_budget_string);

        // color percentage label
        totalPercentage.setBackground(Util.evaluatePercentageBG(current_percentage, this));
    }
}
