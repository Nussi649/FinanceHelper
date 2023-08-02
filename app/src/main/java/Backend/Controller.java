package Backend;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.privat.pitz.financehelper.MainActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import Logic.AccountBE;
import Logic.BudgetAccountBE;
import Logic.ProjectBudgetBE;
import Logic.TxBE;
import Logic.RecurringTxBE;

public class Controller {
    public static Controller instance;
    Model model;
    Context context;

    public static int LOADED_ACCOUNTS = 10;
    public static int LOADED_NEW_MONTH = 11;
    public static int CREATED_BLANK = 12;

    private Controller(Context context) { this.context = context; }

    private void initController() {
        model = new Model();
    }

    public static void createInstance(Context context) {
        instance = new Controller(context);
        instance.initController();
    }

    public Model getModel() { return model; }

    // resets all account lists
    public void resetAccountLists() {
        model.asset_accounts = new ArrayList<>();
        model.budget_accounts = new ArrayList<>();
        model.recurringTx = new ArrayList<>();
        model.currentIncome = new ArrayList<>();
        setCurrentFileName(Const.getCurrentMonthFileName(model.currentEntity));
    }

    public boolean setCurrentFileName(String newFileName) {
        try {
            model.currentFileAttributes = Util.parseFileName(newFileName);
        } catch (IllegalArgumentException e) {
            Log.println(Log.ERROR, "parse_file_name",
                    String.format("Tried parsing illegal file name: %s", e));
            return false;
        }
        model.currentFileName = newFileName;
        return true;
    }

    public void resetAccounts() {
        for (AccountBE account : getModel().asset_accounts) {
            account.reset();
        }
        for (BudgetAccountBE budgetAccount : getModel().budget_accounts) {
            budgetAccount.reset();
        }
    }

    public void renewAccounts() {
        for (AccountBE account : getModel().asset_accounts) {
            if (!account.getAutoRenew())
                continue;
            account.tryRenew();
        }
        for (BudgetAccountBE budgetAccount : getModel().budget_accounts) {
            budgetAccount.tryRenew();
        }
    }

    private void resetCurrentIncome() {
        model.currentIncome = new ArrayList<>();
    }

    //region Import/Export Account Saves
    public String exportAccounts() throws JSONException {
        JSONObject json = new JSONObject();

        // save Asset accounts
        JSONArray asset_accounts_json = new JSONArray();
        for (AccountBE account : model.asset_accounts) {
            JSONObject new_account_json = Util.serialise_Account(account);
            if (new_account_json != null)
                asset_accounts_json.put(new_account_json);
        }
        json.put(Const.JSON_TAG_ASSET_ACCOUNTS, asset_accounts_json);

        // save Budget accounts
        JSONArray budget_accounts_json = new JSONArray();
        for (BudgetAccountBE budget_account : model.budget_accounts) {
            JSONObject new_budget_account_json = Util.serialise_BudgetAccount(budget_account);
            if (new_budget_account_json != null)
                budget_accounts_json.put(new_budget_account_json);
        }
        json.put(Const.JSON_TAG_BUDGET_ACCOUNTS, budget_accounts_json);

        // save Recurring Orders
        JSONArray recurring_orders_json = new JSONArray();
        for (RecurringTxBE recurring_order : model.recurringTx) {
            JSONObject new_recurring_order_json = Util.serialise_RecurringOrder(recurring_order);
            if (new_recurring_order_json != null)
                recurring_orders_json.put(new_recurring_order_json);
        }
        json.put(Const.JSON_TAG_RECURRING_TX, recurring_orders_json);

        // save Income list
        JSONArray income_list_json = Util.serialise_Income(model.currentIncome);
        json.put(Const.JSON_TAG_CURRENT_INCOME, income_list_json);
        return json.toString(4);
    }

    public void importAccounts(String data) throws JSONException {
        JSONObject json = new JSONObject(data);
        JSONArray accounts;

        // get asset accounts
        model.asset_accounts = new ArrayList<>();
        JSONArray asset_accounts_json = json.getJSONArray(Const.JSON_TAG_ASSET_ACCOUNTS);
        for (int i = 0; i < asset_accounts_json.length(); i++) {
            // get JSONObject of current account
            JSONObject current_account_json = asset_accounts_json.getJSONObject(i);
            // parse new account using parse function in Util
            AccountBE new_account = Util.parseJSON_Account(current_account_json);
            if (new_account != null) {
                model.asset_accounts.add(new_account);
                Model.EntityDefaults defaults = model.getCurrentDefaults();
                if (defaults != null) {
                    // check if account is default Sender account
                    if (defaults.defaultSender.equals(new_account.toString()))
                        model.currentSender = new_account;
                    // check if account is default Receiver account
                    if (defaults.defaultReceiver.equals(new_account.toString()))
                        model.currentReceiver = new_account;
                }
            }
        }

        // get budget accounts
        model.budget_accounts = new ArrayList<>();
        JSONArray budget_accounts_json = json.getJSONArray(Const.JSON_TAG_BUDGET_ACCOUNTS);
        for (int i = 0; i < budget_accounts_json.length(); i++) {
            // get JSONObject of current budget account
            JSONObject current_budget_account_json = budget_accounts_json.getJSONObject(i);
            // parse new budget account using parse function in Util
            BudgetAccountBE new_budget_account = Util.parseJSON_BudgetAccount(current_budget_account_json);
            if (new_budget_account != null) {
                model.budget_accounts.add(new_budget_account);
                Model.EntityDefaults defaults = model.getCurrentDefaults();
                if (defaults != null) {
                    // check if account is default Receiver account
                    if (defaults.defaultReceiver.equals(new_budget_account.toString()))
                        model.currentReceiver = new_budget_account;
                }
            }
        }

        // get recurring Orders
        model.recurringTx = new ArrayList<>();
        accounts = json.getJSONArray(Const.JSON_TAG_RECURRING_TX);
        for (int i = 0; i < accounts.length(); i++) {
            RecurringTxBE new_order = Util.parseJSON_RecurringOrder(accounts.getJSONObject(i));
            if (new_order != null)
                model.recurringTx.add(new_order);
        }

        // get income List
        model.currentIncome = Util.parseJSON_IncomeList(json.getJSONArray(Const.JSON_TAG_CURRENT_INCOME));
    }

    public void writeToInternal(String data, String filename) throws IOException {
        File file = new File(context.getFilesDir(), filename);
        if (!file.exists()) {
            file.createNewFile();
        }
        FileWriter writer = new FileWriter(file);
        writer.append(data);
        writer.flush();
        writer.close();
    }

