package com.privat.pitz.financehelper;

import android.os.Bundle;
import Logic.AccountBE;

public class PayActivity extends AccountActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        populateUI();
    }

    private void populateUI() {
        for (AccountBE a : model.payAccounts) {
            addAccountToUI(a);
        }
    }
}
