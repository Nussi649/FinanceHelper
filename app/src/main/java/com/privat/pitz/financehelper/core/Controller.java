package com.privat.pitz.financehelper.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

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
import java.util.List;
import java.util.Locale;
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
    SaveFileRepository repo;
    TxRedirectionService redirection;
    TxService txService;

    public static int LOADED_ACCOUNTS = 10;
    public static int LOADED_NEW_MONTH = 11;
    public static int CREATED_BLANK = 12;

    private Controller(Context context, SavefileStorage storage) {
        this.context = context;
        this.storage = storage;
        initController();
        repo        = new SaveFileRepository(storage, model);
        redirection = new TxRedirectionService(repo, model);
        txService   = new TxService(repo, model, redirection);
    }

    Controller(SavefileStorage storage) {
        this.storage = storage;
        this.model = new Model();
        repo        = new SaveFileRepository(storage, model);
        redirection = new TxRedirectionService(repo, model);
        txService   = new TxService(repo, model, redirection);
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
        repo.setCurrentFileName(Const.getCurrentMonthFileName(model.currentEntity));
    }

    public boolean setCurrentFileName(String newFileName) {
        return repo.setCurrentFileName(newFileName);
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
        return repo.exportAccounts();
    }

    public void importAccounts(String data) throws JSONException {
        repo.importAccounts(data);
    }

    public void writeToInternal(String data, String filename) throws IOException {
        repo.writeToInternal(data, filename);
    }

    public String readFromInternal(String filename) throws IOException {
        return repo.readFromInternal(filename);
    }

    public void saveAccountsToInternal() throws JSONException, IOException {
        repo.saveAccountsToInternal();
    }

    public void saveAccountsToInternal(String filename) throws JSONException, IOException {
        repo.saveAccountsToInternal(filename);
    }

    public void readAccountsFromInternal(String filename) throws JSONException, IOException {
        repo.readAccountsFromInternal(filename);
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
        return repo.deleteSavefile(name);
    }

    // PARAMS: String period: a String representing the period in which to look for entities with existing save files
    // Format: "YYYY-MM"
    public List<String> getAvailableEntitiesForPeriod(String period) throws IllegalArgumentException {
        return repo.getAvailableEntitiesForPeriod(period);
    }

    public List<String> getCurrentAvailableEntities() {
        return repo.getCurrentAvailableEntities();
    }

    // searches for save files of financial entities regardless of period
    public List<String> getAllAvailableEntities() {
        return repo.getAllAvailableEntities();
    }

    public List<String> getAllPeriodsForEntity(String entityName) {
        return repo.getAllPeriodsForEntity(entityName);
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
        return repo.loadAppSettings();
    }

    public void saveAppSettings() throws JSONException, IOException {
        repo.saveAppSettings();
    }
    //endregion

    // region perform transactions
    // create a transaction between two accounts
    public boolean createTx(String desc, float amount, RedirectionPrompt prompt) throws JSONException, IOException {
        return txService.createTx(desc, amount, prompt);
    }

    // Start the transaction redirection
    public boolean startTxRedirection(String targetEntity, String desc, float amount, RedirectionPrompt prompt) throws JSONException, IOException {
        return redirection.startTxRedirection(targetEntity, desc, amount, prompt);
    }

    // Complete the transaction redirection
    public boolean completeTxRedirection(String targetFileName, String senderName, String desc, float amount, String accountName, JSONObject data) throws JSONException, IOException {
        return redirection.completeTxRedirection(targetFileName, senderName, desc, amount, accountName, data);
    }

    public boolean deleteTx(AccountBE parent, TxBE tx) throws JSONException, IOException {
        return txService.deleteTx(parent, tx);
    }

    // add funds to one account
    public boolean addFunds(float amount, String desc) throws JSONException, IOException {
        return txService.addFunds(amount, desc);
    }

    public boolean addRecurringTx(String desc, float amount) throws JSONException, IOException{
        return txService.addRecurringTx(desc, amount);
    }

    public boolean deleteRecurringTx(RecurringTxBE recurringTx) throws JSONException, IOException {
        return txService.deleteRecurringTx(recurringTx);
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
        repo.saveOrRevert("save_file", "creating asset account", () -> model.asset_accounts.remove(newAccount));
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
        repo.saveOrRevert("save_file", "creating root budget", () -> model.budget_accounts.remove(newAccount));
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
        repo.saveOrRevert("save_file", "creating sub budget", () -> parent.getDirectSubBudgets().remove(newAccount));
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
        repo.saveOrRevert("save_file", "creating project budget", () -> {
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
        repo.saveOrRevert("save_file", "deleting account", () -> {
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
        return txService.updateTx(date, description, source, newAmount);
    }

    public boolean updateTx(Date date, String description, AccountBE source, String newDescription) throws JSONException, IOException {
        return txService.updateTx(date, description, source, newDescription);
    }

    public void updateYearlyBudget(float newBudget, BudgetAccountBE account, boolean adjustAvailable) throws JSONException, IOException {
        float oldYearlyBudget = account.indivYearlyBudget;
        float oldAvailableBudget = account.indivAvailableBudget;
        account.setIndivYearlyBudget(newBudget);
        if (adjustAvailable)
            account.setIndivAvailableBudget(oldAvailableBudget + (newBudget - oldYearlyBudget) * (account.getRenewalPeriod() / 12.0f));
        repo.saveOrRevert("save_file", "updating yearly budget", () -> {
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

        repo.saveOrRevert("save_file", "transferring available budget", () -> {
            sender.setIndivAvailableBudget(oldSenderCurrent);
            recipient.setIndivAvailableBudget(oldRecipientCurrent);
        });
    }

    public boolean transferSubBudget(BudgetAccountBE parent, BudgetAccountBE object, BudgetAccountBE target) throws JSONException, IOException {
        boolean result = parent.transferSubBudget(object, target);
        if (result)
            repo.saveOrRevert("save_file", "transferring sub budget", () -> target.transferSubBudget(object, parent));
        return result;
    }
}
