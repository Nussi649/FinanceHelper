package View;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
    @SuppressLint("InflateParams")
    public static ListItemAccountPreview getInstance(MainActivity parentActivity, LinearLayout parentContainer) {
        ListItemAccountPreview instance = (ListItemAccountPreview) LayoutInflater.from(parentActivity).inflate(R.layout.list_item_account_preview_main, parentContainer, false);
        instance.parentActivity = parentActivity;
        instance.parentContainer = parentContainer;
        return instance;
    }

    private AccountBE referenceAccount;
    private MainActivity parentActivity;
    private LinearLayout parentContainer;
    private final List<ListItemAccountPreview> children = new ArrayList<>();
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

    public void init(AccountBE accountObject, int hierarchyLevel) {
        this.referenceAccount = accountObject;
        this.hierarchyLevel = hierarchyLevel;
        reloadChildren();
        initViews();
        updateUI();
    }

    public void init(AccountBE accountObject) {
        init(accountObject, 0);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initViews() {
        nameLabel = findViewById(R.id.item_name_label);
        valueLabel = findViewById(R.id.item_value_label);
        senderRB = findViewById(R.id.radioButton_Sender);
        receiverRB = findViewById(R.id.radioButton_Receiver);
        spacer = findViewById(R.id.indent_view);

        nameLabel.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // When finger touches the view, set it to selected to start the marquee
                        v.setSelected(true);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // When finger lifts up or the touch event is cancelled, stop the marquee
                        v.setSelected(false);
                        break;
                }
                return true; // return true to indicate that the event is consumed
            }
        });

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
                    float current_budget = budget_account.indivAvailableBudget;
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
                    ListItemAccountPreview newItem = getInstance(parentActivity, parentContainer);
                    newItem.init(subBudget, hierarchyLevel + 1);
                    addChild(newItem);
                }
            }
        } else {
            clearChildren();
        }
    }
    // endregion
}
