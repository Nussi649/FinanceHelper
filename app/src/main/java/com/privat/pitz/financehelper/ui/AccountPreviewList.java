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
        assert accounts != null;

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
            RadioButton rbReceive = newItem.getRBReceiver();
            assert rbReceive != null;
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
                assert rbReceiveChild != null;
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
        assert accounts != null;

        // first clean up target container
        container.removeAllViews();

        // then get number of accounts
        int count = accounts.size();
        if (count == 0)
            return;
        // iterate through all accounts given as argument
        for (int index = 0; index < accounts.size(); index++) {
            AccountBE currentAccount = accounts.get(index);
            // this method should only be fed with Asset Accounts
            assert !(currentAccount instanceof BudgetAccountBE);
            // skip if account is marked as inactive
            if (!currentAccount.getIsActive())
                continue;
            // create list item at hierarchy level 0 (not specifically defined)
            ListItemAccountPreview newItem = ListItemAccountPreview.getInstance(parentActivity, container);
            newItem.init(currentAccount);
            // add list item to parent layout
            container.addView(newItem);
            // add radio button to receiver group
            RadioButton rbReceive = newItem.getRBReceiver();
            assert rbReceive != null;
            receiverManager.addRadioButton(rbReceive, currentAccount);

            // add radio button to sender group
            RadioButton rbSend = newItem.getRBSender();
            assert rbSend != null;
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
                RadioButton rbReceiveChild = newItem.getRBReceiver();
                assert rbReceiveChild != null;
                receiverManager.addRadioButton(rbReceiveChild, currentChildAccount);
                // add radio button to sender group
                RadioButton rbSendChild = newItem.getRBSender();
                assert rbSendChild != null;
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
