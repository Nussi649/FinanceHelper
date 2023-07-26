package View;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.privat.pitz.financehelper.R;

import android.os.Handler;

import Backend.Util;

public class AccountWidgetRow extends LinearLayout {
    public static AccountWidgetRow getInstance(Context context) {
        return (AccountWidgetRow) LayoutInflater.from(context).inflate(R.layout.component_row_widget_asset_account, null);
    }

    Context context;
    String description;
    float amount;

    TextView descriptionLabel;
    TextView amountLabel;
    TextView euroLabel;
    GestureDetector gestureDetector;
    private boolean isLongPress = false;

    // region constructors
    public AccountWidgetRow(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }

    public void init(String description, float amount) {
        this.description = description;
        this.amount = amount;
        initViews();
    }

    public void init(String description) {
        init(description, 0.0f);
    }

    public void initDefault() {
        init("...");
    }

    @SuppressLint("ClickableViewAccessibility")
    public void initViews() {
        descriptionLabel = findViewById(R.id.text_description);
        amountLabel = findViewById(R.id.text_amount);
        euroLabel = findViewById(R.id.eurosign);
        descriptionLabel.setOnTouchListener(new View.OnTouchListener() {
            private Handler handler = new Handler();
            private Runnable longPressedRunnable = new Runnable() {
                @Override
                public void run() {
                    isLongPress = true; // Set flag when long press is detected
                    descriptionLabel.setSelected(true); // Start the marquee
                }
            };
            private MotionEvent downEvent;  // Store the down event

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // When finger touches the view, start delay for long press
                        isLongPress = false; // Reset the long press flag
                        downEvent = MotionEvent.obtain(event);  // Copy the down event
                        handler.postDelayed(longPressedRunnable, ViewConfiguration.getLongPressTimeout());
                        break;
                    case MotionEvent.ACTION_UP:
                        // When finger lifts up, stop the marquee
                        descriptionLabel.setSelected(false);
                        handler.removeCallbacks(longPressedRunnable); // Remove the long press delay
                        if (!isLongPress && downEvent != null) {
                            // If it was not a long press, manually perform click on the parent view
                            ((View)v.getParent().getParent().getParent()).performClick();
                        }
                        downEvent = null;  // Clear the down event
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        // When touch event is cancelled, stop the marquee
                        descriptionLabel.setSelected(false);
                        handler.removeCallbacks(longPressedRunnable); // Remove the long press delay
                        downEvent = null;  // Clear the down event
                        break;
                }
                return true;  // Return true to consume the event
            }
        });
    }
    // endregion

    public void refreshUI() {
        descriptionLabel.setText(description);
        if (amount != 0.0f) {
            amountLabel.setText(Util.formatLargeFloatDisplay(amount));
        } else {
            amountLabel.setVisibility(INVISIBLE);
            euroLabel.setVisibility(INVISIBLE);
        }
    }
}
