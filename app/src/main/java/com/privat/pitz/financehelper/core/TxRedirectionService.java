package com.privat.pitz.financehelper.core;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Calendar;

import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;
import com.privat.pitz.financehelper.data.TxBE;

/**
 * Transaction-redirection cluster extracted from {@link Controller}: starting and completing a
 * redirection of a transaction to another financial entity's save file. Shares its {@code repo}
 * and {@code model} instances with the owning Controller - it does not copy them.
 */
public class TxRedirectionService {
    private final SaveFileRepository repo;
    private final Model model;

    TxRedirectionService(SaveFileRepository repo, Model model) {
        this.repo = repo;
        this.model = model;
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
            String data = repo.readFromInternal(fileNameOther);
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
            repo.writeToInternal(data.toString(), targetFileName);
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
}
