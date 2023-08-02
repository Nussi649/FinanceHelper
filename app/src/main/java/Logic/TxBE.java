package Logic;

import java.util.Calendar;
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

    public boolean inPeriod(String period) {
        if (!Util.validatePeriod(period))
            return false;
        String[] parts = period.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);

        // Extract the year and month from mDate
        Calendar mDateCalendar = Calendar.getInstance();
        mDateCalendar.setTime(mDate);
        int mDateYear = mDateCalendar.get(Calendar.YEAR);
        int mDateMonth = mDateCalendar.get(Calendar.MONTH) + 1;  // Calendar.MONTH is zero-based

        return year == mDateYear && month == mDateMonth;
    }
}
