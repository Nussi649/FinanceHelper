package Logic;

public class EntryBE {
    int mId;
    float mAmount;
    String mDescription;

    public EntryBE(int id, float amount, String description) {
        mId = id;
        mAmount = amount;
        mDescription = description;
    }

    public float getAmount() {
        return mAmount;
    }

    public String getDescription() {
        return mDescription;
    }

    public int getId() {
        return mId;
    }
}
