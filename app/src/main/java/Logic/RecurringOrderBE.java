package Logic;

import java.util.Date;

public class RecurringOrderBE extends EntryBE {
    String mPayAccount;
    String mInvestAccount;

    public RecurringOrderBE(float amount, String description, Date date, String payAccount, String investAccount) {
        super(amount, description, date);
        mPayAccount = payAccount;
        mInvestAccount = investAccount;
    }

    public String getPayAccount() {
        return mPayAccount;
    }

    public String getInvestAccount() {
        return mInvestAccount;
    }
}
