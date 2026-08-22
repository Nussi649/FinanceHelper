package com.privat.pitz.financehelper;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import org.json.JSONException;

import java.io.IOException;

import com.privat.pitz.financehelper.core.Util;
import com.privat.pitz.financehelper.data.RecurringTxBE;
import com.privat.pitz.financehelper.ui.TxListSection;
import com.privat.pitz.financehelper.ui.adapter.RecurringTxAdapter;

public class RecurringTxActivity extends AbstractActivity {
    private TxListSection section;

    // region AbstractActivity & Activity Overrides
    @Override
    protected void workingThread() {

    }

    @Override
    protected void endWorkingThread() {
        setContentView(R.layout.activity_asset_account_details);

        View root = findViewById(R.id.root_layout);

        // The inherited populateUI() used to force this label to the "Σ" short form (rather than
        // the layout's default "Total Σ") whenever this layout backs an activity screen. Preserve
        // that so the recurring-orders screen keeps looking the way it always has.
        TextView labelSigma = findViewById(R.id.container_tx_sum).findViewById(R.id.label_sigma);
        labelSigma.setText(R.string.label_sum_tx);

        TextView indivValue = findViewById(R.id.container_tx_sum).findViewById(R.id.total_current_value);

        RecurringTxAdapter adapter = new RecurringTxAdapter(this);

        // No swipeActions: recurring orders have a per-row delete button (RecurringTxAdapter ->
        // parentActivity.deleteOrder(entry)) instead of the swipe-to-edit/delete gesture that
        // TxListSection would otherwise attach. Passing null here means no ItemTouchHelper is
        // attached at all, which is the correct behavior for this screen.
        section = new TxListSection(
                root,
                adapter,
                () -> getModel().recurringTx,
                RecurringTxAdapter.MATCH_SENDER_RECEIVER_OR_DESCRIPTION,
                sum -> {
                    String newString = Util.formatLargeFloatDisplay(sum) + "x";
                    indivValue.setText(newString.replace("x", getString(R.string.label_currency)));
                },
                null);

        // The inherited "no entries" check used to look at model.currentIncome (the income list),
        // which has nothing to do with recurring orders - a live bug. This keys the toast off the
        // correct list (model.recurringTx, via the section that was just populated from it).
        if (section.isEmpty()) {
            showToastLong(R.string.toast_error_no_entries);
        }

        setTitle();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) { return true; }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) { return true; }
    // endregion

    public void deleteOrder(RecurringTxBE recurringTx) {
        try {
            boolean result = controller.deleteRecurringTx(recurringTx);
            if (result) {
                showToastLong(R.string.toast_success_delete_recurring_tx);
                onRefresh();
            } else {
                showToastLong(R.string.toast_error_recurring_tx_not_found);
            }
        } catch (JSONException e) {
            showToastLong(R.string.toast_error_JSONError);
        } catch (IOException e) {
            showToastLong(R.string.toast_error_IOError);
        }
    }

    protected void setTitle() {
        setCustomTitle();
        setCustomTitleDetails(getString(R.string.label_recurring_orders));
    }

    @Override
    public void onRefresh() {
        section.refresh();
    }
}
