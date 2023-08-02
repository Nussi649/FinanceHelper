package View.Dialogs;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;

import com.privat.pitz.financehelper.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import Logic.TxBE;

@SuppressLint("DefaultLocale")
public abstract class EditTxDialog {
    private final Context context;
    private final TxBE tx;
    private Calendar calendar;

    // View objects
    TextView tvDate;
    TextView tvTime;
    EditText etDescription;
    EditText etAmount;

    public EditTxDialog(Context context, TxBE tx) {
        this.context = context;
        this.tx = tx;
        this.calendar = Calendar.getInstance();
        this.calendar.setTime(tx.getDate());
    }

    public abstract void onConfirm(TxBE tx);

    @SuppressLint("InflateParams")
    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);

        View view = inflater.inflate(R.layout.dialog_edit_tx, null);
        tvDate = view.findViewById(R.id.tv_date);
        tvTime = view.findViewById(R.id.tv_time);
        etDescription = view.findViewById(R.id.et_description);
        etAmount = view.findViewById(R.id.et_amount);

        // Pre-fill the fields with the current values
        tvDate.setText(new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(calendar.getTime()));
        tvTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.getTime()));
        etDescription.setText(tx.getDescription());
        etAmount.setText(String.valueOf(tx.getAmount()));

        tvDate.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(context, (view1, year, month, dayOfMonth) -> {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                tvDate.setText(String.format("%02d.%02d.%04d", dayOfMonth, month+1, year));
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
            datePickerDialog.show();
        });

        tvTime.setOnClickListener(v -> {
            TimePickerDialog timePickerDialog = new TimePickerDialog(context, (view12, hourOfDay, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                calendar.set(Calendar.MINUTE, minute);
                tvTime.setText(String.format("%02d:%02d", hourOfDay, minute));
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true);
            timePickerDialog.show();
        });

        builder.setView(view)
                .setPositiveButton(context.getString(R.string.confirm), null)
                .setNegativeButton(context.getString(R.string.cancel), null)
                .setTitle(R.string.label_transaction_edit);

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view1 -> {
                String description = etDescription.getText().toString();
                String amountString = etAmount.getText().toString();

                if (description.isEmpty())
                    Toast.makeText(context, context.getString(R.string.toast_error_empty_description), Toast.LENGTH_LONG).show();
                else if (amountString.isEmpty())
                    Toast.makeText(context, context.getString(R.string.toast_error_empty_amount), Toast.LENGTH_LONG).show();
                else {
                    float amount = Float.parseFloat(amountString);
                    tx.setDate(calendar.getTime());
                    tx.setDescription(description);
                    tx.setAmount(amount);
                    onConfirm(tx);
                    dialog.dismiss();
                }
            });
        });

        dialog.show();
    }
}