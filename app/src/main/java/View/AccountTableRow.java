package View;

import android.content.Context;
import android.widget.TableRow;
import android.widget.TextView;

public class AccountTableRow extends TableRow {

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
        TextView des = new TextView(context);
        des.setText(description);
        des.setTextAlignment(TEXT_ALIGNMENT_TEXT_START);
        TextView am = new TextView(context);
        am.setText(String.valueOf(amount));
        am.setTextAlignment(TEXT_ALIGNMENT_TEXT_END);
        addView(des);
        addView(am);
    }
}
