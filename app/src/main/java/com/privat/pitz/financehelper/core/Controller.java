package com.privat.pitz.financehelper.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;
import com.privat.pitz.financehelper.data.ProjectBudgetBE;
import com.privat.pitz.financehelper.data.TxBE;
import com.privat.pitz.financehelper.data.RecurringTxBE;

public class Controller {
    public static Controller instance;
    Model model;
    Context context;
    SavefileStorage storage;

    public static int LOADED_ACCOUNTS = 10;
    public static int LOADED_NEW_MONTH = 11;
    public static int CREATED_BLANK = 12;

    private Controller(Context context, SavefileStorage storage) {
        this.context = context;
        this.storage = storage;
        initController();
    }

    Controller(SavefileStorage storage) {
        this.storage = storage;
        this.model = new Model();
    }

    private void initController() {
        model = new Model();
    }

    public static void createInstance(Context context) {
        Context app = context.getApplicationContext();
        instance = new Controller(app, new DirectoryStorage(app.getFilesDir()));
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
        storage.write(filename, data);
    }

    public String readFromInternal(String filename) throws IOException {
        return storage.read(filename);
    }

    public void saveAccountsToInternal() throws JSONException, IOException {
        saveAccountsToInternal(getModel().currentFileName);
    }

    public void saveAccountsToInternal(String filename) throws JSONException, IOException {
        String payload = exportAccounts();
        writeToInternal(payload, filename);
    }

