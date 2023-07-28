package View.Dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.privat.pitz.financehelper.R;

import Backend.TxListAdapter;
import Backend.Util;
import Logic.TxBE;

import java.util.ArrayList;
import java.util.List;

public class IncomeListDialog {
    private final Context context;
    private final List<TxBE> incomeList;
    private final TxListAdapter listAdapter = new TxListAdapter();
    private RecyclerView recyclerView;
    private SearchView searchView;
    private TextView textSum;

    public IncomeListDialog(Context context, List<TxBE> incomeList) {
        this.context = context;
        this.incomeList = incomeList;
    }

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);

        View view = inflater.inflate(R.layout.activity_asset_account_details, null);
        recyclerView = view.findViewById(R.id.recyclerView);
        searchView = view.findViewById(R.id.search_filter);
        textSum = view.findViewById(R.id.display_sum);

        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(listAdapter);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextChange(String newText) {
                filterEntries(newText);
                return true;
            }

            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }
        });

        filterEntries(null);

        builder.setView(view)
                .setPositiveButton(context.getString(R.string.confirm), null);
        builder.setTitle(R.string.label_income_list);

        builder.create().show();
    }

    private void filterEntries(CharSequence filter) {
        float sum = 0.0f;
        List<TxBE> entries;
        if (filter == null) {
            entries = incomeList;
        } else {
            entries = new ArrayList<>();
            for (TxBE entr : incomeList) {
                if (entr.getDescription().toUpperCase().contains(filter.toString().toUpperCase())) {
                    entries.add(entr);
                }
            }
        }

        for (TxBE entr : entries) {
            sum += entr.getAmount();
        }
        textSum.setText(Util.formatLargeFloatDisplay(sum));
        listAdapter.setEntries(entries);
    }
}