package com.privat.pitz.financehelper.ui.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.privat.pitz.financehelper.R;
import com.privat.pitz.financehelper.RecurringTxActivity;

import java.util.ArrayList;
import java.util.List;

import com.privat.pitz.financehelper.core.Util;
import com.privat.pitz.financehelper.data.RecurringTxBE;
import com.privat.pitz.financehelper.data.TxBE;
import com.privat.pitz.financehelper.ui.TxListSection;

import java.util.function.BiPredicate;

public class RecurringTxAdapter extends RecyclerView.Adapter<RecurringTxAdapter.RecurringEntryViewHolder> implements TxListSection.EntryListAdapter {

    /**
     * Reproduces RecurringTxActivity.filterEntries's predicate: matches when the (case-sensitive)
     * query is contained in the description, sender, or receiver.
     */
    public static final BiPredicate<TxBE, String> MATCH_SENDER_RECEIVER_OR_DESCRIPTION = (tx, query) -> {
        RecurringTxBE entry = (RecurringTxBE) tx;
        return entry.getDescription().contains(query) ||
                entry.getSenderStr().contains(query) ||
                entry.getReceiverStr().contains(query);
    };
    static class RecurringEntryViewHolder extends RecyclerView.ViewHolder {
        TextView labelDescription;
        TextView labelAmount;
        TextView senderReceiver;
        ImageView deleteButton;

        RecurringEntryViewHolder(@NonNull View itemView) {
            super(itemView);
            labelDescription = itemView.findViewById(R.id.label_description);
            labelAmount = itemView.findViewById(R.id.label_amount);
            senderReceiver = itemView.findViewById(R.id.label_sender_receiver);
            deleteButton = itemView.findViewById(R.id.delete_button);
        }
    }

    private final RecurringTxActivity parentActivity;
    private List<RecurringTxBE> entries = new ArrayList<>();

    public RecurringTxAdapter(RecurringTxActivity parent) {
        super();
        this.parentActivity = parent;
    }

    @SuppressWarnings("unchecked")
    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void setEntries(List<? extends TxBE> entries) {
        // Every caller supplies RecurringTxBE elements (see MATCH_SENDER_RECEIVER_OR_DESCRIPTION
        // and RecurringTxActivity.getEntries()); cast is safe in practice.
        this.entries = (List<RecurringTxBE>) entries;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecurringEntryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_tx_recurring_orders, parent, false);
        return new RecurringEntryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final RecurringEntryViewHolder holder, int position) {
        RecurringTxBE entry = entries.get(position);

        // Set sender and receiver data
        String senderReceiverStr = entry.getSenderStr() + " -> " + entry.getReceiverStr();
        holder.senderReceiver.setText(senderReceiverStr);
        // Set description
        holder.labelDescription.setText(entry.getDescription());
        // Set amount
        holder.labelAmount.setText(Util.formatLargeFloatDisplay(entry.getAmount()));

        // Set delete button listener
        holder.deleteButton.setOnClickListener(v -> {
            parentActivity.deleteOrder(entry);
        });
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }
}
