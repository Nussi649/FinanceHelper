package Backend;

import java.util.ArrayList;
import java.util.List;

import Logic.AccountBE;
import Logic.BudgetAccountBE;
import Logic.TxBE;
import Logic.RecurringTxBE;

public class Model {
    public static class Settings {
        public String defaultEntityName;
        public String defaultSender;
        public String defaultReceiver;
        public Settings() {

        }
        public Settings(String defaultEntityName) {
            this.defaultEntityName = defaultEntityName;
        }
    }
    public Settings settings = new Settings();
    public List<AccountBE> asset_accounts;
    public List<BudgetAccountBE> budget_accounts;
    public List<RecurringTxBE> recurringTx;
    public List<TxBE> currentIncome;
    public AccountBE currentSender;
    public AccountBE currentReceiver;
    public AccountBE currentInspectedAccount;
    public String nameFinancialEntity;
    public String currentFileName;
    public Util.FileNameParts currentFileAttributes;

    public List<BudgetAccountBE> getAllBudgetAccounts() {
        List<BudgetAccountBE> budgetAccounts = new ArrayList<>();
        for (BudgetAccountBE acc : budget_accounts) {
            budgetAccounts.add(acc);
            budgetAccounts.addAll(acc.getAllSubBudgets());
        }
        return budgetAccounts;
    }

    public AccountBE getAccountByName(String name) {
        AccountBE re = getAssetAccountByName(name);
        if (re == null) {
            re = getBudgetAccountByName(name);
        }
        return re;
    }

    public AccountBE getAssetAccountByName(String name) {
        for (AccountBE a : asset_accounts) {
            if (a.getName().equals(name))
                return a;
        }
        return null;
    }

    public BudgetAccountBE getBudgetAccountByName(String name) {
        List<BudgetAccountBE> allBudgetAccounts = getAllBudgetAccounts();
        for (BudgetAccountBE a : allBudgetAccounts) {
            if (a.getName().equals(name))
                return a;
        }
        return null;
    }

    public BudgetAccountBE getRootBudgetAccountByName(String name) {
        for (BudgetAccountBE a : budget_accounts) {
            if (a.getName().equals(name))
                return a;
        }
        return null;
    }

    public float sumAllExpenses() {
        float sum = 0.0f;
        List<BudgetAccountBE> allBudgets = getAllBudgetAccounts();
        for (BudgetAccountBE acc : allBudgets) {
            sum += acc.getSum();
        }
        return sum;
    }

    public float sumAllAssets() {
        float sum = 0.0f;
        for (AccountBE a: asset_accounts) {
            sum += a.getSumWithoutClosing();
        }
        return sum;
    }

    public float sumAllIncome() {
        float sum = 0.0f;
        for (TxBE e: currentIncome) {
            sum += e.getAmount();
        }
        return sum;
    }
}
