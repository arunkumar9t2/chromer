package arun.com.chromer.webheads.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.ColorInt;
import android.util.AttributeSet;
import android.view.View;

import arun.com.chromer.preferences.manager.Preferences;
import arun.com.chromer.util.Util;

/**
 * A simple circle view that fills the canvas with a circle fill color. Padding is given to pre L
 * devices to accommodate shadows if needed.
 */
public class CircleView extends View {
    /**
     * Paint used to draw the circle background
     */
    final Paint mBgPaint;

    public CircleView(Context context) {
        this(context, null, 0);
    }

    public CircleView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBgPaint.setStyle(Paint.Style.FILL);
        mBgPaint.setColor(Preferences.webHeadColor(getContext()));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(getMeasuredWidth(), getMeasuredWidth());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float outerRadius;
        if (Util.isLollipopAbove()) {
            outerRadius = getMeasuredWidth() / 2;
            canvas.drawCircle(getMeasuredWidth() / 2,
                    getMeasuredWidth() / 2,
                    outerRadius,
                    mBgPaint);
        } else {
            outerRadius = (float) (getMeasuredWidth() / 2.4);
            canvas.drawCircle(getMeasuredWidth() / 2,
                    getMeasuredWidth() / 2,
                    outerRadius,
                    mBgPaint);
        }
    }

    public void setColor(@ColorInt int color) {
        mBgPaint.setColor(color);
        invalidate();
    }
}