    public String readFromInternal(String filename) throws IOException {
        StringBuilder data = new StringBuilder();

        File file = new File(context.getFilesDir(), filename);
        FileReader fReader;
        try {
            fReader = new FileReader(file);
        } catch (FileNotFoundException e) {
            Log.println(Log.ERROR, "load_file",
                    String.format("Error reading file: File not found: %s", e));
            throw e;
        }
        BufferedReader reader = new BufferedReader(fReader);
        String line;
        while ((line = reader.readLine()) != null) {
            data.append(line).append("\n");
        }
        reader.close();
        fReader.close();
        return data.toString();
    }

    public void saveAccountsToInternal() throws JSONException, IOException {
        saveAccountsToInternal(getModel().currentFileName);
    }

    public void saveAccountsToInternal(String filename) throws JSONException, IOException {
        String payload = exportAccounts();
        writeToInternal(payload, filename);
    }

    public void readAccountsFromInternal(String filename) throws JSONException, IOException {
        String payload = readFromInternal(filename);
        boolean filenameValid = setCurrentFileName(filename);
        if (filenameValid) {
            importAccounts(payload);
            Util.FileNameParts parts = Util.parseFileName(filename);
            model.currentEntity = parts.entityName;
            model.settings.defaultEntityName = parts.entityName;
            Model.EntityDefaults defaults = model.getCurrentDefaults();
            if (defaults != null) {
                AccountBE sender = model.getAccountByName(defaults.defaultSender);
                AccountBE receiver = model.getAccountByName(defaults.defaultReceiver);
                if (sender != null)
                    model.currentSender = sender;
                if (receiver != null)
                    model.currentReceiver = receiver;
            }
        }
    }

    public void loadEntityCurrentPeriod(String entityName) throws JSONException, IllegalArgumentException, IOException {
        try {
            readAccountsFromInternal(Const.getCurrentMonthFileName(entityName));
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("No save file found for that entity and current period");
        }
    }

    public void loadEntityCurrentPeriod() throws JSONException, IllegalArgumentException, IOException {
        loadEntityCurrentPeriod(model.currentEntity);
    }

    public void loadEntity(String entityName) throws JSONException, IllegalArgumentException, IOException {
        // Validate parameter - check if the entity exists in the available entities
        List<String> allEntities = getAllAvailableEntities();
        if (!allEntities.contains(entityName))
            throw new IllegalArgumentException("No save file found for that entity.");

        // Find all available periods for the given entity
        List<String> availablePeriods = getAllPeriodsForEntity(entityName); // Will return list of strings with format "YYYY-MM"

        // Select the latest period. We can order the periods in descending order and get the first one,
        // which will be the latest. Since the first validation passed, we know the list is non-empty.
        availablePeriods.sort(Comparator.reverseOrder());
        String latestPeriod = availablePeriods.get(0);

        // Load the save file by constructing the file name out of the period and entityName
        String filename = latestPeriod + "-" + entityName + ".jso";
        try {
            readAccountsFromInternal(filename);
        } catch (JSONException | IOException e) {
            if (e instanceof JSONException)
                Log.println(Log.ERROR, "load_entity",
                        String.format("Error parsing save file of latest available period (%s) for entity %s: %s", latestPeriod, entityName, e));
            else
                Log.println(Log.ERROR, "load_entity",
                        String.format("Error reading save file of latest available period (%s) for entity %s: %s", latestPeriod, entityName, e));
            throw e;
        }

        // Get current period
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
        String currentPeriod = sdf.format(calendar.getTime());

        // Check if the new save file's period is the current one. If not, initiate a new period.
        if (!latestPeriod.equals(currentPeriod)) {
            initiateNewPeriod();
        }
    }

    public boolean deleteCurrentSave() {
        File file = new File(context.getFilesDir(), getModel().currentFileName + Const.ACCOUNTS_FILE_TYPE);
        resetAccounts();
        return file.delete();
    }

    public boolean deleteSavefile(String name) {
        File file = new File(context.getFilesDir(), Util.reduceFileTypeEnding(name) + Const.ACCOUNTS_FILE_TYPE);
        return file.delete();
    }

    // PARAMS: String period: a String representing the period in which to look for entities with existing save files
    // Format: "YYYY-MM"
    public List<String> getAvailableEntitiesForPeriod(String period) throws IllegalArgumentException {
        // Verify the format and plausibility of the period
        Pattern pattern = Pattern.compile("^\\d{4}-\\d{2}$");
        if (!pattern.matcher(period).matches()) {
            throw new IllegalArgumentException("The period should have the format 'YYYY-MM'");
        }

        int year = Integer.parseInt(period.split("-")[0]);
        int month = Integer.parseInt(period.split("-")[1]);
        if (year < 2000 || year > 2050 || month < 1 || month > 12) {
            throw new IllegalArgumentException("The year should be between 2000 and 2050 and the month should be between 1 and 12");
        }

        // Get all files in the directory
        File[] files = context.getFilesDir().listFiles();
        List<String> entityNames = new ArrayList<>();
        if (files == null || files.length == 0)
            return entityNames;

        // Filter the file names and extract the entity names
        pattern = Pattern.compile("^" + period + "-([^.]+)\\.jso$");
        for (File file : files) {
            Matcher matcher = pattern.matcher(file.getName());
            if (matcher.matches()) {
                entityNames.add(matcher.group(1));
            }
        }
        return entityNames;
    }

    public List<String> getCurrentAvailableEntities() {
        Calendar calendar = Calendar.getInstance();
        // get current period in format YYYY-MM
        String month = String.format(Locale.US, "%02d", calendar.get(Calendar.MONTH) + 1); // Calendar.MONTH is zero-based
        String year = String.valueOf(calendar.get(Calendar.YEAR));
        String period = year + "-" + month;

        try {
            // return getAvailableEntitiesForPeriod(period)
            return getAvailableEntitiesForPeriod(period);
        } catch (IllegalArgumentException e) {
            // exception handling should log an Error
            Log.println(Log.ERROR, "settings_backend",
                    String.format("Error getting available entities for current period: %s", e));
            return new ArrayList<>(); // return an empty list in case of an error
        }
    }

