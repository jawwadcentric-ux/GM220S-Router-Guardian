package com.metawebdesigner.gm220rebooter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayDeque;

/** Lightweight recent-traffic chart. Download is solid; upload is secondary. */
public final class SpeedSparkline extends View {
    private static final int LIMIT = 30;
    private final ArrayDeque<Long> down = new ArrayDeque<>(), up = new ArrayDeque<>();
    private final Paint downPaint = new Paint(Paint.ANTI_ALIAS_FLAG), upPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SpeedSparkline(Context context) { this(context, null); }
    public SpeedSparkline(Context context, AttributeSet attrs) {
        this(context, 0xFF006B59, 0xFFB05D00);
    }

    public SpeedSparkline(Context context, int downColor, int upColor) {
        super(context);
        downPaint.setColor(downColor); downPaint.setStrokeWidth(dp(2.5f)); downPaint.setStyle(Paint.Style.STROKE);
        upPaint.setColor(upColor); upPaint.setStrokeWidth(dp(1.8f)); upPaint.setStyle(Paint.Style.STROKE); upPaint.setAlpha(180);
        setContentDescription("Recent download and upload activity chart");
    }

    public void add(long download, long upload) {
        if (down.size() == LIMIT) { down.removeFirst(); up.removeFirst(); }
        down.addLast(download); up.addLast(upload); invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long max = 1L;
        for (long value : down) max = Math.max(max, value);
        for (long value : up) max = Math.max(max, value);
        drawLine(canvas, down, max, downPaint);
        drawLine(canvas, up, max, upPaint);
    }

    private void drawLine(Canvas canvas, ArrayDeque<Long> values, long max, Paint paint) {
        if (values.size() < 2) return;
        Path path = new Path(); int i = 0; int count = values.size();
        for (long value : values) {
            float x = getPaddingLeft() + i * (getWidth() - getPaddingLeft() - getPaddingRight()) / (float) (count - 1);
            float y = getHeight() - getPaddingBottom() - (value / (float) max) * (getHeight() - getPaddingTop() - getPaddingBottom());
            if (i++ == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        canvas.drawPath(path, paint);
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
