package View;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.privat.pitz.financehelper.AbstractActivity;
import com.privat.pitz.financehelper.R;

import java.util.ArrayList;
import java.util.List;

import Backend.BudgetAccountListHandler;
import Backend.Util;
import Logic.BudgetAccountBE;

@SuppressLint("ViewConstructor")
public class BudgetAccountTableRow extends TableRow {

    private BudgetAccountBE refAcc;
    private AbstractActivity parentActivity;
    private BudgetAccountListHandler budgetListener;
    private boolean isLast;
    private final List<BudgetAccountTableRow> children = new ArrayList<>();
    private int hierarchyLevel;
    private boolean isExtended;
    private Button toggleButton;
    private TextView nameLabel;

    private TextView valueLabel;
    private TextView currentPercentageLabel;
    private TextView yearlyBudgetLabel;

    // region Constructors
    public BudgetAccountTableRow(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    public BudgetAccountTableRow(AbstractActivity parentActivity, BudgetAccountListHandler handler, BudgetAccountBE accountObject) {
        this(parentActivity, handler, accountObject, false, 0);
    }

    public BudgetAccountTableRow(AbstractActivity parentActivity, BudgetAccountListHandler handler, BudgetAccountBE accountObject, boolean isLast) {
        this(parentActivity, handler, accountObject, isLast, 0);
    }

    public BudgetAccountTableRow(AbstractActivity parentActivity, BudgetAccountListHandler handler, BudgetAccountBE accountObject, boolean isLast, int level) {
        super(parentActivity);
        this.refAcc = accountObject;
        this.parentActivity = parentActivity;
        this.budgetListener = handler;
        this.hierarchyLevel = level;
        this.isExtended = true;
        this.isLast = isLast;
        handler.addBudgetViewToBackend(this);
        reloadChildren();
    }
    // endregion

    // region sets / gets
    public void initViews() {
        nameLabel = findViewById(R.id.item_name_label);
        toggleButton = findViewById(R.id.toggle_button);
        valueLabel = findViewById(R.id.item_value_label);
        currentPercentageLabel = findViewById(R.id.item_current_percentage);
        yearlyBudgetLabel = findViewById(R.id.item_yearly_budget);
    }
    public void init(AbstractActivity parentActivity, BudgetAccountListHandler handler, BudgetAccountBE accountObject, boolean isLast, int level) {
        refAcc = accountObject;
        this.parentActivity = parentActivity;
        budgetListener = handler;
        hierarchyLevel = level;
        isExtended = true;
        this.isLast = isLast;
        handler.addBudgetViewToBackend(this);
        reloadChildren();
        initViews();
    }

    public void init(AbstractActivity parentActivity, BudgetAccountListHandler handler, BudgetAccountBE accountObject, boolean isLast) {
        init(parentActivity, handler, accountObject, isLast, 0);
    }

    public List<BudgetAccountTableRow> getChildren() {
        return this.children;
    }

    public BudgetAccountBE getReferenceAccount() {
        return refAcc;
    }

    public void addChild(BudgetAccountTableRow newChild) {
        this.children.add(newChild);
    }

    public void addChildren(List<BudgetAccountTableRow> newChildren) {
        this.children.addAll(newChildren);
    }

    public void clearChildren() {
        for (BudgetAccountTableRow child : children) {
            // safely remove children from budgetViews in parentActivity
            try {
                assert budgetListener.removeBudgetViewFromBackend(child);
            } catch (AssertionError e) {
                Log.println(Log.INFO, "budget_overview",
                        String.format("A parent budget requested removal of a self proclaimed child " +
                                "budget, which was not registered with the activity! %s", e));
            }
            children.remove(child);
        }
    }

    public void setIsLast(boolean newIsLast) {
        this.isLast = newIsLast;
    }
    // endregion

    // region UI related including refresh
    // loads UI elements of this object, children need to be handled separately
    public void populateUI() {
        // setup toggle button on click listener
        toggleButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleExtension();
            }
        });

        // setup open details click listener
        this.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                budgetListener.showAccountDetails(refAcc);
            }
        });
        updateUI();
    }

    public void toggleExtension() {
        if (children.isEmpty()) return;
        isExtended = !isExtended;
        updateUItoExtState();
        for (BudgetAccountTableRow child : children) {
            child.setVisibility(isExtended ? VISIBLE : GONE);
        }
    }

    // PARAMS: inclChildren (boolean) true to propagate reduce command through to children
    public void reduce(boolean inclChildren) {
        isExtended = false;
        if (inclChildren)
            for (BudgetAccountTableRow child : children)
                child.reduce(true);
        updateUItoExtState();
    }

    public void reduce(boolean inclChildren, boolean setVisibility) {
        reduce(inclChildren);
        if (setVisibility)
            for (BudgetAccountTableRow child : children)
                child.setVisibility(GONE);
    }

    // PARAMS: inclChildren (boolean) true to propagate reduce command through to children
    public void expand(boolean inclChildren) {
        isExtended = true;
        if (inclChildren)
            for (BudgetAccountTableRow child : children)
                child.reduce(true);
        updateUItoExtState();
    }

    public void expand(boolean inclChildren, boolean setVisibility) {
        expand(inclChildren);
        if (setVisibility)
            for (BudgetAccountTableRow child : children)
                child.setVisibility(VISIBLE);
    }

    private void updateUItoExtState() {
        parentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                toggleButton.setText(parentActivity.getString(
                        isExtended ? R.string.sign_contract : R.string.sign_expand));
                updateUINumbers();
            }
        });
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (!children.isEmpty()) {
            if (!isExtended && visibility == VISIBLE)
                return;
            for (BudgetAccountTableRow child : children) {
                child.setVisibility(visibility);
            }
        }
    }

    public void updateUI() {
        parentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                nameLabel.setText(refAcc.getName());
                updateUINumbers();

                // adjust visibility of expand button
                int childCount = refAcc.getDirectSubBudgets().size();
                toggleButton.setActivated(childCount > 0);
                toggleButton.setVisibility(childCount > 0 ? VISIBLE : GONE);

                // adjust length of vertical line depending on if this is the last TableRow
                View lineView = findViewById(R.id.vertical_line);
                lineView.post(new Runnable() {
                    @Override
                    public void run() {
                        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) lineView.getLayoutParams();
                        params.height = isLast ? getHeight() / 2 : getHeight();
                        lineView.setLayoutParams(params);
                    }
                });
                View indentView = findViewById(R.id.indent_view);
                ViewGroup.LayoutParams params = indentView.getLayoutParams();
                params.width = (int) (22 * hierarchyLevel * getResources().getDisplayMetrics().density);
                indentView.setLayoutParams(params);

                // Set background color based on hierarchy level
                if (hierarchyLevel > 0) {
                    if (hierarchyLevel > 1)
                        setBackgroundColor(ContextCompat.getColor(getContext(), R.color.colorSubtleDistinction2));  // Light grey color
                    else
                        setBackgroundColor(ContextCompat.getColor(getContext(), R.color.colorSubtleDistinction));  // Lighter grey color
                } else {
                    setBackgroundColor(ContextCompat.getColor(getContext(), R.color.colorPrimaryLight));  // Primary Light color
                }
            }
        });
    }

    // loads View object for all direct descendants on Backend. Doesn't load them to UI
    public void reloadChildren() {
        // clear children list. If later none get created, then there shouldn't be any
        clearChildren();
        // check if budgetAccount has sub budgets
        List<BudgetAccountBE> subBudgets = refAcc.getDirectSubBudgets();
        int childCount = subBudgets.size();
        // only act, if referenceAccount has sub budgets
        if (childCount > 0) {
            int newLevel = hierarchyLevel + 1;
            // differentiate isLast for children
            for (int index = 0; index < childCount; index++) {
                BudgetAccountTableRow newRow = (BudgetAccountTableRow) LayoutInflater.from(parentActivity).inflate(R.layout.table_row_budget_account_overview, null);
                children.add(newRow);
                newRow.init(parentActivity, budgetListener, subBudgets.get(index), (index == childCount -1), newLevel);
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private void updateUINumbers() {
        float current_sum = isExtended ? refAcc.getSum() : refAcc.getTotalSum();
        float current_budget = isExtended ? refAcc.indivCurrentBudget : refAcc.getTotalCurrentBudget();
        float current_percentage = current_sum / current_budget;
        float yearly_budget = isExtended ? refAcc.indivYearlyBudget : refAcc.getTotalYearlyBudget();
        String currentBudgetString = Util.formatToFixedLength(Util.formatLargeFloatShort(current_budget),5);
        String currentSumString = String.format("%s / %s",
                Util.formatLargeFloatShort(current_sum),
                currentBudgetString);
        String currentPercentageString = String.format("%.0f%%",
                (current_percentage) * 100);
        String yearly_budget_string = Util.formatLargeFloatShort(yearly_budget);
        valueLabel.setText(currentSumString);
        currentPercentageLabel.setText(currentPercentageString);
        yearlyBudgetLabel.setText(yearly_budget_string);
        char percentageEval = Util.evaluatePercentage(current_percentage);
        if (percentageEval == '+')
            currentPercentageLabel.setBackground(Util.createBackground(ContextCompat.getColor(getContext(), R.color.colorNegative)));
        if (percentageEval == '-')
            currentPercentageLabel.setBackground(Util.createBackground(ContextCompat.getColor(getContext(), R.color.colorPositive)));
        if (percentageEval == 'O')
            currentPercentageLabel.setBackground(Util.createBackground(ContextCompat.getColor(getContext(), R.color.colorNeutral)));
    }
    // endregion
}