    /**
     * The optimistic-mutate / save / revert-on-failure pattern used by every mutating operation:
     * the caller mutates the model first, then calls this with an action that undoes that mutation
     * if persisting fails.
     *
     * @param logTag log tag, e.g. "save_file"
     * @param what   noun phrase completing "... after %s", e.g. "adding funds"
     * @param revert undoes the caller's in-memory mutation; must not throw
     */
    private void saveOrRevert(String logTag, String what, Runnable revert)
            throws JSONException, IOException {
        try {
            saveAccountsToInternal();
        } catch (JSONException | IOException e) {
            revert.run();
            Log.println(Log.ERROR, logTag, String.format(
                    e instanceof JSONException
                            ? "Error serializing save file after %s: %s%nChanges have been reverted."
                            : "Error writing save file after %s: %s%nChanges have been reverted.",
                    what, e));
            throw e;
        }
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

    // bundles all save files (and the app settings) into a single zip file at the given SAF Uri,
    // so it can be picked up by a file manager, cloud sync folder, or copied off the device over USB
    public int exportAllSavefilesToUri(Uri targetUri) throws IOException {
        List<String> names = storage.list();
        int count = 0;
        OutputStream os = context.getContentResolver().openOutputStream(targetUri);
        if (os == null)
            throw new IOException("Could not open output stream for target Uri");
        try (ZipOutputStream zos = new ZipOutputStream(os)) {
            for (String name : names) {
                if (!Util.isSyncableName(name))
                    continue;
                zos.putNextEntry(new ZipEntry(name));
                try (InputStream is = storage.openRead(name)) {
                    Util.copyStream(is, zos);
                }
                zos.closeEntry();
                count++;
            }
        }
        return count;
    }

    // extracts all save files (and app settings, if present) from a previously exported zip
    // file at the given SAF Uri back into internal storage
    public int importSavefilesFromZipUri(Uri sourceUri) throws IOException {
        int count = 0;
        InputStream is = context.getContentResolver().openInputStream(sourceUri);
        if (is == null)
            throw new IOException("Could not open input stream for source Uri");
        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // use only the plain file name to prevent path traversal ("zip slip") via entry names
                String name = new File(entry.getName()).getName();
                if (!Util.isSyncableName(name)) {
                    zis.closeEntry();
                    continue;
                }
                try (OutputStream os = storage.openWrite(name)) {
                    Util.copyStream(zis, os);
                }
                zis.closeEntry();
                count++;
            }
        }
        return count;
    }

    // pushes all save files (and app settings) as individual, unzipped files into a SAF tree Uri
    // (e.g. a folder inside a cloud-sync app like Google Drive), overwriting any same-named file
    // already there. This is the "no zip" counterpart to exportAllSavefilesToUri, meant to let a
    // remote tool/agent read and edit the plain JSON files directly.
    public int pushSavefilesToFolder(Uri treeUri) throws IOException {
        DocumentFile treeDir = DocumentFile.fromTreeUri(context, treeUri);
        if (treeDir == null || !treeDir.canWrite())
            throw new IOException("Cannot write to the selected sync folder");
        List<String> names = storage.list();
        int count = 0;
        for (String name : names) {
            if (!Util.isSyncableName(name))
                continue;
            // remove any existing file with the same name so createFile doesn't produce a duplicate
            DocumentFile existing = treeDir.findFile(name);
            if (existing != null)
                existing.delete();
            DocumentFile target = treeDir.createFile("application/octet-stream", name);
            if (target == null)
                continue;
            OutputStream os = context.getContentResolver().openOutputStream(target.getUri());
            if (os == null)
                continue;
            try (OutputStream out = os; InputStream is = storage.openRead(name)) {
                Util.copyStream(is, out);
            }
            count++;
        }
        return count;
    }

    // pulls all save files (and app settings) from a SAF tree Uri back into internal storage,
    // overwriting local files of the same name. Counterpart to pushSavefilesToFolder.
    public int pullSavefilesFromFolder(Uri treeUri) throws IOException {
        DocumentFile treeDir = DocumentFile.fromTreeUri(context, treeUri);
        if (treeDir == null || !treeDir.canRead())
            throw new IOException("Cannot read from the selected sync folder");
        int count = 0;
        for (DocumentFile child : treeDir.listFiles()) {
            if (child.isDirectory())
                continue;
            String name = child.getName();
            if (!Util.isSyncableName(name))
                continue;
            InputStream is = context.getContentResolver().openInputStream(child.getUri());
            if (is == null)
                continue;
            try (InputStream in = is; OutputStream out = storage.openWrite(name)) {
                Util.copyStream(in, out);
            }
            count++;
        }
        return count;
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
        String name = getModel().currentFileName + Const.ACCOUNTS_FILE_TYPE;
        resetAccounts();
        return storage.delete(name);
    }

    public boolean deleteSavefile(String name) {
        return storage.delete(Util.reduceFileTypeEnding(name) + Const.ACCOUNTS_FILE_TYPE);
    }

    // PARAMS: String period: a String representing the period in which to look for entities with existing save files
    // Format: "YYYY-MM"
    public List<String> getAvailableEntitiesForPeriod(String period) throws IllegalArgumentException {
        // Verify the format and plausibility of the period
        if (!Util.validatePeriod(period)) {
            throw new IllegalArgumentException(
                    "The period should have the format 'YYYY-MM', with year 2000-2050 and month 01-12. Given: " + period);
        }

        // Get all files in the directory
        List<String> names = storage.list();
        List<String> entityNames = new ArrayList<>();
        if (names.isEmpty())
            return entityNames;

        // Filter the file names and extract the entity names
        Pattern pattern = Pattern.compile("^" + period + "-([^.]+)\\.jso$");
        for (String name : names) {
            Matcher matcher = pattern.matcher(name);
            if (matcher.matches()) {
                entityNames.add(matcher.group(1));
            }
        }
        return entityNames;
    }

    public List<String> getCurrentAvailableEntities() {
        String period = Util.getPresentPeriod();

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
        List<String> names = storage.list();
        Set<String> entityNames = new HashSet<>();
        if (names.isEmpty())
            return new ArrayList<>(entityNames);

        // Filter the file names and extract the entity names
        Pattern pattern = Pattern.compile("^\\d{4}-\\d{2}-([^.]+)\\.jso$");
        for (String name : names) {
            Matcher matcher = pattern.matcher(name);
            if (matcher.matches()) {
                entityNames.add(matcher.group(1));
            }
        }
        return new ArrayList<>(entityNames);
    }

    public List<String> getAllPeriodsForEntity(String entityName) {
        // Get all files in the directory
        List<String> names = storage.list();
        List<String> periods = new ArrayList<>();
        if (names.isEmpty())
            return periods;

        // Filter the file names and extract the periods for the specified entity
        Pattern pattern = Pattern.compile("^(\\d{4}-\\d{2})-" + Pattern.quote(entityName) + "\\.jso$");
        for (String name : names) {
            Matcher matcher = pattern.matcher(name);
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
    public boolean createTx(String desc, float amount, RedirectionPrompt prompt) throws JSONException, IOException {
        AccountBE from_acc = model.currentSender;
        AccountBE to_acc = model.currentReceiver;
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
            if (otherEntity != null && !otherEntity.isEmpty())
                result = startTxRedirection(otherEntity, desc, amount, prompt);
        }
        if (result) {
            saveOrRevert("save_file", "creating a Tx", () -> {
                from_acc.removeTx(entry_from);
                to_acc.removeTx(entry_to);
            });
            return true;
        }
        return false;
    }

    // Start the transaction redirection
    public boolean startTxRedirection(String targetEntity, String desc, float amount, RedirectionPrompt prompt) throws JSONException, IOException {
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
        prompt.getTransactionRedirectionInput(fileNameOther, json, desc, amount, allAccounts);
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
        saveOrRevert("delete_tx", "deleting a transaction (" + tx + ")", () -> parent.addTx(position, tx));
        return true;
    }

    // add funds to one account
    public boolean addFunds(float amount, String desc) throws JSONException, IOException {
        TxBE newFunds = new TxBE(amount, desc, Calendar.getInstance().getTime());
        if (model.currentReceiver == null) {
            return false;
        }
        final AccountBE receiver = model.currentReceiver;
        receiver.addTx(newFunds);
        model.currentIncome.add(newFunds);
        saveOrRevert("save_file", "adding funds", () -> {
            receiver.dropLastTx();
            model.currentIncome.remove(newFunds);
        });
        return true;
    }

    public boolean addRecurringTx(String desc, float amount) throws JSONException, IOException{
        Calendar calendar = Calendar.getInstance();
        AccountBE sender = model.currentSender;
        AccountBE receiver = model.currentReceiver;
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
        saveOrRevert("save_file", "adding recurring transaction", () -> model.recurringTx.remove(newOrder));
        return true;
    }

    public boolean deleteRecurringTx(RecurringTxBE recurringTx) throws JSONException, IOException {
        int position = model.recurringTx.indexOf(recurringTx);
        if (position == -1)
            return false;
        // mutate before the save, matching every other site (the removal cannot throw a checked
        // exception, so hoisting it out of the try is behaviour-identical)
        model.recurringTx.remove(recurringTx);
        saveOrRevert("save_file", "deleting recurring transaction",
                () -> model.recurringTx.add(position, recurringTx));
        return true;
    }

    private void triggerRecurringTx() throws JSONException, IOException {
        class AccountTxCombo {
            final AccountBE account;
            final TxBE tx;

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
        saveOrRevert("save_file", "triggering recurring transactions", () -> {
            for (AccountTxCombo entry : addedTx) {
                entry.account.getTxList().remove(entry.tx);
            }
        });
    }
    // endregion

    // region Account Handling
    public void sortAllTransactions() {
        for (AccountBE account : model.asset_accounts) {
            account.sortTxByDate();
        }
        for (BudgetAccountBE budgetAccount : model.budget_accounts) {
            budgetAccount.sortTxByDate();
            for (BudgetAccountBE subBudget : budgetAccount.getAllSubBudgets()) {
                subBudget.sortTxByDate();
            }
        }
    }

    public AccountBE createAssetAccount(String name) throws JSONException, IOException {
        // check if name is already in use
        AccountBE similarName = model.getAccountByName(name);
        if (similarName != null) {
            return null;
        }
        AccountBE newAccount = new AccountBE(name);
        model.asset_accounts.add(newAccount);
        saveOrRevert("save_file", "creating asset account", () -> model.asset_accounts.remove(newAccount));
        return newAccount;
    }

    public BudgetAccountBE createRootBudget(String name,float currentBudget, float yearlyBudget) throws JSONException, IOException {
        // check if name is already in use
        AccountBE similarName = model.getAccountByName(name);
        if (similarName != null) {
            return null;
        }
        BudgetAccountBE newAccount = new BudgetAccountBE(name, currentBudget, yearlyBudget);
        model.budget_accounts.add(newAccount);
        saveOrRevert("save_file", "creating root budget", () -> model.budget_accounts.remove(newAccount));
        return newAccount;
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
        saveOrRevert("save_file", "creating sub budget", () -> parent.getDirectSubBudgets().remove(newAccount));
        return newAccount;
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
        saveOrRevert("save_file", "creating project budget", () -> {
            parent.getDirectSubBudgets().remove(newAccount);
            parent.adjustIndivYearlyBudget(total_budget);
        });
        return newAccount;
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
        // both are reassigned in the search loop above, so they are not effectively final and
        // cannot be captured by the revert lambda directly
        final int removedAt = position;
        final BudgetAccountBE removedFrom = parentBudget;
        saveOrRevert("save_file", "deleting account", () -> {
            if (account instanceof BudgetAccountBE) {
                if (removedFrom == null)
                    model.budget_accounts.add(removedAt, (BudgetAccountBE) account);
                else
                    removedFrom.getDirectSubBudgets().add(removedAt, (BudgetAccountBE) account);
            } else
                model.asset_accounts.add(removedAt, account);
        });
        return true;
    }
    // endregion

    // sets up accounts for new month by loading last month, closing all accounts, and transferring the balances to new month´s accounts
    // returns true if transfer finalized successfully
    // returns false if couldn't read last month (e.g. there is no file of last month´s accounts)

    @SuppressLint("SimpleDateFormat")
    public void initiateNewPeriod() throws JSONException, IOException {
        Calendar cal = Calendar.getInstance();
        List<String> availableFiles = storage.list();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM");
        Date latestDate = null;
        String latestFile = null;
        String entityPattern = "\\d{4}-\\d{2}-" + model.currentEntity + "\\.jso";
        // Check if availableFiles is null or empty
        if (availableFiles.isEmpty()) {
            throw new FileNotFoundException("Error while trying to read save files in internal storage. No files were found.");
        }
        // Inspect available files
        for (String name : availableFiles) {
            if (name.matches(entityPattern)) {
                try {
                    Date fileDate = sdf.parse(name.substring(0, 7));
                    assert fileDate != null;
                    if (latestDate == null || fileDate.after(latestDate)) {
                        latestDate = fileDate;
                        latestFile = name;
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
            if (!latestFile.equals(model.currentFileName))
                readAccountsFromInternal(latestFile);
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
            } else if (model.currentEntity == null || model.currentEntity.isEmpty()) {
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
        // assigned inside the search loop above, so not effectively final and not capturable by
        // the revert lambda without a copy
        final TxBE foundEntry = sourceEntry;

        // setup list of all accounts to search for other part of transaction
        // for this take asset accounts which are already of type AccountBE
        // (copy the list! it must not be mutated, since it's the live model list)
        List<AccountBE> toSearch = new ArrayList<>(model.asset_accounts);
        // then transform budget accounts and first order sub budgets
        List<AccountBE> transformed_budget_accounts = new ArrayList<>();
        for (BudgetAccountBE budget_account : model.budget_accounts) {
            transformed_budget_accounts.add(budget_account);
            List<BudgetAccountBE> sub_budgets = budget_account.getDirectSubBudgets();
            if (!sub_budgets.isEmpty())
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
                    foundEntry.setAmount(newAmount);
                    saveOrRevert("save_file", "updating entry amount", () -> {
                        entry.setAmount(oldAmount * (-1.0f));
                        foundEntry.setAmount(oldAmount);
                    });
                    return true;
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
        // assigned inside the search loop above, so not effectively final and not capturable by
        // the revert lambda without a copy
        final TxBE foundEntry = sourceEntry;

        // setup list of all accounts to search for other part of transaction
        // for this take asset accounts which are already of type AccountBE
        // (copy the list! it must not be mutated, since it's the live model list)
        List<AccountBE> toSearch = new ArrayList<>(model.asset_accounts);
        // then transform budget accounts and first order sub budgets
        List<AccountBE> transformed_budget_accounts = new ArrayList<>();
        for (BudgetAccountBE budget_account : model.budget_accounts) {
            transformed_budget_accounts.add(budget_account);
            List<BudgetAccountBE> sub_budgets = budget_account.getDirectSubBudgets();
            if (!sub_budgets.isEmpty())
                for (BudgetAccountBE sub_budget : sub_budgets)
                    transformed_budget_accounts.add(sub_budget);

        }
        // and add to toSearch list
        toSearch.addAll(transformed_budget_accounts);
        // remove source account, which by then will inevitably have been added
        toSearch.remove(source);

        for (AccountBE account : toSearch) {
            for (TxBE entry : account.getTxList()) {
                if (entry.getDate().equals(date) && entry.getDescription().equals(description)) {
                    entry.setDescription(newDescription);
                    foundEntry.setDescription(newDescription);
                    saveOrRevert("save_file", "updating entry description", () -> {
                        entry.setDescription(description);
                        foundEntry.setDescription(description);
                    });
                    return true;
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
        saveOrRevert("save_file", "updating yearly budget", () -> {
            account.setIndivYearlyBudget(oldYearlyBudget);
            if (adjustAvailable)
                account.setIndivAvailableBudget(oldAvailableBudget);
        });
    }

    public void transferAvailableBudget(float amount, BudgetAccountBE sender, BudgetAccountBE recipient) throws JSONException, IOException, InvalidParameterException {
        if (amount <= 0)
            throw new InvalidParameterException("transferAvailableBudget called with amount <= 0");
        float oldSenderCurrent = sender.indivAvailableBudget;
        float oldRecipientCurrent = recipient.indivAvailableBudget;

        sender.setIndivAvailableBudget(oldSenderCurrent - amount);
        recipient.setIndivAvailableBudget(oldRecipientCurrent + amount);

        saveOrRevert("save_file", "transferring available budget", () -> {
            sender.setIndivAvailableBudget(oldSenderCurrent);
            recipient.setIndivAvailableBudget(oldRecipientCurrent);
        });
    }

    public boolean transferSubBudget(BudgetAccountBE parent, BudgetAccountBE object, BudgetAccountBE target) throws JSONException, IOException {
        boolean result = parent.transferSubBudget(object, target);
        if (result)
            saveOrRevert("save_file", "transferring sub budget", () -> target.transferSubBudget(object, parent));
        return result;
    }
}
