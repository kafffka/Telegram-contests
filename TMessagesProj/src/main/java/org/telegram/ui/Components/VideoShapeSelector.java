package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoShape;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;

import java.util.List;

/**
 * Round video shape picker: icons in their own glass pill next to the camera buttons. Scrolls horizontally
 * when the shapes don't fit and fades the edge that has hidden content (DST_OUT gradient over a layer, the
 * same technique as the emoji search field).
 */
@SuppressLint("ViewConstructor")
public class VideoShapeSelector extends View {

    private static final int[] ORDER = VideoShape.ORDER;
    private static final int COUNT = ORDER.length;

    private final Theme.ResourcesProvider resourcesProvider;
    private final int shadowPadding = dp(6);
    private final int itemWidth = dp(42), itemHeight = dp(44), glyphSize = dp(20);
    private final int chromeHorizontal = shadowPadding + dp(2);
    private final String[] labels = new String[COUNT];
    private final Path[] glyphs = new Path[COUNT];

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fallbackBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lockBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable lockDrawable;
    private final Paint fadeLeftPaint = new Paint(), fadeRightPaint = new Paint();
    private final int fadeWidth = dp(20);
    private final RectF rect = new RectF();
    private final RectF content = new RectF();

    private final AnimatedFloat highlightPosition;
    private final AnimatedFloat[] itemSelected = new AnimatedFloat[COUNT];
    private final AnimatedFloat fadeLeft, fadeRight;
    private final AnimatedFloat lockAlpha;

    private BlurredBackgroundDrawable backgroundDrawable;
    private Utilities.Callback<Integer> onShapeSelected;
    private Utilities.Callback<Integer> onLockedShapeTapped;
    private int selectedIndex;
    private int account = UserConfig.selectedAccount;
    /** Selection that follows a swipe on the preview, between two items; -1 when not dragging. */
    private float fractionalIndex = -1;

    private int shakeIndex = -1;
    private float shakeX;
    private SpringAnimation shakeAnimation;

    private final OverScroller scroller;
    private VelocityTracker velocityTracker;
    private float offset;
    private boolean pendingScrollToSelected = true;
    private float downX, downY, downOffset;
    private boolean dragging, pressed;
    private final int touchSlop, minimumFlingVelocity, maximumFlingVelocity;

    public VideoShapeSelector(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        ViewConfiguration configuration = ViewConfiguration.get(context);
        touchSlop = configuration.getScaledTouchSlop();
        minimumFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        maximumFlingVelocity = configuration.getScaledMaximumFlingVelocity();
        scroller = new OverScroller(context);

        for (int i = 0; i < COUNT; i++) {
            labels[i] = VideoShape.getLabel(ORDER[i]);
            glyphs[i] = new Path();
            buildGlyph(glyphs[i], VideoShape.unitPoints(ORDER[i]), glyphSize);
            itemSelected[i] = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_BACK);
        }
        highlightPosition = new AnimatedFloat(this, 0, 380, CubicBezierInterpolator.EASE_OUT_QUINT);
        fadeLeft = new AnimatedFloat(this, 0, 200, CubicBezierInterpolator.EASE_OUT_QUINT);
        fadeRight = new AnimatedFloat(this, 0, 200, CubicBezierInterpolator.EASE_OUT_QUINT);
        lockAlpha = new AnimatedFloat(this, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);
        lockAlpha.force(isLocked());
        fadeLeftPaint.setShader(new LinearGradient(0, 0, fadeWidth, 0, new int[]{0xffffffff, 0}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        fadeLeftPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        fadeRightPaint.setShader(new LinearGradient(0, 0, fadeWidth, 0, new int[]{0, 0xffffffff}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        fadeRightPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1.5f));
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        fallbackBackgroundPaint.setColor(0x66000000);
        lockDrawable = context.getResources().getDrawable(R.drawable.msg_mini_lock3).mutate();
        lockDrawable.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        accessibilityHelper = new AccessibilityHelper();
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper);
        updateContentDescription();
    }

    private final AccessibilityHelper accessibilityHelper;

    @Override
    protected boolean dispatchHoverEvent(MotionEvent event) {
        if (accessibilityHelper.dispatchHoverEvent(event)) {
            return true;
        }
        return super.dispatchHoverEvent(event);
    }

    /** One virtual item per shape: name, selected state, "locked, Telegram Premium", click to pick. */
    private class AccessibilityHelper extends ExploreByTouchHelper {

        AccessibilityHelper() {
            super(VideoShapeSelector.this);
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            int index = indexAt(x);
            return index >= 0 ? index : INVALID_ID;
        }

        @Override
        protected void getVisibleVirtualViews(List<Integer> ids) {
            for (int i = 0; i < COUNT; i++) {
                ids.add(i);
            }
        }

