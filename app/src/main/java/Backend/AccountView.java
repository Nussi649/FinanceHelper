package Backend;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.privat.pitz.financehelper.R;

import java.util.List;

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
        LinearLayout sollPanel = findViewById(R.id.sollPanel);
        LinearLayout habenPanel = findViewById(R.id.habenPanel);
        List<EntryBE> sollEntries = mAccount.getSoll();
        List<EntryBE> habenEntries = mAccount.getHaben();

        for (EntryBE e : sollEntries) {
            TextView tv = new TextView(context);
            tv.setText(e.getDescription() + ": " + e.getAmount());
            sollPanel.addView(tv);
        }
        for (EntryBE e : habenEntries) {
            TextView tv = new TextView(context);
            tv.setText(e.getDescription() + ": " + e.getAmount());
            habenPanel.addView(tv);
        }
    }
}
