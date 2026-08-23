package com.privat.pitz.financehelper.ui.dialog;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.privat.pitz.financehelper.R;

/**
 * Shared scaffolding for the validating input dialogs: inflate a body layout, wire a localized
 * Confirm/Cancel pair, and keep the dialog open when validation fails.
 *
 * <p>The positive button is registered with a {@code null} listener and only re-wired inside
 * {@link AlertDialog#setOnShowListener}. That is what stops AlertDialog from dismissing itself on
 * every click: a rejected {@link #onConfirmClicked()} leaves the dialog on screen with the user's
 * input intact. Subclasses that want the default auto-dismiss behaviour should not extend this.
 */
public abstract class BaseInputDialog {
    protected final Context context;
    private AlertDialog dialog;

    protected BaseInputDialog(Context context) {
        this.context = context;
    }

    /** Layout inflated as the dialog body. */
    protected abstract int getLayoutRes();

    /**
     * Dialog title. Resolved inside {@link #show()} rather than in the constructor, so it may
     * depend on subclass state.
     */
    protected abstract int getTitleRes();

    /** Bind and pre-fill views. Runs after inflation, before the dialog is created. */
    protected abstract void bindViews(View view);

    /**
     * Validate the input and act on it.
     *
     * @return {@code true} to dismiss the dialog, {@code false} to leave it open — in which case
     *         the implementation is responsible for having told the user why.
     */
    protected abstract boolean onConfirmClicked();

    @SuppressLint("InflateParams")
    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(getLayoutRes(), null);

        bindViews(view);

        builder.setView(view)
                .setTitle(getTitleRes())
                .setPositiveButton(context.getString(R.string.confirm), null)
                .setNegativeButton(context.getString(R.string.cancel), null);

        dialog = builder.create();
        dialog.setOnShowListener(dialogInterface ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    if (onConfirmClicked()) {
                        dialog.dismiss();
                    }
                }));
        dialog.show();
    }

    protected void toastLong(int messageRes) {
        Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_LONG).show();
    }

    protected void toastShort(int messageRes) {
        Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show();
    }
}
