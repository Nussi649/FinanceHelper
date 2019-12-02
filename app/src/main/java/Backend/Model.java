package Backend;

import java.util.List;

import Logic.AccountBE;
import Logic.EntryBE;
import Logic.RecurringOrderBE;

public class Model {
    public List<AccountBE> payAccounts;
    public List<AccountBE> investAccounts;
    public List<RecurringOrderBE> recurringOrders;
    public List<EntryBE> incomeList;
    public List<PastMonth> history;
    public AccountBE currentPayAcc;
    public AccountBE currentInvestAcc;
    public AccountBE transferRecipientAcc;
    public AccountBE currentInspectedAccount;
    public String currentFileName;

    public float sumAllExpenses() {
        float sum = 0.0f;
        for (AccountBE a : investAccounts) {
            sum += a.getSum();
        }
        return sum;
    }

    public float sumAllStocks() {
        float sum = 0.0f;
        for (AccountBE a: payAccounts) {
            sum += a.getSumRefreshed();
        }
        return sum;
    }

    public float sumAllIncome() {
        float sum = 0.0f;
        for (EntryBE e: incomeList) {
            sum += e.getAmount();
        }
        return sum;
    }
}
