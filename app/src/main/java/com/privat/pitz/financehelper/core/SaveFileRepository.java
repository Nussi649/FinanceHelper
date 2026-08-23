package com.privat.pitz.financehelper.core;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;
import com.privat.pitz.financehelper.data.RecurringTxBE;

/**
 * Persistence/discovery cluster extracted from {@link Controller}: JSON (de)serialisation of the
 * account model, reading/writing save files through {@link SavefileStorage}, and discovering
 * which entities/periods have save files on disk. Shares its {@code storage} and {@code model}
 * instances with the owning Controller - it does not copy them.
 */
public class SaveFileRepository {
    private final SavefileStorage storage;
    private final Model model;

    SaveFileRepository(SavefileStorage storage, Model model) {
        this.storage = storage;
        this.model = model;
    }

    //region Import/Export Account Saves
    public String exportAccounts() throws JSONException {
        JSONObject json = new JSONObject();

        json.put(Const.JSON_TAG_ASSET_ACCOUNTS,
                Util.serialiseAll(model.asset_accounts, Util::serialise_Account));
        json.put(Const.JSON_TAG_BUDGET_ACCOUNTS,
                Util.serialiseAll(model.budget_accounts, Util::serialise_BudgetAccount));
        json.put(Const.JSON_TAG_RECURRING_TX,
                Util.serialiseAll(model.recurringTx, Util::serialise_RecurringOrder));

        // save Income list
        JSONArray income_list_json = Util.serialise_Income(model.currentIncome);
        json.put(Const.JSON_TAG_CURRENT_INCOME, income_list_json);
        return json.toString(4);
    }

    public void importAccounts(String data) throws JSONException {
        ParseReport report = new ParseReport();
        importAccounts(data, report);
        model.addLoadReport(report);
    }

    /**
     * Parses a save file into the model, recording every field it had to default and every record
     * it had to discard into {@code report}.
     *
     * <p>The report is the point: this parser used to drop whole accounts on a single missing key
     * and the caller discarded the resulting null in silence, so a user could lose an account and
     * never be told. Callers surface the report - see AbstractActivity.reportLoadProblems.
     */
    public void importAccounts(String data, ParseReport report) throws JSONException {
        JSONObject json = new JSONObject(data);
        JSONArray accounts;

        // get asset accounts
        model.asset_accounts = new ArrayList<>();
        JSONArray asset_accounts_json = json.getJSONArray(Const.JSON_TAG_ASSET_ACCOUNTS);
        for (int i = 0; i < asset_accounts_json.length(); i++) {
            // get JSONObject of current account
            JSONObject current_account_json = asset_accounts_json.getJSONObject(i);
            // parse new account using parse function in Util
            AccountBE new_account = Util.parseJSON_Account(current_account_json, report);
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
            BudgetAccountBE new_budget_account = Util.parseJSON_BudgetAccount(current_budget_account_json, report);
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
            RecurringTxBE new_order = Util.parseJSON_RecurringOrder(accounts.getJSONObject(i), report);
            if (new_order != null)
                model.recurringTx.add(new_order);
        }

        // get income List
        model.currentIncome = Util.parseJSON_IncomeList(json.getJSONArray(Const.JSON_TAG_CURRENT_INCOME), report);

        // Every account object above is newly built, so any selection still pointing at an object
        // from before this call is now an orphan - a real-looking AccountBE that no list in the
        // model contains. addTx on one mutates something the serialiser never visits, so a
        // transaction gets booked on one side of the transfer only and the other side vanishes.
        //
        // The loops above already re-point a selection when it matches this entity's defaults.
        // This covers the case they cannot: no defaults recorded yet, or a default naming an
        // account that no longer exists. Re-point by name where possible, clear otherwise -
        // an empty selection is visible in the UI and refused by createTx, an orphan is neither.
        model.currentSender = model.reattachSelection(model.currentSender);
        model.currentReceiver = model.reattachSelection(model.currentReceiver);
        model.currentInspectedAccount = model.reattachSelection(model.currentInspectedAccount);
    }

    public void writeToInternal(String data, String filename) throws IOException {
        storage.write(filename, data);
    }

    public String readFromInternal(String filename) throws IOException {
        return storage.read(filename);
    }

    public void saveAccountsToInternal() throws JSONException, IOException {
        saveAccountsToInternal(model.currentFileName);
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
    public void saveOrRevert(String logTag, String what, Runnable revert)
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
    /**
     * Names of every valid save file in storage. Replaces callers reaching for
     * Context.getFilesDir() directly, which bypassed the storage seam entirely.
     */
    public List<String> getValidSavefileNames() {
        List<String> result = new ArrayList<>();
        for (String name : storage.list()) {
            if (Util.isValidSavefileName(name))
                result.add(name);
        }
        return result;
    }

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

    public boolean loadAppSettings() {
        try {
            String payload = readFromInternal(Const.APPLICATION_SETTINGS_FILENAME);
            ParseReport report = new ParseReport();
            model.settings = Util.parseJSON_Settings(new JSONObject(payload), report);
            model.addLoadReport(report);
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

    // moved alongside the persistence cluster: readAccountsFromInternal needs to set the current
    // file name as part of the load, and initiateNewPeriod/resetAccountLists (which stay on
    // Controller) need the same operation, so it lives here as the single source of truth.
    // Controller.setCurrentFileName(String) delegates to this method.
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
}
