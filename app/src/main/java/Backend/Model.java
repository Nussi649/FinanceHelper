package Backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Logic.AccountBE;
import Logic.BudgetAccountBE;
import Logic.TxBE;
import Logic.RecurringTxBE;

public class Model {
    public static class Settings {
        public String defaultEntityName;
        public Map<String, EntityDefaults> entityDefaultsMap;

        public Settings() {
            entityDefaultsMap = new HashMap<>();
        }

        public Settings(String defaultEntityName) {
            this.defaultEntityName = defaultEntityName;
            entityDefaultsMap = new HashMap<>();
        }

        public void setEntityDefaults(String entityName, String defaultSender, String defaultReceiver) {
            EntityDefaults currentDefaults = entityDefaultsMap.get(entityName);
            if (currentDefaults != null) {
                // If there are already defaults for this entity, preserve any values that are not being explicitly overwritten
                if (defaultSender != null) {
                    currentDefaults.defaultSender = defaultSender;
                }
                if (defaultReceiver != null) {
                    currentDefaults.defaultReceiver = defaultReceiver;
                }
            } else {
                // If there are no defaults for this entity yet, create them
                entityDefaultsMap.put(entityName, new EntityDefaults(defaultSender, defaultReceiver));
            }
        }

        public EntityDefaults getEntityDefaults(String entityName) {
            return entityDefaultsMap.get(entityName);
        }
    }

    public static class EntityDefaults {
        public String defaultSender;
        public String defaultReceiver;

        public EntityDefaults(String defaultSender, String defaultReceiver) {
            this.defaultSender = defaultSender != null ? defaultSender : "";
            this.defaultReceiver = defaultReceiver != null ? defaultReceiver : "";
        }
    }
    public Settings settings = new Settings();
    public List<String> availableEntities = new ArrayList<>();
    public String currentEntity;
    public String currentFileName;
    public Util.FileNameParts currentFileAttributes;
    public List<AccountBE> asset_accounts = new ArrayList<>();
    public List<BudgetAccountBE> budget_accounts = new ArrayList<>();
    public List<RecurringTxBE> recurringTx = new ArrayList<>();
    public List<TxBE> currentIncome = new ArrayList<>();
    public AccountBE currentSender;
    public AccountBE currentReceiver;
    public AccountBE currentInspectedAccount;

    public EntityDefaults getCurrentDefaults() {
        return settings.getEntityDefaults(currentEntity);
    }

    public void setCurrentDefaultSender(String newDefaultSender) {
        settings.setEntityDefaults(currentEntity, newDefaultSender, null);
    }

    public void setCurrentDefaultReceiver(String newDefaultReceiver) {
        settings.setEntityDefaults(currentEntity, null, newDefaultReceiver);
    }

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
