package View;

import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.privat.pitz.financehelper.R;

import java.util.List;

import Backend.Const;
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
        TextView label = findViewById(R.id.label_account);
        label.setText(mAccount.getName());

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

    public void adjustWidth() {
        ViewGroup.LayoutParams params = getLayoutParams();
        TextView sollLabel = findViewById(R.id.textViewSoll);
        TextView habenLabel = findViewById(R.id.textViewHaben);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(params);
        if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            layoutParams = new LinearLayout.LayoutParams(
                    Const.ACOUNT_VIEW_LANDSCAPE_WIDTH, params.height);
            layoutParams.setMargins(Const.MARGIN_ACCOUNT_VIEW, Const.MARGIN_ACCOUNT_VIEW, Const.MARGIN_ACCOUNT_VIEW, 0);
            sollLabel.setWidth(Const.ACOUNT_VIEW_LANDSCAPE_WIDTH/2-1);
            habenLabel.setWidth(Const.ACOUNT_VIEW_LANDSCAPE_WIDTH/2-1);
        } else {
            layoutParams = new TableRow.LayoutParams(
                    ((View) getParent().getParent()).getMeasuredWidth()/2, params.height);
            layoutParams.setMargins(Const.MARGIN_ACCOUNT_VIEW, Const.MARGIN_ACCOUNT_VIEW, Const.MARGIN_ACCOUNT_VIEW, 0);
            sollLabel.setWidth(params.width/2-1);
            habenLabel.setWidth(params.width/2-1);
        }
        setLayoutParams(layoutParams);
    }
}
