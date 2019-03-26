package com.privat.pitz.financehelper;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.UUID;

import Backend.Controller;
import Backend.Model;
import Logic.AccountBE;

public abstract class AbstractActivity extends AppCompatActivity {

    private final int MY_PERMISSIONS_REQUEST_INTERNET = 1;
    private final int MY_PERMISSIONS_REQUEST_PHONE_STATE = 2;
    private final int MY_PERMISSIONS_WRITE_EXTERNAL_STORAGE = 3;
    private final int MY_PERMISSIONS_READ_EXTERNAL_STORAGE = 4;

    Controller controller = Controller.instance;
    public Model model;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (controller != null) {
            model = controller.getModel();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                workingThread();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        endWorkingThread();
                    }
                });
            }
        }).start();
    }

    protected void workingThread() { }

    protected void endWorkingThread() { }

    public Controller getController() { return controller; }

    public Model getModel() { return model; }

    //region Toast
    protected void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    protected void showToast(int resID) {
        Toast.makeText(this, resID, Toast.LENGTH_SHORT).show();
    }

    protected void showToastLong(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    protected void showToastLong(int resID) {
        Toast.makeText(this, resID, Toast.LENGTH_LONG).show();
    }
    //endregion

    protected ProgressDialog getWaitDialog() {
        ProgressDialog re = new ProgressDialog(this);
        re.setTitle(R.string.loadingscreen_title);
        re.setMessage(getString(R.string.loadingscreen_body));
        re.setCancelable(false);
        return re;
    }

    protected void showConfirmDialog(int msgID, AlertDialog.OnClickListener acceptListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.label_are_u_sure);
        builder.setMessage(msgID);
        builder.setNegativeButton(R.string.cancel, getDoNothingClickListener());
        builder.setPositiveButton(R.string.accept, acceptListener);
        builder.show();
    }

    protected DialogInterface.OnClickListener getDoNothingClickListener() {
        return new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

            }
        };
    }

    protected void startActivity(Class<? extends AbstractActivity> target) {
        Intent intent = new Intent(this, target);
        startActivity(intent);
    }

    protected void onAppStartup() {
        if (controller != null) {
            model = controller.getModel();
            return;
        }
        askForPermissions();
        ProgressDialog dialog = getWaitDialog();
        dialog.show();
        initController();
        dialog.dismiss();
    }

    protected void initController() {
        Controller.createInstance(this);
        controller = Controller.instance;
        model = controller.getModel();
        model.payAccounts = new ArrayList<>();
        model.investAccounts = new ArrayList<>();
        model.recurringOrders = new ArrayList<>();
    }

    //region Permissions
    private void askForPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.READ_EXTERNAL_STORAGE }, MY_PERMISSIONS_READ_EXTERNAL_STORAGE);
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, MY_PERMISSIONS_WRITE_EXTERNAL_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, final String permissions[], final int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_WRITE_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MY_PERMISSIONS_READ_EXTERNAL_STORAGE);
                    }
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_WRITE_EXTERNAL_STORAGE);
                } return;
            }
            case MY_PERMISSIONS_READ_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_WRITE_EXTERNAL_STORAGE);
                    }
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MY_PERMISSIONS_READ_EXTERNAL_STORAGE);
                } return;
            }
        }
    }
    //endregion
}
