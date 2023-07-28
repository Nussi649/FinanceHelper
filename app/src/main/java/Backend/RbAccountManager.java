package Backend;

import android.widget.RadioButton;
import android.util.Log;

import org.json.JSONException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import Logic.AccountBE;

public class RbAccountManager {
    final private HashMap<RadioButton, AccountBE> radioButtonToAccountMap;
    final private Controller controller;
    final private String name;

    public RbAccountManager(String name, Controller controller) {
        radioButtonToAccountMap = new HashMap<>();
        this.name = name;
        this.controller = controller;
    }

    @Override
    public String toString() {
        return name;
    }

    public void addRadioButton(RadioButton radioButton, AccountBE account) {
        radioButton.setOnClickListener(v -> onRadioButtonClicked(radioButton));
        removeAccount(account);  // remove the account if it is already registered with another RadioButton
        radioButtonToAccountMap.put(radioButton, account);
    }

    public void removeRadioButton(RadioButton radioButton) {
        radioButtonToAccountMap.remove(radioButton);
    }

    public void removeAccount(AccountBE account) {
        Iterator<Map.Entry<RadioButton, AccountBE>> iterator = radioButtonToAccountMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<RadioButton, AccountBE> entry = iterator.next();
            if (entry.getValue().equals(account)) {
                iterator.remove();
            }
        }
    }

    public void onRadioButtonClicked(RadioButton clickedRadioButton) {
        AccountBE newAccount = radioButtonToAccountMap.get(clickedRadioButton);

        if (newAccount == null) {
            Log.println(Log.ERROR, "RbAccountManager",
                    String.format("RbGroup %s: No account found for clicked radio button.", name));
            return;
        }

        // Deactivate the previously selected radio button
        AccountBE currentAccount = controller.getSelectedAccount(name);
        if (currentAccount != null) {
            RadioButton oldRadioButton = getRadioButtonForAccount(currentAccount);
            if (oldRadioButton != null) {
                oldRadioButton.setChecked(false);
            } else {
                Log.println(Log.ERROR,"RbAccountManager",
                        String.format("RbGroup %s: No radio button found for previously selected account.", name));
            }
        }

        // Activate the newly clicked radio button and update the current account
        clickedRadioButton.setChecked(true);
        controller.updateSelectedAccount(name, newAccount);
        try {
            controller.saveAppSettings();
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
    }

    public RadioButton getRadioButtonForAccount(AccountBE account) {
        if (account == null)
            return null;
        for (RadioButton radioButton : radioButtonToAccountMap.keySet()) {
            if (radioButtonToAccountMap.get(radioButton).equals(account)) {
                return radioButton;
            }
        }
        return null;
    }

    public boolean isRadioButtonInGroup(RadioButton radioButton) {
        return radioButtonToAccountMap.containsKey(radioButton);
    }
}