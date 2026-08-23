package com.privat.pitz.financehelper.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

import androidx.core.graphics.ColorUtils;

import com.privat.pitz.financehelper.R;

import java.time.LocalDate;
import java.time.YearMonth;

// Moved here from core.Util: evaluatePercentageBG needs an Android Context, and createBackground is
// only ever used to build the background it and other UI classes draw, so both belong in the UI layer.
public abstract class PercentageBackground {

    public static GradientDrawable evaluatePercentageBG(float percentage, Context context) {
        LocalDate today = LocalDate.now();
        YearMonth yearMonthObject = YearMonth.of(today.getYear(), today.getMonth());
        int daysInMonth = yearMonthObject.lengthOfMonth();
        float monthProgress = (float) today.getDayOfMonth() / daysInMonth;

        int bgColor;
        if (percentage > monthProgress + 0.1) {
            bgColor = context.getColor(R.color.colorNegative);
        } else if (percentage < monthProgress - 0.1) {
            bgColor = context.getColor(R.color.colorPositive);
        } else {
            bgColor = context.getColor(R.color.colorNeutral);
        }
        return createBackground(bgColor);
    }

    public static GradientDrawable createBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(7); // 7dp rounded corners
        drawable.setColor(ColorUtils.setAlphaComponent(color, (int) (255 * 0.6))); // 60% opacity
        return drawable;
    }
}
