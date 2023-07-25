package View;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.widget.RadioButton;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.privat.pitz.financehelper.MainActivity;
import com.privat.pitz.financehelper.R;

import java.util.ArrayList;
import java.util.List;

import Backend.RefreshListener;
import Backend.Util;
import Logic.AccountBE;
import Logic.BudgetAccountBE;

@SuppressLint("ViewConstructor")
public class AccountPreviewTableRow extends TableRow {

    private final AccountBE referenceAccount;
    private final MainActivity parentActivity;
    private final List<AccountPreviewTableRow> children = new ArrayList<>();
    private final MutableLiveData<Boolean> refreshLiveData = new MutableLiveData<>();
    private RefreshListener refreshListener;

    // region Constructors
    public AccountPreviewTableRow(MainActivity parentActivity, AccountBE accountObject) {
        super(parentActivity);
        this.referenceAccount = accountObject;
        this.parentActivity = parentActivity;
        this.refreshListener = parentActivity;
        populateUI();
    }
    // endregion

    // region sets / gets
    public List<AccountPreviewTableRow> getChildren() {
        return this.children;
    }

    public RadioButton getRBSender() {
        return findViewById(R.id.radioButton_Sender);
    }

    public RadioButton getRBReceiver() {
        return findViewById(R.id.radioButton_Receiver);
    }

    public AccountBE getReferenceAccount() {
        return referenceAccount;
    }

    public void addChild(AccountPreviewTableRow newChild) {
        this.children.add(newChild);
    }

    public void addChildren(List<AccountPreviewTableRow> newChildren) {
        this.children.addAll(newChildren);
    }

    public void clearChildren() {
        this.children.clear();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (!children.isEmpty()) {
            for (AccountPreviewTableRow child : children) {
                child.setVisibility(visibility);
            }
        }
    }
    // endregion

    // region UI related including refresh
    private void populateUI() {
        LayoutInflater.from(parentActivity).inflate(R.layout.table_row_main_account_preview, this);
        reloadChildren();
        updateUI();
    }

    @SuppressLint("DefaultLocale")
    public void updateUI() {
        parentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView name_label = findViewById(R.id.item_name_label);
                TextView value_label = findViewById(R.id.item_value_label);
                RadioButton senderRB = findViewById(R.id.radioButton_Sender);

                name_label.setText(referenceAccount.getName());

                // check if reference_account is a BudgetAccount, otherwise it's an AssetAccount
                if (referenceAccount instanceof BudgetAccountBE) {
                    BudgetAccountBE budget_account = (BudgetAccountBE) referenceAccount;
                    float current_sum = referenceAccount.getSum();
                    float current_budget = budget_account.indivCurrentBudget;
                    String value = String.format("%s (%.0f%%)",
                            Util.formatLargeFloatShort(current_sum),
                            (current_sum / current_budget) * 100);
                    value_label.setText(value);
                    senderRB.setVisibility(GONE);
                } else {
                    value_label.setText(Util.formatLargeFloatShort(referenceAccount.getSum()));
                    senderRB.setVisibility(VISIBLE);
                }
            }
        });
    }

    public void reloadChildren() {
        // only act if TableRow is referring to a BudgetAccount
        if (referenceAccount instanceof BudgetAccountBE) {
            // clear children list. If later none get created, then there shouldn't be any
            children.clear();
            // cast reference_account to BudgetAccountBE to access subBudgets
            BudgetAccountBE budget_account = (BudgetAccountBE) referenceAccount;
            // check if budgetAccount has sub budgets
            List<BudgetAccountBE> subBudgets = budget_account.getDirectSubBudgets();
            if (subBudgets.size() > 0) {
                for (BudgetAccountBE subBudget : subBudgets) {
                    children.add(new AccountPreviewTableRow(parentActivity, subBudget));
                }
            }
        } else {
            children.clear();
        }
    }


    public void setRefreshListener(RefreshListener listener) {
        this.refreshListener = listener;
    }

    private void notifyParentToRefresh() {
        if (refreshListener != null) {
            refreshListener.onRefresh();
        }
    }

    public LiveData<Boolean> getRefreshLiveData() {
        return refreshLiveData;
    }

    public void triggerRefresh() {
        refreshLiveData.setValue(true);
    }
    // endregion
}
