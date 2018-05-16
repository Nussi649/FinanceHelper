package Logic;

import java.util.Date;

public class EntryBE {
    float mAmount;
    String mDescription;
    Date mDate;

    public EntryBE(float amount, String description) {
        mAmount = amount;
        mDescription = description;
    }

    public EntryBE(float amount, String description, Date date) {
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

    @Override
    public String toString() {
        return getDescription();
    }
}
