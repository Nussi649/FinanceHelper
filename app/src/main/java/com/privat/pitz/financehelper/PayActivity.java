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

import org.json.JSONException;

import java.io.IOException;
import java.util.Calendar;

import Backend.Const;
import Backend.Util;
import Logic.AccountBE;
import Logic.EntryBE;

public class PayActivity extends AccountListActivity {

    EditText addFundsAmount;
    EditText transferAmount;
    EditText descriptionText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_list);
    }

    @Override
    protected void populateUI() {
        String cut = Util.cutFileNameIfNecessary(getModel().currentFileName);
        String title = Const.getMonthNameById(Integer.valueOf(cut.substring(0, cut.length() - 1)) - 1);
        setTitle(title);
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

    //region show Dialog
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
                if (amount == 0.0f) {
                    showToastLong(R.string.toast_error_empty_amount);
                    return;
                } else if (desc.equals("")) {
                    showToastLong(R.string.toast_error_empty_description);
                    return;
                } else {
                    addFunds(amount, desc);
                }
            }
        });
        LinearLayout payAccounts = dialogView.findViewById(R.id.linLayPayAccounts);
        Util.populatePayAccountsList(this, payAccounts);
        builder.show();
    }

    private void showTransferDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.label_transfer);
        final View dialogView = getLayoutInflater().inflate(R.layout.dialog_transfer, null);
        transferAmount = dialogView.findViewById(R.id.edit_amount);
        descriptionText = dialogView.findViewById(R.id.edit_new_description);
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, getDoNothingClickListener());
        builder.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                float amount = 0.0f;
                String desc = "";
                try {
                    amount = Float.parseFloat(transferAmount.getText().toString());
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
        Util.populatePayAccountsList(this, payAccounts1, Const.APPENDIX_PAY_SENDER);
        Util.populatePayAccountsList(this, payAccounts2, Const.APPENDIX_PAY_RECIPIENT);

        builder.show();
    }
    //endregion

    //region react to dialog
    private void addFunds(float amount, String desc) {
        model.currentPayAcc.addEntry(new EntryBE(amount, desc, Calendar.getInstance().getTime()));
        model.incomeList.add(new EntryBE(amount, desc, Calendar.getInstance().getTime()));
        try {
            controller.saveAccountsToInternal();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        reloadContent();
    }

    private void transferFunds(float amount, String desc) {
        getController().addEntry(desc, amount, model.currentPayAcc, model.transferRecipientAcc);
        try {
            controller.saveAccountsToInternal();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        reloadContent();
    }
    //endregion
}
