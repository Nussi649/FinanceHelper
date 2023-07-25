package com.privat.pitz.financehelper;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TableLayout;
import android.widget.TableRow;

import java.util.ArrayList;
import java.util.List;

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
        TableLayout content = findViewById(R.id.layout_filter_container);
        List<AccountBE> allAccounts = new ArrayList<>();
        allAccounts.addAll(model.asset_accounts);
        allAccounts.addAll(model.budget_accounts);
        int size = allAccounts.size();
        for (int i = 0; i<size/2; i++) {
            final CheckBox check1 = new CheckBox(this);
            check1.setText(allAccounts.get(2*i).getName());
            check1.setChecked(allAccounts.get(2*i).getIsActive());
            check1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    model.getAccountByName(check1.getText().toString()).setActive(b);
                }
            });
            check1.setLayoutParams(new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0.5f));
            final CheckBox check2 = new CheckBox(this);
            check2.setText(allAccounts.get(2*i+1).getName());
            check2.setChecked(allAccounts.get(2*i+1).getIsActive());
            check2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    model.getAccountByName(check2.getText().toString()).setActive(b);
                }
            });
            check2.setLayoutParams(new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0.5f));
            TableRow row = new TableRow(this);
            row.addView(check1);
            row.addView(check2);
            content.addView(row);
        }
        if (size % 2 == 1) {
            final CheckBox check = new CheckBox(this);
            check.setText(allAccounts.get(size-1).getName());
            check.setChecked(allAccounts.get(size-1).getIsActive());
            check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    model.getAccountByName(check.getText().toString()).setActive(b);
                }
            });
            TableRow row = new TableRow(this);
            row.addView(check);
            content.addView(row);
        }
    }

    @Override
    public void onRefresh() {

    }
}
