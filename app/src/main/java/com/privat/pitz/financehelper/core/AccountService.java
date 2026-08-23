package com.privat.pitz.financehelper.core;

import android.util.Log;

import org.json.JSONException;

import java.io.IOException;
import java.security.InvalidParameterException;

import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;
import com.privat.pitz.financehelper.data.ProjectBudgetBE;

/**
 * Account-handling cluster extracted from {@link Controller}: creating, deleting and updating
 * asset/budget accounts, budget transfers, and account selection/reset/renewal. Shares its
 * {@code repo} and {@code model} instances with the owning Controller - it does not copy them.
 */
public class AccountService {
    private final SaveFileRepository repo;
    private final Model model;

    AccountService(SaveFileRepository repo, Model model) {
        this.repo = repo;
        this.model = model;
    }

    public void resetAccounts() {
        for (AccountBE account : model.asset_accounts) {
            account.reset();
        }
        for (BudgetAccountBE budgetAccount : model.budget_accounts) {
            budgetAccount.reset();
        }
    }

    public void renewAccounts() {
        for (AccountBE account : model.asset_accounts) {
            if (!account.getAutoRenew())
                continue;
            account.tryRenew();
        }
        for (BudgetAccountBE budgetAccount : model.budget_accounts) {
            budgetAccount.tryRenew();
        }
    }

    public void updateSelectedAccount(String selectionGroup, AccountBE newTarget) {
        if (selectionGroup.equals(Const.GROUP_SENDER)) {
            model.currentSender = newTarget;
            model.setCurrentDefaultSender(newTarget.toString());
        }
        else if (selectionGroup.equals(Const.GROUP_RECEIVER)) {
            model.currentReceiver = newTarget;
            model.setCurrentDefaultReceiver(newTarget.toString());
        }
        else
            Log.println(Log.INFO, "account_selection",
                    String.format("Unknown Selection Group (%s) called to set Account %s!", selectionGroup, newTarget));
    }

    public AccountBE getSelectedAccount(String selectionGroup) {
        if (selectionGroup.equals(Const.GROUP_SENDER))
            return model.currentSender;
        else if (selectionGroup.equals(Const.GROUP_RECEIVER))
            return model.currentReceiver;
        Log.println(Log.INFO, "account_selection",
                    String.format("Unknown Selection Group (%s) called for get Account!", selectionGroup));
        return null;
    }

    // region Account Handling
    public void sortAllTransactions() {
        for (AccountBE account : model.asset_accounts) {
            account.sortTxByDate();
        }
        for (BudgetAccountBE budgetAccount : model.budget_accounts) {
            budgetAccount.sortTxByDate();
            for (BudgetAccountBE subBudget : budgetAccount.getAllSubBudgets()) {
                subBudget.sortTxByDate();
            }
        }
    }

    public AccountBE createAssetAccount(String name) throws JSONException, IOException {
        // check if name is already in use
        AccountBE similarName = model.getAccountByName(name);
        if (similarName != null) {
            return null;
        }
        AccountBE newAccount = new AccountBE(name);
        model.asset_accounts.add(newAccount);
        repo.saveOrRevert("save_file", "creating asset account", () -> model.asset_accounts.remove(newAccount));
        return newAccount;
    }

    public BudgetAccountBE createRootBudget(String name,float currentBudget, float yearlyBudget) throws JSONException, IOException {
        // check if name is already in use
        AccountBE similarName = model.getAccountByName(name);
        if (similarName != null) {
            return null;
        }
        BudgetAccountBE newAccount = new BudgetAccountBE(name, currentBudget, yearlyBudget);
        model.budget_accounts.add(newAccount);
        repo.saveOrRevert("save_file", "creating root budget", () -> model.budget_accounts.remove(newAccount));
        return newAccount;
    }

    public BudgetAccountBE createSubBudget(BudgetAccountBE parent,
                                           String name,
                                           float current_budget,
                                           float yearly_budget) throws JSONException, IOException {
        // check if name is already in use
        AccountBE similarName = model.getAccountByName(name);
        if (similarName != null) {
            return null;
        }
        BudgetAccountBE newAccount = new BudgetAccountBE(name, current_budget, yearly_budget);
        parent.addSubBudget(newAccount);
        repo.saveOrRevert("save_file", "creating sub budget", () -> parent.getDirectSubBudgets().remove(newAccount));
        return newAccount;
    }

    public BudgetAccountBE createSubBudget(BudgetAccountBE parent,
                                           String name,
                                           float yearly_budget) throws JSONException, IOException {
        return createSubBudget(parent, name, yearly_budget / 12, yearly_budget);
    }

