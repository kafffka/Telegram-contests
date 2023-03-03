package org.telegram.ui.Components.voip;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public class VoIPBackground extends View {
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final android.graphics.Rect rect = new android.graphics.Rect();
    private Bitmap bitmap;
    private VoIPColorsController voIPColorsController;

    public VoIPBackground(Context context) {
        super(context);
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, rect, paint);
            voIPColorsController.invalidate();
        }
    }

    public void setVoIPColorsController(VoIPColorsController voIPColorsController) {
        this.voIPColorsController = voIPColorsController;
        this.bitmap = voIPColorsController.getBitmap();
    }
}
