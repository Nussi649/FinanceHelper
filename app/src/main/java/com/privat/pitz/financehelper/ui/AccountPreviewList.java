package com.privat.pitz.financehelper.ui;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;

import java.util.List;

import com.privat.pitz.financehelper.MainActivity;
import com.privat.pitz.financehelper.R;
import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;

// Moved here from core.Util: these methods inflate and populate Android views (ListItemAccountPreview),
// so they belong in the UI layer, not in the framework-agnostic core layer.
public abstract class AccountPreviewList {

    // region populate AccountsPreview TableLayouts with Account lists
    @SuppressLint("InflateParams")
    public static void populateBudgetAccountsPreview(final List<BudgetAccountBE> accounts,
                                               final MainActivity parentActivity,
                                               final LinearLayout container,
                                               final RbAccountManager receiverManager) {
        // Public entry point taking a caller-supplied list: a null here is a caller bug, not
        // something to silently no-op on device (assert never evaluated this on Android anyway).
        if (accounts == null) {
            Log.println(Log.ERROR, "account_preview", "populateBudgetAccountsPreview called with null accounts list");
            return;
        }

        // first clean up target container
        container.removeAllViews();

        // then get number of accounts
        int count = accounts.size();
        if (count == 0)
            return;
        // iterate through all accounts given as argument
        for (int index = 0; index < accounts.size(); index++) {
            AccountBE currentAccount = accounts.get(index);
            // skip if account is marked as inactive
            if (!currentAccount.getIsActive())
                continue;
            // create list item at hierarchy level 0 (not specifically defined)
            ListItemAccountPreview newItem = ListItemAccountPreview.getInstance(parentActivity, container);
            newItem.init(currentAccount);
            // add list item to parent layout
            container.addView(newItem);
            // add radio button to receiver group
            // (getRBReceiver() cannot be null here: it returns a field set from findViewById() on
            // a layout this class itself inflates in initViews(), which would already have NPE'd
            // there with a clearer stack trace if the layout were broken)
            RadioButton rbReceive = newItem.getRBReceiver();
            receiverManager.addRadioButton(rbReceive, currentAccount);

            // check if list item has children. If so, add them
            // get all children (down every hierarchy layer)
            List<ListItemAccountPreview> children = newItem.getAllChildren();
            // abort if there are none
            if (children.isEmpty())
                continue;
            // iterate through all children
            for (ListItemAccountPreview child : children) {
                BudgetAccountBE currentChildAccount = (BudgetAccountBE) child.getReferenceAccount();
                // add child to parent layout and add listeners
                container.addView(child);
                // add radio button to receiver group
                RadioButton rbReceiveChild = child.getRBReceiver();
                receiverManager.addRadioButton(rbReceiveChild, currentChildAccount);
            }
        }
        // hide divider of last item
        int viewCount = container.getChildCount();
        if (viewCount == 0)
            return;
        View lastView = container.getChildAt(viewCount - 1);
        if (lastView instanceof ListItemAccountPreview)
            lastView.findViewById(R.id.horizontal_divider).setVisibility(View.GONE);
        else
            Log.println(Log.INFO, "account_preview", "After populating AssetAccount preview, last list item was not of type ListItemAccountPreview. This is unexpected");
    }

    @SuppressLint("InflateParams")
    public static void populateAssetAccountsPreview(final List<AccountBE> accounts,
                                               final MainActivity parentActivity,
                                               final LinearLayout container,
                                               final RbAccountManager receiverManager,
                                               final RbAccountManager senderManager) {
        // Public entry point taking a caller-supplied list: a null here is a caller bug, not
        // something to silently no-op on device (assert never evaluated this on Android anyway).
        if (accounts == null) {
            Log.println(Log.ERROR, "account_preview", "populateAssetAccountsPreview called with null accounts list");
            return;
        }

        // first clean up target container
        container.removeAllViews();

        // then get number of accounts
        int count = accounts.size();
        if (count == 0)
            return;
        // iterate through all accounts given as argument
        for (int index = 0; index < accounts.size(); index++) {
            AccountBE currentAccount = accounts.get(index);
            // This method must only be fed Asset Accounts: a BudgetAccountBE here would be mixed
            // into the transfer sender/receiver lists as if it were an asset account, which is a
            // genuine caller bug. Skip and log it rather than silently mis-rendering it (the old
            // `assert` documented this invariant but never actually enforced it on device).
            if (currentAccount instanceof BudgetAccountBE) {
                Log.println(Log.ERROR, "account_preview",
                        "populateAssetAccountsPreview was given a BudgetAccountBE (" + currentAccount.getName() + "); skipping it");
                continue;
            }
            // skip if account is marked as inactive
            if (!currentAccount.getIsActive())
                continue;
            // create list item at hierarchy level 0 (not specifically defined)
            ListItemAccountPreview newItem = ListItemAccountPreview.getInstance(parentActivity, container);
            newItem.init(currentAccount);
            // add list item to parent layout
            container.addView(newItem);
            // add radio button to receiver group
            // (see populateBudgetAccountsPreview above: these getters cannot return null here)
            RadioButton rbReceive = newItem.getRBReceiver();
            receiverManager.addRadioButton(rbReceive, currentAccount);

            // add radio button to sender group
            RadioButton rbSend = newItem.getRBSender();
            senderManager.addRadioButton(rbSend, currentAccount);

            // check if list item has children. If so, add them
            // get all children (down every hierarchy layer)
            List<ListItemAccountPreview> children = newItem.getAllChildren();
            // abort if there are none
            if (children.isEmpty())
                continue;
            // iterate through all children
            for (ListItemAccountPreview child : children) {
                AccountBE currentChildAccount = child.getReferenceAccount();
                // add child to parent layout and add listeners
                container.addView(child);
                // add radio button to receiver group
                RadioButton rbReceiveChild = child.getRBReceiver();
                receiverManager.addRadioButton(rbReceiveChild, currentChildAccount);
                // add radio button to sender group
                RadioButton rbSendChild = child.getRBSender();
                senderManager.addRadioButton(rbSendChild, currentChildAccount);
            }
        }
        // hide divider of last item
        int viewCount = container.getChildCount();
        if (viewCount == 0)
            return;
        View lastView = container.getChildAt(viewCount - 1);
        if (lastView instanceof ListItemAccountPreview)
            lastView.findViewById(R.id.horizontal_divider).setVisibility(View.GONE);
        else
            Log.println(Log.INFO, "account_preview", "After populating AssetAccount preview, last list item was not of type ListItemAccountPreview. This is unexpected");
    }
    // endregion
}