    // searches for save files of financial entities regardless of period
    public List<String> getAllAvailableEntities() {
        // Get all files in the directory
        File[] files = context.getFilesDir().listFiles();
        Set<String> entityNames = new HashSet<>();
        if (files == null || files.length == 0)
            return new ArrayList<>(entityNames);

        // Filter the file names and extract the entity names
        Pattern pattern = Pattern.compile("^\\d{4}-\\d{2}-([^.]+)\\.jso$");
        for (File file : files) {
            Matcher matcher = pattern.matcher(file.getName());
            if (matcher.matches()) {
                entityNames.add(matcher.group(1));
            }
        }
        return new ArrayList<>(entityNames);
    }

    public List<String> getAllPeriodsForEntity(String entityName) {
        // Get all files in the directory
        File[] files = context.getFilesDir().listFiles();
        List<String> periods = new ArrayList<>();
        if (files == null || files.length == 0)
            return periods;

        // Filter the file names and extract the periods for the specified entity
        Pattern pattern = Pattern.compile("^(\\d{4}-\\d{2})-" + Pattern.quote(entityName) + "\\.jso$");
        for (File file : files) {
            Matcher matcher = pattern.matcher(file.getName());
            if (matcher.matches()) {
                periods.add(matcher.group(1));  // This will match the YYYY-MM part of the filename
            }
        }
        return periods;
    }

    public void switchToEntity(String targetEntity) throws JSONException, IOException, IllegalArgumentException {
        // first validate argument
        List<String> availableEntities = getAllAvailableEntities();
        if (!availableEntities.contains(targetEntity)) {
            Log.println(Log.ERROR, "switch_entity",
                    "Error while trying to switch entity. Entity not valid. Aborting process!");
            throw new IllegalArgumentException("Entity not available");
        }
        // save current state
        String oldEntity = model.currentEntity;
        try {
            saveAccountsToInternal();
        } catch (JSONException | IOException e) {
            if (e instanceof JSONException)
                Log.println(Log.ERROR, "switch_entity",
                        String.format("Error while trying to switch entity. Could not serialize old state. Aborting process! Exception: %s", e));
            else
                Log.println(Log.ERROR, "switch_entity",
                        String.format("Error while trying to switch entity. Could not write old state. Aborting process! Exception: %s", e));
            throw e;
        }
        // load new entity
        try {
            loadEntity(targetEntity);
        } catch (JSONException | IOException | IllegalArgumentException e) {
            if (e instanceof JSONException)
                Log.println(Log.ERROR, "switch_entity",
                        String.format("Error while trying to switch entity. Could not parse new state. Aborting process! Exception: %s", e));
            else if (e instanceof IOException)
                Log.println(Log.ERROR, "switch_entity",
                        String.format("Error while trying to switch entity. Could not read new state. Aborting process! Exception: %s", e));
            else
                Log.println(Log.ERROR, "switch_entity",
                        String.format("Error while trying to switch entity. Entity not valid. Aborting process! Exception: %s", e));
            throw e;
        }
    }

    public void updateSelectedAccount(String selectionGroup, AccountBE newTarget) {
        if (selectionGroup.equals(Const.GROUP_SENDER)) {
            model.currentSender = newTarget;
            model.setCurrentDefaultSender(newTarget.toString());
        }
        else if (selectionGroup.equals(Const.GROUP_RECEIVER)) {
            model.currentReceiver = newTarget;
            model.setCurrentDefaultReceiver(newTarget.toString());
        }
        else
            Log.println(Log.INFO, "account_selection",
                    String.format("Unknown Selection Group (%s) called to set Account %s!", selectionGroup, newTarget));
    }

    public AccountBE getSelectedAccount(String selectionGroup) {
        if (selectionGroup.equals(Const.GROUP_SENDER))
            return model.currentSender;
        else if (selectionGroup.equals(Const.GROUP_RECEIVER))
            return model.currentReceiver;
        Log.println(Log.INFO, "account_selection",
                    String.format("Unknown Selection Group (%s) called for get Account!", selectionGroup));
        return null;
    }

    public boolean loadAppSettings() {
        try {
            String payload = readFromInternal(Const.APPLICATION_SETTINGS_FILENAME);
            model.settings = Util.parseJSON_Settings(new JSONObject(payload));
            model.currentEntity = model.settings.defaultEntityName;
            return true;
        } catch (JSONException | IOException e) {
            // settings could not be read due to any reason
            // set current financial entity name (nothing to load, so set default)
            model.currentEntity = "User";
            // set default name to settings
            model.settings.defaultEntityName = "User";

            // differentiate between exceptions
            if (e instanceof FileNotFoundException) {
                Log.println(Log.ERROR, "load_settings",
                        String.format("Error reading settings file. The file could not be located. Loaded defaults and saved them: %s", e));
                // no settings file available (e.g. at first start) -> save newly created settings
                try {
                    saveAppSettings();
                } catch (JSONException | IOException newE) {
                    newE.printStackTrace();
                }
            } else if (e instanceof IOException) {
                // the file exists but could not be read
                Log.println(Log.ERROR, "load_settings",
                        String.format("Error reading settings file. The file could be located but not read. Loaded defaults instead: %s", e));
            } else {
                // file could be read but is corrupted in some sort
                Log.println(Log.ERROR, "load_settings",
                        String.format("Error parsing settings file. The file is probably corrupted. Loaded defaults instead: %s", e));
            }
        }
        return false;
    }

    public void saveAppSettings() throws JSONException, IOException {
        JSONObject settingsJSON;
        try {
            settingsJSON = Util.serialise_Settings(model.settings);
            writeToInternal(settingsJSON.toString(), Const.APPLICATION_SETTINGS_FILENAME);
        } catch (JSONException | IOException e) {
            if (e instanceof JSONException)
                Log.println(Log.ERROR, "save_settings",
                        String.format("Can't save... %s", e));
            else
                Log.println(Log.ERROR, "save_settings",
                        String.format("Error trying to write settings to storage: %s", e));
            throw e;
        }
    }
    //endregion

