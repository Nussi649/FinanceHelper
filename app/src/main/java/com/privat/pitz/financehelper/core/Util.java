package com.privat.pitz.financehelper.core;

import android.annotation.SuppressLint;
import android.util.Log;

import com.privat.pitz.financehelper.data.ProjectBudgetBE;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;
import com.privat.pitz.financehelper.data.TxBE;
import com.privat.pitz.financehelper.data.RecurringTxBE;

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
     * Parses a user-entered amount, accepting both '.' and ',' as the decimal separator
     * (German-locale number keyboards produce ',').
     *
     * @param input The string to parse.
     * @return The parsed float.
     * @throws NumberFormatException if the input isn't a valid number.
     */
    public static float parseAmount(String input) throws NumberFormatException {
        return Float.parseFloat(input.replace(",", "."));
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
     * Renders a 0..1 fraction as a whole-number percentage, e.g. 0.372f -> "37%".
     * No decimals, so this is locale-insensitive in practice; Locale.getDefault() is passed
     * explicitly because that is what the bare String.format(String, Object...) overload this
     * replaced was already using.
     */
    public static String formatPercentage(float fraction) {
        return String.format(Locale.getDefault(), "%.0f%%", fraction * 100);
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

    public static String reduceFileTypeEnding(String filename) {
        // Known file types
        List<String> knownFileTypes = new ArrayList<>();
        knownFileTypes.add(".txt");
        knownFileTypes.add(".jso");
        knownFileTypes.add(".json");

        // Check if input contains file type ending
        if (!filename.contains("."))
            return filename;

        // Separate filetype
        String[] chunks = filename.split("\\.");

        // Get last chunk
        String fileType = chunks[chunks.length - 1];

        // Check for known file types
        if (knownFileTypes.contains("." + fileType)) {
            return filename.substring(0, filename.length() - fileType.length() - 1);
        } else {
            return filename;
        }
    }

    public static float calculateAdvancedPercentage(float current_budget, float current_sum, float allotted_budget) {
        // calculate current percentage differentiate between cases:
        if (current_budget > allotted_budget)
            // current_budget > mean allotted amount -> normalize by current_budget
            return current_sum / current_budget;
        else if (current_budget > 0)
            // current_budget < mean allotted amount -> normalize by halfway current_budget to mean allotted amount (assume halving of deficit)
            return 2*current_sum / (current_budget + allotted_budget);
        else
            // current_budget < 0 -> normalize by mean allotted amount start at >100% (assume budget overflow)
            return 1 + ((current_sum - current_budget) / allotted_budget);
    }

    private static final Pattern SAVEFILE_NAME_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\S+\\.jso");

    public static boolean isValidSavefileName(String filename) {
        return SAVEFILE_NAME_PATTERN.matcher(filename).find();
    }

    /**
     * True if the file is one the backup/sync feature should carry: either a save file
     * (YYYY-MM-Entity.jso) or the application settings file. Null-safe.
     */
    public static boolean isSyncableName(String filename) {
        if (filename == null) return false;
        return isValidSavefileName(filename) || filename.equals(Const.APPLICATION_SETTINGS_FILENAME);
    }

    /**
     * Copies all bytes from in to out using a 4096-byte buffer. Neither stream is closed.
     */
    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[4096];
        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
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

    public static boolean validatePeriod(String period) {
        Pattern pattern = Pattern.compile("^\\d{4}-\\d{2}$");
        if (!pattern.matcher(period).matches())
            return false;

        int year = Integer.parseInt(period.split("-")[0]);
        int month = Integer.parseInt(period.split("-")[1]);
        return !(year < 2000 || year > 2050 || month < 1 || month > 12);
    }

    public static String getPresentPeriod() {
        Calendar calendar = Calendar.getInstance();
        // get current period in format YYYY-MM
        String month = String.format(Locale.US, "%02d", calendar.get(Calendar.MONTH) + 1); // Calendar.MONTH is zero-based
        String year = String.valueOf(calendar.get(Calendar.YEAR));
        return year + "-" + month;
    }

    /**
     * The period following the present one, "YYYY-MM".
     *
     * <p>This is the default next-renewal date for a newly created budget account. It has to be
     * strictly after the present period: {@link com.privat.pitz.financehelper.data.BudgetAccountBE#tryRenew()}
     * renews as soon as nextRenewal is *not* after the present period, so defaulting to the
     * present one would wipe the account's transactions the moment it was created.
     */
    public static String getNextPeriod() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, 1);
        String month = String.format(Locale.US, "%02d", calendar.get(Calendar.MONTH) + 1);
        String year = String.valueOf(calendar.get(Calendar.YEAR));
        return year + "-" + month;
    }

    public static boolean isAfter(String periodA, String periodB) throws IllegalArgumentException {
        // validate inputs
        if (!validatePeriod(periodA) || !validatePeriod(periodB))
            throw new IllegalArgumentException(String.format("Periods must have the format 'YYYY-MM'. Given: %s, %s", periodA, periodB));
        // Parse the year and month from periodA
        String[] partsA = periodA.split("-");
        int yearA = Integer.parseInt(partsA[0]);
        int monthA = Integer.parseInt(partsA[1]);

        // Parse the year and month from periodB
        String[] partsB = periodB.split("-");
        int yearB = Integer.parseInt(partsB[0]);
        int monthB = Integer.parseInt(partsB[1]);

        // Compare the years and months
        return yearA > yearB || (yearA == yearB && monthA > monthB);
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
            // optional field, absent in settings files written before the folder-sync feature existed
            settings.syncFolderUri = json_in.has(Const.JSON_TAG_SYNC_FOLDER_URI) ?
                    json_in.getString(Const.JSON_TAG_SYNC_FOLDER_URI) : null;

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
            boolean auto_renew = json_in.getBoolean(Const.JSON_TAG_AUTO_RENEW);
            new_account = new AccountBE(account_name);
            new_account.setActive(is_active);
            new_account.setAutoRenew(auto_renew);
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

        BudgetAccountBE new_account;
        // check for project account status
        try {
            boolean isProject = json_in.getBoolean(Const.JSON_TAG_PROJECT_BUDGET);
            new_account = isProject ? new ProjectBudgetBE(parsed_account) : new BudgetAccountBE(parsed_account);
        } catch (JSONException e) {
            // no entry for project budget
            new_account = new BudgetAccountBE(parsed_account);
        }

        // read renewal information if new_account is not a project budget. Both fields have
        // sensible defaults (next period / monthly), so a save file missing them still loads -
        // a missing renew_next used to discard the entire account without telling anyone.
        if (!(new_account instanceof ProjectBudgetBE)) {
            String nextRenewal = json_in.optString(Const.JSON_TAG_RENEWAL_NEXT, null);
            if (nextRenewal == null) {
                Log.println(Log.INFO, "parse_budget_account",
                        String.format("No next renewal date for account %s, defaulting to %s",
                                new_account.getName(), new_account.getNextRenewal()));
            } else {
                try {
                    new_account.setNextRenewal(nextRenewal);
                } catch (IllegalArgumentException e) {
                    // Malformed rather than absent - the raw-JSON editor can produce this. Drop
                    // this one account instead of letting the exception abort the whole load.
                    Log.println(Log.ERROR, "parse_budget_account",
                            String.format("Error parsing budget account %s: invalid next renewal date! %s",
                                    new_account.getName(), e));
                    return null;
                }
            }
            new_account.setRenewalPeriod(json_in.optInt(Const.JSON_TAG_RENEWAL_PERIOD,
                    new_account.getRenewalPeriod()));
        }

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
            current_budget = new_account.getMeanAllottedIndivBudget();
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
        String description;
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

    /**
     * Writes {@code value} under {@code key}, substituting {@code fallback} when it is null.
     *
     * <p>{@link JSONObject#put(String, Object)} <em>removes</em> the key when handed a null rather
     * than storing a JSON null. Every nullable value written to a save file therefore has to go
     * through here: otherwise it vanishes from the file, the parser sees a missing key, and -
     * depending on that field's policy - the enclosing record can be discarded outright. That is
     * exactly how every budget account created in the app used to disappear on the next load.
     */
    public static void putOrDefault(JSONObject target, String key, String value, String fallback)
            throws JSONException {
        target.put(key, value != null ? value : fallback);
    }

    /**
     * Formats a transaction date for the save file, substituting the current time for a null.
     *
     * <p>{@link #formatDateSave} throws on a null date, which would abort the entire save, and
     * writing no key at all would cost the whole entry on the next load. Neither is acceptable
     * for a record of money, so the amount and description are kept and the substitution is
     * logged rather than made silently.
     */
    private static String formatDateSaveOrNow(Date date, String what) {
        if (date != null)
            return formatDateSave(date);
        Log.println(Log.ERROR, "serialise_date", String.format(
                "%s has no date; substituting the current time so the entry is not lost.", what));
        return formatDateSave(new Date());
    }

    public static JSONObject serialise_Settings(Model.Settings settings) throws JSONException {
        JSONObject settingsJSON = new JSONObject();

        try {
            // a dropped defaultEntity key makes parseJSON_Settings throw, and loadAppSettings then
            // falls back to a blank Settings - one null here would discard every entity default
            putOrDefault(settingsJSON, Const.JSON_TAG_DEFAULT_ENTITY, settings.defaultEntityName, "User");
            if (settings.syncFolderUri != null)
                settingsJSON.put(Const.JSON_TAG_SYNC_FOLDER_URI, settings.syncFolderUri);

            // Create a JSON array to hold the defaults for each entity
            JSONArray entitiesArray = new JSONArray();

            // Add each entity's defaults to the array
            for (Map.Entry<String, Model.EntityDefaults> entry : settings.entityDefaultsMap.entrySet()) {
                JSONObject entityJSON = new JSONObject();
                putOrDefault(entityJSON, Const.JSON_TAG_NAME, entry.getKey(), "");
                putOrDefault(entityJSON, Const.JSON_TAG_SENDER, entry.getValue().defaultSender, "");
                putOrDefault(entityJSON, Const.JSON_TAG_RECEIVER, entry.getValue().defaultReceiver, "");

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
            putOrDefault(new_entry, Const.JSON_TAG_DESCRIPTION, entry_in.getDescription(), "");
            new_entry.put(Const.JSON_TAG_AMOUNT, Util.formatFloatSave(entry_in.getAmount()));
            new_entry.put(Const.JSON_TAG_TIME,
                    formatDateSaveOrNow(entry_in.getDate(), "Transaction entry"));
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
            putOrDefault(serialised_account, Const.JSON_TAG_NAME, account_in.getName(), "");
            serialised_account.put(Const.JSON_TAG_ISACTIVE, account_in.getIsActive());
            serialised_account.put(Const.JSON_TAG_AUTO_RENEW, account_in.getAutoRenew());
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

        // add ProjectBudget attribute / renewal specifics
        try {
            if (budgetAccount_in instanceof ProjectBudgetBE) {
                new_account.put(Const.JSON_TAG_PROJECT_BUDGET, true);
            } else {
                new_account.put(Const.JSON_TAG_PROJECT_BUDGET, false);
                new_account.put(Const.JSON_TAG_RENEWAL_PERIOD, budgetAccount_in.getRenewalPeriod());
                // JSONObject.put(key, null) REMOVES the key rather than storing a null. That is
                // how newly created budget accounts used to be written without renew_next and
                // then silently discarded by parseJSON_BudgetAccount on the next load. Every
                // BudgetAccountBE now starts with a renewal date, but be explicit about the null
                // case rather than depending on that.
                String nextRenewal = budgetAccount_in.getNextRenewal();
                if (nextRenewal != null)
                    new_account.put(Const.JSON_TAG_RENEWAL_NEXT, nextRenewal);
            }
        } catch (JSONException e) {
            Log.println(Log.ERROR, "serialise_BudgetAccount",
                    String.format("Error serialising BudgetAccountBE %s: %s", budgetAccount_in.getName(), e));
        }

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
                    String.format("Error serialising BudgetAccountBE %s: %s", budgetAccount_in.getName(), e));
        }

        // add sub budgets
        List<BudgetAccountBE> sub_budgets = budgetAccount_in.getDirectSubBudgets();
        if (sub_budgets != null && !sub_budgets.isEmpty()) {
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
            putOrDefault(new_order, Const.JSON_TAG_DESCRIPTION, recurringOrder_in.getDescription(), "");
            new_order.put(Const.JSON_TAG_TIME,
                    formatDateSaveOrNow(recurringOrder_in.getDate(), "Recurring order"));
            // an empty sender is meaningful here - it is how a recurring income is represented -
            // but a *missing* sender key is not, and would cost the whole order on the next load
            putOrDefault(new_order, Const.JSON_TAG_SENDER, recurringOrder_in.getSenderStr(), "");
            putOrDefault(new_order, Const.JSON_TAG_RECEIVER, recurringOrder_in.getReceiverStr(), "");
            return new_order;
        } catch (JSONException e) {
            Log.println(Log.ERROR, "serialise_RecurrOrder",
                    String.format("Error serialising RecurringOrderBE %s: %s", recurringOrder_in.getDescription(), e));
        }
        return null;
    }

    /**
     * Serialises a list into a JSONArray, silently dropping any element the serialiser rejects
     * with null. The four serialise loops in the save file all had this shape.
     */
    public static <T> JSONArray serialiseAll(List<T> items, Function<T, JSONObject> serialiser) {
        JSONArray array = new JSONArray();
        for (T item : items) {
            JSONObject json = serialiser.apply(item);
            if (json != null)
                array.put(json);
        }
        return array;
    }

    public static JSONArray serialise_Income(List<TxBE> income_in) {
        return serialiseAll(income_in, Util::serialise_Entry);
    }
    // endregion
}
