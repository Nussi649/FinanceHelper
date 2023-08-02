package View.Dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

        View view = inflater.inflate(R.layout.dialog_load_file, null);
        LinearLayout availableFilesLayout = view.findViewById(R.id.layout_available_files);
        EditText filenameEdit = view.findViewById(R.id.edit_load_name);
        Button importButton = view.findViewById(R.id.button_import);

        importButton.setOnClickListener(v -> onImport());

        List<String> availableFileNames = Util.getFileNames(availableFiles);
        if (availableFileNames == null || availableFileNames.size() == 0)
            Toast.makeText(context, R.string.toast_error_no_valid_files, Toast.LENGTH_LONG).show();
        else {
            for (String s : availableFileNames) {
                LinearLayout item = (LinearLayout) inflater.inflate(R.layout.table_row_filename, availableFilesLayout, false);
                final TextView name = item.findViewById(R.id.text_filename);
                ImageView delete = item.findViewById(R.id.delete_button);
                name.setText(Util.reduceFileTypeEnding(s));
                item.setOnClickListener(v -> filenameEdit.setText(Util.reduceFileTypeEnding(s)));
                delete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onDelete(s);
                        availableFilesLayout.removeView(item);
                    }
                });
                availableFilesLayout.addView(item);
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