    // region perform transactions
    // create a transaction between two accounts
    public boolean createTx(MainActivity parentActivity, String desc, float amount) throws JSONException, IOException {
        AccountBE from_acc = parentActivity.model.currentSender;
        AccountBE to_acc = parentActivity.model.currentReceiver;
        Calendar calendar = Calendar.getInstance();
        TxBE entry_from = new TxBE(amount*(-1.0f), desc, calendar.getTime());
        TxBE entry_to = new TxBE(amount, desc, calendar.getTime());
        from_acc.addTx(entry_from);
        to_acc.addTx(entry_to);
        boolean result = true;

        // handle case if to_acc relays payment to other financial entity if it isn't a BudgetAccount, pass
        if (to_acc instanceof BudgetAccountBE) {
            BudgetAccountBE to_budgetAcc = (BudgetAccountBE) to_acc;
            String otherEntity = to_budgetAcc.getOtherEntity();
            if (otherEntity != null && !otherEntity.equals(""))
                result = startTxRedirection(parentActivity, otherEntity, desc, amount);
        }
        if (result) {
            try {
                saveAccountsToInternal();
                return true;
            } catch (JSONException | IOException e) {
                from_acc.removeTx(entry_from);
                to_acc.removeTx(entry_to);
                if (e instanceof JSONException)
                    Log.println(Log.ERROR, "save_file",
                            String.format("Error serializing the JSONObject to save changes after creating a Tx: %s\nChanges have been reverted.", e));
                else
                    Log.println(Log.ERROR, "save_file",
                            String.format("Error writing save file after creating a Tx: %s\nChanges have been reverted.", e));
                throw e;
            }
        }
        return false;
    }

    // Start the transaction redirection
    public boolean startTxRedirection(MainActivity parentActivity, String targetEntity, String desc, float amount) throws JSONException, IOException {
        Util.FileNameParts curAttrs = model.currentFileAttributes;
        // try finding save file to other entity
        // construct filename to look for
        String fileNameOther = model.currentFileName.replace(
                curAttrs.entityName, targetEntity);
        JSONObject json;
        JSONArray allAccounts = new JSONArray();
        JSONArray budgetAccounts;
        try {
            String data = readFromInternal(fileNameOther);
            json = new JSONObject(data);
            budgetAccounts = json.getJSONArray(Const.JSON_TAG_BUDGET_ACCOUNTS);
            json.getJSONArray(Const.JSON_TAG_CURRENT_INCOME);
            allAccounts = Util.copyJSONArray(json.getJSONArray(Const.JSON_TAG_ASSET_ACCOUNTS));
            for (int i = 0; i < budgetAccounts.length(); i++) {
                allAccounts.put(budgetAccounts.getJSONObject(i));
            }
        } catch (IOException | JSONException e) {
            if (e instanceof IOException)
                Log.println(Log.ERROR, "load_other_file",
                        String.format("Error loading other save file: %s", e));
            else
                Log.println(Log.ERROR, "load_other_file",
                        String.format("Error parsing other save file: %s", e));
            throw e;
        }
        parentActivity.getTransactionRedirectionInput(fileNameOther, json, desc, amount, allAccounts);
        return true;
    }

    // Complete the transaction redirection
    public boolean completeTxRedirection(String targetFileName, String senderName, String desc, float amount, String accountName, JSONObject data) throws JSONException, IOException {
        Calendar calendar = Calendar.getInstance();
        TxBE new_entry = new TxBE(amount, desc, calendar.getTime());
        boolean foundTargetAccount = false;
        boolean foundAssetAccounts = false;
        boolean foundBudgetAccounts = false;
        JSONArray assetAccounts = new JSONArray();
        JSONArray budgetAccounts = new JSONArray();
        JSONArray incomeList = new JSONArray();
        try {
            incomeList = data.getJSONArray(Const.JSON_TAG_CURRENT_INCOME);
        } catch (JSONException e) {
            return false;
        }
        try {
            assetAccounts = data.getJSONArray(Const.JSON_TAG_ASSET_ACCOUNTS);
            foundAssetAccounts = true;
        } catch (JSONException ignored) { }
        if (foundAssetAccounts) {
            // iterate through all asset accounts as json objects
            for (int i = 0; i < assetAccounts.length(); i++) {
                // set variables for access out of try/catch
                JSONObject currentAccount;
                String currentAccountName;
                // get current asset account as json object and corresponding account name
                try {
                    currentAccount = assetAccounts.getJSONObject(i);
                    currentAccountName = currentAccount.getString(Const.JSON_TAG_NAME);
                } catch (JSONException e) {
                    continue;
                }
                // if current account name equals target account name
                if (currentAccountName.equals(accountName)) {
                    // parse current account
                    AccountBE curAccount = Util.parseJSON_Account(currentAccount);
                    // check if parsing worked
                    if (curAccount != null) {
                        // add pre-calculated entry to parsed account object
                        curAccount.addTx(new_entry);
                        // serialise adjusted account object and replace its old version in account list
                        // replace asset accounts in save file json object
                        try {
                            assetAccounts.put(i, Util.serialise_Account(curAccount));
                            data.put(Const.JSON_TAG_ASSET_ACCOUNTS, assetAccounts);
                            foundTargetAccount = true;
                            break;
                        } catch (JSONException e) {
                            Log.println(Log.ERROR, "pass_on_transaction",
                                    String.format("Error serializing target asset account after adding new entry: %s", e));
                            throw e;
                        }
                    }
                    // error happened parsing the current account
                    else {
                        Log.println(Log.ERROR, "pass_on_transaction",
                                String.format("Error passing on transaction. Could not parse asset account object! targetAccountName: %s", accountName));
                        return false;
                    }
                }
            }
        }
        // if target has not yet been found, iterate through all budget accounts as json objects
        if (!foundTargetAccount) {
            try {
                budgetAccounts = data.getJSONArray(Const.JSON_TAG_BUDGET_ACCOUNTS);
                foundBudgetAccounts = true;
            } catch (JSONException ignored) {
            }
            if (foundBudgetAccounts) {
                for (int i = 0; i < budgetAccounts.length(); i++) {
                    // set variables for access out of try/catch
                    JSONObject currentAccount;
                    String currentAccountName;
                    // get current budget account as json object and corresponding account name
                    try {
                        currentAccount = budgetAccounts.getJSONObject(i);
                        currentAccountName = currentAccount.getString(Const.JSON_TAG_NAME);
                    } catch (JSONException e) {
                        continue;
                    }
                    // if current account name equals target account name
                    if (currentAccountName.equals(accountName)) {
                        // parse current account
                        BudgetAccountBE curAccount = Util.parseJSON_BudgetAccount(currentAccount);
                        if (curAccount != null) {
                            // add pre-calculated entry to parsed account object
                            curAccount.addTx(new_entry);
                            // serialise adjusted account object and replace its old version in account list
                            // replace budget accounts in save file json object
                            try {
                                budgetAccounts.put(i, Util.serialise_BudgetAccount(curAccount));
                                data.put(Const.JSON_TAG_BUDGET_ACCOUNTS, budgetAccounts);
                                foundTargetAccount = true;
                                break;
                            } catch (JSONException e) {
                                Log.println(Log.ERROR, "pass_on_transaction",
                                        String.format("Error serializing target budget account after adding new entry: %s", e));
                                throw e;
                            }
                        }
                        // error happened parsing the current account
                        else {
                            Log.println(Log.ERROR, "pass_on_transaction",
                                    String.format("Error passing on transaction. Could not parse asset account object! targetAccountName: %s", accountName));
                            return false;
                        }
                    }
                }
            }
        }
        if (! foundTargetAccount)
            return false;
        // create entry for other entities income list
        TxBE incomeEntry = new TxBE(amount, String.format("%s: %s", senderName, desc), calendar.getTime());
        incomeList.put(Util.serialise_Entry(incomeEntry));
        // rewrite edited income list to json object
        try {
            data.put(Const.JSON_TAG_CURRENT_INCOME, incomeList);
            writeToInternal(data.toString(), targetFileName);
        } catch (JSONException | IOException e) {
            if (e instanceof JSONException)
                Log.println(Log.ERROR, "pass_on_transaction",
                        String.format("Error putting adjusted income list into save file json object: %s", e));
            else
                Log.println(Log.ERROR, "pass_on_transaction",
                        String.format("Error writing adjusted save file json object: %s", e));
            throw e;
        }
        return true;
    }

