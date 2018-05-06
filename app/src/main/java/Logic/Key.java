package Logic;

import java.math.BigInteger;

public abstract class Key {
    protected BigInteger value;
    protected BigInteger modul;

    public BigInteger getValue() {
        return value;
    }

    public BigInteger getModul() {
        return modul;
    }
}