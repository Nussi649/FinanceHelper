package com.privat.pitz.financehelper.ui;

import com.privat.pitz.financehelper.data.BudgetAccountBE;
import com.privat.pitz.financehelper.ui.BudgetAccountTableRow;

public interface BudgetAccountListHandler {
    void showAccountDetails(BudgetAccountBE referenceAccount);
    void addBudgetViewToBackend(BudgetAccountTableRow newItem);
    boolean removeBudgetViewFromBackend(BudgetAccountTableRow removeItem);
}
