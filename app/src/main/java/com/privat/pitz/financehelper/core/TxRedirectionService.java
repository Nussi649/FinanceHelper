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

    /** Outcome of scanning one top-level account array for the redirection target. */
    private enum Outcome { NOT_FOUND, INJECTED, PARSE_FAILED }

    /** Parses one account JSON object and serialises it back after mutation. */
    private interface AccountCodec {
        AccountBE parse(JSONObject json);
        JSONObject serialise(AccountBE account) throws JSONException;
    }

    private static final AccountCodec ASSET_CODEC = new AccountCodec() {
        @Override
        public AccountBE parse(JSONObject json) {
            return Util.parseJSON_Account(json);
        }

        @Override
        public JSONObject serialise(AccountBE account) {
            return Util.serialise_Account(account);
        }
    };

    private static final AccountCodec BUDGET_CODEC = new AccountCodec() {
        @Override
        public AccountBE parse(JSONObject json) {
            return Util.parseJSON_BudgetAccount(json);
        }

        @Override
        public JSONObject serialise(AccountBE account) {
            return Util.serialise_BudgetAccount((BudgetAccountBE) account);
        }
    };

    /**
     * Scans the top-level array under {@code jsonTag} for an account named {@code accountName},
     * appends {@code newEntry} to it and writes the updated array back into {@code data}.
     */
    private Outcome injectTx(JSONObject data, String jsonTag, String accountName, TxBE newEntry,
                              AccountCodec codec, String accountKind) throws JSONException {
        JSONArray accounts;
        try {
            accounts = data.getJSONArray(jsonTag);
        } catch (JSONException ignored) {
            return Outcome.NOT_FOUND;
        }
        // iterate through all accounts as json objects
        for (int i = 0; i < accounts.length(); i++) {
            // set variables for access out of try/catch
            JSONObject currentAccount;
            String currentAccountName;
            // get current account as json object and corresponding account name
            try {
                currentAccount = accounts.getJSONObject(i);
                currentAccountName = currentAccount.getString(Const.JSON_TAG_NAME);
            } catch (JSONException e) {
                continue;
            }
            // if current account name equals target account name
            if (currentAccountName.equals(accountName)) {
                // parse current account
                AccountBE curAccount = codec.parse(currentAccount);
                // check if parsing worked
                if (curAccount != null) {
                    // add pre-calculated entry to parsed account object
                    curAccount.addTx(newEntry);
                    // serialise adjusted account object and replace its old version in account list
                    // replace accounts in save file json object
                    try {
                        accounts.put(i, codec.serialise(curAccount));
                        data.put(jsonTag, accounts);
                        return Outcome.INJECTED;
                    } catch (JSONException e) {
                        Log.println(Log.ERROR, "pass_on_transaction",
                                String.format("Error serializing target %s account after adding new entry: %s", accountKind, e));
                        throw e;
                    }
                }
                // error happened parsing the current account
                else {
                    Log.println(Log.ERROR, "pass_on_transaction", String.format(
                            "Error passing on transaction. Could not parse %s account object! targetAccountName: %s",
                            accountKind,
                            accountName));
                    return Outcome.PARSE_FAILED;
                }
            }
        }
        return Outcome.NOT_FOUND;
    }

    // Complete the transaction redirection
    public boolean completeTxRedirection(String targetFileName, String senderName, String desc, float amount, String accountName, JSONObject data) throws JSONException, IOException {
        Calendar calendar = Calendar.getInstance();
        TxBE new_entry = new TxBE(amount, desc, calendar.getTime());
        JSONArray incomeList;
        try {
            incomeList = data.getJSONArray(Const.JSON_TAG_CURRENT_INCOME);
        } catch (JSONException e) {
            return false;
        }
        Outcome outcome = injectTx(data, Const.JSON_TAG_ASSET_ACCOUNTS, accountName, new_entry,
                ASSET_CODEC, "asset");
        if (outcome == Outcome.PARSE_FAILED) return false;
        if (outcome == Outcome.NOT_FOUND) {
            outcome = injectTx(data, Const.JSON_TAG_BUDGET_ACCOUNTS, accountName, new_entry,
                    BUDGET_CODEC, "budget");
            if (outcome == Outcome.PARSE_FAILED) return false;
        }
        if (outcome != Outcome.INJECTED) return false;
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
