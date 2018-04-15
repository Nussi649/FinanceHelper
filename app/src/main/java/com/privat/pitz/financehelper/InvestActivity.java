package com.privat.pitz.financehelper;

import android.os.Bundle;
import Logic.AccountBE;

public class InvestActivity extends AccountActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        populateUI();
    }

    private void populateUI() {
        for (AccountBE a : model.investAccounts) {
            addAccountToUI(a);
        }
    }
}