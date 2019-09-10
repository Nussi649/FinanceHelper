package Backend;

import android.support.annotation.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Logic.AccountBE;

public class PastMonth {
    private String mName;
    private Map<String, Float> mInvestAccounts;
    private float mTotalSum;

    // Constructors
    public PastMonth(String mName) {
        this.mName = mName;
        mInvestAccounts = new HashMap<>();
    }

    public PastMonth(String mName, @NonNull List<AccountBE> accounts) {
        this.mName = mName;
        mInvestAccounts = new HashMap<>();
        mTotalSum = 0.0f;
        for (AccountBE a : accounts) {
            mInvestAccounts.put(a.getName(), a.getSum());
            mTotalSum += a.getSum();
        }
    }


    // getters
    public Map<String, Float> getAccountList() {
        return mInvestAccounts;
    }

    public String getName() {
        return mName;
    }

    public float getTotalSum() {
        return mTotalSum;
    }


    // setter
    public void addEntry(@NonNull AccountBE entry) {
        mInvestAccounts.put(entry.getName(), entry.getSum());
        mTotalSum += entry.getSum();
    }
}
