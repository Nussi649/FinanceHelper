package com.privat.pitz.financehelper.core;

import android.content.Context;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.Date;
import java.util.List;

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
    AccountService accounts;
    EntityService entities;
    SavefileTransferService transfers;

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
        accounts    = new AccountService(repo, model);
        entities    = new EntityService(repo, model, txService, accounts, storage);
        transfers   = new SavefileTransferService(context, storage);
    }

    /**
     * Test constructor: no Context, so everything Android-free is usable from a plain JVM test.
     * The four zip/SAF transfer methods are the exception - they need a Context, so `transfers`
     * stays null here and calling them from a test would NPE. That is deliberate: SAF cannot be
     * meaningfully faked, so those paths are verified on-device rather than in unit tests.
     */
    Controller(SavefileStorage storage) {
        this.storage = storage;
        this.model = new Model();
        repo        = new SaveFileRepository(storage, model);
        redirection = new TxRedirectionService(repo, model);
        txService   = new TxService(repo, model, redirection);
        accounts    = new AccountService(repo, model);
        entities    = new EntityService(repo, model, txService, accounts, storage);
        transfers   = null;
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
        entities.resetAccountLists();
    }

    public boolean setCurrentFileName(String newFileName) {
        return repo.setCurrentFileName(newFileName);
    }

    public void resetAccounts() {
        accounts.resetAccounts();
    }

    public void renewAccounts() {
        accounts.renewAccounts();
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
        return transfers.exportAllSavefilesToUri(targetUri);
    }

    // extracts all save files (and app settings, if present) from a previously exported zip
    // file at the given SAF Uri back into internal storage
    public int importSavefilesFromZipUri(Uri sourceUri) throws IOException {
        return transfers.importSavefilesFromZipUri(sourceUri);
    }

    // pushes all save files (and app settings) as individual, unzipped files into a SAF tree Uri
    // (e.g. a folder inside a cloud-sync app like Google Drive), overwriting any same-named file
    // already there. This is the "no zip" counterpart to exportAllSavefilesToUri, meant to let a
    // remote tool/agent read and edit the plain JSON files directly.
    public int pushSavefilesToFolder(Uri treeUri) throws IOException {
        return transfers.pushSavefilesToFolder(treeUri);
    }

    // pulls all save files (and app settings) from a SAF tree Uri back into internal storage,
    // overwriting local files of the same name. Counterpart to pushSavefilesToFolder.
    public int pullSavefilesFromFolder(Uri treeUri) throws IOException {
        return transfers.pullSavefilesFromFolder(treeUri);
    }

    public void loadEntityCurrentPeriod(String entityName) throws JSONException, IllegalArgumentException, IOException {
        entities.loadEntityCurrentPeriod(entityName);
    }

    public void loadEntityCurrentPeriod() throws JSONException, IllegalArgumentException, IOException {
        entities.loadEntityCurrentPeriod();
    }

    public void loadEntity(String entityName) throws JSONException, IllegalArgumentException, IOException {
        entities.loadEntity(entityName);
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
        entities.switchToEntity(targetEntity);
    }

    public void updateSelectedAccount(String selectionGroup, AccountBE newTarget) {
        accounts.updateSelectedAccount(selectionGroup, newTarget);
    }

    public AccountBE getSelectedAccount(String selectionGroup) {
        return accounts.getSelectedAccount(selectionGroup);
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
        accounts.sortAllTransactions();
    }

    public AccountBE createAssetAccount(String name) throws JSONException, IOException {
        return accounts.createAssetAccount(name);
    }

    public BudgetAccountBE createRootBudget(String name,float currentBudget, float yearlyBudget) throws JSONException, IOException {
        return accounts.createRootBudget(name, currentBudget, yearlyBudget);
    }

    public BudgetAccountBE createSubBudget(BudgetAccountBE parent,
                                           String name,
                                           float current_budget,
                                           float yearly_budget) throws JSONException, IOException {
        return accounts.createSubBudget(parent, name, current_budget, yearly_budget);
    }

    public BudgetAccountBE createSubBudget(BudgetAccountBE parent,
                                           String name,
                                           float yearly_budget) throws JSONException, IOException {
        return accounts.createSubBudget(parent, name, yearly_budget);
    }

    public ProjectBudgetBE createProjectBudget(BudgetAccountBE parent, String name, float total_budget) throws JSONException, IOException {
        return accounts.createProjectBudget(parent, name, total_budget);
    }

    public boolean deleteAccount(String accountName) throws JSONException, IOException {
        return accounts.deleteAccount(accountName);
    }

    public boolean deleteAccount(AccountBE account) throws JSONException, IOException {
        return accounts.deleteAccount(account);
    }
    // endregion

    // sets up accounts for new month by loading last month, closing all accounts, and transferring the balances to new month´s accounts
    // returns true if transfer finalized successfully
    // returns false if couldn't read last month (e.g. there is no file of last month´s accounts)

    public void initiateNewPeriod() throws JSONException, IOException {
        entities.initiateNewPeriod();
    }

    // sets up the lists AssetAccounts and BudgetAccounts
    // @params
    // blank: if true makes new accounts, if false tries to load saved accounts and starts new month if there´s no save file for current month
    // returns CREATED_BLANK if blank accounts were created
    // returns LOADED_ACCOUNTS if saved accounts have been loaded
    // returns LOADED_NEW_MONTH if new month accounts have been created
    public int setupAccounts(boolean blank) {
        return entities.setupAccounts(blank);
    }

    // region update objects
    public boolean updateTx(Date date, String description, AccountBE source, float newAmount) throws JSONException, IOException {
        return txService.updateTx(date, description, source, newAmount);
    }

    public boolean updateTx(Date date, String description, AccountBE source, String newDescription) throws JSONException, IOException {
        return txService.updateTx(date, description, source, newDescription);
    }

    public void updateYearlyBudget(float newBudget, BudgetAccountBE account, boolean adjustAvailable) throws JSONException, IOException {
        accounts.updateYearlyBudget(newBudget, account, adjustAvailable);
    }

    public void transferAvailableBudget(float amount, BudgetAccountBE sender, BudgetAccountBE recipient) throws JSONException, IOException, InvalidParameterException {
        accounts.transferAvailableBudget(amount, sender, recipient);
    }

    public boolean transferSubBudget(BudgetAccountBE parent, BudgetAccountBE object, BudgetAccountBE target) throws JSONException, IOException {
        return accounts.transferSubBudget(parent, object, target);
    }
}
