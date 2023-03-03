package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;

public class VoIPToggleButton2 extends FrameLayout {
    private static final long ANIMATION_DURATION = 200L;
    private static final int ANIMATION_FLAG_NONE = 0;
    private static final int ANIMATION_FLAG_ICON = 1 << 1;
    private static final int ANIMATION_FLAG_ICON_COLOR = 1 << 2;
    private static final int ANIMATION_FLAG_BACKGROUND_COLOR = 1 << 3;
    private static final int ANIMATION_FLAG_TEXT = 1 << 4;
    private static final int ANIMATION_FLAG_SIZE = 1 << 5;

    private final Paint bigCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final TextView[] textView = new TextView[2];

    private float replaceProgress;
    private ValueAnimator replaceAnimator;
    private int prevBackgroundColor;
    private int currentBackgroundColor;
    private String currentText;
    private final float size;
    private final float iconSize;
    private final RLottieImageView lottieImageView;
    private int iconRes;
    private Drawable prevDrawable;
    public int animationDelay;
    private int iconColor;
    private int prevIconColor;
    private boolean isLottieIcon;
    private String[] layers;
    private int animationFlag;
    private Drawable rippleDrawable;
    private boolean isRepeatAnimation = false;
    private boolean checked;

    // waves
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean isShowWaves = false;
    private float smallRadius;
    private boolean expandSmallRadius = true;

    public VoIPToggleButton2(@NonNull Context context) {
        this(context, 52f, 48);
    }

