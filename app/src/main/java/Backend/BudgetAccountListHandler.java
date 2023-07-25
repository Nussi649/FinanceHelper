package Backend;

import Logic.BudgetAccountBE;
import View.BudgetAccountTableRow;

public interface BudgetAccountListHandler {
    void showAccountDetails(BudgetAccountBE referenceAccount);
    void addBudgetViewToBackend(BudgetAccountTableRow newItem);
    boolean removeBudgetViewFromBackend(BudgetAccountTableRow removeItem);
}
