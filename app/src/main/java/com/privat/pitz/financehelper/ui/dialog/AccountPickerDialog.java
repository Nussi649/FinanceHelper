package com.privat.pitz.financehelper.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.privat.pitz.financehelper.R;
import com.privat.pitz.financehelper.core.Model;
import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;

/**
 * Lets the user pick the account an entry should live on: every active asset account, then every
 * active budget account with its sub-budgets indented beneath it, the same set the main screen
 * offers. The entry's current account is shown but cannot be picked, so the list still reads as
 * "you are here".
 */
public class AccountPickerDialog {

    private static final class Row {
        final AccountBE account;  // null for a section header
        final String label;
        final int depth;

        Row(AccountBE account, String label, int depth) {
            this.account = account;
            this.label = label;
            this.depth = depth;
        }
    }

    private final Context context;
    private final AccountBE current;
    private final List<Row> rows = new ArrayList<>();

    public AccountPickerDialog(Context context, Model model, AccountBE current) {
        this.context = context;
        this.current = current;

        rows.add(new Row(null, context.getString(R.string.label_assets), 0));
        for (AccountBE account : model.asset_accounts)
            if (account.getIsActive())
                rows.add(new Row(account, account.getName(), 0));

        rows.add(new Row(null, context.getString(R.string.label_budgets), 0));
        for (BudgetAccountBE account : model.budget_accounts)
            addBudgetRows(account, 0);
    }

    private void addBudgetRows(BudgetAccountBE account, int depth) {
        if (!account.getIsActive())
            return;
        rows.add(new Row(account, account.getName(), depth));
        for (BudgetAccountBE sub : account.getDirectSubBudgets())
            addBudgetRows(sub, depth + 1);
    }

    public void show(Consumer<AccountBE> onPicked) {
        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() { return rows.size(); }

            @Override
            public Object getItem(int position) { return rows.get(position); }

            @Override
            public long getItemId(int position) { return position; }

            @Override
            public boolean areAllItemsEnabled() { return false; }

            @Override
            public boolean isEnabled(int position) {
                AccountBE account = rows.get(position).account;
                return account != null && account != current;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = convertView instanceof TextView
                        ? (TextView) convertView : new TextView(context);
                Row row = rows.get(position);
                float density = context.getResources().getDisplayMetrics().density;
                int pad = (int) (12 * density);
                if (row.account == null) {
                    view.setText(row.label);
                    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    view.setTypeface(null, Typeface.BOLD);
                    view.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary));
                    view.setPadding(2 * pad, pad + pad / 2, 2 * pad, pad / 3);
                } else {
                    boolean isCurrent = row.account == current;
                    view.setText(isCurrent
                            ? context.getString(R.string.label_account_current, row.label)
                            : row.label);
                    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                    view.setTypeface(null, Typeface.NORMAL);
                    view.setTextColor(isCurrent ? 0xFF9E9E9E : 0xFF212121);
                    view.setPadding(2 * pad + row.depth * 2 * pad, pad, 2 * pad, pad);
                }
                return view;
            }
        };

        new AlertDialog.Builder(context)
                .setTitle(R.string.label_pick_account)
                .setAdapter(adapter, (d, which) -> onPicked.accept(rows.get(which).account))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
