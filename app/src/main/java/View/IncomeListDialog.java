package View;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;

import com.privat.pitz.financehelper.AbstractActivity;
import com.privat.pitz.financehelper.R;

import java.util.ArrayList;
import java.util.List;

import Backend.TxListAdapter;
import Backend.Util;
import Logic.TxBE;

public class IncomeListDialog extends LinearLayout {
    List<TxBE> mIncomeList;
    AbstractActivity parent;
    TxListAdapter listAdapter;

    public IncomeListDialog(AbstractActivity parent, List<TxBE> incomeList) {
        super(parent);
        this.mIncomeList = incomeList;
        this.parent = parent;
        populateUI();
    }

    private void populateUI() {
        LayoutInflater inflater = LayoutInflater.from(parent);
        inflater.inflate(R.layout.activity_asset_account_details, this, true);
        // Now this is the LinearLayout as defined in the inflated layout

        if (mIncomeList.size() == 0) {
            this.setVisibility(View.GONE);
            parent.showToastLong(R.string.toast_error_no_entries);
            return;
        }

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        listAdapter = new TxListAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(parent));
        recyclerView.setAdapter(listAdapter);

        filterEntries(null);

        SearchView searchView = findViewById(R.id.search_filter);
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
    }

    private void filterEntries(CharSequence filter) {
        float sum = 0.0f;
        List<TxBE> entries;
        if (filter == null) {
            entries = mIncomeList;
        } else {
            entries = new ArrayList<>();
            for (TxBE entr : mIncomeList) {
                if (entr.getDescription().toUpperCase().contains(filter.toString().toUpperCase())) {
                    entries.add(entr);
                }
            }
        }

        for (TxBE entr : entries) {
            sum += entr.getAmount();
        }

        TextView textSum = findViewById(R.id.display_sum);
        textSum.setText(Util.formatLargeFloatDisplay(sum));

        listAdapter.setEntries(entries);
    }
}
