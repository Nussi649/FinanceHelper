package Logic;

import java.util.ArrayList;
import java.util.List;

public class AccountBE {
    private String mName;
    private List<EntryBE> mSoll;
    private List<EntryBE> mHaben;
    private float mSumSoll;
    private float mSumHaben;

    public AccountBE(String name) {
        mName = name;
        mSoll = new ArrayList<>();
        mHaben = new ArrayList<>();
    }

    //region refresh
    public float refreshSumSoll() {
        float sum = 0.0f;
        for (EntryBE e: mSoll) {
            sum += e.getAmount();
        }
        return mSumSoll = sum;
    }

    public float refreshSumHaben() {
        float sum = 0.0f;
        for (EntryBE e: mHaben) {
            sum += e.getAmount();
        }
        return mSumHaben = sum;
    }

    public void refreshAll() {
        refreshSumSoll();
        refreshSumHaben();
    }
    //endregion

    //region get
    public List<EntryBE> getHaben() {
        return mHaben;
    }

    public List<EntryBE> getSoll() {
        return mSoll;
    }

    public float getSumSoll() {
        if (mSumSoll == 0.0f)
            return refreshSumSoll();
        return mSumSoll;
    }

    public float getSumHaben() {
        if (mSumHaben == 0.0f)
            return refreshSumHaben();
        return mSumHaben;
    }

    public String getName() {
        return mName;
    }
    //endregion

    //region add entries
    public void addSollEntry(EntryBE newEntry) {
        mSoll.add(newEntry);
        refreshSumSoll();
    }

    public void addHabenEntry(EntryBE newEntry) {
        mHaben.add(newEntry);
        refreshSumHaben();
    }
    //endregion
}
