package com.privat.pitz.financehelper.data;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import com.privat.pitz.financehelper.core.Util;

public class TxBE {
    private float mAmount;
    private String mDescription;
    private Date mDate;
    // stable identity across devices and edits; lets sync match "the same transaction" without
    // relying on date+description, which both change when a transaction is edited
    private final String mId;
    // UTC time of the last change (not the booking date, that is mDate); decides "newer wins"
    // when two copies of the same transaction diverge. Whole-second resolution: that is all the
    // save format keeps.
    private Date mModified;

    public TxBE(float amount, String description, Date date) {
        this(amount, description, date, newId(), now());
    }

    /** For parsing: a transaction that already has an identity and a modification time. */
    public TxBE(float amount, String description, Date date, String id, Date modified) {
        mAmount = amount;
        mDescription = description;
        mDate = date;
        mId = id;
        mModified = modified;
    }

    /** 12 hex characters (48 bit): short enough to read in a file, collision-free in practice. */
    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static Date now() {
        return new Date(System.currentTimeMillis() / 1000L * 1000L);
    }

    public String getId() { return mId; }

    public Date getModified() { return mModified; }

    public void setModified(Date modified) { mModified = modified; }

    public float getAmount() {
        return mAmount;
    }

    public String getDescription() {
        return mDescription;
    }

    public Date getDate() { return mDate; }

    public void setAmount(float newAmount) { mAmount = newAmount; mModified = now(); }

    public void setDescription(String newDesc) { mDescription = newDesc; mModified = now(); }

    public void setDate(Date newDate) {
        mDate = newDate;
        mModified = now();
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
