package Logic;

import java.util.Date;

import Backend.Util;

public class TxBE {
    private float mAmount;
    private String mDescription;
    private Date mDate;

    public TxBE(float amount, String description, Date date) {
        mAmount = amount;
        mDescription = description;
        mDate = date;
    }

    public float getAmount() {
        return mAmount;
    }

    public String getDescription() {
        return mDescription;
    }

    public Date getDate() { return mDate; }

    public void setAmount(float newAmount) { mAmount = newAmount; }

    public void setDescription(String newDesc) { mDescription = newDesc; }

    public void setDate(Date newDate) {
        mDate = newDate;
    }

    @Override
    public String toString() {
        return String.format("%s: %s",mDescription, Util.formatFloatDisplay(mAmount));
    }
}
