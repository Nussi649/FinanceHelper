package com.privat.pitz.financehelper.core;

import android.util.Log;

import org.json.JSONException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Entity/period cluster extracted from {@link Controller}: switching and loading financial
 * entities, resetting account lists, and rolling accounts over into a new period. Shares its
 * {@code repo}, {@code model}, {@code txService} and {@code accounts} instances with the owning
 * Controller - it does not copy them.
 */
public class EntityService {
    private final SaveFileRepository repo;
    private final Model model;
    private final TxService txService;
    private final AccountService accounts;
    private final SavefileStorage storage;

    EntityService(SaveFileRepository repo, Model model, TxService txService, AccountService accounts, SavefileStorage storage) {
        this.repo = repo;
        this.model = model;
        this.txService = txService;
        this.accounts = accounts;
        this.storage = storage;
    }

    // resets all account lists
    public void resetAccountLists() {
        model.asset_accounts = new ArrayList<>();
        model.budget_accounts = new ArrayList<>();
        model.recurringTx = new ArrayList<>();
        model.currentIncome = new ArrayList<>();
        repo.setCurrentFileName(Const.getCurrentMonthFileName(model.currentEntity));
    }

    private void resetCurrentIncome() {
        model.currentIncome = new ArrayList<>();
    }

    public void loadEntityCurrentPeriod(String entityName) throws JSONException, IllegalArgumentException, IOException {
        try {
            repo.readAccountsFromInternal(Const.getCurrentMonthFileName(entityName));
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("No save file found for that entity and current period");
        }
    }

    public void loadEntityCurrentPeriod() throws JSONException, IllegalArgumentException, IOException {
        loadEntityCurrentPeriod(model.currentEntity);
    }

    public void loadEntity(String entityName) throws JSONException, IllegalArgumentException, IOException {
        // Validate parameter - check if the entity exists in the available entities
        List<String> allEntities = repo.getAllAvailableEntities();
        if (!allEntities.contains(entityName))
            throw new IllegalArgumentException("No save file found for that entity.");

        // Find all available periods for the given entity
        List<String> availablePeriods = repo.getAllPeriodsForEntity(entityName); // Will return list of strings with format "YYYY-MM"

        // Select the latest period. We can order the periods in descending order and get the first one,
        // which will be the latest. Since the first validation passed, we know the list is non-empty.
        availablePeriods.sort(Comparator.reverseOrder());
        String latestPeriod = availablePeriods.get(0);

        // Load the save file by constructing the file name out of the period and entityName
        String filename = latestPeriod + "-" + entityName + ".jso";
        try {
            repo.readAccountsFromInternal(filename);
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

    public void switchToEntity(String targetEntity) throws JSONException, IOException, IllegalArgumentException {
        // first validate argument
        List<String> availableEntities = repo.getAllAvailableEntities();
        if (!availableEntities.contains(targetEntity)) {
            Log.println(Log.ERROR, "switch_entity",
                    "Error while trying to switch entity. Entity not valid. Aborting process!");
            throw new IllegalArgumentException("Entity not available");
        }
        // save current state
        String oldEntity = model.currentEntity;
        try {
            repo.saveAccountsToInternal();
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

    // sets up accounts for new month by loading last month, closing all accounts, and transferring the balances to new month´s accounts
    // returns true if transfer finalized successfully
    // returns false if couldn't read last month (e.g. there is no file of last month´s accounts)
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
                repo.readAccountsFromInternal(latestFile);
        } catch (JSONException | IOException e) {
            if (e instanceof JSONException)
                Log.println(Log.ERROR, "initiate_period",
                        String.format("Error while initiating new period. An exception occurred while parsing latest save file: %s", e));
            else
                Log.println(Log.ERROR, "initiate_period",
                        String.format("Error while initiating new period. An exception occurred while reading latest save file: %s", e));
            throw e;
        }

        accounts.renewAccounts();
        resetCurrentIncome();
        repo.setCurrentFileName(Const.getCurrentMonthFileName(model.currentEntity));

        try {
            txService.triggerRecurringTx();
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
                return Controller.CREATED_BLANK;
            } else if (model.currentEntity == null || model.currentEntity.isEmpty()) {
                // no current entity stored in model. loadEntity won't know what to load. crash safely
                Log.println(Log.ERROR, "load_accounts",
                        "Error loading default entity: no entity available in model. Loading no accounts.");
                return Controller.CREATED_BLANK;
            } else {
                // at this point model.currentEntity contains a non-empty String
                loadEntityCurrentPeriod();
                return Controller.LOADED_ACCOUNTS;
            }
        } catch (IllegalArgumentException e) {
            // no save file for current entity and month found. cause could be either
            try {
                // try initiating a new period with current entity
                initiateNewPeriod();
                return Controller.LOADED_NEW_MONTH;
            } catch (FileNotFoundException ex) {
                // no valid file could be found
                Log.println(Log.ERROR, "load_accounts",
                        String.format("While trying to initiate a new period, no valid source file " +
                                "for currentEntity could be located. This probably implies, that it is invalid. Exception: %s", ex));
                resetAccountLists();
                return Controller.CREATED_BLANK;
            } catch (JSONException | IOException ex) {
                // a valid file as source for initiating a new period could be located but not read. exception has already been logged on that level.
                Log.println(Log.INFO, "load_accounts", "While trying to initiate a new period, a valid save file could be located but not read. " +
                        "This probably implies, that the currentEntity is correct but its latest save file is corrupted.");
                resetAccountLists();
                return Controller.CREATED_BLANK;
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
            return Controller.CREATED_BLANK;
        }
    }
}
