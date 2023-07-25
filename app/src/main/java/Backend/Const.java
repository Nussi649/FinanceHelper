package Backend;

import java.util.Calendar;

public class Const {
    public static int MARGIN_ACCOUNT_VIEW = 5;
    public static int ACOUNT_VIEW_LANDSCAPE_WIDTH = 500;

    // File related
    public static String ACCOUNTS_FASTSAVE_DIRECTORY_NAME = "fastsave";
    public static String ACCOUNTS_FASTSAVE_FILE_NAME = ACCOUNTS_FASTSAVE_DIRECTORY_NAME + "/accounts";
    public static String ACCOUNTS_HIDDEN_DIRECTORY = "hidden";
    public static String APPLICATION_SETTINGS_FILENAME = "settings.json";
    public static String ACCOUNTS_FILE_TYPE = ".jso";
    public static String STATS_FILE_NAME = "stats" + ACCOUNTS_FILE_TYPE;

    public static String APPENDIX_PAY_SENDER = "-1";
    public static String APPENDIX_PAY_RECIPIENT = "-2";

    // Top Level JSON Tags
    public static String JSON_TAG_ASSET_ACCOUNTS = "assets";
    public static String JSON_TAG_BUDGET_ACCOUNTS = "budgets";
    public static String JSON_TAG_RECURRING_TX = "recur_tx";
    public static String JSON_TAG_CURRENT_INCOME = "income";

    // Account Level JSON Tags
    public static String JSON_TAG_TRANSACTIONS = "tx";
    public static String JSON_TAG_NAME = "name";
    public static String JSON_TAG_ISACTIVE = "active";
    public static String JSON_TAG_PROFIT_NEUTRAL = "profit_neutral";
    public static String JSON_TAG_TO_OTHER = "to_other_entity";
    public static String JSON_TAG_YEARLY_BUDGET = "budget_year";
    public static String JSON_TAG_CURRENT_BUDGET = "budget_cur";
    public static String JSON_TAG_SUB_BUDGETS = "sub_budgets";

    // Transaction Level JSON Tags
    public static String JSON_TAG_TIME = "t";
    public static String JSON_TAG_AMOUNT = "a";
    public static String JSON_TAG_DESCRIPTION = "d";

    // Recurring Order JSON Tags
    public static String JSON_TAG_SENDER = "sender";
    public static String JSON_TAG_RECEIVER = "receiver";

    // Application Settings JSON Tags
    public static String JSON_TAG_DEFAULT_ENTITY = "defaultEntity";

    // Selection Groups
    public static String GROUP_SENDER = JSON_TAG_SENDER;
    public static String GROUP_RECEIVER = JSON_TAG_RECEIVER;

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

    public static String getCurrentMonthFileName(String entityName) {
        Calendar cal = Calendar.getInstance();
        Util.FileNameParts parts = new Util.FileNameParts(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                entityName);
        return Util.serializeFileName(parts);
    }

    public static String getLastMonthFileName(String entityName) {
        Calendar cal = Calendar.getInstance();
        int thisMonth = cal.get(Calendar.MONTH);
        int thisYear = cal.get(Calendar.YEAR);
        int newMonth;
        int newYear;
        // remember: thisMonth is indexed at 0, newMonth is indexed at 1
        if (thisMonth == 0) {
            newYear = thisYear - 1;
            newMonth = 12;
        } else {
            newYear = thisYear;
            newMonth = thisMonth;
        }
        Util.FileNameParts parts = new Util.FileNameParts(
                newYear,
                newMonth,
                entityName);
        return Util.serializeFileName(parts);
    }

    public static String getDisplayableCurrentMonthName() {
        Calendar cal = Calendar.getInstance();
        return getMonthNameById(cal.get(Calendar.MONTH));
    }

    public static String getLastMonthName() {
        Calendar cal = Calendar.getInstance();
        int thisMonth = cal.get(Calendar.MONTH);
        if (thisMonth == 0)
            return "12." + (cal.get(Calendar.YEAR) - 1);
        else
            // index of thisMonth = name of last month
            return thisMonth + "." + cal.get(Calendar.YEAR);
    }

    public static Calendar getFirstOfMonth() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.clear(Calendar.MINUTE);
        calendar.clear(Calendar.SECOND);
        return calendar;
    }

}
