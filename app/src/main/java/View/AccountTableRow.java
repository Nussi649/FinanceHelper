package View;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.privat.pitz.financehelper.R;

public class AccountTableRow extends LinearLayout {

    Context context;
    String description;
    float amount;

    public AccountTableRow(Context context, String description, float amount) {
        super(context);
        this.context = context;
        this.description = description;
        this.amount = amount;
        populateUI();
    }

    private void populateUI() {
        RelativeLayout relative = (RelativeLayout) LayoutInflater.from(context).inflate(R.layout.layout_account_table_row, null);
        TextView des = relative.findViewById(R.id.text_description);
        des.setText(description);
        TextView am = relative.findViewById(R.id.text_amount);
        am.setText(String.valueOf(amount));
        addView(relative);
    }

    public void setWidth(int width) {
        //setLayoutParams(new LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
