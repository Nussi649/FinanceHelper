package Backend;

import android.annotation.SuppressLint;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.privat.pitz.financehelper.AssetAccountDetailsActivity;
import com.privat.pitz.financehelper.R;

import Logic.TxBE;

public class TxAdvancedAdapter extends TxListAdapter{
    static class EntryViewHolder extends RecyclerView.ViewHolder {
        TextView date;
        TextView labelDescription;
        EditText editDescription;
        ViewSwitcher switcherDesc;
        TextView labelAmount;
        EditText editAmount;
        ViewSwitcher switcherAmount;

        EntryViewHolder(@NonNull View itemView) {
            super(itemView);
            date = itemView.findViewById(R.id.label_time);
            labelDescription = itemView.findViewById(R.id.label_description);
//            editDescription = itemView.findViewById(R.id.edit_text_description);
//            switcherDesc = itemView.findViewById(R.id.viewSwitcher_description);
            labelAmount = itemView.findViewById(R.id.label_amount);
//            editAmount = itemView.findViewById(R.id.edit_text_amount);
//            switcherAmount = itemView.findViewById(R.id.viewSwitcher_amount);
        }
    }
    private final AssetAccountDetailsActivity parentActivity;

    public TxAdvancedAdapter(AssetAccountDetailsActivity parent) {
        super();
        parentActivity = parent;
    }


    @SuppressLint("DefaultLocale")
    @Override
    public void onBindViewHolder(@NonNull final TxListAdapter.EntryViewHolder holder, int position) {
        TxBE entry = entries.get(position);

        // Set data
        holder.date.setText(Util.formatDateDisplay(entry.getDate()));
        holder.labelDescription.setText(entry.getDescription());
//        holder.editDescription.setText(entry.getDescription());
        holder.labelAmount.setText(String.format("%.2f", entry.getAmount()));
//        holder.editAmount.setText(String.valueOf(entry.getAmount()));

        // Initialize ViewSwitchers
//        holder.switcherDesc.setDisplayedChild(0); // show TextView
//        holder.switcherAmount.setDisplayedChild(0); // show TextView

        // Set up ViewSwitchers
//        holder.labelDescription.setOnLongClickListener(v -> {
//            // Switch to EditText
//            holder.switcherDesc.showNext();
//            return true;
//        });
//
//        holder.labelAmount.setOnLongClickListener(v -> {
//            // Switch to EditText
//            holder.switcherAmount.showNext();
//            return true;
//        });
//
//        // Set up Listeners to execute changes when editText loses focus
//        holder.editDescription.setOnFocusChangeListener((v, hasFocus) -> {
//            if (!hasFocus) { // when focus is lost
//                TxBE entry1 = entries.get(position);
//                if (parentActivity.updateEntryDescription(entry1, holder.editDescription.getText().toString())) {
//                    // Update was successful, notify the RecyclerView to refresh the display for this item
//                    notifyItemChanged(position);
//                }
//            }
//        });
//
//        holder.editAmount.setOnFocusChangeListener((v, hasFocus) -> {
//            if (!hasFocus) { // when focus is lost
//                TxBE entry12 = entries.get(position);
//                if (parentActivity.updateEntryAmount(entry12, holder.editAmount.getText().toString())) {
//                    // Update was successful, notify the RecyclerView to refresh the display for this item
//                    notifyItemChanged(position);
//                }
//            }
//        });
    }
}
