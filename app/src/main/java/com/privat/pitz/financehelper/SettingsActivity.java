package com.privat.pitz.financehelper;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import Logic.AccountBE;

public class SettingsActivity extends AbstractActivity {

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
    }

    @Override
    protected void endWorkingThread() {
        populateUI();
    }

    private void populateUI() {
        LinearLayout content = findViewById(R.id.layout_filter_container);
        Button deleteFastsaveButton = findViewById(R.id.button_delete_fastsave);

        for (AccountBE a : model.payAccounts) {
            final CheckBox check = new CheckBox(this);
            check.setText(a.getName());
            check.setChecked(a.getIsActive());
            check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    controller.getPayAccountByName(check.getText().toString()).setActive(b);
                }
            });
            content.addView(check);
        }
        for (AccountBE a : model.investAccounts) {
            final CheckBox check = new CheckBox(this);
            check.setText(a.getName());
            check.setChecked(a.getIsActive());
            check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    controller.getInvestAccountByName(check.getText().toString()).setActive(b);
                }
            });
            content.addView(check);
        }
        deleteFastsaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showToastLong(getController().deleteFastSave() ? R.string.toast_success_accounts_deleted : R.string.toast_error_unknown);
            }
        });
    }
}
