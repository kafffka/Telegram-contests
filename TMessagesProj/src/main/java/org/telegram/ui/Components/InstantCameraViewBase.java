package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoShape;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.utils.settings.SharedSettings;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;

/** Common UI contract implemented by the legacy and Camera2 round-video views. */
public abstract class InstantCameraViewBase extends FrameLayout {

    /** Receives camera visibility changes used by ChatActivity animations. */
    public interface AnimationCallback {
        void onAnimation(boolean open, boolean fromPaused);
    }

    /** Receives normalized trim changes initiated by the camera ring. */
    public interface TrimCallback {
        void onTrimChanged(float start, float end);
    }

    /** Receives recording UI ticks synchronized with camera preview frames. */
    public interface RecordingUiFrameCallback {
        void onActiveChanged(boolean active);
        void onFrame(long durationMs);
    }

    private AnimationCallback animationCallback;
    private TrimCallback trimCallback;
    private RecordingUiFrameCallback recordingUiFrameCallback;
    private boolean recordingUiFrameClockActive;

    /** Mask of the preview; render-only, the recorded video stays square. */
    protected final VideoShapeDrawer videoShapeDrawer = new VideoShapeDrawer(this::onVideoShapeUpdate);
    private VideoShapeSelector videoShapeStrip;
    private LinearLayout controlsRow;
    private final int videoShapeAccount = UserConfig.selectedAccount;
    private Utilities.Callback<Integer> onLockedVideoShapeTapped;
    /** Locked shape the user tapped; selected if they get Premium while the camera is open. */
    private int pendingLockedShape = -1;
    private boolean lastHasPremium;

    private final NotificationCenter.NotificationCenterDelegate premiumObserver = (id, account, args) -> {
        if (id == NotificationCenter.currentUserPremiumStatusChanged) {
            onPremiumStatusChanged();
        }
    };

    protected InstantCameraViewBase(Context context) {
        super(context);
        videoShapeDrawer.setLive(true);
        ViewConfiguration configuration = ViewConfiguration.get(context);
        swipeSlop = configuration.getScaledTouchSlop();
        minimumFlingVelocity = configuration.getScaledMinimumFlingVelocity() * 4;
        maximumFlingVelocity = configuration.getScaledMaximumFlingVelocity();
        lastHasPremium = VideoShape.hasPremium(videoShapeAccount);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(videoShapeAccount).addObserver(premiumObserver, NotificationCenter.currentUserPremiumStatusChanged);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(videoShapeAccount).removeObserver(premiumObserver, NotificationCenter.currentUserPremiumStatusChanged);
        cancelSwipeHint();
        if (swipeVelocity != null) {
            swipeVelocity.recycle();
            swipeVelocity = null;
        }
    }

    /** Host opens the Premium sheet when a non-premium user picks a locked shape (tap or swipe). */
    public final void setOnLockedVideoShapeTapped(Utilities.Callback<Integer> callback) {
        onLockedVideoShapeTapped = callback;
    }

    private void onLockedShape(int shape) {
        pendingLockedShape = shape;
        if (onLockedVideoShapeTapped != null) {
            onLockedVideoShapeTapped.run(shape);
        }
    }

    /** Premium gained: the locks fade out and a shape tapped while locked is selected. Lost: back to circle. */
    private void onPremiumStatusChanged() {
        final boolean premium = VideoShape.hasPremium(videoShapeAccount);
        if (videoShapeStrip != null) {
            videoShapeStrip.updateLocks();
        }
        if (premium == lastHasPremium) {
            return;
        }
        lastHasPremium = premium;
        if (premium) {
            if (pendingLockedShape >= 0 && getVisibility() == VISIBLE) {
                onShapePicked(pendingLockedShape);
                if (videoShapeStrip != null) videoShapeStrip.setShape(pendingLockedShape, true);
            }
        } else if (videoShapeDrawer.getShape() != VideoShape.CIRCLE) {
            cancelShapeSwipe();
            setVideoShape(VideoShape.CIRCLE, true);
        }
        pendingLockedShape = -1;
    }

    /** Returns the shape the preview is masked with and the sent message will use. */
    public final int getVideoShape() {
        return videoShapeDrawer.getShape();
    }

    /** Changes the preview mask, morphing from the current outline when animated. */
    public final void setVideoShape(int shape, boolean animated) {
        cancelShapeSwipe();
        videoShapeDrawer.setShape(shape, animated);
        if (videoShapeStrip != null) videoShapeStrip.setShape(shape, animated);
        onVideoShapeUpdate();
    }

    private boolean previewPlaybackHeld;

