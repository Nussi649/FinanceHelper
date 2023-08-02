package Logic;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import Backend.Const;

public class AccountBE {
    protected String name;
    protected List<TxBE> txList;
    protected boolean isActive;
    protected boolean autoRenew;

    public AccountBE(String name) {
        this.name = name;
        txList = new ArrayList<>();
        isActive = true;
        autoRenew = true;
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

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }

    public boolean getIsActive() { return isActive;}
    public boolean getAutoRenew() { return autoRenew;}
    //endregion

    //region add/remove entries
    public int getTxIndex(TxBE tx) {
        return txList.indexOf(tx);
    }
    public void addTx(TxBE newEntry) {
        txList.add(newEntry);
    }

    public void addTx(int position, TxBE newEntry) {
        txList.add(position, newEntry);
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
    public void setAutoRenew(boolean autoRenew) {
        this.autoRenew = autoRenew;
    }

    // normal reset, used for starting new reporting period. only empty tx list if account is not profit neutral
    public void tryRenew() {
        float oldSaldo = getSum();
        txList = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        txList.add(new TxBE(oldSaldo, Const.DESC_OPENING, calendar.getTime()));
    }

    public void reset() {
        txList = new ArrayList<>();
    }
}
