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

import com.privat.pitz.financehelper.core.Const;
import com.privat.pitz.financehelper.core.Controller;
import com.privat.pitz.financehelper.core.Model;
import com.privat.pitz.financehelper.core.ParseReport;
import com.privat.pitz.financehelper.ui.RefreshListener;
import com.privat.pitz.financehelper.core.Util;

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

    protected void setupActionBar() {
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
                    reportLoadProblems();
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
        builder.setPositiveButton(R.string.ok, null);
        return builder.create();
    }

    /**
     * Surfaces whatever the last parse had to default or discard, and clears it.
     *
     * <p>Call after any load. The rule this enforces: the app may repair a save file, but it may
     * not do so behind the user's back. A budget account once vanished on load because the parser
     * returned null and the caller dropped it in silence - the next save then wrote the model back
     * without it and the loss became permanent. Nobody was ever told.
     *
     * <p>A discard means data is gone, so it gets a dialog that has to be dismissed. A defaulted
     * field means the record survived, so it gets a toast.
     *
     * <p>Safe to call from a worker thread: MainActivity.initiateAccounts runs on one - with its
     * own prepared Looper, so Toast works there but a dialog would not - so the dialog is posted
     * to the UI thread rather than shown inline.
     */
    protected void reportLoadProblems() {
        if (model == null)
            return;
        ParseReport report = model.takeLoadReport();
        if (report.isEmpty())
            return;

        for (ParseReport.Note note : report.getNotes())
            Log.println(note.severity == ParseReport.Severity.DISCARDED ? Log.ERROR : Log.INFO,
                    "load_report", note.message);

        if (!report.hasDiscards()) {
            showToastLong(getString(R.string.toast_info_load_repaired,
                    report.getNotes().size()));
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (ParseReport.Note note : report.getNotes())
            sb.append("• ").append(note.message).append("\n\n");
        final String body = sb.toString().trim();
        final String title = getString(R.string.label_load_problems_title, report.countDiscards());
        runOnUiThread(() -> showScrollableMessageDialog(title, body));
    }

    /** Shows a dismissable dialog carrying a long, scrollable diagnostic message. */
    protected void showScrollableMessageDialog(String title, String message) {
        AlertDialog dialog = getBasicEditDialog();
        dialog.setTitle(title);
        dialog.show();
        TextView body = dialog.findViewById(R.id.edit_text);
        if (body != null)
            body.setText(message);
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
        } catch (JSONException | IOException e) {
            showErrorToast(e);
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
        List<String> availableEntities = controller.getAllAvailableEntities();
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

    protected void setCustomTitle() {
        // set Activity title
        try {
            Util.FileNameParts parts = Util.parseFileName(getModel().currentFileName);
            String monthName = Const.getMonthNameById(parts.month - 1);
            setCustomTitle(monthName + ":");
        } catch (IllegalArgumentException e) {
            Log.println(Log.ERROR, "parse_file_name", e.toString());
        }
    }
}
