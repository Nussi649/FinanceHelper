package com.privat.pitz.financehelper;

import android.os.Bundle;
import Logic.AccountBE;

public class InvestActivity extends AccountActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
    }

    @Override
    protected void endWorkingThread() {
        populateUI();
    }

    private void populateUI() {
        for (AccountBE a : model.investAccounts) {
            if (a.getIsActive())
                addAccountToUI(a);
        }
    }
}
