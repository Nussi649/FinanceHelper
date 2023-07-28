package com.privat.pitz.financehelper;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

import Backend.Controller;
import Backend.Model;
import Backend.RefreshListener;

public abstract class AbstractActivity extends AppCompatActivity implements RefreshListener {
    Controller controller = Controller.instance;
    public Model model;
    TextView titleView;
    TextView titleDetailsView;
    Spinner titleSpinner;
    protected boolean passedOnCreate = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        passedOnCreate = true;

        if (controller != null) {
            model = controller.getModel();
        }
        AbstractActivity self = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                workingThread();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setupActionBar();
                        endWorkingThread();
                    }
                });
            }
        }).start();
    }

    private void setupActionBar() {
        try {
            // set custom Action Bar
            getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
            getSupportActionBar().setCustomView(R.layout.component_actionbar);

            // set view objects
            titleView = findViewById(R.id.appBarTitle);
            titleDetailsView = findViewById(R.id.appBarDetails);
            titleSpinner = findViewById(R.id.appBarSpinner);
            // set spinner adapter
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.component_actionbar_spinner_item, model.availableEntities);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            titleSpinner.setAdapter(adapter);
            // select current entity
            titleSpinner.setSelection(model.availableEntities.indexOf(model.currentEntity));
            // set spinner listener
            titleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    // get target entity string
                    String targetEntity = (String) titleSpinner.getSelectedItem();
                    // catch case where no change has been made
                    if (targetEntity.equals(model.currentEntity))
                        return;
                    // try switching to target entity
                    try {
                        controller.switchToEntity(targetEntity);
                    } catch (JSONException | IOException | IllegalArgumentException e) {
                        if (e instanceof JSONException)
                            Log.println(Log.ERROR, "switch_entity",
                                    String.format("Error while trying to switch entity. Could not serialize old state. Aborting process! Exception: %s", e));
                        else if (e instanceof  IOException)
                            Log.println(Log.ERROR, "switch_entity",
                                    String.format("Error while trying to switch entity. Could not write old state. Aborting process! Exception: %s", e));
                        else
                            Log.println(Log.ERROR, "switch_entity",
                                    String.format("Error while trying to switch entity. Invalid target entity. Aborting process! Exception: %s", e));
                        return;
                    }
                    if (titleSpinner.getSelectedItemPosition() != position) {
                        titleSpinner.setSelection(position);  // This will close the dropdown
                    }
                    // start main activity to load UI
                    startActivity(MainActivity.class);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // not possible
                }
            });

        } catch (NullPointerException e) {
            Log.println(Log.ERROR, "setup_actionbar",
                    String.format("Error setting up ActionBar: Could not get SupportActionBar: %s", e));
        }
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
        builder.setPositiveButton(R.string.confirm, acceptListener);
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
        if (controller.loadAppSettings())
            showToast(R.string.toast_success_settings_loaded);
        else
            showToast(R.string.toast_error_settings_not_loaded);
        List<String> availableEntities = controller.getCurrentAvailableEntities();
        if (availableEntities.size() > 0)
            model.availableEntities = availableEntities;
    }

    protected void showErrorToast(Exception e) {
        if (e instanceof JSONException)
            showToastLong(R.string.toast_error_JSONError);
        if (e instanceof IOException)
            showToastLong(R.string.toast_error_IOError);
        if (e instanceof NumberFormatException)
            showToastLong(R.string.toast_error_NaN);
        if (e instanceof ParseException)
            showToastLong(R.string.toast_error_parsing);
    }

    protected void setCustomTitle(String value) {
        titleView.setText(value);
    }

    protected void setCustomTitleDetails(String value) {
        titleDetailsView.setText(value);
    }
}
