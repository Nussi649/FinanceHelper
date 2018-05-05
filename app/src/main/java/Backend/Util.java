package Backend;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.privat.pitz.financehelper.AbstractActivity;
import com.privat.pitz.financehelper.R;

import Logic.AccountBE;

public abstract class Util {


    public static void populatePayAccountsList(final AbstractActivity act, final LinearLayout parent) {
        final Model m = act.model;
        for (AccountBE a : m.payAccounts) {
            if (!a.getIsActive()) {
                continue;
            }
            TextView tv = (TextView) act.getLayoutInflater().inflate(R.layout.textview_accountselect, parent, false);
            tv.setText(a.getName());
            tv.setTag(a.getName());
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    parent.findViewWithTag(m.currentPayAcc.getName()).setBackgroundColor(act.getResources().getColor(R.color.colorPrimary));
                    m.currentPayAcc = act.getController().getPayAccountByName((String)v.getTag());
                    parent.findViewWithTag(m.currentPayAcc.getName()).setBackgroundColor(act.getResources().getColor(R.color.colorPrimaryDark));
                }
            });
            parent.addView(tv);
        }
        int i = 0;
        if (m.currentPayAcc == null) {
            while (parent.findViewWithTag(m.payAccounts.get(i).toString()) == null)
                i++;
            m.currentPayAcc = m.payAccounts.get(i);
        }
        parent.findViewWithTag(m.currentPayAcc.getName()).setBackgroundColor(act.getResources().getColor(R.color.colorPrimaryDark));
    }

    public static void populatePayAccountsList(final AbstractActivity act, final LinearLayout parent, final String appendix) {
        final Model m = act.model;
        for (AccountBE a : m.payAccounts) {
            if (!a.getIsActive()) {
                continue;
            }
            TextView tv = (TextView) act.getLayoutInflater().inflate(R.layout.textview_accountselect, parent, false);
            tv.setText(a.getName());
            tv.setTag(a.getName() + appendix);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    parent.findViewWithTag(m.currentPayAcc.getName() + appendix).setBackgroundColor(act.getResources().getColor(R.color.colorPrimary));
                    m.currentPayAcc = act.getController().getPayAccountByName((String)v.getTag());
                    parent.findViewWithTag(m.currentPayAcc.getName() + appendix).setBackgroundColor(act.getResources().getColor(R.color.colorPrimaryDark));
                }
            });
            parent.addView(tv);
        }
        int i = 0;
        if (m.currentPayAcc == null) {
            while (parent.findViewWithTag(m.payAccounts.get(i).toString()) == null)
                i++;
            m.currentPayAcc = m.payAccounts.get(i);
        }
        parent.findViewWithTag(m.currentPayAcc.getName() + appendix).setBackgroundColor(act.getResources().getColor(R.color.colorPrimaryDark));
    }

    public static void populateInvestAccountsList(final AbstractActivity act, final LinearLayout parent) {
        final Model m = act.model;
        for (AccountBE a : m.investAccounts) {
            if (!a.getIsActive()) {
                continue;
            }
            TextView tv = (TextView) act.getLayoutInflater().inflate(R.layout.textview_accountselect, parent, false);
            tv.setText(a.getName());
            tv.setTag(a.getName());
            tv.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    parent.findViewWithTag(m.currentInvestAcc.getName()).setBackgroundColor(act.getResources().getColor(R.color.colorPrimary));
                    m.currentInvestAcc = act.getController().getInvestAccountByName((String)v.getTag());
                    parent.findViewWithTag(m.currentInvestAcc.getName()).setBackgroundColor(act.getResources().getColor(R.color.colorPrimaryDark));
                }
            });
            parent.addView(tv);
        }
        int i = 0;
        if (m.currentInvestAcc == null) {
            while (parent.findViewWithTag(m.investAccounts.get(i).toString()) == null)
                i++;
            m.currentInvestAcc = m.investAccounts.get(i);
        }
        parent.findViewWithTag(m.currentInvestAcc.getName()).setBackgroundColor(act.getResources().getColor(R.color.colorPrimaryDark));
    }
}
