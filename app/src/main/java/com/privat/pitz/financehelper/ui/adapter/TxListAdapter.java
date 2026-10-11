package com.privat.pitz.financehelper.ui.adapter;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.privat.pitz.financehelper.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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

    /** Taps and long-presses on rows; only screens that support selecting entries set this. */
    public interface EntryInteraction {
        void onEntryClicked(TxBE tx);
        void onEntryLongClicked(TxBE tx);
    }

    private EntryInteraction interaction;
    // Insertion-ordered on purpose: the first entry selected is the anchor a merge folds the
    // others into, so the order of selection is data, not presentation.
    private final LinkedHashSet<TxBE> selected = new LinkedHashSet<>();

    public void setEntryInteraction(EntryInteraction interaction) {
        this.interaction = interaction;
    }

    /** Selects {@code tx}, or deselects it if it already is. */
    @SuppressLint("NotifyDataSetChanged")
    public void toggleSelected(TxBE tx) {
        if (!selected.remove(tx))
            selected.add(tx);
        // whole list, not one row: deselecting the anchor promotes another row to anchor
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void clearSelection() {
        selected.clear();
        notifyDataSetChanged();
    }

    /** The selected entries in the order they were selected; the first one is the anchor. */
    public List<TxBE> getSelected() {
        return new ArrayList<>(selected);
    }

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

        int background = Color.TRANSPARENT;
        if (selected.contains(entry)) {
            boolean isAnchor = selected.iterator().next() == entry;
            background = ContextCompat.getColor(holder.itemView.getContext(),
                    isAnchor ? R.color.colorSelectionAnchor : R.color.colorSelection);
        }
        holder.itemView.setBackgroundColor(background);

        if (interaction != null) {
            holder.itemView.setOnClickListener(v -> interaction.onEntryClicked(entry));
            holder.itemView.setOnLongClickListener(v -> {
                interaction.onEntryLongClicked(entry);
                return true;
            });
        }
    }

    public TxBE getTxAtPosition(int position) {
        return entries.get(position);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }
}
