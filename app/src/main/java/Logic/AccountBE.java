package Logic;

import java.util.ArrayList;
import java.util.List;

public class AccountBE {
    private String mName;
    private List<EntryBE> mEntries;
    private float mSum;
    private boolean isActive;

    public AccountBE(String name) {
        mName = name;
        mEntries = new ArrayList<>();
        isActive = true;
    }

    public float refreshSum() {
        float sum = 0.0f;
        for (EntryBE e: mEntries) {
            sum += e.getAmount();
        }
        return mSum = sum;
    }

    //region get
    public List<EntryBE> getEntries() {
        return mEntries;
    }

    public float getSum() {
        if (mSum == 0.0f)
            return refreshSum();
        return mSum;
    }

    public String getName() {
        return mName;
    }

    public boolean getIsActive() { return isActive;}
    //endregion

    //region add entries
    public void addEntry(EntryBE newEntry) {
        mEntries.add(newEntry);
        refreshSum();
    }
    //endregion

    public void setActive(boolean active) {
        isActive = active;
    }
}
