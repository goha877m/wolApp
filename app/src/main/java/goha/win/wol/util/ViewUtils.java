package goha.win.wol.util;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

/** 视图相关的零碎工具。 */
public final class ViewUtils {

    private ViewUtils() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** 圆角矩形背景。 */
    public static GradientDrawable roundRect(int color, float radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    /** 圆形背景，用于状态点。 */
    public static GradientDrawable oval(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    /** 圆角矩形 + 描边。 */
    public static GradientDrawable roundRect(int color, float radiusPx, int strokeWidthPx, int strokeColor) {
        GradientDrawable drawable = roundRect(color, radiusPx);
        drawable.setStroke(strokeWidthPx, strokeColor);
        return drawable;
    }
}