    public boolean deleteTx(AccountBE parent, TxBE tx) throws JSONException, IOException {
        int position = parent.getTxIndex(tx);
        if (position == -1)
            return false;
        parent.removeTx(tx);
        try {
            saveAccountsToInternal();
            return true;
        } catch (JSONException | IOException e) {
            parent.addTx(position, tx);
            if (e instanceof JSONException)
                Log.println(Log.ERROR, "delete_tx",
                        String.format("Error serializing the JSONObject to save changes after deleting tx (%s): %s\nChanges have been reverted.", tx, e));
            else
                Log.println(Log.ERROR, "delete_tx",
                        String.format("Error writing save file after deleting tx (%s): %s\nChanges have been reverted.", tx, e));
            throw e;
        }
    }

    // add funds to one account
    public boolean addFunds(float amount, String desc) throws JSONException, IOException {
        TxBE newFunds = new TxBE(amount, desc, Calendar.getInstance().getTime());
        if (model.currentReceiver == null) {
            return false;
        }
        model.currentReceiver.addTx(newFunds);
        model.currentIncome.add(newFunds);
        try {
            saveAccountsToInternal();
        } catch (JSONException | IOException e) {
            model.currentReceiver.dropLastTx();
            model.currentIncome.remove(newFunds);
            if (e instanceof JSONException)
                Log.println(Log.ERROR, "save_file",
                        String.format("Error serializing the JSONObject to save changes after adding funds: %s\nChanges have been reverted.", e));
            else
                Log.println(Log.ERROR, "save_file",
                        String.format("Error writing save file after adding funds: %s\nChanges have been reverted.", e));
            throw e;
        }
        return true;
    }

    public boolean addRecurringTx(MainActivity parent, String desc, float amount) throws JSONException, IOException{
        Calendar calendar = Calendar.getInstance();
        AccountBE sender = parent.model.currentSender;
        AccountBE receiver = parent.model.currentReceiver;
        try {
            assert sender != null;
            assert receiver != null;
        } catch (AssertionError e) {
            Log.println(Log.ERROR, "get_tx_partners",
                    String.format("Error trying to retrieve sender or receiver account from model: %s", e));
            return false;
        }
        RecurringTxBE newOrder = new RecurringTxBE(amount, desc, calendar.getTime(), sender.getName(), receiver.getName());
        model.recurringTx.add(newOrder);
        try {
            saveAccountsToInternal();
        } catch (JSONException | IOException e) {
            model.recurringTx.remove(newOrder);
            if (e instanceof JSONException)
                Log.println(Log.ERROR, "save_file",
                        String.format("Error serializing save file after adding recurring transaction: %s\nChanges have been reverted.", e));
            else
                Log.println(Log.ERROR, "save_file",
                        String.format("Error writing save file after adding recurring transaction: %s\nChanges have been reverted.", e));
            throw e;
        }
        return true;
    }

    public boolean deleteRecurringTx(RecurringTxBE recurringTx) throws JSONException, IOException {
        int position = model.recurringTx.indexOf(recurringTx);
        if (position == -1)
            return false;
        try {
            model.recurringTx.remove(recurringTx);
            saveAccountsToInternal();
        } catch (JSONException | IOException e) {
            // revert changes
            model.recurringTx.add(position, recurringTx);
            if (e instanceof JSONException)
                Log.println(Log.ERROR, "save_file",
                        String.format("Error serializing save file after deleting recurring transaction: %s\nChanges have been reverted.", e));
            else
                Log.println(Log.ERROR, "save_file",
                        String.format("Error writing save file after deleting recurring transaction: %s\nChanges have been reverted.", e));
            throw e;
        }
        return true;
    }

    private void triggerRecurringTx() throws JSONException, IOException {
        class AccountTxCombo {
            AccountBE account;
            TxBE tx;

            public AccountTxCombo(AccountBE acc, TxBE tx) {
                this.account = acc;
                this.tx = tx;
            }
        }

        Calendar fom = Const.getFirstOfMonth();
        List<AccountTxCombo> addedTx = new ArrayList<>();
        for (RecurringTxBE r : getModel().recurringTx) {
            AccountBE receiver = model.getAccountByName(r.getReceiverStr());
            TxBE receiverTx = new TxBE(r.getAmount(), r.getDescription(), fom.getTime());
            try {
                assert receiver != null;
            } catch (AssertionError e) {
                Log.println(Log.INFO, "execute_recur_tx",
                        String.format("Error triggering recurring Transactions: Could not find Sender (%s) or Receiver (%s) account",
                                r.getSenderStr(),
                                r.getReceiverStr()));
                continue;
            }
            try {
                assert !r.getSenderStr().equals("");
            } catch (AssertionError e) {
                // assuming RecurringTx is a recurring income -> add to currentIncome instead, ignore for addedTx
                TxBE incomeTx = new TxBE(r.getAmount(), r.getDescription(), fom.getTime());
                model.currentIncome.add(incomeTx);
                receiver.addTx(receiverTx);
                addedTx.add(new AccountTxCombo(receiver, receiverTx));
                continue;
            }
            AccountBE sender = model.getAccountByName(r.getSenderStr());
            try {
                assert sender != null;
                TxBE senderTx = new TxBE(r.getAmount()*(-1.0f), r.getDescription(), fom.getTime());
                sender.addTx(senderTx);
                receiver.addTx(receiverTx);
                addedTx.add(new AccountTxCombo(sender, senderTx));
                addedTx.add(new AccountTxCombo(receiver, receiverTx));
            } catch (AssertionError e) {
                Log.println(Log.INFO, "execute_recur_tx",
                        String.format("Error triggering recurring Transactions: Could not find Sender (%s) or Receiver (%s) account",
                                r.getSenderStr(),
                                r.getReceiverStr()));
            }
        }
        try {
            saveAccountsToInternal();
        } catch (JSONException | IOException e) {
            // revert changes
            for (AccountTxCombo entry : addedTx) {
                entry.account.getTxList().remove(entry.tx);
            }
            if (e instanceof JSONException)
                Log.println(Log.ERROR, "save_file",
                        String.format("Error serializing save file after triggering recurring transactions: %s\nChanges have been reverted.", e));
            else
                Log.println(Log.ERROR, "save_file",
                        String.format("Error writing save file after triggering recurring transactions: %s\nChanges have been reverted.", e));
            throw e;
        }
    }
    // endregion