    /**
     * Holds the recorded-video preview paused while something covers it (the Premium sheet), and resumes it on
     * release. Also applies to a preview that only starts after the hold, e.g. right after pausing a recording.
     */
    public final void setPreviewPlaybackHeld(boolean held) {
        if (previewPlaybackHeld == held) return;
        previewPlaybackHeld = held;
        changeVideoPreviewState(held ? 1 : 0, 0);
    }

    protected final boolean isPreviewPlaybackHeld() {
        return previewPlaybackHeld;
    }

    /** Shape the message is sent with; circle if the account can't use the selected one. */
    public final int getVideoShapeForSend() {
        return VideoShape.forSend(videoShapeAccount, videoShapeDrawer.getShape());
    }

    private void onShapePicked(int shape) {
        cancelShapeSwipe();
        VideoShape.saveToPrefs(shape);
        videoShapeDrawer.setShape(shape, true);
        onVideoShapeUpdate();
    }

    /** Called on every morph / live frame and when the mask settles; invalidate the masked views here. */
    protected abstract void onVideoShapeUpdate();

    /**
     * Bottom controls row: the camera buttons pill followed by the shape picker in its own scrollable glass
     * pill. Alpha animations of the returned row apply to both.
     */
    protected final LinearLayout createControlsRow(View cameraButtons, Theme.ResourcesProvider resourcesProvider) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(cameraButtons, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 56));
        videoShapeStrip = new VideoShapeSelector(getContext(), resourcesProvider);
        videoShapeStrip.setAccount(videoShapeAccount);
        videoShapeStrip.setOnShapeSelected(this::onShapePicked);
        videoShapeStrip.setOnLockedShapeTapped(this::onLockedShape);
        row.addView(videoShapeStrip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 56, 4, 0, 0, 0));
        controlsRow = row;
        return row;
    }

    /** Uses the camera controls glass for the shape picker. */
    protected final void setVideoShapeStripBackground(
            BlurredBackgroundDrawableViewFactory factory,
            BlurredBackgroundColorProvider colorProvider
    ) {
        if (videoShapeStrip != null) videoShapeStrip.setGlassBackground(factory, colorProvider);
    }

    /** Applies the remembered shape when a new recording starts (circle if the account lost Premium). */
    protected final void resetVideoShape(boolean fromPaused) {
        if (fromPaused) return;
        pendingLockedShape = -1;
        previewPlaybackHeld = false;
        lastHasPremium = VideoShape.hasPremium(videoShapeAccount);
        if (videoShapeStrip != null) videoShapeStrip.updateLocks();
        setVideoShape(VideoShape.effective(videoShapeAccount), false);
        maybeShowSwipeHint();
    }

    // Swipe on the preview to change shape: the outline follows the finger between neighbours in picker order,
    // then springs to the nearest shape (or the next one in the fling direction).

    private static final float SWIPE_DISTANCE = 0.6f, RUBBER_BAND = 0.3f;
    private final int swipeSlop, minimumFlingVelocity, maximumFlingVelocity;
    private VelocityTracker swipeVelocity;
    private boolean swipeTracking, swiping;
    private float swipeDownX, swipeDownY, swipeStart, swipePosition;
    private int swipeCommittedIndex, swipeHapticIndex;
    private SpringAnimation swipeSpring;
    private ValueAnimator peekAnimator;

    private boolean isShapeSwipeAvailable() {
        return videoShapeStrip != null && controlsRow != null && controlsRow.getVisibility() == VISIBLE && controlsRow.getAlpha() > 0.5f && getVisibility() == VISIBLE;
    }

    private static int orderIndex(int shape) {
        for (int i = 0; i < VideoShape.ORDER.length; i++) {
            if (VideoShape.ORDER[i] == shape) return i;
        }
        return 0;
    }

    /**
     * Feed touches on the preview here first. Returns true while a horizontal shape swipe owns the gesture; a second
     * pointer cancels it, so pinch-to-zoom wins.
     */
    protected final boolean handleShapeSwipe(MotionEvent event, boolean insidePreview) {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            swipeTracking = insidePreview && isShapeSwipeAvailable();
            swiping = false;
            if (swipeTracking) {
                swipeDownX = event.getX();
                swipeDownY = event.getY();
                if (swipeVelocity == null) swipeVelocity = VelocityTracker.obtain();
                swipeVelocity.clear();
                swipeVelocity.addMovement(event);
            }
            return false;
        }
        if (!swipeTracking) {
            return false;
        }
        if (swipeVelocity != null) swipeVelocity.addMovement(event);
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            if (swiping) settleSwipe(Utilities.clamp(Math.round(swipePosition), VideoShape.ORDER.length - 1, 0), 0);
            swipeTracking = swiping = false;
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            final float dx = event.getX() - swipeDownX, dy = event.getY() - swipeDownY;
            if (!swiping) {
                if (Math.abs(dx) > swipeSlop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    swiping = true;
                    swipeDownX = event.getX();
                    if (swipeSpring != null) {
                        swipeStart = swipePosition;
                        swipeSpring.cancel();
                        swipeSpring = null;
                    } else {
                        swipeCommittedIndex = orderIndex(videoShapeDrawer.getShape());
                        swipeStart = swipeCommittedIndex;
                    }
                    if (peekAnimator != null) peekAnimator.cancel();
                    swipeHapticIndex = Math.round(swipeStart);
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                } else if (Math.abs(dy) > swipeSlop) {
                    swipeTracking = false;
                }
                return swiping;
            }
            final float max = VideoShape.ORDER.length - 1;
            float position = swipeStart + direction() * -(event.getX() - swipeDownX) / (SWIPE_DISTANCE * getWidth());
            if (position < 0) {
                position *= RUBBER_BAND;
            } else if (position > max) {
                position = max + (position - max) * RUBBER_BAND;
            }
            applySwipePosition(position, true);
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            swipeTracking = false;
            if (!swiping) {
                return false;
            }
            swiping = false;
            float velocity = 0;
            if (swipeVelocity != null && action == MotionEvent.ACTION_UP) {
                swipeVelocity.computeCurrentVelocity(1000, maximumFlingVelocity);
                velocity = swipeVelocity.getXVelocity();
            }
            final float positionVelocity = direction() * -velocity / (SWIPE_DISTANCE * getWidth());
            int target;
            if (Math.abs(velocity) > minimumFlingVelocity) {
                target = positionVelocity > 0 ? (int) Math.floor(swipePosition) + 1 : (int) Math.ceil(swipePosition) - 1;
            } else {
                target = Math.round(swipePosition);
            }
            target = Utilities.clamp(target, VideoShape.ORDER.length - 1, 0);
            settleSwipe(target, positionVelocity);
            return true;
        }
        return swiping;
    }

    /** Swipe left moves forward through the picker; mirrored in RTL, like the picker itself. */
    private static float direction() {
        return LocaleController.isRTL ? -1f : 1f;
    }

    private void applySwipePosition(float position, boolean haptic) {
        swipePosition = position;
        final int max = VideoShape.ORDER.length - 1;
        final float clamped = Utilities.clamp(position, max, 0);
        final int from = Math.min((int) Math.floor(clamped), max - 1);
        videoShapeDrawer.setInteractiveMorph(VideoShape.ORDER[from], VideoShape.ORDER[from + 1], clamped - from);
        if (videoShapeStrip != null) videoShapeStrip.setFractionalSelection(clamped);
        final int nearest = Math.round(clamped);
        if (haptic && nearest != swipeHapticIndex) {
            swipeHapticIndex = nearest;
            try {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignore) {}
        }
        onVideoShapeUpdate();
    }

    private void settleSwipe(int target, float velocity) {
        final int shape = VideoShape.ORDER[target];
        final boolean locked = videoShapeStrip != null && videoShapeStrip.isLocked(shape);
        final int end = locked ? swipeCommittedIndex : target;
        if (swipeSpring != null) swipeSpring.cancel();
        swipeSpring = new SpringAnimation(new FloatValueHolder(swipePosition))
                .setSpring(new SpringForce(end).setStiffness(500f).setDampingRatio(0.75f))
                .setStartVelocity(velocity);
        swipeSpring.setMinimumVisibleChange(0.001f);
        swipeSpring.addUpdateListener((animation, value, v) -> applySwipePosition(value, false));
        swipeSpring.addEndListener((animation, canceled, value, v) -> {
            if (canceled) return;
            swipeSpring = null;
            finishSwipe(end);
        });
        swipeSpring.start();
        if (locked) {
            videoShapeStrip.shake(shape);
            onLockedShape(shape);
        }
    }

    private void finishSwipe(int index) {
        final int shape = VideoShape.ORDER[index];
        videoShapeDrawer.finishInteractive(shape);
        if (videoShapeStrip != null) videoShapeStrip.setShape(shape, true);
        if (index != swipeCommittedIndex) {
            VideoShape.saveToPrefs(shape);
        }
        swipeCommittedIndex = index;
        onVideoShapeUpdate();
    }

    /** Stops a running swipe settle or peek and lands on the nearest shape. */
    private void cancelShapeSwipe() {
        swipeTracking = swiping = false;
        if (peekAnimator != null) {
            peekAnimator.cancel();
            peekAnimator = null;
        }
        if (swipeSpring != null) {
            swipeSpring.cancel();
            swipeSpring = null;
            final int index = Utilities.clamp(Math.round(swipePosition), VideoShape.ORDER.length - 1, 0);
            videoShapeDrawer.finishInteractive(VideoShape.ORDER[index]);
            if (videoShapeStrip != null) videoShapeStrip.setFractionalSelection(-1);
        }
    }

    // First recordings: the outline peeks a bit toward the next shape and back, then a hint explains the swipe.

    private static final String SWIPE_HINT_PREF = "videoShapeSwipeHint";
    private static final int SWIPE_HINT_MAX = 2;
    private HintView2 swipeHint;
    private Runnable showSwipeHintRunnable;
    private Utilities.Callback0Return<Boolean> otherHintVisible;

    /** The swipe hint only shows when no other recording hint (lock, pause, view once) is on screen. */
    public final void setOtherHintVisible(Utilities.Callback0Return<Boolean> otherHintVisible) {
        this.otherHintVisible = otherHintVisible;
    }

    public final void hideSwipeHint() {
        cancelSwipeHint();
        if (swipeHint != null) swipeHint.hide();
    }

    private void cancelSwipeHint() {
        if (showSwipeHintRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(showSwipeHintRunnable);
            showSwipeHintRunnable = null;
        }
    }

    private void maybeShowSwipeHint() {
        cancelSwipeHint();
        if (videoShapeStrip == null) return;
        if (MessagesController.getGlobalMainSettings().getInt(SWIPE_HINT_PREF, 0) >= SWIPE_HINT_MAX) return;
        AndroidUtilities.runOnUIThread(showSwipeHintRunnable = () -> {
            showSwipeHintRunnable = null;
            if (!isAttachedToWindow() || getVisibility() != VISIBLE) return;
            if (!isShapeSwipeAvailable() || swiping || swipeSpring != null) return;
            if (otherHintVisible != null && otherHintVisible.run()) return;
            final int shown = MessagesController.getGlobalMainSettings().getInt(SWIPE_HINT_PREF, 0);
            if (shown >= SWIPE_HINT_MAX) return;
            MessagesController.getGlobalMainSettings().edit().putInt(SWIPE_HINT_PREF, shown + 1).apply();
            peekSwipe();
            showSwipeHint();
        }, 900);
    }

    private void peekSwipe() {
        final int index = orderIndex(videoShapeDrawer.getShape());
        final float toward = index < VideoShape.ORDER.length - 1 ? 0.18f : -0.18f;
        swipeCommittedIndex = index;
        peekAnimator = ValueAnimator.ofFloat(0, 1);
        peekAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            applySwipePosition(index + toward * (float) Math.sin(Math.PI * t), false);
        });
        peekAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (peekAnimator != animation) return;
                peekAnimator = null;
                videoShapeDrawer.finishInteractive(VideoShape.ORDER[index]);
                if (videoShapeStrip != null) videoShapeStrip.setFractionalSelection(-1);
                onVideoShapeUpdate();
            }
        });
        peekAnimator.setDuration(900);
        peekAnimator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
        peekAnimator.start();
    }

    private void showSwipeHint() {
        if (swipeHint == null) {
            swipeHint = new HintView2(getContext(), HintView2.DIRECTION_BOTTOM)
                    .setMultilineText(false)
                    .setRounding(8)
                    .setDuration(3500);
            swipeHint.setText(LocaleController.getString(R.string.VideoShapeSwipeHint));
            addView(swipeHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.TOP | Gravity.FILL_HORIZONTAL));
        }
        swipeHint.bringToFront();
        swipeHint.post(() -> {
            if (controlsRow == null || videoShapeStrip == null) return;
            swipeHint.setTranslationY(controlsRow.getY() - swipeHint.getHeight() + AndroidUtilities.dp(4));
            swipeHint.setJointPx(0, controlsRow.getX() + videoShapeStrip.getX() + videoShapeStrip.getWidth() / 2f - swipeHint.getX());
            swipeHint.show();
        });
    }

    /** Creates the currently selected round-video implementation. */
    public static InstantCameraViewBase create(
            Context context,
            InstantCameraView.Delegate delegate,
            Theme.ResourcesProvider resourcesProvider,
            boolean isNewDesign
    ) {
        return SharedSettings.roundVideoCamera2Enabled.get()
                ? new InstantCameraView2(context, delegate, resourcesProvider, isNewDesign)
                : new InstantCameraView(context, delegate, resourcesProvider, isNewDesign);
    }

    /** Selects the implementation used by subsequently created views. */
    public static void setUseCamera2Implementation(boolean enabled) {
        SharedSettings.roundVideoCamera2Enabled.set(enabled);
    }

    /** Returns whether subsequently created views use the Camera2 implementation. */
    public static boolean isUsingCamera2Implementation() {
        return SharedSettings.roundVideoCamera2Enabled.get();
    }

    /** Sets the host animation callback. */
    public final void setAnimationCallback(AnimationCallback animationCallback) {
        this.animationCallback = animationCallback;
    }

    /** Sets the callback used to keep the host timeline synchronized. */
    public final void setTrimCallback(TrimCallback trimCallback) {
        this.trimCallback = trimCallback;
    }

    /** Sets the callback used to synchronize surrounding recording UI. */
    public final void setRecordingUiFrameCallback(RecordingUiFrameCallback callback) {
        recordingUiFrameCallback = callback;
        if (callback != null) callback.onActiveChanged(recordingUiFrameClockActive);
    }

    /** Dispatches a visibility change without owning the host animation. */
    protected final void dispatchAnimationState(boolean open, boolean fromPaused) {
        if (animationCallback != null) animationCallback.onAnimation(open, fromPaused);
    }

    /** Dispatches a normalized trim range without exposing recorder internals. */
    protected final void dispatchTrimRange(float start, float end) {
        if (trimCallback != null) trimCallback.onTrimChanged(start, end);
    }

    /** Enables or disables preview-frame-driven recording UI updates. */
    protected final void dispatchRecordingUiFrameClockActive(boolean active) {
        if (recordingUiFrameClockActive == active) return;
        recordingUiFrameClockActive = active;
        if (recordingUiFrameCallback != null) {
            recordingUiFrameCallback.onActiveChanged(active);
        }
    }

    /** Dispatches the current recording duration on a preview UI frame. */
    protected final void dispatchRecordingUiFrame(long durationMs) {
        if (recordingUiFrameCallback != null) recordingUiFrameCallback.onFrame(durationMs);
    }

    /** Sets the background used by camera controls. */
    public abstract void setButtonsBackground(
            BlurredBackgroundDrawableViewFactory factory,
            BlurredBackgroundColorProvider colorProvider
    );

    /** Applies the bottom inset used by the host layout. */
    public abstract void setInternalPadding(int padding);

    /** Releases camera, playback and recording resources. */
    public abstract void destroy(boolean async);

    /** Switches between recording and recorded-video preview. */
    public abstract void togglePause();

    /** Returns whether recording is paused or changing pause state. */
    public abstract boolean isPaused();

    /** Opens the camera UI and starts the selected implementation. */
    public abstract void showCamera(boolean fromPaused);

    /** Animates the camera UI visibility. */
    public abstract void startAnimation(boolean open, boolean fromPaused);

    /** Returns the camera bounds in screen coordinates. */
    public abstract RectF getCameraRect();

    /** Applies an external preview playback command. */
    public abstract void changeVideoPreviewState(int state, float progress);

    /** Handles Telegram's round-video send command. */
    public abstract void send(
            int state,
            boolean notify,
            int scheduleDate,
            int scheduleRepeatPeriod,
            int ttl,
            long effectId,
            long stars
    );

    /** Cancels the active recording. */
    public abstract void cancel(boolean byGesture);

    /** Returns the camera controls container. */
    public abstract View getButtonsLayout();

    /** Returns the preview mute indicator. */
    public abstract View getMuteImageView();

    /** Returns the paint used by the send transition. */
    public abstract Paint getPaint();

    /** Hides the camera UI and releases its active resources. */
    public abstract void hideCamera(boolean async);

    /** Returns the texture used by preview or recorded-video playback. */
    public abstract TextureView getTextureView();

    /** Returns the container used by the send transition. */
    public abstract InstantViewCameraContainer getCameraContainer();

    /** Updates whether the camera is participating in a message transition. */
    public abstract void setIsMessageTransition(boolean messageTransition);

    /** Clears implementation-specific output file state. */
    public abstract void resetCameraFile();

    /** Applies the host pan translation. */
    public abstract void onPanTranslationUpdate(float translationY);

    /** Common camera container required by the send-to-message transition. */
    public abstract static class InstantViewCameraContainer extends FrameLayout {
        /** Creates a transition container. */
        public InstantViewCameraContainer(Context context) {
            super(context);
        }

        /** Sets the message image drawn during the send transition. */
        public abstract void setImageReceiver(ImageReceiver imageReceiver);
    }
}
