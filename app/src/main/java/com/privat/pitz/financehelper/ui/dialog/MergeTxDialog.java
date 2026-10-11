package com.privat.pitz.financehelper.ui.dialog;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

import com.privat.pitz.financehelper.R;
import com.privat.pitz.financehelper.core.TxService;
import com.privat.pitz.financehelper.core.Util;
import com.privat.pitz.financehelper.data.TxBE;

/**
 * Confirms a merge before anything changes. Date, time and description start out as the anchor's
 * (the entry selected first) and can be edited; the resulting amount is shown but not editable -
 * a merge must leave the account total exactly as it was. When the entries cancel out, the dialog
 * says so and offers to remove them instead.
 */
public class MergeTxDialog {
    private final Context context;
    private final TxBE anchor;
    private final List<TxBE> others;

    public MergeTxDialog(Context context, TxBE anchor, List<TxBE> others) {
        this.context = context;
        this.anchor = anchor;
        this.others = others;
    }

    /** @param onConfirm receives the description and date for the merged entry */
    @SuppressLint({"InflateParams", "DefaultLocale"})
    public void show(BiConsumer<String, Date> onConfirm) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_merge_tx, null);
        TextView tvDate = view.findViewById(R.id.tv_merge_date);
        TextView tvTime = view.findViewById(R.id.tv_merge_time);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(anchor.getDate());
        tvDate.setText(new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(calendar.getTime()));
        tvTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.getTime()));
        tvDate.setOnClickListener(v -> new DatePickerDialog(context, (picker, year, month, day) -> {
            calendar.set(year, month, day);
            tvDate.setText(String.format("%02d.%02d.%04d", day, month + 1, year));
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show());
        tvTime.setOnClickListener(v -> new TimePickerDialog(context, (picker, hour, minute) -> {
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            tvTime.setText(String.format("%02d:%02d", hour, minute));
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show());

        View resultContainer = view.findViewById(R.id.container_merge_result);
        EditText etDescription = view.findViewById(R.id.et_merge_description);
        TextView tvAmount = view.findViewById(R.id.tv_merge_amount);
        TextView tvCancelOut = view.findViewById(R.id.tv_merge_cancel_out);

        float amount = TxService.mergedAmount(anchor, others);
        boolean cancelsOut = amount == 0.0f;
        if (cancelsOut) {
            resultContainer.setVisibility(View.GONE);
            tvCancelOut.setVisibility(View.VISIBLE);
        } else {
            etDescription.setText(anchor.getDescription());
            tvAmount.setText(String.format("%s %s", Util.formatFloatDisplay(amount),
                    context.getString(R.string.label_currency)));
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.label_merge_title, others.size() + 1))
                .setView(view)
                .setPositiveButton(cancelsOut ? R.string.label_remove : R.string.label_merge, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        // positive button re-wired after show so an empty description keeps the dialog open
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String description = etDescription.getText().toString().trim();
                    if (!cancelsOut && description.isEmpty()) {
                        Toast.makeText(context, R.string.toast_error_empty_description,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    dialog.dismiss();
                    onConfirm.accept(description, calendar.getTime());
                }));
        dialog.show();
    }
}
