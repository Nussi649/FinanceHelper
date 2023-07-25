package Backend;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.privat.pitz.financehelper.AssetAccountDetailsActivity;
import com.privat.pitz.financehelper.R;

import java.util.ArrayList;
import java.util.List;

import Logic.TxBE;

public class TxListAdapter extends RecyclerView.Adapter<TxListAdapter.EntryViewHolder> {
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

    @SuppressLint("NotifyDataSetChanged")
    public void setEntries(List<TxBE> entries) {
        this.entries = entries;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EntryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_entry_tx_account_details, parent, false);
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


    @Override
    public int getItemCount() {
        return entries.size();
    }
}
