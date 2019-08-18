package View;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.privat.pitz.financehelper.R;

import Backend.Util;

public class AccountWidgetRow extends LinearLayout {

    Context context;
    String description;
    float amount;

    public AccountWidgetRow(Context context, String description, float amount) {
        super(context);
        this.context = context;
        this.description = description;
        this.amount = amount;
        populateUI();
    }

    public AccountWidgetRow(Context context, String description) {
        super(context);
        this.context = context;
        this.description = description;
        this.amount = 0.0f;
        populateUI();
    }

    private void populateUI() {
        RelativeLayout relative = (RelativeLayout) LayoutInflater.from(context).inflate(R.layout.table_row_account_list, null);
        TextView des = relative.findViewById(R.id.text_description);
        des.setText(description);
        if (amount != 0.0f) {
            TextView am = relative.findViewById(R.id.text_amount);
            am.setText(Util.formatFloat(amount));
        } else {
            relative.findViewById(R.id.text_amount).setVisibility(GONE);
            relative.findViewById(R.id.eurosign).setVisibility(GONE);
        }
        addView(relative);
    }

    public void setWidth(int width) {
        //setLayoutParams(new LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