        @Override
        protected void onPopulateNodeForVirtualView(int id, @NonNull AccessibilityNodeInfoCompat info) {
            final int shape = ORDER[id];
            info.setContentDescription(isLocked(shape) ? LocaleController.formatString(R.string.AccDescrVideoShapeLocked, labels[id]) : labels[id]);
            info.setClassName("android.widget.RadioButton");
            info.setCheckable(true);
            info.setChecked(id == selectedIndex);
            info.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            final float left = content.left - offset + slot(id) * itemWidth;
            final Rect bounds = new Rect((int) Math.max(content.left, left), (int) content.top, (int) Math.min(content.right, left + itemWidth), (int) content.bottom);
            if (bounds.isEmpty()) {
                bounds.set((int) content.left, (int) content.top, (int) content.left + 1, (int) content.top + 1);
            }
            info.setBoundsInParent(bounds);
        }

        @Override
        protected boolean onPerformActionForVirtualView(int id, int action, Bundle arguments) {
            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                selectIndex(id);
                invalidateVirtualView(id);
                return true;
            }
            return false;
        }
    }

    public void setOnShapeSelected(Utilities.Callback<Integer> onShapeSelected) {
        this.onShapeSelected = onShapeSelected;
    }

    /** Called with the shape when a non-premium user taps a locked shape; the selection doesn't change. */
    public void setOnLockedShapeTapped(Utilities.Callback<Integer> onLockedShapeTapped) {
        this.onLockedShapeTapped = onLockedShapeTapped;
    }

    public void setAccount(int account) {
        this.account = account;
        updateContentDescription();
        invalidate();
    }

    private boolean isLocked() {
        return !VideoShape.hasPremium(account);
    }

    /** Whether a shape needs Premium the current account doesn't have. */
    public boolean isLocked(int shape) {
        return shape != VideoShape.CIRCLE && isLocked();
    }

    /** Re-reads premium status: locks fade in or out. */
    public void updateLocks() {
        updateContentDescription();
        invalidate();
    }

    /**
     * Moves the highlight to a fractional position in picker order while a swipe on the preview drags between two
     * shapes, keeping it centred. Pass -1 to stop following; {@link #setShape} then animates from where it is.
     */
    public void setFractionalSelection(float index) {
        if (index < 0) {
            if (fractionalIndex >= 0) {
                fractionalIndex = -1;
                invalidate();
            }
            return;
        }
        fractionalIndex = Utilities.clamp(index, COUNT - 1, 0);
        highlightPosition.force(fractionalIndex);
        if (content.width() > 0) {
            scroller.forceFinished(true);
            offset = Utilities.clamp(slot(fractionalIndex) * itemWidth + itemWidth / 2f - content.width() / 2f, getMaxOffset(), 0);
        }
        invalidate();
    }

    /** Short spring shake of one item, the feedback for a locked shape. */
    public void shake(int shape) {
        shakeIndex = indexOf(shape);
        if (shakeAnimation != null) {
            shakeAnimation.cancel();
        }
        shakeX = 0;
        shakeAnimation = new SpringAnimation(new FloatValueHolder(0))
                .setSpring(new SpringForce(0).setStiffness(600f))
                .setStartVelocity(-dp(6) * 100);
        shakeAnimation.addUpdateListener((animation, value, velocity) -> {
            shakeX = value;
            invalidate();
        });
        shakeAnimation.addEndListener((animation, canceled, value, velocity) -> {
            shakeX = 0;
            shakeIndex = -1;
            invalidate();
        });
        shakeAnimation.start();
        try {
            performHapticFeedback(Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.REJECT : HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignore) {}
    }

    public void setGlassBackground(BlurredBackgroundDrawableViewFactory factory, BlurredBackgroundColorProvider colorProvider) {
        if (factory == null) {
            return;
        }
        backgroundDrawable = factory.create(this, colorProvider);
        backgroundDrawable.setPadding(shadowPadding);
        backgroundDrawable.setRadius(dp(21));
        setBackground(backgroundDrawable);
    }

    public void setShape(int shape, boolean animated) {
        int index = indexOf(shape);
        if (fractionalIndex >= 0) {
            fractionalIndex = -1;
            if (index == selectedIndex) {
                invalidate();
            }
        }
        if (index == selectedIndex) {
            if (!animated) {
                scroller.forceFinished(true);
                pendingScrollToSelected = true;
                invalidate();
            }
            return;
        }
        selectedIndex = index;
        if (!animated) {
            highlightPosition.force(index);
            for (int i = 0; i < COUNT; i++) {
                itemSelected[i].force(i == index);
            }
            pendingScrollToSelected = true;
        } else {
            scrollToSelected(true);
        }
        updateContentDescription();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int available = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE : MeasureSpec.getSize(widthMeasureSpec);
        int minimum = itemWidth * 2 + chromeHorizontal * 2;
        setMeasuredDimension(Math.max(Math.min(minimum, available), Math.min(available, itemWidth * COUNT + chromeHorizontal * 2)), itemHeight + shadowPadding * 2);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        content.set(chromeHorizontal, shadowPadding, w - chromeHorizontal, h - shadowPadding);
        offset = Utilities.clamp(offset, getMaxOffset(), 0);
        pendingScrollToSelected = true;
    }

    private float getMaxOffset() {
        return Math.max(0, itemWidth * COUNT - content.width());
    }

    private void scrollToSelected(boolean animated) {
        if (content.width() <= 0) {
            return;
        }
        float itemLeft = slot(selectedIndex) * itemWidth;
        float target = offset;
        if (!animated) {
            target = itemLeft + itemWidth / 2f - content.width() / 2f;
        } else if (itemLeft - itemWidth * 0.5f < offset) {
            target = itemLeft - itemWidth * 0.5f;
        } else if (itemLeft + itemWidth * 1.5f > offset + content.width()) {
            target = itemLeft + itemWidth * 1.5f - content.width();
        }
        target = Utilities.clamp(target, getMaxOffset(), 0);
        if (target == offset) {
            return;
        }
        scroller.forceFinished(true);
        if (animated) {
            scroller.startScroll((int) offset, 0, (int) (target - offset), 0, 320);
        } else {
            offset = target;
        }
        invalidate();
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            offset = Utilities.clamp(scroller.getCurrX(), getMaxOffset(), 0);
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (pendingScrollToSelected && content.width() > 0) {
            pendingScrollToSelected = false;
            scrollToSelected(false);
        }
        if (backgroundDrawable == null) {
            rect.set(shadowPadding, shadowPadding, getWidth() - shadowPadding, getHeight() - shadowPadding);
            canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, fallbackBackgroundPaint);
        }
        // Same tint as the flip/flash icons next to it (white, multiplied to 40% in light themes).
        final boolean dark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        final int colorUnselected = dark ? Color.WHITE : 0xff666666;
        final int colorSelected = Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider);

        final float left = fadeLeft.set(offset > 1);
        final float right = fadeRight.set(offset < getMaxOffset() - 1);
        final boolean fade = left > 0 || right > 0;
        if (fade) {
            canvas.saveLayerAlpha(content.left, 0, content.right, getHeight(), 255, Canvas.ALL_SAVE_FLAG);
        } else {
            canvas.save();
        }
        canvas.clipRect(content.left, 0, content.right, getHeight());

        final float locks = lockAlpha.set(isLocked());
        final float x0 = content.left - offset, top = content.top;
        final float position = fractionalIndex >= 0 ? fractionalIndex : highlightPosition.set(selectedIndex);
        rect.set(x0 + slot(position) * itemWidth, top, x0 + (slot(position) + 1) * itemWidth, top + itemHeight);
        rect.inset(dp(1), dp(2));
        highlightPaint.setColor(Theme.multAlpha(colorSelected, 0.12f));
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, highlightPaint);

        for (int i = 0; i < COUNT; i++) {
            final float animated = fractionalIndex >= 0
                    ? itemSelected[i].set(Utilities.clamp01(1f - Math.abs(i - fractionalIndex)), true)
                    : itemSelected[i].set(i == selectedIndex);
            final float cx = x0 + (slot(i) + 0.5f) * itemWidth + (i == shakeIndex ? shakeX : 0);
            if (cx + itemWidth < content.left || cx - itemWidth > content.right) {
                continue;
            }
            final float selected = Utilities.clamp01(animated);
            drawGlyph(canvas, i, cx, top + itemHeight / 2f, lerp(1f, 1.08f, animated), selected, ColorUtils.blendARGB(colorUnselected, colorSelected, selected));
            if (locks > 0 && ORDER[i] != VideoShape.CIRCLE) {
                drawLock(canvas, cx + glyphSize * 0.38f, top + itemHeight / 2f + glyphSize * 0.36f, dark, locks);
            }
        }

        if (left > 0) {
            fadeLeftPaint.setAlpha((int) (255 * left));
            canvas.save();
            canvas.translate(content.left, 0);
            canvas.drawRect(0, 0, fadeWidth, getHeight(), fadeLeftPaint);
            canvas.restore();
        }
        if (right > 0) {
            fadeRightPaint.setAlpha((int) (255 * right));
            canvas.save();
            canvas.translate(content.right - fadeWidth, 0);
            canvas.drawRect(0, 0, fadeWidth, getHeight(), fadeRightPaint);
            canvas.restore();
        }
        canvas.restore();
    }

    private void drawLock(Canvas canvas, float cx, float cy, boolean dark, float alpha) {
        final float radius = dp(6.5f) * lerp(0.6f, 1f, alpha);
        lockBackgroundPaint.setColor(dark ? 0x8C000000 : 0xff8e8e93);
        lockBackgroundPaint.setAlpha((int) (lockBackgroundPaint.getAlpha() * alpha));
        canvas.drawCircle(cx, cy, radius, lockBackgroundPaint);
        final int size = (int) (dp(10) * lerp(0.6f, 1f, alpha));
        lockDrawable.setBounds((int) (cx - size / 2f), (int) (cy - size / 2f), (int) (cx + size / 2f), (int) (cy + size / 2f));
        lockDrawable.setAlpha((int) (255 * alpha));
        lockDrawable.draw(canvas);
    }

    private void drawGlyph(Canvas canvas, int index, float cx, float cy, float scale, float fill, int color) {
        canvas.save();
        canvas.translate(cx - glyphSize / 2f * scale, cy - glyphSize / 2f * scale);
        canvas.scale(scale, scale);
        if (fill < 1f) {
            strokePaint.setColor(color);
            strokePaint.setAlpha((int) (Color.alpha(color) * (1f - fill)));
            canvas.drawPath(glyphs[index], strokePaint);
        }
        if (fill > 0f) {
            fillPaint.setColor(color);
            fillPaint.setAlpha((int) (Color.alpha(color) * fill));
            canvas.drawPath(glyphs[index], fillPaint);
        }
        canvas.restore();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            downOffset = offset;
            pressed = true;
            dragging = false;
            scroller.forceFinished(true);
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        } else if (action == MotionEvent.ACTION_MOVE && pressed) {
            if (!dragging && getMaxOffset() > 0 && Math.abs(event.getX() - downX) > touchSlop) {
                dragging = true;
                downX = event.getX();
            }
            if (dragging) {
                offset = Utilities.clamp(downOffset - (event.getX() - downX), getMaxOffset(), 0);
                invalidate();
            } else if (Math.hypot(event.getX() - downX, event.getY() - downY) > touchSlop * 2) {
                pressed = false;
            }
            return true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (pressed && action == MotionEvent.ACTION_UP && !dragging) {
                int index = indexAt(event.getX());
                if (index >= 0) {
                    selectIndex(index);
                }
            } else if (dragging && action == MotionEvent.ACTION_UP) {
                velocityTracker.computeCurrentVelocity(1000, maximumFlingVelocity);
                float velocity = velocityTracker.getXVelocity();
                if (Math.abs(velocity) > minimumFlingVelocity) {
                    scroller.fling((int) offset, 0, (int) -velocity, 0, 0, (int) getMaxOffset(), 0, 0);
                    invalidate();
                }
            }
            pressed = dragging = false;
            velocityTracker.recycle();
            velocityTracker = null;
            return true;
        }
        return pressed;
    }

    private int indexAt(float x) {
        if (x < content.left || x > content.right) {
            return -1;
        }
        int index = (int) Math.floor((x - content.left + offset) / itemWidth);
        return index >= 0 && index < COUNT ? (int) slot(index) : -1;
    }

    private void selectIndex(int index) {
        if (index == selectedIndex) {
            return;
        }
        if (isLocked(ORDER[index])) {
            shake(ORDER[index]);
            if (onLockedShapeTapped != null) {
                onLockedShapeTapped.run(ORDER[index]);
            }
            return;
        }
        selectedIndex = index;
        updateContentDescription();
        try {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignore) {}
        scrollToSelected(true);
        invalidate();
        if (onShapeSelected != null) {
            onShapeSelected.run(ORDER[index]);
        }
    }

    private void updateContentDescription() {
        setContentDescription(LocaleController.getString(R.string.AccDescrVideoShapes) + ", " + labels[selectedIndex]);
        if (accessibilityHelper != null) {
            accessibilityHelper.invalidateRoot();
        }
    }

    /** Visual slot of a picker-order position: the picker runs right to left in RTL (its own inverse). */
    private static float slot(float index) {
        return LocaleController.isRTL ? COUNT - 1 - index : index;
    }

    private static int indexOf(int shape) {
        for (int i = 0; i < COUNT; i++) {
            if (ORDER[i] == shape) {
                return i;
            }
        }
        return 0;
    }

    private static void buildGlyph(Path path, float[] points, float size) {
        path.rewind();
        final int n = points.length / 2;
        path.moveTo(points[0] * size, points[1] * size);
        for (int i = 1; i < n; i++) {
            path.lineTo(points[i * 2] * size, points[i * 2 + 1] * size);
        }
        path.close();
    }
}
