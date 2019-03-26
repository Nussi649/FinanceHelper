package Backend;

import java.util.List;

import Logic.AccountBE;
import Logic.RecurringOrderBE;

public class Model {
    public List<AccountBE> payAccounts;
    public List<AccountBE> investAccounts;
    public List<RecurringOrderBE> recurringOrders;
    public AccountBE currentPayAcc;
    public AccountBE currentInvestAcc;
    public AccountBE transferRecipientAcc;
    public AccountBE currentInspectedAccount;
    public String currentFileName;
    public String nextFileName;
    public boolean nextFileHidden;
}
