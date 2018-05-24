package Backend;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.privat.pitz.financehelper.AbstractActivity;
import com.privat.pitz.financehelper.R;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import Logic.AccountBE;

public abstract class Util {


    public static void populatePayAccountsList(final AbstractActivity act, final LinearLayout parent) {
        final Model m = act.model;
        parent.removeAllViews();
        if (m.payAccounts == null) {
            return;
        }
        if (m.payAccounts.size() == 0) {
            return;
        }
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
        parent.removeAllViews();
        if (m.payAccounts == null) {
            return;
        }
        if (m.payAccounts.size() == 0) {
            return;
        }
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
                    String tag = (String) v.getTag();
                    if (appendix == Const.APPENDIX_PAY_SENDER) {
                        parent.findViewWithTag(m.currentPayAcc.getName() + appendix).setBackgroundColor(act.getResources().getColor(R.color.colorPrimary));
                        m.currentPayAcc = act.getController().getPayAccountByName(tag.substring(0, tag.length() - appendix.length()));
                        parent.findViewWithTag(m.currentPayAcc.getName() + appendix).setBackgroundColor(act.getResources().getColor(R.color.colorPrimaryDark));
                    } else if (appendix == Const.APPENDIX_PAY_RECIPIENT) {
                        if (m.transferRecipientAcc != null)
                            parent.findViewWithTag(m.transferRecipientAcc.getName() + appendix).setBackgroundColor(act.getResources().getColor(R.color.colorPrimary));
                        m.transferRecipientAcc = act.getController().getPayAccountByName(tag.substring(0, tag.length() - appendix.length()));
                        parent.findViewWithTag(m.transferRecipientAcc.getName() + appendix).setBackgroundColor(act.getResources().getColor(R.color.colorPrimaryDark));
                    }
                }
            });
            parent.addView(tv);
        }
        if (appendix == Const.APPENDIX_PAY_SENDER) {
            int i = 0;
            if (m.currentPayAcc == null) {
                while (parent.findViewWithTag(m.payAccounts.get(i).toString()) == null)
                    i++;
                m.currentPayAcc = m.payAccounts.get(i);
            }
            parent.findViewWithTag(m.currentPayAcc.getName() + appendix).setBackgroundColor(act.getResources().getColor(R.color.colorPrimaryDark));
        }
    }

    public static void populateInvestAccountsList(final AbstractActivity act, final LinearLayout parent) {
        final Model m = act.model;
        parent.removeAllViews();
        if (m.investAccounts == null) {
            return;
        }
        if (m.investAccounts.size() == 0) {
            return;
        }
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

    public static String formatFloat(float input) {
        return String.format("%.2f", input).replace(',','.');
    }

    public static String formatDateDisplay(Date input) {
        android.text.format.DateFormat df = new android.text.format.DateFormat();
        return df.format(Const.DATE_FORMAT_DISPLAY, input).toString();
    }

    public static String formatDateSave(Date input) {
        android.text.format.DateFormat df = new android.text.format.DateFormat();
        return df.format(Const.DATE_FORMAT_SAVE, input).toString();
    }

    public static Date parseDateSave(String input) throws ParseException {
        SimpleDateFormat df = new SimpleDateFormat(Const.DATE_FORMAT_SAVE);
        return df.parse(input);
    }

    public static String sha256(String s) {
        try {
            // Create SHA-256 Hash
            MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            // Create Hex String
            StringBuffer hexString = new StringBuffer();
            for (int i=0; i<messageDigest.length; i++)
                hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static BigInteger stringToBigInt(String s) {
        byte[] bytes = s.getBytes();
        return new BigInteger(bytes);
    }

    public static String bigIntToString(BigInteger b) {
        byte[] bytes = b.toByteArray();
        return new String(bytes);
    }
}
