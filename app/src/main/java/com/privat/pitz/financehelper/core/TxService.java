package com.privat.pitz.financehelper.core;

import android.util.Log;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;
import com.privat.pitz.financehelper.data.TxBE;
import com.privat.pitz.financehelper.data.RecurringTxBE;

/**
 * Transaction cluster extracted from {@link Controller}: creating, deleting and updating
 * transactions, adding funds, and recurring transactions. Shares its {@code repo} and
 * {@code model} instances with the owning Controller - it does not copy them.
 */
public class TxService {
    private final SaveFileRepository repo;
    private final Model model;
    private final TxRedirectionService redirection;

    TxService(SaveFileRepository repo, Model model, TxRedirectionService redirection) {
        this.repo = repo;
        this.model = model;
        this.redirection = redirection;
    }

    // region perform transactions
    // create a transaction between two accounts
    public boolean createTx(String desc, float amount, RedirectionPrompt prompt) throws JSONException, IOException {
        AccountBE from_acc = model.currentSender;
        AccountBE to_acc = model.currentReceiver;
        Calendar calendar = Calendar.getInstance();
        TxBE entry_from = new TxBE(amount*(-1.0f), desc, calendar.getTime());
        TxBE entry_to = new TxBE(amount, desc, calendar.getTime());
        from_acc.addTx(entry_from);
        to_acc.addTx(entry_to);
        boolean result = true;

        // handle case if to_acc relays payment to other financial entity if it isn't a BudgetAccount, pass
        if (to_acc instanceof BudgetAccountBE) {
            BudgetAccountBE to_budgetAcc = (BudgetAccountBE) to_acc;
            String otherEntity = to_budgetAcc.getOtherEntity();
            if (otherEntity != null && !otherEntity.isEmpty())
                result = redirection.startTxRedirection(otherEntity, desc, amount, prompt);
        }
        if (result) {
            repo.saveOrRevert("save_file", "creating a Tx", () -> {
                from_acc.removeTx(entry_from);
                to_acc.removeTx(entry_to);
            });
            return true;
        }
        return false;
    }

    public boolean deleteTx(AccountBE parent, TxBE tx) throws JSONException, IOException {
        int position = parent.getTxIndex(tx);
        if (position == -1)
            return false;
        parent.removeTx(tx);
        repo.saveOrRevert("delete_tx", "deleting a transaction (" + tx + ")", () -> parent.addTx(position, tx));
        return true;
    }

    // add funds to one account
    public boolean addFunds(float amount, String desc) throws JSONException, IOException {
        TxBE newFunds = new TxBE(amount, desc, Calendar.getInstance().getTime());
        if (model.currentReceiver == null) {
            return false;
        }
        final AccountBE receiver = model.currentReceiver;
        receiver.addTx(newFunds);
        model.currentIncome.add(newFunds);
        repo.saveOrRevert("save_file", "adding funds", () -> {
            receiver.dropLastTx();
            model.currentIncome.remove(newFunds);
        });
        return true;
    }

    public boolean addRecurringTx(String desc, float amount) throws JSONException, IOException{
        Calendar calendar = Calendar.getInstance();
        AccountBE sender = model.currentSender;
        AccountBE receiver = model.currentReceiver;
        if (sender == null || receiver == null) {
            Log.println(Log.ERROR, "get_tx_partners",
                    "Error trying to retrieve sender or receiver account from model: no selection");
            return false;
        }
        RecurringTxBE newOrder = new RecurringTxBE(amount, desc, calendar.getTime(), sender.getName(), receiver.getName());
        model.recurringTx.add(newOrder);
        repo.saveOrRevert("save_file", "adding recurring transaction", () -> model.recurringTx.remove(newOrder));
        return true;
    }

    public boolean deleteRecurringTx(RecurringTxBE recurringTx) throws JSONException, IOException {
        int position = model.recurringTx.indexOf(recurringTx);
        if (position == -1)
            return false;
        // mutate before the save, matching every other site (the removal cannot throw a checked
        // exception, so hoisting it out of the try is behaviour-identical)
        model.recurringTx.remove(recurringTx);
        repo.saveOrRevert("save_file", "deleting recurring transaction",
                () -> model.recurringTx.add(position, recurringTx));
        return true;
    }

