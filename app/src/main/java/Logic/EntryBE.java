package Logic;

public class EntryBE {
    int mId;
    float mAmount;
    float mTotalAmount;
    String mDescription;

    public EntryBE(int id, float amount, float totalAmount, String description) {
        mId = id;
        mAmount = amount;
        mTotalAmount = totalAmount;
        mDescription = description;
    }

    public float getAmount() {
        return mAmount;
    }

    public float getTotalAmount() {
        return mTotalAmount;
    }

    public String getDescription() {
        return mDescription;
    }

    public int getId() {
        return mId;
    }
}
