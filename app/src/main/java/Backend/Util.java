package Backend;

import android.annotation.SuppressLint;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TableLayout;

import androidx.core.graphics.ColorUtils;

import com.privat.pitz.financehelper.MainActivity;
import com.privat.pitz.financehelper.R;

import View.ListItemAccountPreview;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import Logic.AccountBE;
import Logic.BudgetAccountBE;
import Logic.TxBE;
import Logic.RecurringTxBE;

public abstract class Util {
    public static class FileNameParts {
        public final int year;
        public final int month;
        public final String entityName;

        public FileNameParts(int year, int month, String entityName) {
            this.year = year;
            this.month = month;
            this.entityName = entityName;
        }
    }

    // Create DateFormat instances
    @SuppressLint("SimpleDateFormat")
    private static final DateFormat DISPLAY_DATE_FORMAT = new SimpleDateFormat(Const.DATE_FORMAT_DISPLAY);
    @SuppressLint("SimpleDateFormat")
    private static final DateFormat SAVE_DATE_FORMAT = new SimpleDateFormat(Const.DATE_FORMAT_SAVE);

    // region populate AccountsPreview TableLayouts with Account lists
    @SuppressLint("InflateParams")
    public static void populateBudgetAccountsPreview(final List<BudgetAccountBE> accounts,
                                               final MainActivity parentActivity,
                                               final LinearLayout container,
                                               final RbAccountManager receiverManager) {
        assert accounts != null;

        // first clean up target container
        container.removeAllViews();

        // then get number of accounts
        int count = accounts.size();
        if (count == 0)
            return;
        // iterate through all accounts given as argument
        for (int index = 0; index < accounts.size(); index++) {
            AccountBE currentAccount = accounts.get(index);
            // skip if account is marked as inactive
            if (!currentAccount.getIsActive())
                continue;
            // create list item at hierarchy level 0 (not specifically defined)
            ListItemAccountPreview newItem = ListItemAccountPreview.getInstance(parentActivity, container);
            newItem.init(currentAccount);
            // add list item to parent layout
            container.addView(newItem);
            // add radio button to receiver group
            RadioButton rbReceive = newItem.getRBReceiver();
            assert rbReceive != null;
            receiverManager.addRadioButton(rbReceive, currentAccount);

            // check if list item has children. If so, add them
            // get all children (down every hierarchy layer)
            List<ListItemAccountPreview> children = newItem.getAllChildren();
            // abort if there are none
            if (children.size() == 0)
                continue;
            // iterate through all children
            for (ListItemAccountPreview child : children) {
                BudgetAccountBE currentChildAccount = (BudgetAccountBE) child.getReferenceAccount();
                // add child to parent layout and add listeners
                container.addView(child);
                // add radio button to receiver group
                RadioButton rbReceiveChild = child.getRBReceiver();
                assert rbReceiveChild != null;
                receiverManager.addRadioButton(rbReceiveChild, currentChildAccount);
            }
        }
        // hide divider of last item
        int viewCount = container.getChildCount();
        if (viewCount == 0)
            return;
        View lastView = container.getChildAt(viewCount - 1);
        if (lastView instanceof ListItemAccountPreview)
            lastView.findViewById(R.id.horizontal_divider).setVisibility(View.GONE);
        else
            Log.println(Log.INFO, "account_preview", "After populating AssetAccount preview, last list item was not of type ListItemAccountPreview. This is unexpected");
    }

