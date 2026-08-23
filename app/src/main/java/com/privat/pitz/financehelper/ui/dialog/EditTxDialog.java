package com.privat.pitz.financehelper.ui.dialog;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.EditText;

import com.privat.pitz.financehelper.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import com.privat.pitz.financehelper.core.Util;
import com.privat.pitz.financehelper.data.TxBE;

@SuppressLint("DefaultLocale")
public abstract class EditTxDialog extends BaseInputDialog {
    private final TxBE tx;
    private Calendar calendar;

    // View objects
    TextView tvDate;
    TextView tvTime;
    EditText etDescription;
    EditText etAmount;

    public EditTxDialog(Context context, TxBE tx) {
        super(context);
        this.tx = tx;
        this.calendar = Calendar.getInstance();
        this.calendar.setTime(tx.getDate());
    }

    public abstract void onConfirm(TxBE tx);

    @Override
    protected int getLayoutRes() {
        return R.layout.dialog_edit_tx;
    }

    @Override
    protected int getTitleRes() {
        return R.string.label_transaction_edit;
    }

    @Override
    protected void bindViews(View view) {
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
    }

    @Override
    protected boolean onConfirmClicked() {
        String description = etDescription.getText().toString();
        String amountString = etAmount.getText().toString();

        if (description.isEmpty()) {
            toastLong(R.string.toast_error_empty_description);
            return false;
        } else if (amountString.isEmpty()) {
            toastLong(R.string.toast_error_empty_amount);
            return false;
        } else {
            try {
                float amount = Util.parseAmount(amountString);
                tx.setDate(calendar.getTime());
                tx.setDescription(description);
                tx.setAmount(amount);
                onConfirm(tx);
                return true;
            } catch (NumberFormatException e) {
                toastLong(R.string.toast_error_invalid_amount);
                return false;
            }
        }
    }
}
