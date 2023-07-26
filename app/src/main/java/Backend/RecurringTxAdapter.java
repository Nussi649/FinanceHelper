package Backend;

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

import Logic.RecurringTxBE;

public class RecurringTxAdapter extends RecyclerView.Adapter<RecurringTxAdapter.RecurringEntryViewHolder> {
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

    @SuppressLint("NotifyDataSetChanged")
    public void setEntries(List<RecurringTxBE> entries) {
        this.entries = entries;
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