    @SuppressLint("InflateParams")
    public static void populateAssetAccountsPreview(final List<AccountBE> accounts,
                                               final MainActivity parentActivity,
                                               final LinearLayout container,
                                               final RbAccountManager receiverManager,
                                               final RbAccountManager senderManager) {
        assert accounts != null;

        // first clean up target container
        container.removeAllViews();

        // then get number of accounts
        int count = accounts.size();
        if (count == 0)
            return;
        // iterate through all accounts given as argument
        for (int index = 0; index < accounts.size(); index++) {
            AccountBE currentAccount = accounts.get(index);
            // this method should only be fed with Asset Accounts
            assert !(currentAccount instanceof BudgetAccountBE);
            // skip if account is marked as inactive
            if (!currentAccount.getIsActive())
                continue;
            // create list item at hierarchy level 0 (not specifically defined)
            ListItemAccountPreview newItem = ListItemAccountPreview.getInstance(parentActivity, container);
            newItem.init(currentAccount);
            // add list item to parent layout
            container.addView(newItem);
            // add radio button to receiver group
            RadioButton rbReceive = newItem.getRBReceiver();
            assert rbReceive != null;
            receiverManager.addRadioButton(rbReceive, currentAccount);

            // add radio button to sender group
            RadioButton rbSend = newItem.getRBSender();
            assert rbSend != null;
            senderManager.addRadioButton(rbSend, currentAccount);

            // check if list item has children. If so, add them
            // get all children (down every hierarchy layer)
            List<ListItemAccountPreview> children = newItem.getAllChildren();
            // abort if there are none
            if (children.size() == 0)
                continue;
            // iterate through all children
            for (ListItemAccountPreview child : children) {
                AccountBE currentChildAccount = child.getReferenceAccount();
                // add child to parent layout and add listeners
                container.addView(child);
                // add radio button to receiver group
                RadioButton rbReceiveChild = newItem.getRBReceiver();
                assert rbReceiveChild != null;
                receiverManager.addRadioButton(rbReceiveChild, currentChildAccount);
                // add radio button to sender group
                RadioButton rbSendChild = newItem.getRBSender();
                assert rbSendChild != null;
                senderManager.addRadioButton(rbSendChild, currentChildAccount);
            }
        }
        // hide divider of last item
        int viewCount = container.getChildCount();
        if (viewCount == 0)
            return;
        View lastView = container.getChildAt(viewCount - 1);
        if (lastView instanceof ListItemAccountPreview)
            lastView.findViewById(R.id.horizontal_divider).setVisibility(View.GONE);
        else
            Log.println(Log.INFO, "account_preview", "After populating AssetAccount preview, last list item was not of type ListItemAccountPreview. This is unexpected");
    }
    // endregion

    /**
     * Formats a float to a string with two decimal places for saving i.e. using '.' as decimal separator.
     *
     * @param input The float to format.
     * @return The formatted string.
     */
    @SuppressLint("DefaultLocale")
    public static String formatFloatSave(float input) {
        return String.format("%.2f", input).replace(',','.');
    }

    /**
     * Formats a float to a string with two decimal places for display i.e. using ',' as decimal separator.
     *
     * @param input The float to format.
     * @return The formatted string.
     */
    @SuppressLint("DefaultLocale")
    public static String formatFloatDisplay(float input) {
        return String.format("%.2f", input);
    }

    /**
     * Formats a float to a string with two decimal places for display and with thousands separator.
     *
     * @param input The float to format.
     * @return The formatted string.
     */
    public static String formatLargeFloatDisplay(float input) {
        return String.format(Locale.getDefault(), "%,.2f", input).replace(" ", ".");
    }

    /**
     * Formats a float to a string with no decimal places for display and with thousands separator.
     *
     * @param input The float to format.
     * @return The formatted string.
     */
    public static String formatLargeFloatShort(float input) {
        return String.format(Locale.getDefault(), "%,.0f", input).replace(" ", ".");
    }

    /**
     * Formats a Date object into a string for display.
     *
     * @param input The Date object to format.
     * @return The formatted string.
     */
    public static String formatDateDisplay(Date input) {
        return DISPLAY_DATE_FORMAT.format(input);
    }

    /**
     * Formats a Date object into a string for saving.
     *
     * @param input The Date object to format.
     * @return The formatted string.
     */
    public static String formatDateSave(Date input) {
        return SAVE_DATE_FORMAT.format(input);
    }

