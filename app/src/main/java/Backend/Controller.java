package Backend;

import android.accounts.Account;
import android.content.Context;

import com.privat.pitz.financehelper.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Logic.AccountBE;
import Logic.EntryBE;
import Logic.PrivateKey;
import Logic.RecurringOrderBE;

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

    public void initAccountLists() {
        if (model.payAccounts.size() == 0) {
            model.payAccounts.add(new AccountBE(Const.ACCOUNT_BARGELD));
            model.payAccounts.add(new AccountBE(Const.ACCOUNT_UNI));
            model.payAccounts.add(new AccountBE(Const.ACCOUNT_BANK));
            model.payAccounts.add(new AccountBE(Const.ACCOUNT_CREDIT_CARD));
            model.payAccounts.add(new AccountBE(Const.ACCOUNT_SAVINGS));
        }

        if (model.investAccounts.size() == 0) {
            model.investAccounts.add(new AccountBE(Const.ACCOUNT_INVESTMENTS));
            model.investAccounts.add(new AccountBE(Const.ACCOUNT_GROCERIES));
            model.investAccounts.add(new AccountBE(Const.ACCOUNT_COSMETICS));
            model.investAccounts.add(new AccountBE(Const.ACCOUNT_GO_OUT));
            model.investAccounts.add(new AccountBE(Const.ACCOUNT_DRUGS));
            model.investAccounts.add(new AccountBE(Const.ACCOUNT_NECESSARY));
            model.investAccounts.add(new AccountBE(Const.ACCOUNT_BUS));
        }

        getModel().currentFileName = Const.getCurrentMonthName();
    }

    public void resetAccounts() {
        for (AccountBE a : getModel().payAccounts) {
            a.reset();
        }
        for (AccountBE a : getModel().investAccounts) {
            a.reset();
        }
    }

    private void resetIncomeList() {
        model.incomeList = new ArrayList<>();
    }

    //region Import/Export Account Saves
    public String exportAccounts() throws JSONException {
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
                entr.put(Const.JSON_TAG_DESCRIPTION, e.getDescription());
                entr.put(Const.JSON_TAG_AMOUNT, Util.formatFloat(e.getAmount()));
                entr.put(Const.JSON_TAG_TIME, Util.formatDateSave(e.getDate()));
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
                entr.put(Const.JSON_TAG_DESCRIPTION, e.getDescription());
                entr.put(Const.JSON_TAG_AMOUNT, Util.formatFloat(e.getAmount()));
                entr.put(Const.JSON_TAG_TIME, Util.formatDateSave(e.getDate()));
                entries.put(entr);
            }
            acc.put(Const.JSON_TAG_ENTRIES, entries);
            array.put(acc);
        }
        json.put(Const.JSON_TAG_IACCOUNTS, array);
        //endregion

        // region save recurring Orders
        array = new JSONArray();
        for (RecurringOrderBE r : model.recurringOrders) {
            JSONObject order = new JSONObject();
            order.put(Const.JSON_TAG_AMOUNT, Util.formatFloat(r.getAmount()));
            order.put(Const.JSON_TAG_DESCRIPTION, r.getDescription());
            order.put(Const.JSON_TAG_TIME, Util.formatDateSave(r.getDate()));
            order.put(Const.JSON_TAG_PACCOUNT, r.getPayAccount());
            order.put(Const.JSON_TAG_IACCOUNT, r.getInvestAccount());
            array.put(order);
        }
        json.put(Const.JSON_TAG_RECURRING_ORDERS, array);
        //endregion

        //region save income list
        array = new JSONArray();
        for (EntryBE e : model.incomeList) {
            JSONObject entry = new JSONObject();
            entry.put(Const.JSON_TAG_AMOUNT, Util.formatFloat(e.getAmount()));
            entry.put(Const.JSON_TAG_DESCRIPTION, e.getDescription());
            entry.put(Const.JSON_TAG_TIME, Util.formatDateSave(e.getDate()));
            array.put(entry);
        }
        json.put(Const.JSON_TAG_INCOME_LIST, array);
        //endregion
        return json.toString();
    }

    public void importAccounts(String data) throws JSONException, ParseException {
        JSONObject json = new JSONObject(data);
        JSONArray accounts;

        //region get pay accounts
        model.payAccounts = new ArrayList<>();
        accounts = json.getJSONArray(Const.JSON_TAG_PACCOUNTS);
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject cur = accounts.getJSONObject(i);
            AccountBE curAcc = new AccountBE(cur.getString(Const.JSON_TAG_NAME));
            curAcc.setActive(cur.getBoolean(Const.JSON_TAG_ISACTIVE));
            JSONArray entries = cur.getJSONArray(Const.JSON_TAG_ENTRIES);
            for (int j = 0; j < entries.length(); j++) {
                JSONObject curEntry = entries.getJSONObject(j);
                EntryBE entry = new EntryBE((float)curEntry.getDouble(Const.JSON_TAG_AMOUNT), curEntry.getString(Const.JSON_TAG_DESCRIPTION), Util.parseDateSave(curEntry.getString(Const.JSON_TAG_TIME)));
                curAcc.addEntry(entry);
            }
            model.payAccounts.add(curAcc);
        }
        //endregion

        //region get invest accounts
        model.investAccounts = new ArrayList<>();
        accounts = json.getJSONArray(Const.JSON_TAG_IACCOUNTS);
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject cur = accounts.getJSONObject(i);
            AccountBE curAcc = new AccountBE(cur.getString(Const.JSON_TAG_NAME));
            curAcc.setActive(cur.getBoolean(Const.JSON_TAG_ISACTIVE));
            JSONArray entries = cur.getJSONArray(Const.JSON_TAG_ENTRIES);
            for (int j = 0; j < entries.length(); j++) {
                JSONObject curEntry = entries.getJSONObject(j);
                EntryBE entry = new EntryBE((float)curEntry.getDouble(Const.JSON_TAG_AMOUNT), curEntry.getString(Const.JSON_TAG_DESCRIPTION), Util.parseDateSave(curEntry.getString(Const.JSON_TAG_TIME)));
                curAcc.addEntry(entry);
            }
            model.investAccounts.add(curAcc);
        }
        //endregion

        //region get recurring Orders
        model.recurringOrders = new ArrayList<>();
        accounts = json.getJSONArray(Const.JSON_TAG_RECURRING_ORDERS);
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject cur = accounts.getJSONObject(i);
            RecurringOrderBE order = new RecurringOrderBE((float)cur.getDouble(Const.JSON_TAG_AMOUNT), cur.getString(Const.JSON_TAG_DESCRIPTION), Util.parseDateSave(cur.getString(Const.JSON_TAG_TIME)), cur.getString(Const.JSON_TAG_PACCOUNT), cur.getString(Const.JSON_TAG_IACCOUNT));
            model.recurringOrders.add(order);
        }
        //endregion

        //region get income List
        model.incomeList = new ArrayList<>();
        accounts = json.getJSONArray(Const.JSON_TAG_INCOME_LIST);
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject cur = accounts.getJSONObject(i);
            EntryBE curEntry = new EntryBE((float)cur.getDouble(Const.JSON_TAG_AMOUNT), cur.getString(Const.JSON_TAG_DESCRIPTION), Util.parseDateSave(cur.getString(Const.JSON_TAG_TIME)));
            model.incomeList.add(curEntry);
        }
        //endregion
    }

    public void writeToInternal(String data, String filename) throws IOException {
        checkDirectories();
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
        String data = "";

        File file = new File(context.getFilesDir(), filename);
        FileReader fReader = new FileReader(file);
        BufferedReader reader = new BufferedReader(fReader);
        data = reader.readLine();
        reader.close();
        fReader.close();
        return data;
    }

    public void saveAccountsToInternal() throws JSONException, IOException {
        saveAccountsToInternal(getModel().currentFileName);
    }

    public void saveAccountsToInternal(String filename) throws JSONException, IOException {
        String payload = exportAccounts();
        writeToInternal(payload, filename + Const.ACCOUNTS_FILE_TYPE);
    }

    public void saveEncryptedAccountsToInternal(String filename, String password) throws  JSONException, IOException {
        String payload = encryptData(exportAccounts(), password);
        writeToInternal(payload, filename + Const.ACCOUNTS_FILE_TYPE);
    }

    public void readAccountsFromInternal() throws JSONException, IOException, ParseException{
        readAccountsFromInternal(Const.getCurrentMonthName());
    }

    public void readAccountsFromInternal(String filename) throws JSONException, IOException, ParseException {
        String payload = readFromInternal(filename + Const.ACCOUNTS_FILE_TYPE);
        importAccounts(payload);
        getModel().currentFileName = filename;
    }

    public void readEncryptedAccountsFromInternal(String filename, String password) throws JSONException, IOException, ParseException {
        String payload = readFromInternal(filename + Const.ACCOUNTS_FILE_TYPE);
        importAccounts(decryptData(payload, password));
    }

    public boolean deleteCurrentSave() {
        File file = new File(context.getFilesDir(), getModel().currentFileName + Const.ACCOUNTS_FILE_TYPE);
        resetAccounts();
        return file.delete();
    }

    public boolean deleteSavefile(String name) {
        File file = new File(context.getFilesDir(), name + Const.ACCOUNTS_FILE_TYPE);
        return file.delete();
    }

    public List<String> getAvailableSaveFiles() {
        File[] files = context.getFilesDir().listFiles();
        List<String> re = new ArrayList<>();
        if (files.length == 0)
            return re;
        int size = files.length;
        for (int i = 0; i < size; i++) {
            String name = files[i].getName();
            if (name.equals(Const.ACCOUNTS_FASTSAVE_DIRECTORY_NAME))
                continue;
            if (name.equals(Const.ACCOUNTS_HIDDEN_DIRECTORY))
                continue;
            re.add(name.substring(0, name.length() - 4));
        }
        return re;
    }

    private void checkDirectories() {
        File fastsave = new File(context.getFilesDir(), Const.ACCOUNTS_FASTSAVE_DIRECTORY_NAME);
        if (!fastsave.exists())
            fastsave.mkdir();
        File hidden = new File(context.getFilesDir(), Const.ACCOUNTS_HIDDEN_DIRECTORY);
        if (!hidden.exists())
            hidden.mkdir();
    }
    //endregion

    //region encryption
    private String encryptData(String data, String decryptionKey) {
        PrivateKey priv = PrivateKey.generateSpecificKey(decryptionKey);
        return priv.publicKey.encrypt(data);
    }

    private String decryptData(String data, String decryptionKey) {
        PrivateKey priv = PrivateKey.generateSpecificKey(decryptionKey);
        return priv.decrypt(data);
    }
    //endregion

    public void addEntry(String desc, float amount, AccountBE payAccount, AccountBE investAccount) {
        Calendar calendar = Calendar.getInstance();
        EntryBE entryPay = new EntryBE(amount*(-1.0f), desc, calendar.getTime());
        EntryBE entryInvest = new EntryBE(amount, desc, calendar.getTime());
        payAccount.addEntry(entryPay);
        investAccount.addEntry(entryInvest);
    }

    public AccountBE getAccountByName(String name) {
        AccountBE re = getPayAccountByName(name);
        if (re == null) {
            re = getInvestAccountByName(name);
        }
        return re;
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

    public boolean doNewMonthIfPossible() {
        Calendar cal = Calendar.getInstance();
        try {
            readAccountsFromInternal(Const.getLastMonthName());
        } catch (JSONException jsone) {
            jsone.printStackTrace();
        } catch (IOException ioe) {
            return false;
        } catch (ParseException pe) {
            pe.printStackTrace();
        }
        Map<String, Float> oldPayValues = new HashMap<>();
        for (AccountBE a : model.payAccounts) {
            if (a.getSum() != 0.0f) {
                oldPayValues.put(a.getName(), a.getSum());
                a.addEntry(new EntryBE(a.getSum() * (-1.0f), Const.DESC_CLOSING, cal.getTime()));
            }
        }
        try {
            saveAccountsToInternal(Const.getLastMonthName());
        } catch (JSONException jsone) {
            jsone.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        resetAccounts();
        resetIncomeList();
        for (String s : oldPayValues.keySet()) {
            getPayAccountByName(s).addEntry(new EntryBE(oldPayValues.get(s), Const.DESC_OPENING, cal.getTime()));
        }
        getModel().currentFileName = Const.getCurrentMonthName();
        triggerRecurringOrders();
        return true;
    }

    public boolean setupAccounts(boolean blank) {
        try {
            if (!blank) {
                readAccountsFromInternal();
                return false;
            }
            else {
                initAccountLists();
                return true;
            }
        } catch (JSONException jsone) {
            jsone.printStackTrace();
            initAccountLists();
            return true;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            if (!doNewMonthIfPossible()) {
                initAccountLists();
                return true;
            }
            else {
                return false;
            }
        } catch (ParseException pe) {
            pe.printStackTrace();
            initAccountLists();
            return false;
        }
    }

    public void addRecurringOrder(AccountBE pay, AccountBE invest, String desc, float amount) {
        Calendar calendar = Calendar.getInstance();
        RecurringOrderBE newOrder = new RecurringOrderBE(amount, desc, calendar.getTime(), pay.getName(), invest.getName());
        getModel().recurringOrders.add(newOrder);
    }

    public void triggerRecurringOrders() {
        for (RecurringOrderBE r : getModel().recurringOrders) {
            AccountBE pay = getPayAccountByName(r.getPayAccount());
            AccountBE invest = getInvestAccountByName(r.getInvestAccount());
            pay.addEntry(new EntryBE(r.getAmount()*(-1.0f), r.getDescription()));
            invest.addEntry(new EntryBE(r.getAmount(), r.getDescription()));
        }
    }
}
