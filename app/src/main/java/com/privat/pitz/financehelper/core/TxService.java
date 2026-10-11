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
        // A transaction is a pair of entries that must balance, so it is booked only when both
        // sides can actually be written. Three ways that fails, all refused rather than
        // half-applied:
        //
        //  - nothing selected. addFunds already guarded its receiver this way; this path did not,
        //    so it threw NullPointerException out of addTx instead of reporting the problem.
        //  - the selection is an orphan: an AccountBE that no list in the model contains any more,
        //    because a load replaced every account object or the account was deleted. It looks
        //    entirely valid - right name, right transactions - but addTx on it mutates something
        //    the serialiser never visits, so that side of the transfer is silently dropped and
        //    only the other account ends up with an entry. This is the reported "the entry is
        //    only made to one account" symptom, and it is why the check is by identity.
        //  - sender and receiver are the same account, which nets to zero and is always a
        //    misclick rather than an intent.
        if (!model.containsAccount(from_acc) || !model.containsAccount(to_acc)) {
            Log.println(Log.ERROR, "create_tx", String.format(
                    "Refusing transaction: sender (%s) or receiver (%s) is not a live account in "
                            + "the current model. Both sides of the balance must be writable.",
                    from_acc, to_acc));
            return false;
        }
        if (from_acc == to_acc) {
            Log.println(Log.ERROR, "create_tx", String.format(
                    "Refusing transaction: sender and receiver are the same account (%s).",
                    from_acc));
            return false;
        }
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
        // Both entries were added optimistically above. Without undoing them here they would sit
        // in the model unsaved, and the next navigation - AbstractActivity.startActivity saves on
        // every one - would write them to disk anyway, booking a transaction whose redirection
        // had failed. startTxRedirection cannot return false today, so this is currently
        // unreachable; it is the shape of the bug rather than the bug itself.
        from_acc.removeTx(entry_from);
        to_acc.removeTx(entry_to);
        Log.println(Log.ERROR, "create_tx",
                "Transaction redirection did not complete; the transaction was not booked.");
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

    /**
     * The two halves of a transfer, matched by date+description: {@code sourceEntry} is the
     * entry on the account the caller already has a handle on, {@code counterpartEntry} is the
     * matching entry found on the other side of the transfer, however deep in the sub-budget
     * tree it lives.
     */
    private static final class TxPair {
        final TxBE sourceEntry;
        final TxBE counterpartEntry;

        TxPair(TxBE sourceEntry, TxBE counterpartEntry) {
            this.sourceEntry = sourceEntry;
            this.counterpartEntry = counterpartEntry;
        }
    }

    /**
     * Finds the entry matching {@code date}/{@code description} on {@code source}, and its
     * counterpart entry (same date+description) on whichever other account holds it. Returns
     * {@code null} if either side cannot be found.
     */
    private TxPair findTxPair(Date date, String description, AccountBE source) {
        // try identifying the entry making the call
        TxBE sourceEntry = null;
        for (TxBE e : source.getTxList()) {
            if (e.getDate().equals(date) && e.getDescription().equals(description)) {
                sourceEntry = e;
            }
        }
        // if none was found, then return null
        if (sourceEntry == null)
            return null;

        // search every account - asset accounts and budget accounts at every sub-budget depth -
        // for the other half of the transfer. model.getAllAccounts() recurses the whole
        // sub-budget tree via getAllSubBudgets(), unlike a hand-built one-level list.
        // The remove() below mutates this list, which is only safe because getAllAccounts()
        // allocates a fresh ArrayList on every call rather than handing back a live model list.
        // Do not "optimise" that allocation away.
        List<AccountBE> toSearch = model.getAllAccounts();
        // remove source account, which by then will inevitably have been added
        toSearch.remove(source);

        for (AccountBE account : toSearch) {
            for (TxBE entry : account.getTxList()) {
                if (entry.getDate().equals(date) && entry.getDescription().equals(description)) {
                    return new TxPair(sourceEntry, entry);
                }
            }
        }
        return null;
    }

    public boolean updateTx(Date date, String description, AccountBE source, float newAmount) throws JSONException, IOException {
        TxPair pair = findTxPair(date, description, source);
        if (pair == null)
            return false;
        final TxBE foundEntry = pair.sourceEntry;
        final TxBE entry = pair.counterpartEntry;

        // store old amount for later in case, changes need to be reverted
        float oldAmount = foundEntry.getAmount();

        entry.setAmount(newAmount * (-1.0f));
        foundEntry.setAmount(newAmount);
        repo.saveOrRevert("save_file", "updating entry amount", () -> {
            entry.setAmount(oldAmount * (-1.0f));
            foundEntry.setAmount(oldAmount);
        });
        return true;
    }

    /**
     * Applies a whole edit - date, description and amount - to both halves of a transfer at once.
     *
     * <p>The per-field overloads below each change one thing, which is wrong for the edit dialog:
     * it changes all three together, and the pair is matched on date+description, so applying the
     * changes one at a time would move the goalposts between calls. The date in particular has no
     * per-field overload precisely because changing it unilaterally breaks the matching.
     *
     * <p>{@code oldDate}/{@code oldDescription} must be the values as they were before the user
     * edited anything - the entry has to still be findable. Nothing is mutated unless both halves
     * are found, so a false return leaves the model exactly as it was and the caller is free to
     * apply a one-sided edit instead.
     *
     * @return false if either half of the pair could not be found
     */
    public boolean updateTxPair(Date oldDate, String oldDescription, AccountBE source,
                                Date newDate, String newDescription, float newAmount)
            throws JSONException, IOException {
        TxPair pair = findTxPair(oldDate, oldDescription, source);
        if (pair == null)
            return false;
        final TxBE sourceEntry = pair.sourceEntry;
        final TxBE counterpart = pair.counterpartEntry;

        final Date oldCounterpartDate = counterpart.getDate();
        final String oldCounterpartDescription = counterpart.getDescription();
        final float oldSourceAmount = sourceEntry.getAmount();
        final float oldCounterpartAmount = counterpart.getAmount();

        sourceEntry.setDate(newDate);
        sourceEntry.setDescription(newDescription);
        sourceEntry.setAmount(newAmount);
        // the counterpart carries the opposite sign, exactly as createTx booked it
        counterpart.setDate(newDate);
        counterpart.setDescription(newDescription);
        counterpart.setAmount(newAmount * (-1.0f));

        repo.saveOrRevert("save_file", "updating both sides of a transfer", () -> {
            sourceEntry.setDate(oldDate);
            sourceEntry.setDescription(oldDescription);
            sourceEntry.setAmount(oldSourceAmount);
            counterpart.setDate(oldCounterpartDate);
            counterpart.setDescription(oldCounterpartDescription);
            counterpart.setAmount(oldCounterpartAmount);
        });
        return true;
    }

    public boolean updateTx(Date date, String description, AccountBE source, String newDescription) throws JSONException, IOException {
        TxPair pair = findTxPair(date, description, source);
        if (pair == null)
            return false;
        final TxBE foundEntry = pair.sourceEntry;
        final TxBE entry = pair.counterpartEntry;

        entry.setDescription(newDescription);
        foundEntry.setDescription(newDescription);
        repo.saveOrRevert("save_file", "updating entry description", () -> {
            entry.setDescription(description);
            foundEntry.setDescription(description);
        });
        return true;
    }
    // endregion

    // region move / merge entries
    /**
     * Moves {@code entries} from {@code source} to {@code target}, unchanged.
     *
     * <p>Entries are treated as standalone records here, deliberately. The two halves of a booking
     * are only linked by sharing a date and description, and that link does not survive merges or
     * deletions of offsetting entries, so trying to drag a "counterpart" along would guess wrong.
     * Moving one entry changes exactly two account totals: the one it leaves and the one it joins.
     *
     * <p>Refused without touching anything if either account is not live in the model (the orphan
     * problem described in {@link #createTx}), if source and target are the same, or if any entry
     * is not actually on {@code source}.
     *
     * @return false if the move was refused
     */
    public boolean moveTx(AccountBE source, List<TxBE> entries, AccountBE target)
            throws JSONException, IOException {
        if (!model.containsAccount(source) || !model.containsAccount(target) || source == target
                || entries.isEmpty()) {
            Log.println(Log.ERROR, "move_tx", String.format(
                    "Refusing move of %d entries from %s to %s.", entries.size(), source, target));
            return false;
        }
        for (TxBE tx : entries) {
            if (source.getTxIndex(tx) == -1) {
                Log.println(Log.ERROR, "move_tx", String.format(
                        "Refusing move: %s is not on %s.", tx, source));
                return false;
            }
        }
        final List<TxBE> sourceBefore = new ArrayList<>(source.getTxList());
        final List<TxBE> targetBefore = new ArrayList<>(target.getTxList());
        for (TxBE tx : entries) {
            source.removeTx(tx);
            target.addTx(tx);
        }
        target.sortTxByDate();
        repo.saveOrRevert("move_tx", "moving entries to " + target, () -> {
            restoreTxList(source, sourceBefore);
            restoreTxList(target, targetBefore);
        });
        return true;
    }

    /**
     * Everything needed to undo a {@link #mergeTx}: the anchor's previous state and the entries
     * that were folded into it.
     */
    public static final class Merge {
        public final AccountBE account;
        public final TxBE anchor;
        /** True when the entries summed to zero and were all removed, the anchor included. */
        public final boolean cancelledOut;
        private final List<TxBE> txListBefore;
        private final float anchorAmountBefore;
        private final String anchorDescriptionBefore;
        private final Date anchorDateBefore;

        Merge(AccountBE account, TxBE anchor, boolean cancelledOut, List<TxBE> txListBefore) {
            this.account = account;
            this.anchor = anchor;
            this.cancelledOut = cancelledOut;
            this.txListBefore = txListBefore;
            this.anchorAmountBefore = anchor.getAmount();
            this.anchorDescriptionBefore = anchor.getDescription();
            this.anchorDateBefore = anchor.getDate();
        }

        private void revert() {
            anchor.setAmount(anchorAmountBefore);
            anchor.setDescription(anchorDescriptionBefore);
            anchor.setDate(anchorDateBefore);
            restoreTxList(account, txListBefore);
        }
    }

    /** The amount {@link #mergeTx} would give the anchor, rounded to cents. */
    public static float mergedAmount(TxBE anchor, List<TxBE> others) {
        // Summing floats leaves residue (-3.68 + -9.38 = -13.059999); amounts are cents.
        double sum = anchor.getAmount();
        for (TxBE tx : others)
            sum += tx.getAmount();
        return Math.round(sum * 100.0) / 100.0f;
    }

    /** {@link #mergeTx(AccountBE, TxBE, List, String, Date)}, keeping the anchor's date. */
    public Merge mergeTx(AccountBE account, TxBE anchor, List<TxBE> others, String newDescription)
            throws JSONException, IOException {
        return mergeTx(account, anchor, others, newDescription, anchor.getDate());
    }

    /**
     * Merges {@code others} into {@code anchor}, all on {@code account}: the anchor takes
     * {@code newDescription}, {@code newDate} and the summed amount, and the others are removed.
     * If the sum is zero the entries cancel out and the anchor is removed as well, rather than
     * leaving a 0,00 entry behind. Either way the account total is unchanged - only a move can
     * change a total.
     *
     * @return what is needed to undo the merge, or null if it was refused (an entry not on
     *         {@code account}, the anchor among {@code others}, duplicates, or nothing to merge)
     */
    public Merge mergeTx(AccountBE account, TxBE anchor, List<TxBE> others, String newDescription,
                         Date newDate) throws JSONException, IOException {
        boolean valid = model.containsAccount(account) && !others.isEmpty()
                && account.getTxIndex(anchor) != -1 && !others.contains(anchor);
        for (int i = 0; valid && i < others.size(); i++) {
            TxBE tx = others.get(i);
            valid = account.getTxIndex(tx) != -1 && others.indexOf(tx) == i;
        }
        if (!valid) {
            Log.println(Log.ERROR, "merge_tx", String.format(
                    "Refusing merge of %d entries into %s on %s.", others.size(), anchor, account));
            return null;
        }
        float amount = mergedAmount(anchor, others);
        boolean cancelledOut = amount == 0.0f;
        final Merge merge = new Merge(account, anchor, cancelledOut,
                new ArrayList<>(account.getTxList()));

        for (TxBE tx : others)
            account.removeTx(tx);
        if (cancelledOut) {
            account.removeTx(anchor);
        } else {
            anchor.setAmount(amount);
            anchor.setDescription(newDescription);
            anchor.setDate(newDate);
            account.sortTxByDate();
        }
        repo.saveOrRevert("merge_tx", "merging entries on " + account, merge::revert);
        return merge;
    }

    /** Reverses a {@link #mergeTx}, restoring every entry exactly as it was. */
    public void undoMerge(Merge merge) throws JSONException, IOException {
        final float mergedAmount = merge.anchor.getAmount();
        final String mergedDescription = merge.anchor.getDescription();
        final Date mergedDate = merge.anchor.getDate();
        final List<TxBE> mergedList = new ArrayList<>(merge.account.getTxList());
        merge.revert();
        repo.saveOrRevert("merge_tx", "undoing a merge on " + merge.account, () -> {
            merge.anchor.setAmount(mergedAmount);
            merge.anchor.setDescription(mergedDescription);
            merge.anchor.setDate(mergedDate);
            restoreTxList(merge.account, mergedList);
        });
    }

    private static void restoreTxList(AccountBE account, List<TxBE> entries) {
        account.getTxList().clear();
        account.getTxList().addAll(entries);
    }
    // endregion
}
