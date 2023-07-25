package com.privat.pitz.financehelper;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;

import Backend.Controller;
import Backend.Model;
import Backend.RefreshListener;

public abstract class AbstractActivity extends AppCompatActivity implements RefreshListener {
    Controller controller = Controller.instance;
    public Model model;
    protected boolean passedOnCreate = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        passedOnCreate = true;
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

    @Override
    protected void onStop() {
        passedOnCreate = false;
        super.onStop();
    }

    protected void workingThread() { }

    protected void endWorkingThread() { }

    public Controller getController() { return controller; }

    public Model getModel() { return model; }

    //region Toast
    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public void showToast(int resID) {
        Toast.makeText(this, resID, Toast.LENGTH_SHORT).show();
    }

    public void showToastLong(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    public void showToastLong(int resID) {
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

    protected AlertDialog getBasicEditDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_basic_edit_text, null);
        builder.setView(dialogView);
        builder.setPositiveButton("OK", null);
        return builder.create();
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
        try {
            controller.saveAccountsToInternal();
        }  catch (JSONException e) {
            showToastLong(R.string.toast_error_JSONError);
        } catch (IOException e) {
            showToastLong(R.string.toast_error_IOError);
        }
        startActivity(intent);
    }

    protected void onAppStartup() {
        if (controller != null) {
            model = controller.getModel();
            return;
        }
        ProgressDialog dialog = getWaitDialog();
        dialog.show();
        initController();
        dialog.dismiss();
    }

    protected void initController() {
        Controller.createInstance(this);
        controller = Controller.instance;
        model = controller.getModel();
    }
}
