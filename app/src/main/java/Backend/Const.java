package Backend;


import java.util.Calendar;

public class Const {
    public static int MARGIN_ACCOUNT_VIEW = 5;
    public static int ACOUNT_VIEW_LANDSCAPE_WIDTH = 500;

    public static String ACCOUNTS_FASTSAVE_DIRECTORY_NAME = "fastsave";
    public static String ACCOUNTS_FASTSAVE_FILE_NAME = ACCOUNTS_FASTSAVE_DIRECTORY_NAME + "/accounts";
    public static String ACCOUNTS_HIDDEN_DIRECTORY = "hidden";
    public static String ACCOUNTS_FILE_TYPE = ".jso";

    public static String APPENDIX_PAY_SENDER = "-1";
    public static String APPENDIX_PAY_RECIPIENT = "-2";

    public static String JSON_TAG_TIME = "time";
    public static String JSON_TAG_ENTRIES = "entries";
    public static String JSON_TAG_NAME = "name";
    public static String JSON_TAG_AMOUNT = "amount";
    public static String JSON_TAG_DESCRIPTION = "description";
    public static String JSON_TAG_PACCOUNTS = "paccounts";
    public static String JSON_TAG_IACCOUNTS = "iaccounts";
    public static String JSON_TAG_RECURRING_ORDERS = "rorders";
    public static String JSON_TAG_INCOME_LIST = "incomel";
    public static String JSON_TAG_PACCOUNT = "pay";
    public static String JSON_TAG_IACCOUNT = "invest";
    public static String JSON_TAG_ISACTIVE = "active";

    public static String ACCOUNT_BARGELD = "Bargeld";
    public static String ACCOUNT_BANK = "Sichteinlagen";
    public static String ACCOUNT_SAVINGS = "Sparkonto";
    public static String ACCOUNT_CREDIT_CARD = "Kreditkarte";
    public static String ACCOUNT_UNI = "Mensakarte";

    public static String ACCOUNT_INVESTMENTS = "Anschaffungen";
    public static String ACCOUNT_GROCERIES = "Lebensmittel";
    public static String ACCOUNT_COSMETICS = "Kosmetik";
    public static String ACCOUNT_GO_OUT = "Ausgehen";
    public static String ACCOUNT_DRUGS = "Drogen";
    public static String ACCOUNT_NECESSARY = "Notwendige";
    public static String ACCOUNT_BUS = "Busbudget";

    public static int KEY_BIT_LENGTH = 2048;

    public static String DATE_FORMAT_DISPLAY = "dd.MM. HH:mm";
    public static String DATE_FORMAT_SAVE = "dd.MM.yyyy HH:mm";

    public static String DESC_CLOSING = "Abschluss";
    public static String DESC_OPENING = "Eröffnung";

    public static String getMonthNameById(int month) {
        if (month == 0)
            return "January";
        if (month == 1)
            return "February";
        if (month == 2)
            return "March";
        if (month == 3)
            return "April";
        if (month == 4)
            return "May";
        if (month == 5)
            return "June";
        if (month == 6)
            return "July";
        if (month == 7)
            return "August";
        if (month == 8)
            return "September";
        if (month == 9)
            return "October";
        if (month == 10)
            return "November";
        if (month == 11)
            return "December";
        return null;
    }

    public static String getCurrentMonthName() {
        Calendar cal = Calendar.getInstance();
        return getMonthNameById(cal.get(Calendar.MONTH)) + cal.get(Calendar.YEAR);
    }

    public static String getDisplayableCurrentMonthName() {
        Calendar cal = Calendar.getInstance();
        return getMonthNameById(cal.get(Calendar.MONTH));
    }

    public static String getLastMonthName() {
        Calendar cal = Calendar.getInstance();
        int lastMonth = cal.get(Calendar.MONTH) - 1 % 12;
        if (lastMonth < 11) {
            return getMonthNameById(lastMonth) + cal.get(Calendar.YEAR);
        } else {
            return getMonthNameById(lastMonth) + (cal.get(Calendar.YEAR) - 1);
        }
    }

}
