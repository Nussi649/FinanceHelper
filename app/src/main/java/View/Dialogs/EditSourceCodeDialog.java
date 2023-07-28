package View.Dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.privat.pitz.financehelper.R;

public abstract class EditSourceCodeDialog {
    private final Context context;
    private final AlertDialog dialog;
    private EditText showSavefile;

    public EditSourceCodeDialog(Context context, AlertDialog rawDialog) {
        this.context = context;
        this.dialog = rawDialog;
    }

    public abstract void onConfirm(String newContent);

    public void show(String initialContent) {
        dialog.setTitle(R.string.label_edit_savefile);

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, context.getString(R.string.confirm), (dialogInterface, i) -> {
            String newContent = showSavefile.getText().toString();
            onConfirm(newContent);
            dialog.dismiss();
        });
        dialog.show();
        showSavefile = dialog.findViewById(R.id.edit_text);
        showSavefile.setText(initialContent);
    }
}