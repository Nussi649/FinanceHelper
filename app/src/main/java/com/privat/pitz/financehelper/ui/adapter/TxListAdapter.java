package com.privat.pitz.financehelper.ui.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.privat.pitz.financehelper.R;

import java.util.ArrayList;
import java.util.List;

import com.privat.pitz.financehelper.core.Util;
import com.privat.pitz.financehelper.data.TxBE;
import com.privat.pitz.financehelper.ui.TxListSection;

public class TxListAdapter extends RecyclerView.Adapter<TxListAdapter.EntryViewHolder> implements TxListSection.EntryListAdapter {
    static class EntryViewHolder extends RecyclerView.ViewHolder {
        TextView date;
        TextView labelDescription;
        TextView labelAmount;

        EntryViewHolder(@NonNull View itemView) {
            super(itemView);
            date = itemView.findViewById(R.id.label_time);
            labelDescription = itemView.findViewById(R.id.label_description);
            labelAmount = itemView.findViewById(R.id.label_amount);
        }
    }
    protected List<TxBE> entries = new ArrayList<>();

    @SuppressWarnings("unchecked")
    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void setEntries(List<? extends TxBE> entries) {
        // Stored by reference (not copied) so addEntry/removeEntry mutate the caller's list -
        // pre-existing aliasing behavior, preserved as-is. The cast is safe in practice: every
        // caller passes either a List<TxBE> or a freshly-built ArrayList<TxBE>.
        this.entries = (List<TxBE>) entries;
        notifyDataSetChanged();
    }

    public void addEntry(int position, TxBE tx) {
        entries.add(position, tx);
        notifyItemInserted(position);
    }

    public void removeEntry(TxBE toRemove) {
        int position = entries.indexOf(toRemove);
        if (position != -1) {
            // Entry exists in the list, remove it
            entries.remove(position);
            notifyItemRemoved(position);
        }
    }

    @NonNull
    @Override
    public EntryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_tx_account_details, parent, false);
        return new EntryViewHolder(view);
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void onBindViewHolder(@NonNull final EntryViewHolder holder, int position) {
        TxBE entry = entries.get(position);

        // Set data
        holder.date.setText(Util.formatDateDisplay(entry.getDate()));
        holder.labelDescription.setText(entry.getDescription());
        holder.labelAmount.setText(Util.formatFloatDisplay(entry.getAmount()));
    }

    public TxBE getTxAtPosition(int position) {
        return entries.get(position);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }
}
