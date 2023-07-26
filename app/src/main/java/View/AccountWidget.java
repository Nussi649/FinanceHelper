package View;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.privat.pitz.financehelper.R;

import java.util.ArrayList;
import java.util.List;

import Backend.Util;
import Logic.AccountBE;
import Logic.TxBE;

public class AccountWidget extends LinearLayout {
    @SuppressLint("InflateParams")
    public static AccountWidget getInstance(Context context) {
        return (AccountWidget) LayoutInflater.from(context).inflate(R.layout.widget_asset_account, null);
    }

    Context context;
    AccountBE account;

    TextView accountLabel;
    TextView sumLabel;
    LinearLayout txList;
    List<AccountWidgetRow> rows = new ArrayList<>();

    // region Constructors and init
    public AccountWidget(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }

    public void init(AccountBE acc) {
        account = acc;
        initViews();
    }

    public void initViews() {
        accountLabel = findViewById(R.id.label_account);
        sumLabel = findViewById(R.id.display_sum);
        txList = findViewById(R.id.contentTable);
        reloadTx();
    }
    // endregion

    public AccountBE getAccount() {
        return account;
    }

    @Override
    public String toString() {
        if (account != null)
            return account.toString();
        return super.toString();
    }

    private void reloadTx() {
        // discard all rows from own list and from content table view
        rows = new ArrayList<>();
        txList.removeAllViews();
        // get list of TxBE objects to fill widget with
        List<TxBE> entries = account.getTxList();
        if (entries.size() == 0) {
            findViewById(R.id.sum_block).setVisibility(GONE);
        } else {
            if (entries.size() > 10) {
                AccountWidgetRow firstRow = AccountWidgetRow.getInstance(context);
                firstRow.initDefault();
                rows.add(firstRow);
                txList.addView(firstRow);
                entries = entries.subList(entries.size() - 10, entries.size());
            }
            for (TxBE e : entries) {
                AccountWidgetRow row = AccountWidgetRow.getInstance(context);
                row.init(e.getDescription(), e.getAmount());
                rows.add(row);
                txList.addView(row);
            }
        }
    }

    public void refreshUI() {
        accountLabel.setText(account.getName());
        sumLabel.setText(Util.formatLargeFloatDisplay(account.getSum()));
        for (AccountWidgetRow row : rows) {
            row.refreshUI();
        }
    }
}