    // region Account Handling
    public AccountBE createAssetAccount(String name) throws JSONException, IOException {
        // check if name is already in use
        AccountBE similarName = model.getAccountByName(name);
        if (similarName != null) {
            return null;
        }
        AccountBE newAccount = new AccountBE(name);
        model.asset_accounts.add(newAccount);
        try {
            saveAccountsToInternal();
            return newAccount;
        } catch (JSONException | IOException e) {
            model.asset_accounts.remove(newAccount);
            if (e instanceof JSONException)
                Log.println(Log.ERROR, "save_file",
                        String.format("Error serializing save file after creating asset account: %s\nChanges have been reverted.", e));
            else
                Log.println(Log.ERROR, "save_file",
                        String.format("Error writing save file after creating asset account: %s\nChanges have been reverted.", e));
            throw e;
        }
    }

    public BudgetAccountBE createRootBudget(String name,float currentBudget, float yearlyBudget) throws JSONException, IOException {
        // check if name is already in use
        AccountBE similarName = model.getAccountByName(name);
        if (similarName != null) {
            return null;
        }
        BudgetAccountBE newAccount = new BudgetAccountBE(name, currentBudget, yearlyBudget);
        model.budget_accounts.add(newAccount);
        try {
            saveAccountsToInternal();
            return newAccount;
        } catch (JSONException | IOException e) {
            model.budget_accounts.remove(newAccount);
            if (e instanceof JSONException)
                Log.println(Log.ERROR, "save_file",
                        String.format("Error serializing save file after creating budget account: %s\nChanges have been reverted.", e));
            else
                Log.println(Log.ERROR, "save_file",
                        String.format("Error writing save file after creating budget account: %s\nChanges have been reverted.", e));
            throw e;
        }
    }

    public BudgetAccountBE createSubBudget(BudgetAccountBE parent,
                                           String name,
                                           float current_budget,
                                           float yearly_budget) throws JSONException, IOException {
        // check if name is already in use
        AccountBE similarName = model.getAccountByName(name);
        if (similarName != null) {
            return null;
        }
        BudgetAccountBE newAccount = new BudgetAccountBE(name, current_budget, yearly_budget);
        parent.addSubBudget(newAccount);
        try {
            saveAccountsToInternal();
            return newAccount;
        } catch (JSONException | IOException e) {
            parent.getDirectSubBudgets().remove(newAccount);
            if (e instanceof JSONException)
                Log.println(Log.ERROR, "save_file",
                        String.format("Error serializing save file after creating budget account: %s\nChanges have been reverted.", e));
            else
                Log.println(Log.ERROR, "save_file",
                        String.format("Error writing save file after creating budget account: %s\nChanges have been reverted.", e));
            throw e;
        }
    }

    public BudgetAccountBE createSubBudget(BudgetAccountBE parent,
                                           String name,
                                           float yearly_budget) throws JSONException, IOException {
        return createSubBudget(parent, name, yearly_budget / 12, yearly_budget);
    }

    public ProjectBudgetBE createProjectBudget(BudgetAccountBE parent, String name, float total_budget) throws JSONException, IOException {
        // check if name is already in use
        AccountBE similarName = model.getAccountByName(name);
        if (similarName != null) {
            return null;
        }
        ProjectBudgetBE newAccount = new ProjectBudgetBE(name, total_budget);
        parent.addSubBudget(newAccount);
        parent.adjustIndivYearlyBudget(-total_budget);
        try {
            saveAccountsToInternal();
            return newAccount;
        } catch (JSONException | IOException e) {
            parent.getDirectSubBudgets().remove(newAccount);
            parent.adjustIndivYearlyBudget(total_budget);
            if (e instanceof JSONException)
                Log.println(Log.ERROR, "save_file",
                        String.format("Error serializing save file after creating budget account: %s\nChanges have been reverted.", e));
            else
                Log.println(Log.ERROR, "save_file",
                        String.format("Error writing save file after creating budget account: %s\nChanges have been reverted.", e));
            throw e;
        }
    }

    public boolean deleteAccount(String accountName) throws JSONException, IOException {
        AccountBE account = model.getAccountByName(accountName);
        if (account != null)
            return deleteAccount(account);
        return false;
    }

    public boolean deleteAccount(AccountBE account) throws JSONException, IOException {
        assert account != null;
        // variable to store position at which account was in its list.
        // needed in case of revert to initial state.
        // position != -1 then also signals whether the account has been found and removed
        int position = -1;
        BudgetAccountBE parentBudget = null;
        if (account instanceof BudgetAccountBE) {
            position = model.budget_accounts.indexOf(account);
            if (position != -1)
                model.budget_accounts.remove(account);
            else {
                for (BudgetAccountBE budget : model.budget_accounts) {
                    parentBudget = budget.getSubBudgetParent((BudgetAccountBE) account);
                    if (parentBudget == null)
                        continue;
                    position = parentBudget.getDirectSubBudgets().indexOf(account);
                    parentBudget.getDirectSubBudgets().remove(account);
                    break;
                }
            }
        }
        else {
            position = model.asset_accounts.indexOf(account);
            if (position != -1)
                model.asset_accounts.remove(account);
        }
        // if account to be deleted could not be found, return false
        if (position == -1)
            return false;
        try {
            saveAccountsToInternal();
            return true;
        } catch (JSONException | IOException e) {
            if (account instanceof BudgetAccountBE) {
                if (parentBudget == null)
                    model.budget_accounts.add(position, (BudgetAccountBE) account);
                else
                    parentBudget.getDirectSubBudgets().add(position, (BudgetAccountBE) account);
            } else
                model.asset_accounts.add(position, account);
            if (e instanceof JSONException)
                Log.println(Log.ERROR, "save_file",
                        String.format("Error serializing save file after deleting account: %s\nChanges have been reverted.", e));
            else
                Log.println(Log.ERROR, "save_file",
                        String.format("Error writing save file after deleting account: %s\nChanges have been reverted.", e));
            throw e;
        }
    }
    // endregion

