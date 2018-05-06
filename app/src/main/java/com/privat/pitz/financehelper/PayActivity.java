package com.privat.pitz.financehelper;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import Backend.Util;
import Logic.AccountBE;
import Logic.EntryBE;

public class PayActivity extends AccountActivity {

    EditText addFundsAmount;
    EditText transferAmount;
    EditText descriptionText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
    }

    @Override
    protected void populateUI() {
        for (AccountBE a : model.payAccounts) {
            if (a.getIsActive())
                addAccountToUI(a);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_pay, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.item_add_funds:
                showAddFundsDialog();
                break;
            case R.id.item_transfer:
                showTransferDialog();
                break;
            case R.id.item_new_account:
                showNewAccountDialog();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void createAccount(String name) {
        AccountBE newAccount = new AccountBE(name);
        model.payAccounts.add(newAccount);
        addAccountToUI(newAccount);
    }

    private void showAddFundsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.label_add_funds);
        final View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_funds, null);
        addFundsAmount = dialogView.findViewById(R.id.edit_amount);
        descriptionText = dialogView.findViewById(R.id.edit_new_description);
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, getDoNothingClickListener());
        builder.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                float amount = 0.0f;
                String desc = "";
                try {
                    amount = Float.parseFloat(addFundsAmount.getText().toString());
                    desc = descriptionText.getText().toString();
                } catch (Exception e) {
                    dialogInterface.dismiss();
                    showToast(R.string.toast_error_NaN);
                }
                if (amount != 0.0f && !(desc.equals("")))
                    addFunds(amount, desc);
            }
        });
        LinearLayout payAccounts = dialogView.findViewById(R.id.linLayPayAccounts);
        Util.populatePayAccountsList(this, payAccounts);
        builder.show();
    }

    private void addFunds(float amount, String desc) {
        model.currentPayAcc.addEntry(new EntryBE(model.entrySequenceValue++, amount, desc));
        reloadContent();
    }

    private void showTransferDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.label_transfer);
        final View dialogView = getLayoutInflater().inflate(R.layout.dialog_transfer, null);
        addFundsAmount = dialogView.findViewById(R.id.edit_amount);
        descriptionText = dialogView.findViewById(R.id.edit_new_description);
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, getDoNothingClickListener());
        builder.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                float amount = 0.0f;
                String desc = "";
                try {
                    amount = Float.parseFloat(addFundsAmount.getText().toString());
                    desc = descriptionText.getText().toString();
                } catch (Exception e) {
                    dialogInterface.dismiss();
                    showToast(R.string.toast_error_NaN);
                }
                if (amount != 0.0f && !(desc.equals("")))
                    transferFunds(amount, desc);
            }
        });
        LinearLayout payAccounts1 = dialogView.findViewById(R.id.linLayPayAccounts1);
        LinearLayout payAccounts2 = dialogView.findViewById(R.id.linLayPayAccounts2);
        Util.populatePayAccountsList(this, payAccounts1, "-1");
        Util.populatePayAccountsList(this, payAccounts2, "-2");

        builder.show();
    }

    private void transferFunds(float amount, String desc) {
        getController().addEntry(desc, amount, model.currentPayAcc, model.transferRecipientAcc);
        reloadContent();
    }
}
