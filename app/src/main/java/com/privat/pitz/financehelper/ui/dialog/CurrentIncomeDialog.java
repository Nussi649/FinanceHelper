package com.privat.pitz.financehelper.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.privat.pitz.financehelper.R;

import com.privat.pitz.financehelper.ui.TxListSection;
import com.privat.pitz.financehelper.ui.adapter.TxListAdapter;
import com.privat.pitz.financehelper.core.Util;
import com.privat.pitz.financehelper.data.TxBE;

import java.util.List;

public class CurrentIncomeDialog {
    private final Context context;
    private final List<TxBE> incomeList;
    private final TxListAdapter listAdapter = new TxListAdapter();

    public CurrentIncomeDialog(Context context, List<TxBE> incomeList) {
        this.context = context;
        this.incomeList = incomeList;
    }

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);

        View view = inflater.inflate(R.layout.activity_asset_account_details, null);

        TextView indivValue = view.findViewById(R.id.container_tx_sum).findViewById(R.id.total_current_value);

        new TxListSection(
                view,
                listAdapter,
                () -> incomeList,
                TxListSection.MATCH_DESCRIPTION,
                sum -> {
                    String newString = Util.formatLargeFloatDisplay(sum) + "x";
                    indivValue.setText(newString.replace("x", context.getString(R.string.label_currency)));
                },
                null);

        builder.setView(view)
                .setPositiveButton(context.getString(R.string.confirm), null);
        builder.setTitle(R.string.label_income_list);

        builder.create().show();
    }
}