    // sets up accounts for new month by loading last month, closing all accounts, and transferring the balances to new month´s accounts
    // returns true if transfer finalized successfully
    // returns false if couldn't read last month (e.g. there is no file of last month´s accounts)

    @SuppressLint("SimpleDateFormat")
    public void initiateNewPeriod() throws JSONException, IOException {
        Calendar cal = Calendar.getInstance();
        File[] availableFiles = context.getFilesDir().listFiles();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM");
        Date latestDate = null;
        File latestFile = null;
        String entityPattern = "\\d{4}-\\d{2}-" + model.currentEntity + "\\.jso";
        // Check if availableFiles is null or empty
        if (availableFiles == null || availableFiles.length == 0) {
            throw new FileNotFoundException("Error while trying to read save files in internal storage. No files were found.");
        }
        // Inspect available files
        for (File file : availableFiles) {
            if (file.getName().matches(entityPattern)) {
                try {
                    Date fileDate = sdf.parse(file.getName().substring(0, 7));
                    assert fileDate != null;
                    if (latestDate == null || fileDate.after(latestDate)) {
                        latestDate = fileDate;
                        latestFile = file;
                    }
                } catch (ParseException pe) {
                    pe.printStackTrace();
                }
            }
        }

        // If no file matches the pattern, throw FileNotFoundException
        if (latestFile == null) {
            throw new FileNotFoundException("No valid file found.");
        }

        // If the latest date is not earlier than the current month, return false
        try {
            if (!latestDate.before(sdf.parse(sdf.format(cal.getTime())))) {
                throw new FileNotFoundException("No valid file found.");
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }


        // If the latest date is earlier than the current month, initiate the transfer process
        try {
            // check if it is already loaded (possible for special cases)
            if (!latestFile.getName().equals(model.currentFileName))
                readAccountsFromInternal(latestFile.getName());
        } catch (JSONException | IOException e) {
            if (e instanceof JSONException)
                Log.println(Log.ERROR, "initiate_period",
                        String.format("Error while initiating new period. An exception occurred while parsing latest save file: %s", e));
            else
                Log.println(Log.ERROR, "initiate_period",
                        String.format("Error while initiating new period. An exception occurred while reading latest save file: %s", e));
            throw e;
        }

        renewAccounts();
        resetCurrentIncome();
        setCurrentFileName(Const.getCurrentMonthFileName(model.currentEntity));

        try {
            triggerRecurringTx();
        } catch (JSONException | IOException e) {
            // only log exception, changes have already been reverted
            Log.println(Log.ERROR, "initiate_period",
                    String.format("Error while initiating new period. An exception occurred while triggering recurring tx: %s", e));
        }
    }

    // sets up the lists AssetAccounts and BudgetAccounts
    // @params
    // blank: if true makes new accounts, if false tries to load saved accounts and starts new month if there´s no save file for current month
    // returns CREATED_BLANK if blank accounts were created
    // returns LOADED_ACCOUNTS if saved accounts have been loaded
    // returns LOADED_NEW_MONTH if new month accounts have been created
    public int setupAccounts(boolean blank) {
        try {
            if (blank) {
                resetAccountLists();
                return CREATED_BLANK;
            } else if (model.currentEntity == null || model.currentEntity.equals("")) {
                // no current entity stored in model. loadEntity won't know what to load. crash safely
                Log.println(Log.ERROR, "load_accounts",
                        "Error loading default entity: no entity available in model. Loading no accounts.");
                return CREATED_BLANK;
            } else {
                // at this point model.currentEntity contains a non-empty String
                loadEntityCurrentPeriod();
                return LOADED_ACCOUNTS;
            }
        } catch (IllegalArgumentException e) {
            // no save file for current entity and month found. cause could be either
            try {
                // try initiating a new period with current entity
                initiateNewPeriod();
                return LOADED_NEW_MONTH;
            } catch (FileNotFoundException ex) {
                // no valid file could be found
                Log.println(Log.ERROR, "load_accounts",
                        String.format("While trying to initiate a new period, no valid source file " +
                                "for currentEntity could be located. This probably implies, that it is invalid. Exception: %s", ex));
                resetAccountLists();
                return CREATED_BLANK;
            } catch (JSONException | IOException ex) {
                // a valid file as source for initiating a new period could be located but not read. exception has already been logged on that level.
                Log.println(Log.INFO, "load_accounts", "While trying to initiate a new period, a valid save file could be located but not read. " +
                        "This probably implies, that the currentEntity is correct but its latest save file is corrupted.");
                resetAccountLists();
                return CREATED_BLANK;
            }
        } catch (JSONException | IOException e) {
            if (e instanceof JSONException) {
                // save file for default entity and current month exists but is corrupted
                Log.println(Log.ERROR, "load_accounts",
                        String.format("Error loading default entity: save file for current month could be located, but is corrupted. Loading no accounts. %s", e));
            } else {
                // save file for default entity and current month exists but could not be read
                Log.println(Log.ERROR, "load_accounts",
                        String.format("Error loading default entity: save file for current month could be located, but not read. Loading no accounts. %s", e));
            }
            resetAccountLists();
            return CREATED_BLANK;
        }
    }

    // region update objects
    public boolean updateTx(Date date, String description, AccountBE source, float newAmount) throws JSONException, IOException {
        // try identifying the entry making the call
        TxBE sourceEntry = null;
        for (TxBE e : source.getTxList()) {
            if (e.getDate().equals(date) && e.getDescription().equals(description)) {
                sourceEntry = e;
            }
        }
        // if none was found, then return false
        if (sourceEntry == null)
            return false;

        // setup list of all accounts to search for other part of transaction
        // for this take asset accounts which are already of type AccountBE
        List<AccountBE> toSearch = model.asset_accounts;
        // then transform budget accounts and first order sub budgets
        List<AccountBE> transformed_budget_accounts = new ArrayList<>();
        for (BudgetAccountBE budget_account : model.budget_accounts) {
            transformed_budget_accounts.add(budget_account);
            List<BudgetAccountBE> sub_budgets = budget_account.getDirectSubBudgets();
            if (sub_budgets.size() > 0)
                transformed_budget_accounts.addAll(sub_budgets);

        }
        // and add to toSearch list
        toSearch.addAll(transformed_budget_accounts);
        // remove source account, which by then will inevitably have been added
        toSearch.remove(source);

        // store old amount for later in case, changes need to be reverted
        float oldAmount = sourceEntry.getAmount();

        for (AccountBE account : toSearch) {
            for (TxBE entry : account.getTxList()) {
                if (entry.getDate().equals(date) && entry.getDescription().equals(description)) {
                    entry.setAmount(newAmount * (-1.0f));
                    sourceEntry.setAmount(newAmount);
                    try {
                        saveAccountsToInternal();
                        return true;
                    } catch (JSONException | IOException e) {
                        entry.setAmount(oldAmount * (-1.0f));
                        sourceEntry.setAmount(oldAmount);
                        if (e instanceof JSONException)
                            Log.println(Log.ERROR, "save_file",
                                    String.format("Error serializing save file after updating entry amount: %s\nChanges have been reverted.", e));
                        else
                            Log.println(Log.ERROR, "save_file",
                                    String.format("Error writing save file after updating entry amount: %s\nChanges have been reverted.", e));
                        throw e;
                    }
                }
            }
        }
        return false;
    }

    public boolean updateTx(Date date, String description, AccountBE source, String newDescription) throws JSONException, IOException {
        // try identifying the entry making the call
        TxBE sourceEntry = null;
        for (TxBE e : source.getTxList()) {
            if (e.getDate().equals(date) && e.getDescription().equals(description)) {
                sourceEntry = e;
            }
        }
        // if none was found, then return false
        if (sourceEntry == null)
            return false;

        // setup list of all accounts to search for other part of transaction
        // for this take asset accounts which are already of type AccountBE
        List<AccountBE> toSearch = model.asset_accounts;
        // then transform budget accounts and first order sub budgets
        List<AccountBE> transformed_budget_accounts = new ArrayList<>();
        for (BudgetAccountBE budget_account : model.budget_accounts) {
            transformed_budget_accounts.add((AccountBE) budget_account);
            List<BudgetAccountBE> sub_budgets = budget_account.getDirectSubBudgets();
            if (sub_budgets.size() > 0)
                for (BudgetAccountBE sub_budget : sub_budgets)
                    transformed_budget_accounts.add((AccountBE) sub_budget);

        }
        // and add to toSearch list
        toSearch.addAll(transformed_budget_accounts);
        // remove source account, which by then will inevitably have been added
        toSearch.remove(source);

        for (AccountBE account : toSearch) {
            for (TxBE entry : account.getTxList()) {
                if (entry.getDate().equals(date) && entry.getDescription().equals(description)) {
                    entry.setDescription(newDescription);
                    sourceEntry.setDescription(newDescription);
                    try {
                        saveAccountsToInternal();
                        return true;
                    } catch (JSONException | IOException e) {
                        entry.setDescription(description);
                        sourceEntry.setDescription(description);
                        if (e instanceof JSONException)
                            Log.println(Log.ERROR, "save_file",
                                    String.format("Error serializing save file after updating entry description: %s\nChanges have been reverted.", e));
                        else
                            Log.println(Log.ERROR, "save_file",
                                    String.format("Error writing save file after updating entry description: %s\nChanges have been reverted.", e));
                        throw e;
                    }
                }
            }
        }
        return false;
    }

    public void updateYearlyBudget(float newBudget, BudgetAccountBE account, boolean adjustAvailable) throws JSONException, IOException {
        float oldYearlyBudget = account.indivYearlyBudget;
        float oldAvailableBudget = account.indivAvailableBudget;
        account.setIndivYearlyBudget(newBudget);
        if (adjustAvailable)
            account.setIndivAvailableBudget(oldAvailableBudget + (newBudget - oldYearlyBudget) * (account.getRenewalPeriod() / 12.0f));
        try {
            saveAccountsToInternal();
        }  catch (JSONException | IOException e) {
            account.setIndivYearlyBudget(oldYearlyBudget);
            if (adjustAvailable)
                account.setIndivAvailableBudget(oldAvailableBudget);
            if (e instanceof JSONException)
                Log.println(Log.ERROR, "save_file",
                    String.format("Error serializing save file after updating yearly budget: %s\nChanges have been reverted.", e));
            else
                Log.println(Log.ERROR, "save_file",
                        String.format("Error writing save file after updating yearly budget: %s\nChanges have been reverted.", e));
            throw e;
        }
    }

    public void transferAvailableBudget(float amount, BudgetAccountBE sender, BudgetAccountBE recipient) throws JSONException, IOException, InvalidParameterException {
        if (amount <= 0)
            throw new InvalidParameterException("transferAvailableBudget called with amount <= 0");
        float oldSenderCurrent = sender.indivAvailableBudget;
        float oldRecipientCurrent = recipient.indivAvailableBudget;

        sender.setIndivAvailableBudget(oldSenderCurrent - amount);
        recipient.setIndivAvailableBudget(oldRecipientCurrent + amount);

        try {
            saveAccountsToInternal();
        }  catch (JSONException | IOException e) {
            sender.setIndivAvailableBudget(oldSenderCurrent);
            recipient.setIndivAvailableBudget(oldRecipientCurrent);
            if (e instanceof JSONException)
                Log.println(Log.ERROR, "save_file",
                    String.format("Error serializing save file after transferring available budget: %s\nChanges have been reverted.", e));
            else
                Log.println(Log.ERROR, "save_file",
                        String.format("Error writing save file after transferring available budget: %s\nChanges have been reverted.", e));
            throw e;
        }
    }

    public boolean transferSubBudget(BudgetAccountBE parent, BudgetAccountBE object, BudgetAccountBE target) throws JSONException, IOException {
        boolean result = parent.transferSubBudget(object, target);
        if (result)
            try {
                saveAccountsToInternal();
            }  catch (JSONException | IOException e) {
                target.transferSubBudget(object, parent);
                if (e instanceof JSONException)
                    Log.println(Log.ERROR, "save_file",
                            String.format("Error serializing save file after transferring sub budget: %s\nChanges have been reverted.", e));
                else
                    Log.println(Log.ERROR, "save_file",
                            String.format("Error writing save file after transferring sub budget: %s\nChanges have been reverted.", e));
                throw e;
            }
        return result;
    }
}
