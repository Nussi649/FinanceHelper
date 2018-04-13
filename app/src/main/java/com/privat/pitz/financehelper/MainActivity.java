package com.privat.pitz.financehelper;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import Backend.Controller;

public class MainActivity extends AbstractActivity {

    private final int MY_PERMISSIONS_REQUEST_INTERNET = 1;
    private final int MY_PERMISSIONS_REQUEST_PHONE_STATE = 2;
    private final int MY_PERMISSIONS_WRITE_EXTERNAL_STORAGE = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    private void onAppStartup() {
        if (controller != null) {
            return;
        }
        askForPermissions();
        ProgressDialog dialog = getWaitDialog();
        dialog.show();
        initController();
        dialog.dismiss();
    }

    private void initController() {
        Controller.createInstance();
        controller = Controller.instance;
        model = controller.getModel();
    }

    //region Permissions
    private void askForPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.READ_PHONE_STATE }, MY_PERMISSIONS_REQUEST_PHONE_STATE);
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, final String permissions[], final int[] grantResults) {
//        switch (requestCode) {
//            case MY_PERMISSIONS_REQUEST_PHONE_STATE: {
//                // If request is cancelled, the result arrays are empty.
//                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_WRITE_EXTERNAL_STORAGE);
//                    }
//                } else {
//                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, MY_PERMISSIONS_REQUEST_PHONE_STATE);
//                } return;
//            }
//        }
    }
    //endregion
}
