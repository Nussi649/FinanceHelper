package com.privat.pitz.financehelper.ui;

import android.widget.TextView;

import com.privat.pitz.financehelper.core.Util;

import java.util.Locale;

/**
 * The "spent / available · percentage · yearly" triple that the budget overview, the budget
 * details screen and every row of the budget table all render identically.
 */
public final class BudgetFigures {

    private BudgetFigures() {}

    /**
     * Fills the three summary labels and returns the computed percentage, so the caller can
     * decide for itself how to colour the percentage label.
     */
    public static float render(TextView valueLabel,
                               TextView percentageLabel,
                               TextView yearlyLabel,
                               float sum,
                               float availableBudget,
                               float allottedBudget,
                               float yearlyBudget) {
        float percentage = Util.calculateAdvancedPercentage(availableBudget, sum, allottedBudget);

        String budgetString = Util.formatToFixedLength(Util.formatLargeFloatShort(availableBudget), 5);
        valueLabel.setText(String.format(Locale.getDefault(), "%s / %s",
                Util.formatLargeFloatShort(sum), budgetString));
        percentageLabel.setText(Util.formatPercentage(percentage));
        yearlyLabel.setText(Util.formatLargeFloatShort(yearlyBudget));

        return percentage;
    }
}