    public static String formatToFixedLength(String input, int desiredLength) {
        if (input.length() >= desiredLength) {
            return input;
        }
        int lengthDiff = 2 * (desiredLength - input.length());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lengthDiff; i++) {
            sb.append(' ');
        }
        sb.append(input);
        return sb.toString();
    }

    public static char evaluatePercentage(float percentage) {
        LocalDate today = LocalDate.now();
        YearMonth yearMonthObject = YearMonth.of(today.getYear(), today.getMonth());
        int daysInMonth = yearMonthObject.lengthOfMonth();
        float monthProgress = (float) today.getDayOfMonth() / daysInMonth;

        if (percentage > monthProgress + 0.1) {
            return '+';
        } else if (percentage < monthProgress - 0.1) {
            return '-';
        } else {
            return 'O';
        }
    }

    public static List<File> getValidFiles(File dir) {
        File[] files = dir.listFiles();
        ArrayList<File> matchingFiles = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\d{4}-\\d{2}-\\S+\\.jso");
        if (files != null) {
            for (File file : files) {
                Matcher matcher = pattern.matcher(file.getName());
                if (matcher.find()) {
                    matchingFiles.add(file);
                }
            }
        } else
            return null;
        return matchingFiles;
    }

    public static List<String> getFileNames(List<File> files) {
        if (files == null)
            return null;
        List<String> re = new ArrayList<>();
        for (File file : files) {
            re.add(file.getName());
        }
        return re;
    }

    public static JSONArray copyJSONArray(JSONArray arrayIn) {
        JSONArray copyArray = new JSONArray();
        for (int i = 0; i < arrayIn.length(); i++) {
            try {
                copyArray.put(arrayIn.get(i));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return copyArray;
    }

    public static GradientDrawable createBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(7); // 7dp rounded corners
        drawable.setColor(ColorUtils.setAlphaComponent(color, (int) (255 * 0.6))); // 60% opacity
        return drawable;
    }

    /**
     * Parses a string into a Date object.
     *
     * @param input The string to parse.
     * @return The parsed Date object.
     * @throws ParseException If the string cannot be parsed.
     */
    public static Date parseDateSave(String input) throws ParseException {
        Date date = SAVE_DATE_FORMAT.parse(input);
        if (date == null) {
            throw new ParseException("Could not parse date: " + input, 0);
        }
        return date;
    }

    @SuppressLint("DefaultLocale")
    public static String serializeFileName(FileNameParts parts) {
        // Check for null
        if (parts == null) {
            throw new IllegalArgumentException("FileNameParts cannot be null");
        }

        // Extract entity Name
        String entityName = parts.entityName;

        // Check if the entityName is null or empty
        if (entityName == null || entityName.isEmpty()) {
            throw new IllegalArgumentException("Entity name cannot be null or empty");
        }

        // Return the formatted filename
        return String.format("%04d-%02d-%s.jso", parts.year, parts.month, entityName);
    }


    public static FileNameParts parseFileName(String fileName) {
        // Check for null or empty string
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }

        // Split the string into parts
        String[] parts = fileName.split("[.-]");

        // Check if the filename has the correct format
        if (parts.length != 4 || !parts[3].equals("jso")) {
            throw new IllegalArgumentException("Invalid filename format");
        }

        // Parse the year, month, and entity name
        int year;
        int month;
        try {
            year = Integer.parseInt(parts[0]);
            month = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid year or month in filename", e);
        }
        String entityName = parts[2];

        // Return the parts in a FileNameParts object
        return new FileNameParts(year, month, entityName);
    }

    // region parse JSON to BE objects
    public static Model.Settings parseJSON_Settings(JSONObject json_in) throws JSONException {
        Model.Settings settings = new Model.Settings();
        try {
            settings.defaultEntityName = json_in.getString(Const.JSON_TAG_DEFAULT_ENTITY);

            // Extract the defaults for each entity
            JSONArray entitiesArray = json_in.getJSONArray(Const.JSON_TAG_DEFAULT_ACCOUNTS);
            for (int i = 0; i < entitiesArray.length(); i++) {
                JSONObject entityJSON = entitiesArray.getJSONObject(i);

                // Create a new EntityDefaults object and populate it
                Model.EntityDefaults entityDefaults = new Model.EntityDefaults(entityJSON.getString(Const.JSON_TAG_SENDER),
                        entityJSON.getString(Const.JSON_TAG_RECEIVER));

                // Add the EntityDefaults object to the map
                String entityName = entityJSON.getString(Const.JSON_TAG_NAME);
                settings.entityDefaultsMap.put(entityName, entityDefaults);
            }
        } catch (JSONException e) {
            Log.println(Log.ERROR, "parse_settings",
                    String.format("Error parsing Settings: %s", e));
            throw e;
        }
        return settings;
    }

    public static TxBE parseJSON_Entry(JSONObject json_in) {
        try {
            float amount = (float) json_in.getDouble(Const.JSON_TAG_AMOUNT);
            String description = json_in.getString(Const.JSON_TAG_DESCRIPTION);
            Date time = parseDateSave(json_in.getString(Const.JSON_TAG_TIME));
            return new TxBE(amount, description, time);
        } catch (JSONException e) {
            Log.println(Log.ERROR, "parse_entry",
                    String.format("Error parsing entry! %s", e));
        } catch (ParseException e) {
            Log.println(Log.ERROR, "parse_entry",
                    String.format("Error parsing entry Date time of entry! %s", e));
        }
        return null;
    }

    public static AccountBE parseJSON_Account(JSONObject json_in) {
        AccountBE new_account;
        // try reading obligatory attributes
        try {
            String account_name = json_in.getString(Const.JSON_TAG_NAME);
            boolean is_active = json_in.getBoolean(Const.JSON_TAG_ISACTIVE);
            boolean profit_neutral = json_in.getBoolean(Const.JSON_TAG_PROFIT_NEUTRAL);
            new_account = new AccountBE(account_name);
            new_account.setActive(is_active);
            new_account.setProfitNeutral(profit_neutral);
        } catch (JSONException e) {
            Log.println(Log.ERROR, "parse_account",
                    String.format("Error parsing account: key does not exist! %s", e));
            return null;
        }

        // read entries
        try {
            JSONArray entries = json_in.getJSONArray(Const.JSON_TAG_TRANSACTIONS);
            for (int i = 0; i < entries.length(); i++) {
                JSONObject curEntry = entries.getJSONObject(i);
                TxBE new_entry = parseJSON_Entry(curEntry);
                if (new_entry != null)
                    new_account.addTx(new_entry);
            }
        } catch (JSONException e) {
            Log.println(Log.ERROR, "parse_account",
                    String.format("Error parsing account %s: key does not exist! %s",
                            new_account.getName(), e));
            return null;
        }
        return new_account;
    }

    public static BudgetAccountBE parseJSON_BudgetAccount(JSONObject json_in) {
        AccountBE parsed_account = parseJSON_Account(json_in);
        if (parsed_account == null)
                return null;
        BudgetAccountBE new_account = new BudgetAccountBE(parsed_account);

        // try reading obligatory attributes
        try {
            float yearly_budget = (float)json_in.getDouble(Const.JSON_TAG_YEARLY_BUDGET);
            new_account.setIndivYearlyBudget(yearly_budget);
        } catch (JSONException e) {
            Log.println(Log.ERROR, "parse_budget_account",
                    String.format("Error parsing budget account %s: key does not exist! %s",
                            new_account.getName(), e));
            return null;
        } catch (NumberFormatException e) {
            Log.println(Log.ERROR, "parse_budget_account",
                    String.format("Error parsing budget account %s: yearly budget could not be parsed! %s",
                            new_account.getName(), e));
            return null;
        }

        // try reading current budget
        float current_budget = -1f;
        try {
            current_budget = (float)json_in.getDouble(Const.JSON_TAG_CURRENT_BUDGET);
        } catch (JSONException e) {
            Log.println(Log.INFO, "parse_budget_account",
                    String.format("No current budget for account: %s", new_account.getName()));
            current_budget = new_account.indivYearlyBudget / 12;
        } finally {
            new_account.setIndivAvailableBudget(current_budget);
        }

        // try reading target entity
        try {
            String other_entity = json_in.getString(Const.JSON_TAG_TO_OTHER);
            new_account.setToOtherEntity(other_entity);
        } catch (JSONException e) {
            Log.println(Log.INFO, "parse_budget_account",
                    String.format("No target entity for account: %s", new_account.getName()));
        }

        // read sub budgets
        try {
            JSONArray sub_budgets_json = json_in.getJSONArray(Const.JSON_TAG_SUB_BUDGETS);
            List<BudgetAccountBE> sub_budgets = new ArrayList<>();
            for (int i = 0; i < sub_budgets_json.length(); i++) {
                JSONObject current_sub_budget_json = sub_budgets_json.getJSONObject(i);
                BudgetAccountBE current_sub_budget = parseJSON_BudgetAccount(current_sub_budget_json);
                if (current_sub_budget != null)
                    sub_budgets.add(current_sub_budget);
            }
            new_account.setSubBudgets(sub_budgets);
        } catch (JSONException e) {
            Log.println(Log.INFO, "parse_budget_account",
                    String.format("No sub budgets for account: %s", new_account.getName()));
        }
        return new_account;
    }

    public static RecurringTxBE parseJSON_RecurringOrder(JSONObject json_in) {
        String description = "";
        try {
            description = json_in.getString(Const.JSON_TAG_DESCRIPTION);
        } catch (JSONException e) {
            Log.println(Log.ERROR, "parse_recur_order",
                    String.format("Error parsing description of recurring order: key does not exist! %s", e));
            return null;
        }
        try {
            float amount = (float)json_in.getDouble(Const.JSON_TAG_AMOUNT);
            Date time = parseDateSave(json_in.getString(Const.JSON_TAG_TIME));
            String from_account = json_in.getString(Const.JSON_TAG_SENDER);
            String to_account = json_in.getString(Const.JSON_TAG_RECEIVER);
            return new RecurringTxBE(amount,
                    description,
                    time,
                    from_account,
                    to_account);

        } catch (JSONException e) {
            Log.println(Log.ERROR, "parse_recur_order",
                    String.format("Error parsing recurring order %s: key does not exist! %s",
                            description, e));
        } catch (NumberFormatException e) {
            Log.println(Log.ERROR, "parse_recur_order",
                    String.format("Error parsing recurring order %s: yearly budget could not be parsed! %s",
                            description, e));
        } catch (ParseException e) {
            Log.println(Log.ERROR, "parse_recur_order",
                    String.format("Error parsing entry Date time of recurring order %s! %s",
                            description, e));
        }
        return null;
    }

    public static List<TxBE> parseJSON_IncomeList(JSONArray json_in) {
        List<TxBE> new_income_list = new ArrayList<>();
        for (int i = 0; i < json_in.length(); i++) {
            try {
                TxBE current_income_entry = parseJSON_Entry(json_in.getJSONObject(i));
                if (current_income_entry != null)
                    new_income_list.add(current_income_entry);
            } catch (JSONException e) {
                Log.println(Log.ERROR, "parse_income_list",
                        String.format("Error retrieving Income Entry from JSONArray: %s", e));
            }
        }
        return new_income_list;
    }
    // endregion

    // region serialise BE to JSON objects
    public static JSONObject serialise_Settings(Model.Settings settings) throws JSONException {
        JSONObject settingsJSON = new JSONObject();

        try {
            settingsJSON.put(Const.JSON_TAG_DEFAULT_ENTITY, settings.defaultEntityName);

            // Create a JSON array to hold the defaults for each entity
            JSONArray entitiesArray = new JSONArray();

            // Add each entity's defaults to the array
            for (Map.Entry<String, Model.EntityDefaults> entry : settings.entityDefaultsMap.entrySet()) {
                JSONObject entityJSON = new JSONObject();
                entityJSON.put(Const.JSON_TAG_NAME, entry.getKey());
                entityJSON.put(Const.JSON_TAG_SENDER, entry.getValue().defaultSender);
                entityJSON.put(Const.JSON_TAG_RECEIVER, entry.getValue().defaultReceiver);

                entitiesArray.put(entityJSON);
            }

            // Add the array to the settings JSON object
            settingsJSON.put(Const.JSON_TAG_DEFAULT_ACCOUNTS, entitiesArray);
        } catch (JSONException e) {
            Log.println(Log.ERROR, "serialise_settings",
                    String.format("Error serialising Settings: %s", e));
            throw e;
        }

        return settingsJSON;
    }

    public static JSONObject serialise_Entry(TxBE entry_in) {
        try {
            JSONObject new_entry = new JSONObject();
            new_entry.put(Const.JSON_TAG_DESCRIPTION, entry_in.getDescription());
            new_entry.put(Const.JSON_TAG_AMOUNT, Util.formatFloatSave(entry_in.getAmount()));
            new_entry.put(Const.JSON_TAG_TIME, Util.formatDateSave(entry_in.getDate()));
            return new_entry;
        } catch (JSONException e) {
            Log.println(Log.ERROR, "serialise_entry",
                    String.format("Error serialising EntryBE: %s", e));
        }
        return null;
    }

    public static JSONObject serialise_Account(AccountBE account_in) {
        try {
            JSONObject serialised_account = new JSONObject();
            serialised_account.put(Const.JSON_TAG_NAME, account_in.getName());
            serialised_account.put(Const.JSON_TAG_ISACTIVE, account_in.getIsActive());
            serialised_account.put(Const.JSON_TAG_PROFIT_NEUTRAL, account_in.getIsProfitNeutral());
            JSONArray entries = new JSONArray();
            for (TxBE current_entry : account_in.getTxList()) {
                JSONObject serialised_entry = serialise_Entry(current_entry);
                if (serialised_entry != null)
                    entries.put(serialised_entry);
            }
            serialised_account.put(Const.JSON_TAG_TRANSACTIONS, entries);
            return serialised_account;
        } catch (JSONException e) {
            Log.println(Log.ERROR, "serialise_Account",
                    String.format("Error serialising AccountBE %s: %s", account_in.getName(), e));
        }
        return null;
    }

    @SuppressLint("DefaultLocale")
    public static JSONObject serialise_BudgetAccount(BudgetAccountBE budgetAccount_in) {
        JSONObject new_account = serialise_Account(budgetAccount_in);
        if (new_account == null)
            return null;

        // add BudgetAccount specific attributes
        try {
            String otherEntity = budgetAccount_in.getOtherEntity();
            new_account.put(Const.JSON_TAG_YEARLY_BUDGET, Util.formatFloatSave(budgetAccount_in.indivYearlyBudget));
            new_account.put(Const.JSON_TAG_TO_OTHER, otherEntity == null ? "" : otherEntity);
            float current_budget = budgetAccount_in.indivAvailableBudget;
            if (current_budget != -1.0f)
                new_account.put(Const.JSON_TAG_CURRENT_BUDGET, Util.formatFloatSave(current_budget));
        } catch (JSONException e) {
            Log.println(Log.ERROR, "serialise_BudgetAccount",
                    String.format("Error serialising AccountBE %s: %s", budgetAccount_in.getName(), e));
        }

        // add sub budgets
        List<BudgetAccountBE> sub_budgets = budgetAccount_in.getDirectSubBudgets();
        if (sub_budgets != null && sub_budgets.size() > 0) {
            JSONArray sub_budgets_json = new JSONArray();
            for (BudgetAccountBE sub_budget : sub_budgets) {
                JSONObject sub_budget_json = serialise_BudgetAccount(sub_budget);
                if (sub_budget_json != null)
                    sub_budgets_json.put(sub_budget_json);
            }
            try {
                new_account.put(Const.JSON_TAG_SUB_BUDGETS, sub_budgets_json);
            } catch (JSONException e) {
                Log.println(Log.ERROR, "serialise_BudgetAccount",
                        String.format("Error serialising AccountBE %s: %s", budgetAccount_in.getName(), e));
            }
        }
        return new_account;
    }

    public static JSONObject serialise_RecurringOrder(RecurringTxBE recurringOrder_in) {
        try {
            JSONObject new_order = new JSONObject();
            new_order.put(Const.JSON_TAG_AMOUNT, Util.formatFloatSave(recurringOrder_in.getAmount()));
            new_order.put(Const.JSON_TAG_DESCRIPTION, recurringOrder_in.getDescription());
            new_order.put(Const.JSON_TAG_TIME, Util.formatDateSave(recurringOrder_in.getDate()));
            new_order.put(Const.JSON_TAG_SENDER, recurringOrder_in.getSenderStr());
            new_order.put(Const.JSON_TAG_RECEIVER, recurringOrder_in.getReceiverStr());
            return new_order;
        } catch (JSONException e) {
            Log.println(Log.ERROR, "serialise_RecurrOrder",
                    String.format("Error serialising RecurringOrderBE %s: %s", recurringOrder_in.getDescription(), e));
        }
        return null;
    }

    public static JSONArray serialise_Income(List<TxBE> income_in) {
        JSONArray new_income_list_json = new JSONArray();
        for (TxBE income : income_in) {
            JSONObject income_json = serialise_Entry(income);
            if (income_json != null)
                new_income_list_json.put(income_json);
        }
        return new_income_list_json;
    }
    // endregion
}
