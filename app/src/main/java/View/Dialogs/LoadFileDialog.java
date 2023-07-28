package View.Dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.privat.pitz.financehelper.R;

import java.io.File;
import java.util.List;

import Backend.Util;

public abstract class LoadFileDialog {
    private final Context context;
    private final List<File> availableFiles;

    public LoadFileDialog(Context context, List<File> availableFiles) {
        this.context = context;
        this.availableFiles = availableFiles;
    }

    public abstract void onConfirm(String filename);
    public abstract void onImport();
    public abstract void onDelete(String filename);

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);

        View view = inflater.inflate(R.layout.dialog_load_accounts, null);
        TableLayout availableFilesLayout = view.findViewById(R.id.layout_available_files);
        EditText filenameEdit = view.findViewById(R.id.edit_load_name);
        Button importButton = view.findViewById(R.id.button_import);

        importButton.setOnClickListener(v -> onImport());

        List<String> availableFileNames = Util.getFileNames(availableFiles);
        if (availableFileNames == null || availableFileNames.size() == 0)
            Toast.makeText(context, R.string.toast_error_no_valid_files, Toast.LENGTH_LONG).show();
        else {
            for (String s : availableFileNames) {
                TableRow row = (TableRow) inflater.inflate(R.layout.tablerow_file_list, availableFilesLayout, false);
                row.setTag(s);
                final TextView name = row.findViewById(R.id.text_filename);
                TextView delete = row.findViewById(R.id.text_delete_sign);
                name.setText(s);
                name.setOnClickListener(v -> filenameEdit.setText(((TextView) v).getText()));
                delete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onDelete(name.getText().toString());
                        ((TableLayout) v.getParent().getParent()).removeView((View) v.getParent());
                    }
                });
                delete.setOnClickListener(v -> onDelete(name.getText().toString()));
                availableFilesLayout.addView(row);
            }
        }

        builder.setView(view)
                .setTitle(context.getString(R.string.label_load_accounts))
                .setNegativeButton(context.getString(R.string.cancel), null)
                .setPositiveButton(context.getString(R.string.confirm), (dialog, id) -> {
                    String filename = filenameEdit.getText().toString();
                    onConfirm(filename);
                });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}