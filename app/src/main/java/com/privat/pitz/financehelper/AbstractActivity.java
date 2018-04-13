package com.privat.pitz.financehelper;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import java.util.UUID;

import Backend.Controller;
import Backend.Model;

public class AbstractActivity extends AppCompatActivity {

    Controller controller = Controller.instance;
    Model model;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

    //region DeviceID
    protected String getDeviceId() {
        final int androidIdValue = createAndroidIdValue();
        final long deviceIdValue = createDeviceIdValue();
        final int simSerialValue = createSimSerialValue();

        final UUID deviceUuid = new UUID(androidIdValue, deviceIdValue | simSerialValue);
        if (deviceIdValue + simSerialValue + androidIdValue == 0) {
            return null;
        }
        return deviceUuid.toString();
    }

    private int createAndroidIdValue() {
        final String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        return androidId == null ? 0 : androidId.hashCode();
    }

    private long createDeviceIdValue() {
        final TelephonyManager tm = (TelephonyManager) getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);
        try {
            final String deviceId = tm.getDeviceId();
            return deviceId == null ? 0 : (long) deviceId.hashCode() << 32;
        } catch (SecurityException e) {
            e.printStackTrace();
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE},2);
        }
        return -1;

    }

    private int createSimSerialValue() {
        final TelephonyManager tm = (TelephonyManager) getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);
        try {
            final String simSerialNumber = tm.getSimSerialNumber();
            return simSerialNumber == null ? 0 : simSerialNumber.hashCode();
        } catch (SecurityException e) {
            e.printStackTrace();
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE},2);
        }
        return -1;
    }
    //endregion

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
}
