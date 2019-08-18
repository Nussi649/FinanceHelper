package View;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.privat.pitz.financehelper.R;

import java.util.List;

import Backend.Util;
import Logic.EntryBE;

public class IncomeListDialog extends ScrollView {
    List<EntryBE> mIncomeList;
    Context mContext;

    public IncomeListDialog(Context context, List<EntryBE> incomeList) {
        super(context);
        mIncomeList = incomeList;
        mContext = context;
        populateUI();
    }

    private void populateUI() {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        ScrollView load = (ScrollView) inflater.inflate(R.layout.activity_account, null);
        LinearLayout rootLayout = (LinearLayout) load.findViewById(R.id.root_layout);
        load.removeAllViews();
        addView(rootLayout);
        float sum = 0.0f;
        TableLayout content = findViewById(R.id.contentTable);
        for (EntryBE e : mIncomeList) {
            TableRow row = (TableRow) inflater.inflate(R.layout.table_row_account, content, false);
            TextView date = row.findViewById(R.id.label_time);
            TextView description = row.findViewById(R.id.label_description);
            TextView amount = row.findViewById(R.id.label_amount);
            date.setText(Util.formatDateDisplay(e.getDate()));
            description.setText(e.getDescription());
            amount.setText(Util.formatFloat(e.getAmount()));
            sum += e.getAmount();
            content.addView(row);
        }
        TextView textSum = findViewById(R.id.display_sum);
        textSum.setText(Util.formatFloat(sum));
    }
}
