package View;

import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.privat.pitz.financehelper.R;

import java.util.List;

import Backend.Const;
import Backend.Util;
import Logic.AccountBE;
import Logic.EntryBE;

public class AccountView extends LinearLayout {
    Context context;
    AccountBE mAccount;

    public AccountView(Context context, AccountBE acc) {
        super(context);
        this.context = context;
        mAccount = acc;
        populateUI();
    }

    private void populateUI() {
        LayoutInflater.from(context).inflate(R.layout.view_account, this);
        LinearLayout content = findViewById(R.id.contentTable);
        List<EntryBE> entries = mAccount.getEntries();
        TextView label = findViewById(R.id.label_account);
        label.setText(mAccount.getName());
        if (entries.size() == 0) {
            findViewById(R.id.sum_block).setVisibility(GONE);
        } else {
            if (entries.size() > 10) {
                content.addView(new AccountTableRow(context, "..."));
                for (EntryBE e : entries.subList(entries.size() - 10, entries.size())) {
                    AccountTableRow row = new AccountTableRow(context, e.getDescription(), e.getAmount());
                    content.addView(row);
                }
            } else {
                for (EntryBE e : entries) {
                    AccountTableRow row = new AccountTableRow(context, e.getDescription(), e.getAmount());
                    content.addView(row);
                }
            }
            TextView sum = findViewById(R.id.display_sum);
            sum.setText(Util.formatFloat(mAccount.getSum()));
        }
    }

    public void setLayoutWidth(int width) {
        LinearLayout root = findViewById(R.id.root_layout);
        LayoutParams params = (LinearLayout.LayoutParams) root.getLayoutParams();
        params.width = width;
        root.setLayoutParams(params);
        LinearLayout content = findViewById(R.id.contentTable);
        for (int i = 0; i < content.getChildCount(); i++) {
            ((AccountTableRow) content.getChildAt(i)).setWidth(width);
        }
    }
}
