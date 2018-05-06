package Logic;

import java.math.BigInteger;

import Backend.Util;

public class PublicKey extends Key {

    public PublicKey(BigInteger key, BigInteger modul) {
        value = key;
        this.modul = modul;
    }

    public String encrypt(String message) {
        BigInteger mes = Util.stringToBigInt(message);
        BigInteger mesEnc = mes.modPow(getValue(), getModul());
        return Util.bigIntToString(mesEnc);
    }

    public String checkSignedMessage(String message) {
        return encrypt(message);
    }
}
