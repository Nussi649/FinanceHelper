package com.privat.pitz.financehelper;

import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TextView;

import Logic.AccountBE;

public class SettingsActivity extends AbstractActivity {

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
    }

    @Override
    protected void endWorkingThread() {
        populateUI();
        setCustomTitle();
    }

    private void populateUI() {
        // Get a reference to the TableLayout in the activity_settings layout
        TableLayout layoutContainer = findViewById(R.id.layout_container);

        // For each account in the model's accounts
        for (AccountBE account : model.getAllAccounts()) {
            // Inflate the list_item_account_settings layout and initialize the views
            View view = getLayoutInflater().inflate(R.layout.list_item_account_settings, layoutContainer, false);

            // Get references to the views in the list_item_account_settings layout
            TextView tvName = view.findViewById(R.id.tv_account_name);
            CheckBox cbActive = view.findViewById(R.id.cb_active);
            CheckBox cbAutoRenew = view.findViewById(R.id.cb_auto_renew);
            ImageView ivRenew = view.findViewById(R.id.iv_trigger_renew);

            // Set the account name tv text
            tvName.setText(account.toString());

            // Set the state of the checkboxes based on the account's properties
            cbActive.setChecked(account.getIsActive());
            cbAutoRenew.setChecked(account.getAutoRenew());

            // Set up a listener for the active checkbox to update the account's isActive property
            cbActive.setOnCheckedChangeListener((buttonView, isChecked) -> account.setActive(isChecked));

            // Set up a listener for the auto renew checkbox to update the account's autoRenew property
            cbAutoRenew.setOnCheckedChangeListener((buttonView, isChecked) -> account.setAutoRenew(isChecked));

            // Set up a listener for the renew ImageView to call the account's renew method
            ivRenew.setOnClickListener(v -> {
                account.tryRenew();
                showToastLong(R.string.toast_success_account_renewed);
            });

            // Add the initialized list_item_account_settings view to the layoutContainer TableLayout
            layoutContainer.addView(view);
        }
    }

    @Override
    public void onRefresh() {

    }

    @Override
    public void setCustomTitle() {
        setCustomTitle(getString(R.string.label_account_settings));
    }
}
