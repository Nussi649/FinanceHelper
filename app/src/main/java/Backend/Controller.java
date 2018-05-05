package Backend;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import Logic.AccountBE;
import Logic.EntryBE;

public class Controller {
    public static Controller instance;
    Model model;
    Context context;

    private Controller(Context context) { this.context = context; }

    private void initController() {
        model = new Model();
    }

    public static void createInstance(Context context) {
        instance = new Controller(context);
        instance.initController();
    }

    public Model getModel() { return model; }

    public void saveAccountsToInternal() throws JSONException {
        saveAccountsToInternal(Const.ACCOUNTS_FILE_NAME);
    }

    public void saveAccountsToInternal(String filename) throws JSONException {
        JSONObject json = new JSONObject();
        JSONArray array = new JSONArray();
        //region save payAccounts
        for (AccountBE a : model.payAccounts) {
            JSONObject acc = new JSONObject();
            acc.put(Const.JSON_TAG_NAME, a.getName());
            acc.put(Const.JSON_TAG_ISACTIVE, a.getIsActive());
            JSONArray entries = new JSONArray();
            for (EntryBE e : a.getEntries()) {
                JSONObject entr = new JSONObject();
                entr.put(Const.JSON_TAG_ID, e.getId());
                entr.put(Const.JSON_TAG_DESCRIPTION, e.getDescription());
                entr.put(Const.JSON_TAG_AMOUNT, e.getAmount());
                entries.put(entr);
            }
            acc.put(Const.JSON_TAG_ENTRIES, entries);
            array.put(acc);
        }
        json.put(Const.JSON_TAG_PACCOUNTS, array);
        //endregion

        //region save investAccounts
        array = new JSONArray();
        for (AccountBE a : model.investAccounts) {
            JSONObject acc = new JSONObject();
            acc.put(Const.JSON_TAG_NAME, a.getName());
            acc.put(Const.JSON_TAG_ISACTIVE, a.getIsActive());
            JSONArray entries = new JSONArray();
            for (EntryBE e : a.getEntries()) {
                JSONObject entr = new JSONObject();
                entr.put(Const.JSON_TAG_ID, e.getId());
                entr.put(Const.JSON_TAG_DESCRIPTION, e.getDescription());
                entr.put(Const.JSON_TAG_AMOUNT, e.getAmount());
                entries.put(entr);
            }
            acc.put(Const.JSON_TAG_ENTRIES, entries);
            array.put(acc);
        }
        json.put(Const.JSON_TAG_IACCOUNTS, array);
        //endregion

        //region write data to internal
        String payload = json.toString();
        File file = new File(context.getFilesDir(), filename + Const.ACCOUNTS_FILE_TYPE);
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter writer = new FileWriter(file);
            writer.append(payload);
            writer.flush();
            writer.close();

        } catch (Exception e){
            e.printStackTrace();
        }
        //endregion
    }

    public void readAccountsFromInternal() throws JSONException {
        readAccountsFromInternal(Const.ACCOUNTS_FILE_NAME);
    }

    public void readAccountsFromInternal(String filename) throws JSONException {
        String payload = "";
        JSONObject json = new JSONObject();
        JSONArray accounts;

        //region read data from internal
        File file = new File(context.getFilesDir(), filename + Const.ACCOUNTS_FILE_TYPE);
        try {
            FileReader fReader = new FileReader(file);
            BufferedReader reader = new BufferedReader(fReader);
            payload = reader.readLine();
            reader.close();
            fReader.close();
            json = new JSONObject(payload);
        } catch (Exception e){
            e.printStackTrace();
        }
        //endregion

        //region get pay accounts
        accounts = json.getJSONArray(Const.JSON_TAG_PACCOUNTS);
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject cur = accounts.getJSONObject(i);
            AccountBE curAcc = new AccountBE(cur.getString(Const.JSON_TAG_NAME));
            curAcc.setActive(cur.getBoolean(Const.JSON_TAG_ISACTIVE));
            JSONArray entries = cur.getJSONArray(Const.JSON_TAG_ENTRIES);
            for (int j = 0; j < entries.length(); j++) {
                JSONObject curEntry = entries.getJSONObject(j);
                EntryBE entry = new EntryBE(curEntry.getInt(Const.JSON_TAG_ID), (float)curEntry.getDouble(Const.JSON_TAG_AMOUNT), curEntry.getString(Const.JSON_TAG_DESCRIPTION));
                curAcc.addEntry(entry);
            }
            model.payAccounts.add(curAcc);
        }
        //endregion

        //region get invest accounts
        accounts = json.getJSONArray(Const.JSON_TAG_IACCOUNTS);
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject cur = accounts.getJSONObject(i);
            AccountBE curAcc = new AccountBE(cur.getString(Const.JSON_TAG_NAME));
            curAcc.setActive(cur.getBoolean(Const.JSON_TAG_ISACTIVE));
            JSONArray entries = cur.getJSONArray(Const.JSON_TAG_ENTRIES);
            for (int j = 0; j < entries.length(); j++) {
                JSONObject curEntry = entries.getJSONObject(j);
                EntryBE entry = new EntryBE(curEntry.getInt(Const.JSON_TAG_ID), (float)curEntry.getDouble(Const.JSON_TAG_AMOUNT), curEntry.getString(Const.JSON_TAG_DESCRIPTION));
                curAcc.addEntry(entry);
            }
            model.investAccounts.add(curAcc);
        }
        //endregion
    }

    public boolean deleteSavedAccounts() {
        File file = new File(context.getFilesDir(), Const.ACCOUNTS_FILE_NAME);
        return file.delete();
    }

    public void addEntry(String desc, float amount, AccountBE payAccount, AccountBE investAccount) {
        EntryBE entryPay = new EntryBE(model.entrySequenceValue++, amount*(-1.0f), desc);
        EntryBE entryInvest = new EntryBE(model.entrySequenceValue++, amount, desc);
        payAccount.addEntry(entryPay);
        investAccount.addEntry(entryInvest);
    }

    public AccountBE getPayAccountByName(String name) {
        for (AccountBE a : model.payAccounts) {
            if (a.getName().equals(name))
                return a;
        }
        return null;
    }

    public AccountBE getInvestAccountByName(String name) {
        for (AccountBE a : model.investAccounts) {
            if (a.getName().equals(name))
                return a;
        }
        return null;
    }
}