    public ProjectBudgetBE createProjectBudget(BudgetAccountBE parent, String name, float total_budget) throws JSONException, IOException {
        // check if name is already in use
        AccountBE similarName = model.getAccountByName(name);
        if (similarName != null) {
            return null;
        }
        ProjectBudgetBE newAccount = new ProjectBudgetBE(name, total_budget);
        parent.addSubBudget(newAccount);
        parent.adjustIndivYearlyBudget(-total_budget);
        repo.saveOrRevert("save_file", "creating project budget", () -> {
            parent.getDirectSubBudgets().remove(newAccount);
            parent.adjustIndivYearlyBudget(total_budget);
        });
        return newAccount;
    }

    public boolean deleteAccount(String accountName) throws JSONException, IOException {
        AccountBE account = model.getAccountByName(accountName);
        if (account != null)
            return deleteAccount(account);
        return false;
    }

    public boolean deleteAccount(AccountBE account) throws JSONException, IOException {
        if (account == null)
            return false;
        // variable to store position at which account was in its list.
        // needed in case of revert to initial state.
        // position != -1 then also signals whether the account has been found and removed
        int position = -1;
        BudgetAccountBE parentBudget = null;
        if (account instanceof BudgetAccountBE) {
            position = model.budget_accounts.indexOf(account);
            if (position != -1)
                model.budget_accounts.remove(account);
            else {
                for (BudgetAccountBE budget : model.budget_accounts) {
                    parentBudget = budget.getSubBudgetParent((BudgetAccountBE) account);
                    if (parentBudget == null)
                        continue;
                    position = parentBudget.getDirectSubBudgets().indexOf(account);
                    parentBudget.getDirectSubBudgets().remove(account);
                    break;
                }
            }
        }
        else {
            position = model.asset_accounts.indexOf(account);
            if (position != -1)
                model.asset_accounts.remove(account);
        }
        // if account to be deleted could not be found, return false
        if (position == -1)
            return false;
        // both are reassigned in the search loop above, so they are not effectively final and
        // cannot be captured by the revert lambda directly
        final int removedAt = position;
        final BudgetAccountBE removedFrom = parentBudget;

        // A deleted account that is still the selected sender or receiver is an orphan: it is no
        // longer in any list, so addTx on it writes to nothing and a transaction would be booked
        // on one side only. Clear the selection here rather than leaving the model pointing at a
        // deleted account, and restore it if the save fails.
        final boolean wasSender = model.currentSender == account;
        final boolean wasReceiver = model.currentReceiver == account;
        final boolean wasInspected = model.currentInspectedAccount == account;
        if (wasSender)
            model.currentSender = null;
        if (wasReceiver)
            model.currentReceiver = null;
        if (wasInspected)
            model.currentInspectedAccount = null;

        repo.saveOrRevert("save_file", "deleting account", () -> {
            if (account instanceof BudgetAccountBE) {
                if (removedFrom == null)
                    model.budget_accounts.add(removedAt, (BudgetAccountBE) account);
                else
                    removedFrom.getDirectSubBudgets().add(removedAt, (BudgetAccountBE) account);
            } else
                model.asset_accounts.add(removedAt, account);
            if (wasSender)
                model.currentSender = account;
            if (wasReceiver)
                model.currentReceiver = account;
            if (wasInspected)
                model.currentInspectedAccount = account;
        });
        return true;
    }
    // endregion

    public void updateYearlyBudget(float newBudget, BudgetAccountBE account, boolean adjustAvailable) throws JSONException, IOException {
        float oldYearlyBudget = account.indivYearlyBudget;
        float oldAvailableBudget = account.indivAvailableBudget;
        account.setIndivYearlyBudget(newBudget);
        if (adjustAvailable)
            account.setIndivAvailableBudget(oldAvailableBudget + (newBudget - oldYearlyBudget) * (account.getRenewalPeriod() / 12.0f));
        repo.saveOrRevert("save_file", "updating yearly budget", () -> {
            account.setIndivYearlyBudget(oldYearlyBudget);
            if (adjustAvailable)
                account.setIndivAvailableBudget(oldAvailableBudget);
        });
    }

    public void transferAvailableBudget(float amount, BudgetAccountBE sender, BudgetAccountBE recipient) throws JSONException, IOException, InvalidParameterException {
        if (amount <= 0)
            throw new InvalidParameterException("transferAvailableBudget called with amount <= 0");
        float oldSenderCurrent = sender.indivAvailableBudget;
        float oldRecipientCurrent = recipient.indivAvailableBudget;

        sender.setIndivAvailableBudget(oldSenderCurrent - amount);
        recipient.setIndivAvailableBudget(oldRecipientCurrent + amount);

        repo.saveOrRevert("save_file", "transferring available budget", () -> {
            sender.setIndivAvailableBudget(oldSenderCurrent);
            recipient.setIndivAvailableBudget(oldRecipientCurrent);
        });
    }

    public boolean transferSubBudget(BudgetAccountBE parent, BudgetAccountBE object, BudgetAccountBE target) throws JSONException, IOException {
        boolean result = parent.transferSubBudget(object, target);
        if (result)
            repo.saveOrRevert("save_file", "transferring sub budget", () -> target.transferSubBudget(object, parent));
        return result;
    }
}