    public void triggerRecurringTx() throws JSONException, IOException {
        class AccountTxCombo {
            final AccountBE account;
            final TxBE tx;

            public AccountTxCombo(AccountBE acc, TxBE tx) {
                this.account = acc;
                this.tx = tx;
            }
        }

        Calendar fom = Const.getFirstOfMonth();
        List<AccountTxCombo> addedTx = new ArrayList<>();
        List<TxBE> addedIncome = new ArrayList<>();
        for (RecurringTxBE r : model.recurringTx) {
            AccountBE receiver = model.getAccountByName(r.getReceiverStr());
            if (receiver == null) {
                Log.println(Log.INFO, "execute_recur_tx",
                        String.format("Skipping recurring transaction: could not find Receiver (%s) account",
                                r.getReceiverStr()));
                continue;
            }
            TxBE receiverTx = new TxBE(r.getAmount(), r.getDescription(), fom.getTime());

            String senderName = r.getSenderStr();
            if (senderName == null || senderName.isEmpty()) {
                // no sender means this is a recurring income: credit the receiver and record it
                // in the income list, with no counter-booking anywhere
                TxBE incomeTx = new TxBE(r.getAmount(), r.getDescription(), fom.getTime());
                model.currentIncome.add(incomeTx);
                addedIncome.add(incomeTx);
                receiver.addTx(receiverTx);
                addedTx.add(new AccountTxCombo(receiver, receiverTx));
                continue;
            }

            AccountBE sender = model.getAccountByName(senderName);
            if (sender == null) {
                Log.println(Log.INFO, "execute_recur_tx",
                        String.format("Skipping recurring transaction: could not find Sender (%s) account",
                                senderName));
                continue;
            }
            TxBE senderTx = new TxBE(r.getAmount() * (-1.0f), r.getDescription(), fom.getTime());
            sender.addTx(senderTx);
            receiver.addTx(receiverTx);
            addedTx.add(new AccountTxCombo(sender, senderTx));
            addedTx.add(new AccountTxCombo(receiver, receiverTx));
        }
        repo.saveOrRevert("save_file", "triggering recurring transactions", () -> {
            for (AccountTxCombo entry : addedTx) {
                entry.account.getTxList().remove(entry.tx);
            }
            // the income entries were never rolled back before, because the branch that creates
            // them could not run at all
            model.currentIncome.removeAll(addedIncome);
        });
    }
    // endregion

    // region update objects
    public boolean updateTx(Date date, String description, AccountBE source, float newAmount) throws JSONException, IOException {
        // try identifying the entry making the call
        TxBE sourceEntry = null;
        for (TxBE e : source.getTxList()) {
            if (e.getDate().equals(date) && e.getDescription().equals(description)) {
                sourceEntry = e;
            }
        }
        // if none was found, then return false
        if (sourceEntry == null)
            return false;
        // assigned inside the search loop above, so not effectively final and not capturable by
        // the revert lambda without a copy
        final TxBE foundEntry = sourceEntry;

        // setup list of all accounts to search for other part of transaction
        // for this take asset accounts which are already of type AccountBE
        // (copy the list! it must not be mutated, since it's the live model list)
        List<AccountBE> toSearch = new ArrayList<>(model.asset_accounts);
        // then transform budget accounts and first order sub budgets
        List<AccountBE> transformed_budget_accounts = new ArrayList<>();
        for (BudgetAccountBE budget_account : model.budget_accounts) {
            transformed_budget_accounts.add(budget_account);
            List<BudgetAccountBE> sub_budgets = budget_account.getDirectSubBudgets();
            if (!sub_budgets.isEmpty())
                transformed_budget_accounts.addAll(sub_budgets);

        }
        // and add to toSearch list
        toSearch.addAll(transformed_budget_accounts);
        // remove source account, which by then will inevitably have been added
        toSearch.remove(source);

        // store old amount for later in case, changes need to be reverted
        float oldAmount = sourceEntry.getAmount();

        for (AccountBE account : toSearch) {
            for (TxBE entry : account.getTxList()) {
                if (entry.getDate().equals(date) && entry.getDescription().equals(description)) {
                    entry.setAmount(newAmount * (-1.0f));
                    foundEntry.setAmount(newAmount);
                    repo.saveOrRevert("save_file", "updating entry amount", () -> {
                        entry.setAmount(oldAmount * (-1.0f));
                        foundEntry.setAmount(oldAmount);
                    });
                    return true;
                }
            }
        }
        return false;
    }

    public boolean updateTx(Date date, String description, AccountBE source, String newDescription) throws JSONException, IOException {
        // try identifying the entry making the call
        TxBE sourceEntry = null;
        for (TxBE e : source.getTxList()) {
            if (e.getDate().equals(date) && e.getDescription().equals(description)) {
                sourceEntry = e;
            }
        }
        // if none was found, then return false
        if (sourceEntry == null)
            return false;
        // assigned inside the search loop above, so not effectively final and not capturable by
        // the revert lambda without a copy
        final TxBE foundEntry = sourceEntry;

        // setup list of all accounts to search for other part of transaction
        // for this take asset accounts which are already of type AccountBE
        // (copy the list! it must not be mutated, since it's the live model list)
        List<AccountBE> toSearch = new ArrayList<>(model.asset_accounts);
        // then transform budget accounts and first order sub budgets
        List<AccountBE> transformed_budget_accounts = new ArrayList<>();
        for (BudgetAccountBE budget_account : model.budget_accounts) {
            transformed_budget_accounts.add(budget_account);
            List<BudgetAccountBE> sub_budgets = budget_account.getDirectSubBudgets();
            if (!sub_budgets.isEmpty())
                for (BudgetAccountBE sub_budget : sub_budgets)
                    transformed_budget_accounts.add(sub_budget);

        }
        // and add to toSearch list
        toSearch.addAll(transformed_budget_accounts);
        // remove source account, which by then will inevitably have been added
        toSearch.remove(source);

        for (AccountBE account : toSearch) {
            for (TxBE entry : account.getTxList()) {
                if (entry.getDate().equals(date) && entry.getDescription().equals(description)) {
                    entry.setDescription(newDescription);
                    foundEntry.setDescription(newDescription);
                    repo.saveOrRevert("save_file", "updating entry description", () -> {
                        entry.setDescription(description);
                        foundEntry.setDescription(description);
                    });
                    return true;
                }
            }
        }
        return false;
    }
    // endregion
}
