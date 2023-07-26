package Logic;

import java.util.ArrayList;
import java.util.List;

import Backend.Const;

public class AccountBE {
    protected String name;
    protected List<TxBE> txList;
    protected boolean isActive;
    private boolean isProfitNeutral;

    public AccountBE(String name) {
        this.name = name;
        txList = new ArrayList<>();
        isActive = true;
        isProfitNeutral = false;
    }

    //region get
    public List<TxBE> getTxList() {
        return txList;
    }

    public float getSum() {
        float sum = 0.0f;
        for (TxBE e: txList) {
            sum += e.getAmount();
        }
        return sum;
    }

    public float getSumWithoutClosing() {
        float sum = 0.0f;
        for (TxBE e: txList) {
            if (e.getDescription().equals(Const.DESC_CLOSING))
                continue;
            sum += e.getAmount();
        }
        return sum;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }

    public boolean getIsActive() { return isActive;}
    public boolean getIsProfitNeutral() { return isProfitNeutral;}
    //endregion

    //region add/remove entries
    public void addTx(TxBE newEntry) {
        txList.add(newEntry);
    }

    public void removeTx(TxBE toRemove) {
        txList.remove(toRemove);
    }

    public void dropLastTx() {
        if (txList.size() == 0)
            return;
        txList.remove(txList.size() -1);
    }
    //endregion

    public void setActive(boolean active) {
        isActive = active;
    }
    public void setProfitNeutral(boolean profitNeutral) {
        isProfitNeutral = profitNeutral;
    }

    // normal reset, used for starting new reporting period. only empty tx list if account is not profit neutral
    public void reset() {
        if (isProfitNeutral)
            return;
        txList = new ArrayList<>();
    }

    public void forceReset() {
        txList = new ArrayList<>();
    }
}
