package View.Dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.privat.pitz.financehelper.R;

import Backend.Util;

public abstract class SaveFileDialog {
    private final Context context;
    private final String defaultFileName;

    public SaveFileDialog(Context context) {
        this(context, null);
    }

    public SaveFileDialog(Context context, String defaultFileName) {
        this.context = context;
        this.defaultFileName = Util.reduceFileTypeEnding(defaultFileName);
    }

    public abstract void onConfirm(String saveName);
    public abstract void onExport();

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);

        View view = inflater.inflate(R.layout.dialog_save_file, null);
        EditText saveName = view.findViewById(R.id.edit_save_name);
        Button exportButton = view.findViewById(R.id.button_export);

        exportButton.setOnClickListener(v -> onExport());

        builder.setView(view)
                .setTitle(context.getString(R.string.label_save_accounts))
                .setNegativeButton(context.getString(R.string.cancel), null)
                .setPositiveButton(context.getString(R.string.label_save), (dialog, id) -> {
                    String saveNameStr = saveName.getText().toString();
                    onConfirm(saveNameStr);
                });
        if (defaultFileName != null) {
            saveName.setText(defaultFileName);
        }

        builder.create().show();
        saveName.requestFocus();
    }
}