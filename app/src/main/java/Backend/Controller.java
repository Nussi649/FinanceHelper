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

    public void saveAccountsToInternal() {
        JSONObject json = new JSONObject();
        JSONArray array = new JSONArray();
        for (AccountBE a : model.payAccounts) {
            JSONObject acc = new JSONObject();
            try {
                acc.put("name", a.getName());
            } catch (JSONException jsone) {
                jsone.printStackTrace();
            }
            for (EntryBE e : a.getEntries()) {
                // TODO
            }
            array.put(acc);
        }
        try {
            json.put("accounts", array);
        } catch (JSONException jsone) {
            jsone.printStackTrace();
        }
        String payload = json.toString();
        File file = new File(context.getFilesDir(), Const.ACCOUNTS_FILE_NAME);
        try {
            File gpxfile = new File(file, Const.ACCOUNTS_FILE_NAME);
            FileWriter writer = new FileWriter(gpxfile);
            writer.append(payload);
            writer.flush();
            writer.close();

        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void readAccountsFromInternal() {
        String payload = "";
        JSONObject json = new JSONObject();
        JSONArray accounts = new JSONArray();
        File file = new File(context.getFilesDir(), Const.ACCOUNTS_FILE_NAME);
        try {
            File gpxfile = new File(file, Const.ACCOUNTS_FILE_NAME);
            FileReader fReader = new FileReader(gpxfile);
            BufferedReader reader = new BufferedReader(fReader);
            payload = reader.readLine();
            reader.close();
            fReader.close();
            json = new JSONObject(payload);
            accounts = json.getJSONArray("accounts");
        } catch (Exception e){
            e.printStackTrace();
        }
        for (int i = 0; i < accounts.length(); i++) {
            try {
                JSONObject cur = accounts.getJSONObject(i);
                AccountBE curAcc = new AccountBE(cur.getString("name"));
                model.payAccounts.add(curAcc);
                // TODO soll/haben Listen auslesen
            } catch (JSONException jsone) {
                jsone.printStackTrace();
            }
        }
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
