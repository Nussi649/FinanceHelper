package com.privat.pitz.financehelper;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;

import java.io.IOException;

import com.privat.pitz.financehelper.core.Util;
import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.ui.TxListSection;
import com.privat.pitz.financehelper.ui.TxSwipeActions;
import com.privat.pitz.financehelper.ui.adapter.TxListAdapter;

public class AssetAccountDetailsActivity extends AbstractActivity {
    private AccountBE mAccount;
    private TxListSection section;
    private TextView indivValue;

    // region AbstractActivity & Activity Overrides
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void workingThread() {
        mAccount = getModel().currentInspectedAccount;
    }

    @Override
    protected void endWorkingThread() {
        setContentView(R.layout.activity_asset_account_details);

        View root = findViewById(R.id.root_layout);
        RecyclerView recyclerView = root.findViewById(R.id.recyclerView);
        indivValue = findViewById(R.id.container_tx_sum).findViewById(R.id.total_current_value);

        TxListAdapter listAdapter = new TxListAdapter();
        TxSwipeActions swipeActions = new TxSwipeActions(this, controller, mAccount, listAdapter, recyclerView, this);

        section = new TxListSection(
                root,
                listAdapter,
                () -> mAccount.getTxList(),
                TxListSection.MATCH_DESCRIPTION,
                this::renderTxSum,
                swipeActions);

        // The inherited populateUI() used to force this label to the short "Σ" form (rather than
        // the layout's default "Total Σ") for the per-entry sum card. Preserve that.
        TextView labelSigma = findViewById(R.id.container_tx_sum).findViewById(R.id.label_sigma);
        labelSigma.setText(R.string.label_sum_tx);

        if (section.isEmpty()) {
            showToastLong(R.string.toast_error_no_entries);
        }

        setTitle();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_account_details, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.item_delete_account)
            deleteAccount();
        return super.onOptionsItemSelected(item);
    }
    // endregion

    private void setTitle() {
        setCustomTitle();
        setCustomTitleDetails(mAccount.toString());
    }

    @Override
    public void onRefresh() {
        // Deliberately the full account sum, not section.refresh(). The pre-refactor code did
        // setTxSum(getReference().getSum()) here, which ignores any active search filter - so
        // after editing or deleting a transaction while filtering, the sum shows the whole
        // account while the list shows the filtered subset. That disagreement is pre-existing
        // and BudgetAccountDetailsActivity.onRefresh does the same; changing it is a behaviour
        // fix that belongs in its own commit, not in this move.
        renderTxSum(mAccount.getSum());
    }

    private void renderTxSum(float newValue) {
        String newString = Util.formatLargeFloatDisplay(newValue) + "x";
        indivValue.setText(newString.replace("x", getString(R.string.label_currency)));
    }

    private void deleteAccount() {
        Dialog.OnClickListener listener = (dialogInterface, i) -> {
            try {
                boolean result = controller.deleteAccount(mAccount);
                if (result) {
                    redirectAfterAccountDelete();
                    showToastLong(R.string.toast_success_delete_account);
                } else {
                    showToastLong(R.string.toast_error_account_not_found);
                }
            } catch (JSONException | IOException e) {
                showErrorToast(e);
            }
        };
        showConfirmDialog(R.string.question_delete_account, listener);
    }

    private void redirectAfterAccountDelete() {
        startActivity(MainActivity.class);
    }
}
