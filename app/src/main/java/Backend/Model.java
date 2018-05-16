package Backend;

import java.util.List;

import Logic.AccountBE;

public class Model {
    public List<AccountBE> payAccounts;
    public List<AccountBE> investAccounts;
    public AccountBE currentPayAcc;
    public AccountBE currentInvestAcc;
    public AccountBE transferRecipientAcc;
    public AccountBE currentInspectedAccount;
    public String nextFileName;
    public boolean nextFileHidden;
    public boolean needRefresh = false;
}
