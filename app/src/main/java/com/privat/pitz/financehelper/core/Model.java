package com.privat.pitz.financehelper.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;
import com.privat.pitz.financehelper.data.TxBE;
import com.privat.pitz.financehelper.data.RecurringTxBE;

public class Model {
    public static class Settings {
        public String defaultEntityName;
        public Map<String, EntityDefaults> entityDefaultsMap;
        // persisted SAF tree Uri (as a string) of the folder chosen for the folder-based savefile sync feature; null if none chosen yet
        public String syncFolderUri;

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

    /**
     * What the save-file and settings parsers had to default or discard since a caller last
     * drained this.
     *
     * <p>It accumulates rather than being replaced, because app startup parses the settings file
     * and then a save file, and the UI surfaces both together afterwards - replacing would let
     * the first one vanish, which is the exact failure mode this whole mechanism exists to stop.
     */
    private ParseReport loadReport = new ParseReport();

    public void addLoadReport(ParseReport report) {
        loadReport.addAll(report);
    }

    /** Returns everything collected since the last call, and clears the buffer. */
    public ParseReport takeLoadReport() {
        ParseReport drained = loadReport;
        loadReport = new ParseReport();
        return drained;
    }

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

    public List<AccountBE> getAllAccounts() {
        List<AccountBE> result = new ArrayList<>(asset_accounts);
        result.addAll(getAllBudgetAccounts());
        return result;
    }

    /**
     * True if this exact account object is still part of the model.
     *
     * <p>Compared by identity, not by name, and that is the whole point. Every load replaces every
     * account object, so a reference held from before a load can name a real account while being
     * a different object from the one the model now holds. Calling {@code addTx} on such an orphan
     * mutates something the serialiser never visits: the transaction is silently booked on one
     * side of the transfer only.
     */
    public boolean containsAccount(AccountBE account) {
        if (account == null)
            return false;
        for (AccountBE candidate : getAllAccounts()) {
            if (candidate == account)
                return true;
        }
        return false;
    }

    /**
     * True if a transaction can be booked on both sides right now: two different accounts, both
     * still live in this model.
     */
    public boolean hasLiveTxSelection() {
        return currentSender != currentReceiver
                && containsAccount(currentSender)
                && containsAccount(currentReceiver);
    }

    /**
     * Re-points a selection at the equivalent account in the current model, or clears it.
     *
     * <p>Called after anything that rebuilds the account lists. Returns the same object when it is
     * still live, the same-named account when the object was replaced by a load, and null when the
     * account is gone entirely - never a stale reference.
     */
    public AccountBE reattachSelection(AccountBE previous) {
        if (previous == null || containsAccount(previous))
            return previous;
        return getAccountByName(previous.getName());
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

    public float sumLoadedPeriodExpenses() {
        String period = currentFileName.substring(0, 7);
        float sum = 0.0f;
        List<BudgetAccountBE> allBudgets = getAllBudgetAccounts();
        for (BudgetAccountBE acc : allBudgets) {
            sum += acc.getSum(period);
        }
        return sum;
    }

    public float sumAllAssets() {
        float sum = 0.0f;
        for (AccountBE a: asset_accounts) {
            sum += a.getSum();
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
