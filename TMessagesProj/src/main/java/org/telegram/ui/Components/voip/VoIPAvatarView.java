package org.telegram.ui.Components.voip;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.GroupCallUserCell;
import org.telegram.ui.Components.AvatarDrawable;

@SuppressLint("ViewConstructor")
public class VoIPAvatarView extends View {

    public static final int SIZE = 132;
    public static final int WAVE_BIG_MAX = SIZE + 60;
    public static final int WAVE_MAX_RADIUS = WAVE_BIG_MAX / 2;
    private static final long TO_PAUSE_DURATION = 500;
    private final ImageReceiver imageReceiver;
    private final Interpolator pulsingInterpolator;
    private long lastUpdateTime;
    private float currentProgressTime;
    private boolean isPulsing;
    private boolean stopPulsingRequested;
    private float pulsingValue;
    private boolean risingCircleLength;
    private boolean isWaving;
    private final GroupCallUserCell.AvatarWavesDrawable avatarWavesDrawable;
    private float progressToPause;
    private boolean animateToPause;
    private boolean isPaused;

    public VoIPAvatarView(@NonNull Context context, TLRPC.User user) {
        super(context);
        AvatarDrawable avatarDrawable = new AvatarDrawable(user);
        imageReceiver = new ImageReceiver(this);
        imageReceiver.setImage(ImageLocation.getForUser(user, ImageLocation.TYPE_BIG), "130_130", avatarDrawable, null, null, 0);
        imageReceiver.setRoundRadius(AndroidUtilities.dp(SIZE / 2f));

        pulsingInterpolator = new AccelerateDecelerateInterpolator();
        pulsingValue = 1f;
        isWaving = true;

        avatarWavesDrawable = new GroupCallUserCell.AvatarWavesDrawable(AndroidUtilities.dp(SIZE / 2f + 2), AndroidUtilities.dp(SIZE / 2f + 13), AndroidUtilities.dp(SIZE / 2f + 14), AndroidUtilities.dp(SIZE / 2f + 26));
        avatarWavesDrawable.setUseTimeRelatedAmplitude(true);
        avatarWavesDrawable.setColors(ColorUtils.setAlphaComponent(Color.WHITE, 36), ColorUtils.setAlphaComponent(Color.WHITE, 20));
        avatarWavesDrawable.setZoomPositive();
        setAmplitude(0.5f);
    }

    public void setIsPulsing(boolean isPulsing) {
        if (this.isPulsing == isPulsing) {
            return;
        }
        if (isPulsing) {
            if (!stopPulsingRequested) {
                currentProgressTime = 0f;
            }
            stopPulsingRequested = false;
            risingCircleLength = false;
            lastUpdateTime = System.currentTimeMillis();
            this.isPulsing = true;
        } else {
            stopPulsingRequested = true;
        }
        invalidate();
    }

    public void setIsWaving(boolean isWaving) {
        this.isWaving = isWaving;
        avatarWavesDrawable.setShowWaves(isWaving, this);
        invalidate();
    }

    public void setAmplitude(float amplitude) {
        if (!isPaused && !animateToPause) {
            avatarWavesDrawable.setAmplitude(amplitude * 15f);
        }
    }

    public void resume() {
        if (isPaused || animateToPause) {
            isPaused = false;
            animateToPause = false;
            invalidate();
        }
    }

    public void pause() {
        if (!animateToPause && !isPaused) {
            avatarWavesDrawable.setAmplitude(0);
            animateToPause = true;
            progressToPause = 0f;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getVisibility() != VISIBLE) {
            return;
        }
        long dt = System.currentTimeMillis() - lastUpdateTime;

        if (isPulsing) {
            currentProgressTime += dt;
            float pulsingAnimationTime = 750f;
            if (currentProgressTime >= pulsingAnimationTime) {
                currentProgressTime = pulsingAnimationTime;
            }
            if (currentProgressTime == pulsingAnimationTime) {
                risingCircleLength = !risingCircleLength;
                currentProgressTime = 0;
                if (stopPulsingRequested && !risingCircleLength) {
                    isPulsing = false;
                    stopPulsingRequested = false;
                }
            }
            float interpolatorValue = pulsingInterpolator.getInterpolation(currentProgressTime / pulsingAnimationTime) * 0.05f;
            if (risingCircleLength) {
                pulsingValue = 0.95f + interpolatorValue;
            } else {
                pulsingValue = 1f - interpolatorValue;
            }
        } else {
            pulsingValue = 1f;
        }

        if (animateToPause) {
            progressToPause += 16f / TO_PAUSE_DURATION;
            if (progressToPause >= 1) {
                animateToPause = false;
                isPaused = true;
            }
        }

        float cx = getMeasuredWidth() / 2f;
        float imageSize = AndroidUtilities.dp(SIZE);

        if (isPulsing) {
            canvas.save();
            canvas.scale(pulsingValue, pulsingValue, cx, cx);
        }

        if (isWaving) {
            if (!isPaused) {
                avatarWavesDrawable.update();
            }
            avatarWavesDrawable.draw(canvas, cx, cx, this);
        }

        imageReceiver.setImageCoords(cx - imageSize / 2, cx - imageSize / 2, imageSize, imageSize);
        imageReceiver.draw(canvas);

        if (isPulsing) {
            canvas.restore();
        }

        lastUpdateTime = System.currentTimeMillis();
        if (!isPaused && (isWaving | isPulsing)) {
            invalidate();
        }
    }
}
