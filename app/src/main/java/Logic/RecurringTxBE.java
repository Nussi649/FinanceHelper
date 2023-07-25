package Logic;

import java.util.Date;

public class RecurringTxBE extends TxBE {
    private String senderAccountStr;
    private String receiverAccountStr;

    public RecurringTxBE(float amount, String description, Date date, String sender, String receiver) {
        super(amount, description, date);
        senderAccountStr = sender;
        receiverAccountStr = receiver;
    }

    public String getSenderStr() {
        return senderAccountStr;
    }

    public String getReceiverStr() {
        return receiverAccountStr;
    }

    public void setSenderStr(String newSender) {
        senderAccountStr = newSender;
    }

    public void setReceiverStr(String newReceiver) {
        receiverAccountStr = newReceiver;
    }
}
