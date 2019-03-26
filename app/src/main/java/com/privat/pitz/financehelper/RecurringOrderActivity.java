package com.privat.pitz.financehelper;

import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import Backend.Util;
import Logic.RecurringOrderBE;

public class RecurringOrderActivity extends AccountActivity {
    List<RecurringOrderBE> orders = new ArrayList<>();

    @Override
    protected void workingThread() {
        orders = getModel().recurringOrders;
    }

    @Override
    protected void endWorkingThread() {
        setTitle(getResources().getString(R.string.label_recurring_orders) + " - " + Util.cutFileNameIfNecessary(getModel().currentFileName));
        populateUI();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) { return true; }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) { return true; }

    private void populateUI() {
        if (orders.size() == 0) {
            findViewById(R.id.root_layout).setVisibility(View.GONE);
            showToastLong(R.string.toast_error_no_entries);
            return;
        }
        filterEntries(null);
        EditText filter = findViewById(R.id.edit_filter);
        filter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                removeAllEntries();
                filterEntries(charSequence);
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    @Override
    protected void filterEntries(CharSequence filter) {
        float sum = 0.0f;
        TableLayout tabLay = findViewById(R.id.contentTable);
        if (filter == null) {
            for (RecurringOrderBE r : orders) {
                TableRow row1 = (TableRow) getLayoutInflater().inflate(R.layout.table_row_account, tabLay, false);
                TableRow row2 = (TableRow) getLayoutInflater().inflate(R.layout.table_row_recurring_domain_target, tabLay, false);
                TextView date = row1.findViewById(R.id.label_time);
                TextView description = row1.findViewById(R.id.label_description);
                TextView amount = row1.findViewById(R.id.label_amount);
                TextView pay = row2.findViewById(R.id.label_pay);
                TextView invest = row2.findViewById(R.id.label_invest);
                TextView delete = row2.findViewById(R.id.label_delete);
                delete.setTag(r);
                delete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(final View view) {
                        showConfirmDialog(R.string.question_delete_recurring_order, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                deleteOrder((RecurringOrderBE)view.getTag());
                            }
                        });
                    }
                });
                date.setText(Util.formatDateDisplay(r.getDate()));
                description.setText(r.getDescription());
                amount.setText(Util.formatFloat(r.getAmount()));
                pay.setText(r.getPayAccount());
                invest.setText(r.getInvestAccount());
                sum += r.getAmount();
                tabLay.addView(row1);
                tabLay.addView(row2);
            }
        } else {
            for (RecurringOrderBE r : orders) {
                if (r.getDescription().contains(filter) || r.getInvestAccount().contains(filter) || r.getPayAccount().contains(filter)) {
                    TableRow row1 = (TableRow) getLayoutInflater().inflate(R.layout.table_row_account, tabLay, false);
                    TableRow row2 = (TableRow) getLayoutInflater().inflate(R.layout.table_row_recurring_domain_target, tabLay, false);
                    TextView date = row1.findViewById(R.id.label_time);
                    TextView description = row1.findViewById(R.id.label_description);
                    TextView amount = row1.findViewById(R.id.label_amount);
                    TextView pay = row2.findViewById(R.id.label_pay);
                    TextView invest = row2.findViewById(R.id.label_invest);
                    TextView delete = row2.findViewById(R.id.label_delete);
                    delete.setTag(r);
                    delete.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(final View view) {
                            showConfirmDialog(R.string.question_delete_recurring_order, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    deleteOrder((RecurringOrderBE)view.getTag());
                                }
                            });
                        }
                    });
                    date.setText(Util.formatDateDisplay(r.getDate()));
                    description.setText(r.getDescription());
                    amount.setText(Util.formatFloat(r.getAmount()));
                    pay.setText(r.getPayAccount());
                    invest.setText(r.getInvestAccount());
                    sum += r.getAmount();
                    tabLay.addView(row1);
                    tabLay.addView(row2);
                }
            }
        }
        TextView textSum = findViewById(R.id.display_sum);
        textSum.setText(Util.formatFloat(sum));
    }

    public void deleteOrder(RecurringOrderBE order) {
        orders.remove(order);
        getModel().recurringOrders.remove(order);
        refreshView();
    }
}