    public VoIPToggleButton2(@NonNull Context context, float size, int iconSize) {
        super(context);
        this.size = size;
        this.iconSize = iconSize;
        setWillNotDraw(false);
        bigCirclePaint.setStyle(Paint.Style.FILL);

        FrameLayout textLayoutContainer = new FrameLayout(context);
        addView(textLayoutContainer);

        for (int i = 0; i < 2; i++) {
            TextView textView = new TextView(context);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            textView.setTextColor(Color.WHITE);
            textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            textLayoutContainer.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, size + 4, 0, 0));
            this.textView[i] = textView;
        }
        textView[1].setVisibility(View.GONE);

        lottieImageView = new RLottieImageView(context);
        addView(lottieImageView, LayoutHelper.createFrame(iconSize, iconSize, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, (size - iconSize) / 2, 0, 0));
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (rippleDrawable != null) {
            rippleDrawable.setState(getDrawableState());
        }
    }
    @Override
    public boolean verifyDrawable(Drawable drawable) {
        return rippleDrawable == drawable || super.verifyDrawable(drawable);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (rippleDrawable != null) {
            rippleDrawable.jumpToCurrentState();
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (enabled) {
            textView[0].setTextColor(Color.WHITE);
            textView[1].setTextColor(Color.WHITE);
        } else {
            textView[0].setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.5f)));
            textView[1].setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.5f)));
        }
    }

    public void setIsRepeatAnimation(boolean isRepeatAnimation) {
        this.isRepeatAnimation = isRepeatAnimation;

    }

    public void stopLottieAnimation() {
        if (isLottieIcon && lottieImageView.isPlaying()) {
            lottieImageView.stopAnimation();
        }
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    public boolean getChecked() {
        return checked;
    }

    public void updateBackgroundColor(int color) {
        if (currentBackgroundColor != color && replaceAnimator == null) {
            currentBackgroundColor = color;
            bigCirclePaint.setColor(currentBackgroundColor);
            invalidate();
        }
    }

    public void updateIconColor(int color) {
        if (iconColor != color && replaceAnimator == null) {
            setIconColor(color);
            invalidate();
        }
    }

    public void setShowWaves(boolean isShowWaves) {
        this.isShowWaves = isShowWaves;
    }

    public void setLottieData(int iconRes, int iconColor, int backgroundColor, String text, boolean animated, String[] layers) {
        this.layers = layers;
        this.isLottieIcon = true;
        setData(iconRes, iconColor, backgroundColor, text, animated);
    }

    public void setStaticData(int iconRes, int iconColor, int backgroundColor, String text, boolean animated) {
        if (isLottieIcon && lottieImageView.getAnimatedDrawable() != null) {
            lottieImageView.clearAnimationDrawable();
            lottieImageView.clearLayerColors();
        }

        this.isLottieIcon = false;
        setData(iconRes, iconColor, backgroundColor, text, animated);
    }

    public boolean needAnimateToWideButton;
    private int startX;
    private int startY;
    private ValueAnimator animatorToWide;
    private float animateToWideProgress;
    private RectF wideButtonRect;
    private TextView buttonText;

    public void setTextData(int backgroundColor, String text, int startX, int startY) {
        if (animatorToWide != null) {
            return;
        }
        if (getVisibility() != View.VISIBLE) {
            setVisibility(View.VISIBLE);
        }

        this.startX = startX;
        this.startY = startY;

        this.prevBackgroundColor = currentBackgroundColor;
        this.currentBackgroundColor = backgroundColor;
        lottieImageView.setLayoutParams(LayoutHelper.createFrame((int) iconSize, (int) iconSize, Gravity.START, 0, (size - iconSize) / 2, 0, 0));
        lottieImageView.setX(startX - AndroidUtilities.dp(iconSize) / 2f);
        textView[0].setVisibility(View.GONE);

        float padding = AndroidUtilities.dp(24);
        wideButtonRect = new RectF();

        if (buttonText == null) {
            buttonText = new TextView(getContext());
            buttonText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            buttonText.setGravity(Gravity.CENTER);
            buttonText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            addView(buttonText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        }
        buttonText.setTextColor(Color.BLACK);
        buttonText.setText(text);
        buttonText.setAlpha(0f);

        animatorToWide = ValueAnimator.ofFloat(0f, 1f);
        animatorToWide.addUpdateListener(animation -> {
            animateToWideProgress = (float) animation.getAnimatedFraction();
            int maxWidth = getMeasuredWidth();
            float rightWidth = maxWidth - startX;
            float left = startX - AndroidUtilities.dp(size) / 2f - (startX - AndroidUtilities.dp(size) / 2f - padding) * animateToWideProgress;
            float right = startX + AndroidUtilities.dp(size) / 2f + (rightWidth - AndroidUtilities.dp(size) / 2f - padding) * animateToWideProgress;
            wideButtonRect.set(left, 0f, right, (float) getMeasuredHeight());
            if (animateToWideProgress <= 0.5) {
                lottieImageView.setAlpha(1 - animateToWideProgress * 2);
                lottieImageView.setTranslationX(startX - AndroidUtilities.dp(iconSize) / 2f - AndroidUtilities.dp(42) * animateToWideProgress);
            } else {
                float buttonProgress = (animateToWideProgress - 0.5f) * 2;
                buttonText.setAlpha(buttonProgress);
                float buttonX = buttonText.getMeasuredWidth() * 2;
                buttonText.setTranslationX(buttonX * (1 - buttonProgress));
            }
            invalidate();
        });
        animatorToWide.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                lottieImageView.setVisibility(View.GONE);
                textView[0].setVisibility(View.GONE);
            }
        });

        animatorToWide.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animatorToWide.setDuration(300);
        animatorToWide.start();
    }

    public void setData(int iconRes, int iconColor, int backgroundColor, String text, boolean animated) {
        FileLog.d("LogTest2  ---------------- " + text + " ----------------");
        FileLog.d("LogTest animated: " + animated);
        FileLog.d("LogTest is lottie: " + isLottieIcon);
        FileLog.d("LogTest iconRes: " + iconRes);
        FileLog.d("LogTest iconColor: " + iconColor);

        if (getVisibility() != View.VISIBLE && !needAnimateToWideButton) {
            FileLog.d("LogTest2 change state to visible");
            animated = false;
            setVisibility(View.VISIBLE);
        }

        if (this.iconRes == iconRes && this.iconColor == iconColor && currentBackgroundColor == backgroundColor && (currentText != null && currentText.equals(text))) {
            FileLog.d("LogTest data is the same");
            return;
        }

        if (rippleDrawable == null) {
            if (Color.alpha(backgroundColor) == 255 && AndroidUtilities.computePerceivedBrightness(backgroundColor) > 0.5) {
                rippleDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(size), 0, ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.1f)));
                rippleDrawable.setCallback(this);
            } else {
                rippleDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(size), 0, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.3f)));
                rippleDrawable.setCallback(this);
            }
        }

        animationFlag = ANIMATION_FLAG_NONE;

        if (animated) {
            boolean replaceBackgroundColor = this.currentBackgroundColor != backgroundColor;
            boolean replaceDrawable = this.iconRes != iconRes;
            boolean replaceIconColor = this.iconColor != iconColor;
            boolean replaceText = currentText == null || !currentText.equals(text);

            if (replaceBackgroundColor) {
                FileLog.d("LogTest animation: ANIMATION_FLAG_BACKGROUND_COLOR");
                animationFlag |= ANIMATION_FLAG_BACKGROUND_COLOR;
            }
            if (replaceDrawable) {
                FileLog.d("LogTest animation: ANIMATION_FLAG_ICON");
                animationFlag |= ANIMATION_FLAG_ICON;
            }
            if (replaceBackgroundColor && replaceDrawable) {
                FileLog.d("LogTest animation: ANIMATION_FLAG_SIZE");
                animationFlag |= ANIMATION_FLAG_SIZE;
            }
            if (replaceIconColor) {
                FileLog.d("LogTest animation: ANIMATION_FLAG_ICON_COLOR");
                animationFlag |= ANIMATION_FLAG_ICON_COLOR;
            }
            if (replaceText) {
                FileLog.d("LogTest animation: ANIMATION_FLAG_TEXT");
                animationFlag |= ANIMATION_FLAG_TEXT;
            }
        }

        this.prevBackgroundColor = this.currentBackgroundColor;
        this.prevIconColor = this.iconColor;

        this.currentBackgroundColor = backgroundColor;
        this.iconColor = iconColor;
        this.iconRes = iconRes;
        this.currentText = text;

        if (lottieImageView != null) {
            if (isLottieIcon && lottieImageView.getAnimatedDrawable() != null) {
                RLottieDrawable animatedDrawable = lottieImageView.getAnimatedDrawable();
                animatedDrawable.setCurrentFrame(animatedDrawable.getFramesCount() - 1);
                prevDrawable = lottieImageView.getAnimatedDrawable();
            } else {
                prevDrawable = lottieImageView.getDrawable();
            }
        }

        applyAnimation();
    }

    private void applyAnimation() {
        if (replaceAnimator != null) {
            replaceAnimator.cancel();
            FileLog.d("LogTest cancel animator");
        }
        FileLog.d("LogTest apply animation, flag = " + animationFlag);

        if (animationFlag == ANIMATION_FLAG_NONE) {
            bigCirclePaint.setColor(currentBackgroundColor);

            lottieImageView.setScaleX(1f);
            lottieImageView.setScaleY(1f);
            setCurrentIcon();

            textView[0].setText(currentText);
            textView[0].setAlpha(1f);
            textView[0].setScaleX(1f);
            textView[0].setScaleY(1f);

            replaceProgress = 0f;
            invalidate();
        } else if (animationFlag == ANIMATION_FLAG_ICON) {
            bigCirclePaint.setColor(currentBackgroundColor);

            lottieImageView.setScaleX(1f);
            lottieImageView.setScaleY(1f);
            if (isLottieIcon) {
                setCurrentLottieIcon(iconRes, layers, false);
                startLottieAnimation();
            } else {
                setCurrentStaticIcon(iconRes);
            }

            textView[0].setAlpha(1f);
            textView[0].setScaleX(1f);
            textView[0].setScaleY(1f);
            textView[0].setText(currentText);

            animationFlag = ANIMATION_FLAG_NONE;
            replaceProgress = 0f;
            invalidate();
        } else if (
            (animationFlag & ANIMATION_FLAG_TEXT) != 0
                || (animationFlag & ANIMATION_FLAG_BACKGROUND_COLOR) != 0
                || (animationFlag & ANIMATION_FLAG_ICON) != 0
                || (animationFlag & ANIMATION_FLAG_ICON_COLOR) != 0
                || (animationFlag & ANIMATION_FLAG_SIZE) != 0
        ) {
            runReplaceAnimator();
        }
    }

    private void runReplaceAnimator() {
        if ((animationFlag & ANIMATION_FLAG_SIZE) != 0) {
            bigCirclePaint.setColor(prevBackgroundColor);
            smallCirclePaint.setColor(currentBackgroundColor);
        }

        replaceAnimator = ValueAnimator.ofFloat(0, 1f);
        boolean animateText = !textView[0].getText().toString()
            .equals(currentText) && (animationFlag & ANIMATION_FLAG_TEXT) != 0;
        if (animateText) {
            textView[1].setText(currentText);
            textView[1].setVisibility(View.VISIBLE);
            textView[1].setAlpha(0);
            textView[1].setScaleX(0);
            textView[1].setScaleY(0);
        }
        replaceAnimator.addUpdateListener(valueAnimator -> {
            replaceProgress = (float) valueAnimator.getAnimatedValue();
            FileLog.d("LogTest replaceProgress " + replaceProgress);
            invalidate();
            if (animateText) {
                textView[0].setAlpha(1f - replaceProgress);
                textView[0].setScaleX(1f - replaceProgress);
                textView[0].setScaleY(1f - replaceProgress);

                textView[1].setAlpha(replaceProgress);
                textView[1].setScaleX(replaceProgress);
                textView[1].setScaleY(replaceProgress);
            }

            if ((animationFlag & ANIMATION_FLAG_SIZE) != 0) {
                FileLog.d("LogTest start scaling");
                lottieImageView.setScaleX(replaceProgress);
                lottieImageView.setScaleY(replaceProgress);
                FileLog.d("LogTest end scaling");
            } else if ((animationFlag & ANIMATION_FLAG_ICON_COLOR) != 0) {
                FileLog.d("LogTest start changing animation");
                int color = ColorUtils.blendARGB(prevIconColor, iconColor, replaceProgress);
                setIconColor(color);
            } else {
                FileLog.d("LogTest WOW, animation flag = " + animationFlag);
            }
        });
        replaceAnimator.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                FileLog.d("LogTest onAnimationCancel");
            }

            @Override
            public void onAnimationPause(Animator animation) {
                super.onAnimationPause(animation);
                FileLog.d("LogTest onAnimationPause");
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                replaceAnimator = null;
                prevDrawable = null;
                bigCirclePaint.setColor(currentBackgroundColor);
                if (animateText) {
                    TextView tv = textView[0];
                    textView[0] = textView[1];
                    textView[1] = tv;
                    textView[1].setVisibility(View.GONE);

                    textView[0].setAlpha(1f);
                    textView[0].setScaleX(1f);
                    textView[0].setScaleY(1f);
                }
                FileLog.d("LogTest finish animation, flags: " + animationFlag);
                if ((animationFlag & ANIMATION_FLAG_SIZE) != 0) {
                    lottieImageView.setScaleX(1f);
                    lottieImageView.setScaleY(1f);
                    FileLog.d("LogTest finish scaling");
                } else if ((animationFlag & ANIMATION_FLAG_ICON_COLOR) != 0) {
                    setIconColor(iconColor);
                    FileLog.d("LogTest finish change color");
                }
                replaceProgress = 0f;
                animationFlag = ANIMATION_FLAG_NONE;
                invalidate();
            }
        });


        if ((animationFlag & ANIMATION_FLAG_SIZE) != 0) {
            if (isLottieIcon) {
                if ((animationFlag & ANIMATION_FLAG_ICON) != 0) {
                    FileLog.d("LogTest run delay current lottie icon");
                    setCurrentLottieIcon(iconRes, layers, false);
                    AndroidUtilities.runOnUIThread(this::startLottieAnimation, ANIMATION_DURATION - 50);
                } else {
                    FileLog.d("LogTest set current lottie icon");
                    setCurrentLottieIcon(iconRes, layers, true);
                }
            } else {
                FileLog.d("LogTest set current static icon");
                setCurrentStaticIcon(iconRes);
            }
        }

        if (((animationFlag & ANIMATION_FLAG_ICON_COLOR) != 0) && (animationFlag & ANIMATION_FLAG_SIZE) == 0) {
            setIconColor(prevIconColor);
            FileLog.d("LogTest set prev icon color");
        }

        if (((animationFlag & ANIMATION_FLAG_ICON) != 0) && ((animationFlag & ANIMATION_FLAG_SIZE) == 0)) {
            if (isLottieIcon) {
                setCurrentLottieIcon(iconRes, layers, false);
                AndroidUtilities.runOnUIThread(this::startLottieAnimation, ANIMATION_DURATION - 50);
            } else {
                setCurrentStaticIcon(iconRes);
            }
        }
        replaceAnimator.setDuration(ANIMATION_DURATION).start();
        invalidate();
    }

    public void startLottieAnimation() {
        FileLog.d("LogTest startLottieAnimation");
//        if (isAttachedToWindow()) {
        if (getVisibility() == View.VISIBLE && !lottieImageView.isPlaying()) {
            FileLog.d("LogTest startLottieAnimation inside");
            lottieImageView.playAnimation();
        }
//        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        FileLog.d("LogTest onAttachedToWindow");
    }

    @Override
    protected void onDetachedFromWindow() {
        FileLog.d("LogTest onDetachedFromWindow");
        super.onDetachedFromWindow();
    }

    private void setCurrentIcon() {
        if (isLottieIcon) {
            setCurrentLottieIcon(iconRes, layers, true);
        } else {
            setCurrentStaticIcon(iconRes);
        }
    }

    private void setCurrentLottieIcon(int iconRes, String[] layers, boolean lastFrame) {
        FileLog.d("LogTest setCurrentLottieIcon");
        lottieImageView.setPadding(0, 0, 0, 0);
        lottieImageView.setAutoRepeat(isRepeatAnimation);
        lottieImageView.setAnimation(iconRes, (int) iconSize, (int) iconSize);
        if (lastFrame && !isRepeatAnimation) {
            lottieImageView.getAnimatedDrawable()
                .setCurrentFrame(lottieImageView.getAnimatedDrawable().getFramesCount() - 1);
        }
        if (layers != null) {
            for (String layer : layers) {
                lottieImageView.setLayerColor(layer, iconColor);
            }
        }
    }

    private void setCurrentStaticIcon(int iconRes) {
        lottieImageView.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
        lottieImageView.setImageResource(iconRes);
        lottieImageView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
    }

    private void setIconColor(int color) {
        if (isLottieIcon) {
            if (layers != null) {
                for (String layer : layers) {
                    lottieImageView.setLayerColor(layer, color);
                }
            }
        } else {
            lottieImageView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        }
    }

    public void setTextColor(int color) {
        if (needAnimateToWideButton) {
            if (buttonText != null) {
                buttonText.setTextColor(color);
            }
        } else {
            textView[0].setTextColor(color);
        }
        invalidate();
    }

    public void setCheckable(boolean checkable) {
    }

    public void setCheckableForAccessibility(boolean checkableForAccessibility) {
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isShowWaves) {
            if (expandSmallRadius) {
                smallRadius += AndroidUtilities.dp(2) * 0.06f;
                if (smallRadius > AndroidUtilities.dp(10)) {
                    smallRadius = AndroidUtilities.dp(10);
                    expandSmallRadius = false;
                }
            } else {
                smallRadius -= AndroidUtilities.dp(2) * 0.06f;
                if (smallRadius < 4) {
                    smallRadius = 4;
                    expandSmallRadius = true;
                }
            }
            invalidate();
        }


        float cx = getWidth() / 2f;
        float cy = AndroidUtilities.dp(size) / 2f;
        if (needAnimateToWideButton) {
            cx = startX;
            cy = startY;
        }

        if (animateToWideProgress > 0) {
            bigCirclePaint.setColor(Color.RED);
            canvas.drawLine(startX, 0, startX, getMeasuredHeight(), bigCirclePaint);
            int color = ColorUtils.blendARGB(prevBackgroundColor, currentBackgroundColor, animateToWideProgress);
            bigCirclePaint.setColor(color);

            int radiusStart = AndroidUtilities.dp(iconSize) / 2;
            int radiusEnd = AndroidUtilities.dp(6);
            float radius = (radiusEnd * animateToWideProgress) + (radiusStart * (1 - animateToWideProgress));
            canvas.drawRoundRect(wideButtonRect, radius, radius, bigCirclePaint);
            return;
        }

        if (isShowWaves) {
            wavePaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, 32));
            canvas.drawCircle(cx, cy, cy + smallRadius, wavePaint);

            wavePaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, 16));
            canvas.drawCircle(cx, cy, cy + smallRadius * 1.8f, wavePaint);
        }

        float bigCircleSize = AndroidUtilities.dp(size);

        if (animationFlag == ANIMATION_FLAG_BACKGROUND_COLOR) {
            if (rippleDrawable == null) {
                rippleDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(size), 0, Color.BLACK);
                rippleDrawable.setCallback(this);
            }
            float bigCircleRadius = bigCircleSize / 2f;
            rippleDrawable.setBounds((int) (cx - bigCircleRadius), (int) (cy - bigCircleRadius), (int) (cx + bigCircleRadius), (int) (cy + bigCircleRadius));
            rippleDrawable.draw(canvas);
        }

        if ((animationFlag & ANIMATION_FLAG_SIZE) != 0) {
            float bigCircleProgress = (1f - replaceProgress * 0.5f);
            bigCircleSize *= bigCircleProgress;
        } else if (animationFlag == ANIMATION_FLAG_BACKGROUND_COLOR) {
            int color = ColorUtils.blendARGB(prevBackgroundColor, currentBackgroundColor, replaceProgress);
            bigCirclePaint.setColor(color);
        }

        canvas.drawCircle(cx, cy, bigCircleSize / 2, bigCirclePaint);
        if (prevDrawable != null && ((animationFlag & ANIMATION_FLAG_SIZE) != 0)) {
            float iconProgress = (1f - replaceProgress * 0.3f);
            int iconRadius = (int) (AndroidUtilities.dp(iconSize) / 2f);
            canvas.save();
            canvas.scale(iconProgress, iconProgress, cx, cy);
            prevDrawable.setBounds((int) (cx - iconRadius), (int) (cy - iconRadius), (int) (cx + iconRadius), (int) (cy + iconRadius));
            prevDrawable.draw(canvas);
            canvas.restore();
        }
        if (replaceProgress > 0.25f && ((animationFlag & ANIMATION_FLAG_SIZE) != 0)) {
            float smallCircleSize = AndroidUtilities.dp(size) * (replaceProgress - 0.25f) / 0.75f;
            canvas.drawCircle(cx, cy, smallCircleSize / 2, smallCirclePaint);
        }
    }
}
