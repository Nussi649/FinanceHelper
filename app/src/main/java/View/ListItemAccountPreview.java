package View;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
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
public class ListItemAccountPreview extends LinearLayout {

    private AccountBE referenceAccount;
    private MainActivity parentActivity;
    private final List<ListItemAccountPreview> children = new ArrayList<>();
    private final MutableLiveData<Boolean> refreshLiveData = new MutableLiveData<>();
    private RefreshListener refreshListener;
    private TextView nameLabel;
    private TextView valueLabel;
    private RadioButton senderRB;
    private RadioButton receiverRB;
    private View spacer;
    private int hierarchyLevel;

    // region Constructors and init
    public ListItemAccountPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void init(MainActivity parentActivity, AccountBE accountObject, int hierarchyLevel) {
        this.referenceAccount = accountObject;
        this.parentActivity = parentActivity;
        this.refreshListener = parentActivity;
        this.hierarchyLevel = hierarchyLevel;
        reloadChildren();
        initViews();
        updateUI();
    }

    public void init(MainActivity parentActivity, AccountBE accountObject) {
        init(parentActivity, accountObject, 0);
    }

    private void initViews() {
        nameLabel = findViewById(R.id.item_name_label);
        valueLabel = findViewById(R.id.item_value_label);
        senderRB = findViewById(R.id.radioButton_Sender);
        receiverRB = findViewById(R.id.radioButton_Receiver);
        spacer = findViewById(R.id.indent_view);
    }
    // endregion

    // region sets / gets
    public List<ListItemAccountPreview> getAllChildren() {
        List<ListItemAccountPreview> allChildren = new ArrayList<>();
        for (ListItemAccountPreview child : children) {
            allChildren.add(child);
            allChildren.addAll(child.getAllChildren());
        }
        return allChildren;
    }

    public RadioButton getRBSender() {
        return senderRB;
    }

    public RadioButton getRBReceiver() {
        return receiverRB;
    }

    public AccountBE getReferenceAccount() {
        return referenceAccount;
    }

    public void addChild(ListItemAccountPreview newChild) {
        this.children.add(newChild);
    }

    public void addChildren(List<ListItemAccountPreview> newChildren) {
        this.children.addAll(newChildren);
    }

    public void clearChildren() {
        this.children.clear();
    }
    // endregion

    // region UI related including refresh
    @SuppressLint("DefaultLocale")
    public void updateUI() {
        parentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // set account name
                nameLabel.setText(referenceAccount.getName());

                // set spacer width for indentation of lower hierarchical levels
                ViewGroup.LayoutParams params = spacer.getLayoutParams();
                params.width = (int) (10 * hierarchyLevel * getResources().getDisplayMetrics().density);
                spacer.setLayoutParams(params);

                // set name text size according to hierarchyLevel
                nameLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16 - 2 * hierarchyLevel);

                // Set background color based on hierarchy level
                if (hierarchyLevel > 0)
                    setBackgroundColor(ContextCompat.getColor(getContext(), R.color.colorSubtleDistinction2));  // Light grey color
                else
                    setBackgroundColor(ContextCompat.getColor(getContext(), R.color.colorSubtleDistinction));  // Lighter grey color

                // check if referenceAccount is a BudgetAccount, otherwise it's an AssetAccount
                if (referenceAccount instanceof BudgetAccountBE) {
                    BudgetAccountBE budget_account = (BudgetAccountBE) referenceAccount;
                    float current_sum = referenceAccount.getSum();
                    float current_budget = budget_account.indivCurrentBudget;
                    String value = String.format("%s (%.0f%%)",
                            Util.formatLargeFloatShort(current_sum),
                            (current_sum / current_budget) * 100);
                    valueLabel.setText(value);
                    senderRB.setVisibility(GONE);
                } else {
                    valueLabel.setText(Util.formatLargeFloatShort(referenceAccount.getSum()));
                    senderRB.setVisibility(VISIBLE);
                }
            }
        });
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (!children.isEmpty()) {
            for (ListItemAccountPreview child : children) {
                child.setVisibility(visibility);
            }
        }
    }

    @SuppressLint("InflateParams")
    public void reloadChildren() {
        // only act if TableRow is referring to a BudgetAccount
        if (referenceAccount instanceof BudgetAccountBE) {
            // clear children list. If later none get created, then there shouldn't be any
            clearChildren();
            // cast reference_account to BudgetAccountBE to access subBudgets
            BudgetAccountBE budget_account = (BudgetAccountBE) referenceAccount;
            // check if budgetAccount has sub budgets
            List<BudgetAccountBE> subBudgets = budget_account.getDirectSubBudgets();
            if (subBudgets.size() > 0) {
                for (BudgetAccountBE subBudget : subBudgets) {
                    ListItemAccountPreview newItem = (ListItemAccountPreview) LayoutInflater.from(parentActivity).inflate(R.layout.list_item_account_preview_main, null);
                    newItem.init(parentActivity, subBudget, hierarchyLevel + 1);
                    addChild(newItem);
                }
            }
        } else {
            clearChildren();
        }
    }
    // endregion

    // region refresh related
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
