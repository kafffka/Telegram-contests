package org.telegram.ui;

import static org.telegram.ui.Components.Reactions.ReactionsUtils.ACTIVATE_ANIMATION_FILTER;
import static org.telegram.ui.GroupCallActivity.TRANSITION_DURATION;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.PowerManager;
import android.text.TextUtils;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.voip.EncryptionKeyEmojifier;
import org.telegram.messenger.voip.Instance;
import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.DarkAlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.HintView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.voip.AcceptDeclineView;
import org.telegram.ui.Components.voip.PrivateVideoPreviewDialog;
import org.telegram.ui.Components.voip.VoIPAvatarView;
import org.telegram.ui.Components.voip.VoIPBackground;
import org.telegram.ui.Components.voip.VoIPButtonsLayout;
import org.telegram.ui.Components.voip.VoIPColorsController;
import org.telegram.ui.Components.voip.VoIPFloatingLayout;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.Components.voip.VoIPNotificationsLayout;
import org.telegram.ui.Components.voip.VoIPOverlayBackground;
import org.telegram.ui.Components.voip.VoIPPiPView;
import org.telegram.ui.Components.voip.VoIPStatusTextView;
import org.telegram.ui.Components.voip.VoIPTextureView;
import org.telegram.ui.Components.voip.VoIPToggleButton2;
import org.telegram.ui.Components.voip.VoIPWindowView;
import org.webrtc.EglBase;
import org.webrtc.GlRectDrawer;
import org.webrtc.RendererCommon;
import org.webrtc.TextureViewRenderer;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;

public class VoIPFragment implements VoIPService.StateListener, VoIPColorsController.Listener, NotificationCenter.NotificationCenterDelegate {

    private final static int STATE_GONE = 0;
    private final static int STATE_FULLSCREEN = 1;
    private final static int STATE_FLOATING = 2;

    private final int currentAccount;

    Activity activity;

    TLRPC.User currentUser;
    TLRPC.User callingUser;
    private VoIPColorsController voIPColorsController;
    VoIPToggleButton2[] bottomButtons = new VoIPToggleButton2[4];

    private ViewGroup fragmentView;
    private VoIPOverlayBackground overlayBackground;
    private VoIPBackground voIPBackground;
    private VoIPAvatarView voIPAvatarView;
    private VoIPToggleButton2 expandableCloseRateButton;
    private ImageView colorsSwitchImageView;

    private BackupImageView callingUserPhotoView;
    private TextView callingUserTitle;

    private VoIPStatusTextView statusTextView;
    private ImageView backIcon;

    private LinearLayout emojiContainer;
    private TextView hideEmoji;
    LinearLayout emojiLayout;
    TextView emojiRationalTextView;
    TextView emojiRationalTitleTextView;
    BackupImageView[] emojiViews = new BackupImageView[4];
    Emoji.EmojiDrawable[] emojiDrawables = new Emoji.EmojiDrawable[4];
    TLRPC.TL_availableReaction[] reactions = new TLRPC.TL_availableReaction[4];
    String[] emoji = new String[4];
    private LinearLayout rateCallLayout;
    private LinearLayout rateCallStarsLayout;
    private RLottieImageView ratingAnimationImageView;
    private boolean shouldNavigateToRateCallLayout;
    private LinearLayout statusLayout;
    private VoIPFloatingLayout currentUserCameraFloatingLayout;
    private VoIPFloatingLayout callingUserMiniFloatingLayout;
    private boolean currentUserCameraIsFullscreen;


    private TextureViewRenderer callingUserMiniTextureRenderer;
    private VoIPTextureView callingUserTextureView;
    private VoIPTextureView currentUserTextureView;

    private VoIPToggleButton2[] acceptDeclineButtons = new VoIPToggleButton2[2];
    private boolean isAcceptButtonClicked;
    private boolean needAnimateFromAcceptButton;
    private int[] lastAcceptButtonLocation = new int[2];
    private int[] lastDeclineButtonLocation = new int[2];

    private View bottomShadow;
    private View topShadow;

    private VoIPButtonsLayout buttonsLayout;
    Paint overlayPaint = new Paint();
    Paint overlayBottomPaint = new Paint();

    boolean isOutgoing;
    boolean callingUserIsVideo;
    boolean currentUserIsVideo;

    private PrivateVideoPreviewDialog previewDialog;

    private int currentState;
    private int previousState;
    private WindowInsets lastInsets;

    float touchSlop;

    private static VoIPFragment instance;
    private VoIPWindowView windowView;
    private int statusLayoutAnimateToOffset;

    private AccessibilityManager accessibilityManager;

    private boolean uiVisible = true;
    private boolean shadowsVisible = false;
    private float uiVisibilityAlpha = 0f;
    private boolean canHideUI;
    private Animator cameraShowingAnimator;
    private boolean emojiLoaded;
    private boolean emojiExpanded;

    private boolean canSwitchToPip;
    private boolean switchingToPip;

    private float enterTransitionProgress;
    private boolean isFinished;
    boolean cameraForceExpanded;
    boolean enterFromPiP;
    private boolean deviceIsLocked;

    private long lastContentTapTime;
    private long lastFragmentActionTime;
    private int animationIndex = -1;
    private VoIPNotificationsLayout notificationsLayout;
    private VoIPNotificationsLayout centerNotificationsLayout;

    private HintView tapToVideoTooltip;
    private HintView tapToEmojiTooltip;

    ValueAnimator uiVisibilityAnimator;
    ValueAnimator.AnimatorUpdateListener statusbarAnimatorListener = valueAnimator -> {
        uiVisibilityAlpha = (float) valueAnimator.getAnimatedValue();
        updateSystemBarColors();
    };

    float fillNaviagtionBarValue;
    boolean fillNaviagtionBar;
    ValueAnimator naviagtionBarAnimator;
    ValueAnimator.AnimatorUpdateListener navigationBarAnimationListener = valueAnimator -> {
        fillNaviagtionBarValue = (float) valueAnimator.getAnimatedValue();
        updateSystemBarColors();
    };

    boolean hideUiRunnableWaiting;
    Runnable hideUIRunnable = () -> {
        hideUiRunnableWaiting = false;
        if (canHideUI && uiVisible && !emojiExpanded) {
            lastContentTapTime = System.currentTimeMillis();
            showUi(false);
            previousState = currentState;
            updateViewState();
        }
    };
    private boolean lockOnScreen;
    private boolean screenWasWakeup;
    private boolean isVideoCall;

    /* === pinch to zoom === */
    private float pinchStartCenterX;
    private float pinchStartCenterY;
    private float pinchStartDistance;
    private float pinchTranslationX;
    private float pinchTranslationY;
    private boolean isInPinchToZoomTouchMode;

    private float pinchCenterX;
    private float pinchCenterY;

    private int pointerId1, pointerId2;

    float pinchScale = 1f;
    private boolean zoomStarted;
    private boolean canZoomGesture;
    ValueAnimator zoomBackAnimator;
    /* === pinch to zoom === */
    boolean isHeavyAnimationsStopped;
    private final Runnable stopAnimationsHandler = this::stopHeavyAnimations;
    private boolean isRevealAnimationShown;
    private int[] viewLocation = new int[2];
    private ValueAnimator animatorToRateCall;
    private int[] expandableCloseRateButtonCoords = new int[2];
    private ValueAnimator animatorCallRatingStars;
    private TLRPC.TL_phone_setCallRating rateCallRequest;

    public static void show(Activity activity, int account) {
        show(activity, false, account);
    }

    public static void show(Activity activity, boolean overlay, int account) {
        if (instance != null && instance.windowView.getParent() == null) {
            if (instance != null) {
                instance.callingUserTextureView.renderer.release();
                instance.currentUserTextureView.renderer.release();
                instance.callingUserMiniTextureRenderer.release();
                instance.destroy();
            }
            instance = null;
        }
        if (instance != null || activity.isFinishing()) {
            return;
        }
        boolean transitionFromPip = VoIPPiPView.getInstance() != null;
        if (VoIPService.getSharedInstance() == null || VoIPService.getSharedInstance().getUser() == null) {
            return;
        }
        MediaDataController.getInstance(account).preloadDefaultReactions();
        VoIPFragment fragment = new VoIPFragment(account);
        fragment.activity = activity;
        instance = fragment;
        VoIPWindowView windowView = new VoIPWindowView(activity, !transitionFromPip) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (fragment.isFinished || fragment.switchingToPip) {
                    return false;
                }
                final int keyCode = event.getKeyCode();
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP && !fragment.lockOnScreen) {
                    fragment.onBackPressed();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    if (fragment.currentState == VoIPService.STATE_WAITING_INCOMING) {
                        final VoIPService service = VoIPService.getSharedInstance();
                        if (service != null) {
                            service.stopRinging();
                            return true;
                        }
                    }
                }
                return super.dispatchKeyEvent(event);
            }
        };
        instance.deviceIsLocked = ((KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode();

        PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
        boolean screenOn;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            screenOn = pm.isInteractive();
        } else {
            screenOn = pm.isScreenOn();
        }
        instance.screenWasWakeup = !screenOn;
        windowView.setLockOnScreen(instance.deviceIsLocked);
        fragment.windowView = windowView;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            windowView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    fragment.setInsets(windowInsets);
                }
                if (Build.VERSION.SDK_INT >= 30) {
                    return WindowInsets.CONSUMED;
                } else {
                    return windowInsets.consumeSystemWindowInsets();
                }
            });
        }

        WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams layoutParams = windowView.createWindowLayoutParams();
        if (overlay) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            }
        }
        wm.addView(windowView, layoutParams);
        View view = fragment.createView(activity);
        windowView.addView(view);

        if (transitionFromPip) {
            fragment.enterTransitionProgress = 0f;
            fragment.startTransitionFromPiP();
        } else {
            fragment.enterTransitionProgress = 1f;
            fragment.updateSystemBarColors();
        }
    }

    private void onBackPressed() {
        if (isFinished || switchingToPip) {
            return;
        }
        if (previewDialog != null) {
            previewDialog.dismiss(false, false);
            return;
        }
        if (callingUserIsVideo && currentUserIsVideo && cameraForceExpanded) {
            cameraForceExpanded = false;
            currentUserCameraFloatingLayout.setRelativePosition(callingUserMiniFloatingLayout);
            currentUserCameraIsFullscreen = false;
            previousState = currentState;
            updateViewState();
            return;
        }
        if (emojiExpanded) {
            expandEmoji(false);
        } else {
            if (emojiRationalTextView.getVisibility() != View.GONE) {
                return;
            }
            if (canSwitchToPip && !lockOnScreen) {
                if (AndroidUtilities.checkInlinePermissions(activity)) {
                    switchToPip();
                } else {
                    requestInlinePermissions();
                }
            } else {
                windowView.finish();
            }
        }
    }

    public static void clearInstance() {
        if (instance != null) {
            if (VoIPService.getSharedInstance() != null) {
                int h = instance.windowView.getMeasuredHeight();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                    h -= instance.lastInsets.getSystemWindowInsetBottom();
                }
                if (instance.canSwitchToPip) {
                    VoIPPiPView.show(instance.activity, instance.currentAccount, instance.windowView.getMeasuredWidth(), h, VoIPPiPView.ANIMATION_ENTER_TYPE_SCALE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                        VoIPPiPView.topInset = instance.lastInsets.getSystemWindowInsetTop();
                        VoIPPiPView.bottomInset = instance.lastInsets.getSystemWindowInsetBottom();
                    }
                }
            }
            instance.callingUserTextureView.renderer.release();
            instance.currentUserTextureView.renderer.release();
            instance.callingUserMiniTextureRenderer.release();
            instance.destroy();
        }
        instance = null;
    }

    public static VoIPFragment getInstance() {
        return instance;
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setInsets(WindowInsets windowInsets) {
        lastInsets = windowInsets;
        ((FrameLayout.LayoutParams) buttonsLayout.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) acceptDeclineButtons[0].getLayoutParams()).bottomMargin = AndroidUtilities.dp(54) + lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) acceptDeclineButtons[1].getLayoutParams()).bottomMargin = AndroidUtilities.dp(54) + lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) backIcon.getLayoutParams()).topMargin = lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) topShadow.getLayoutParams()).topMargin = lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) hideEmoji.getLayoutParams()).topMargin = AndroidUtilities.dp(16) + lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) statusLayout.getLayoutParams()).topMargin = AndroidUtilities.dp(68) + lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) centerNotificationsLayout.getLayoutParams()).topMargin = AndroidUtilities.dp(360) + lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) emojiContainer.getLayoutParams()).topMargin = AndroidUtilities.dp(16) + lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) voIPAvatarView.getLayoutParams()).topMargin = AndroidUtilities.dp(80) + lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) rateCallLayout.getLayoutParams()).topMargin = AndroidUtilities.dp(346) + lastInsets.getSystemWindowInsetBottom();

        ((FrameLayout.LayoutParams) callingUserMiniFloatingLayout.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) notificationsLayout.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();

        ((FrameLayout.LayoutParams) bottomShadow.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        currentUserCameraFloatingLayout.setInsets(lastInsets);
        callingUserMiniFloatingLayout.setInsets(lastInsets);
        fragmentView.requestLayout();
        if (previewDialog != null) {
            previewDialog.setBottomPadding(lastInsets.getSystemWindowInsetBottom());
        }
    }

    public VoIPFragment(int account) {
        currentAccount = account;
        currentUser = MessagesController.getInstance(currentAccount)
            .getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        callingUser = VoIPService.getSharedInstance().getUser();
        VoIPService.getSharedInstance().registerStateListener(this);
        isOutgoing = VoIPService.getSharedInstance().isOutgoing();
        previousState = -1;
        currentState = VoIPService.getSharedInstance().getCallState();
        voIPColorsController = new VoIPColorsController(VoIPColorsController.FLAG_BLUE_VIOLET | VoIPColorsController.FLAG_BLUE_GREEN, VoIPColorsController.FLAG_BLUE_VIOLET);
        voIPColorsController.setListener(this);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.voipServiceCreated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.closeInCallActivity);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.reactionsDidLoad);
    }

    private void destroy() {
        final VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            service.unregisterStateListener(this);
        }
        voIPColorsController.setListener(null);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.voipServiceCreated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.closeInCallActivity);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.reactionsDidLoad);
    }

    @Override
    public void onStateChanged(int state) {
        if (currentState != state) {
            previousState = currentState;
            currentState = state;
            if (windowView != null) {
                updateViewState();
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.voipServiceCreated) {
            if (currentState == VoIPService.STATE_BUSY && VoIPService.getSharedInstance() != null) {
                currentUserTextureView.renderer.release();
                callingUserTextureView.renderer.release();
                callingUserMiniTextureRenderer.release();
                initRenderers();
                VoIPService.getSharedInstance().registerStateListener(this);
            }
        } else if (id == NotificationCenter.emojiLoaded) {
            updateKeyView(true);
        } else if (id == NotificationCenter.closeInCallActivity) {
            windowView.finish();
        } else if (id == NotificationCenter.reactionsDidLoad) {
            updateKeyView(true);
        }
    }

    @Override
    public void onSignalBarsCountChanged(int count) {
        if (statusTextView != null) {
            statusTextView.setSignalBarCount(count);
        }
        if (voIPColorsController != null && !currentUserIsVideo && !callingUserIsVideo) {
            centerNotificationsLayout.animate().cancel();
            centerNotificationsLayout.setAlpha(1f);
            centerNotificationsLayout.beforeLayoutChanges();
            if (count <= 1) {
                if (!voIPColorsController.isWeakSignalActive()) {
                    voIPColorsController.showWeakSignal();
                    scheduleStopAnimations();
                }
                centerNotificationsLayout.addNotification(LocaleController.getString("VoipWeakNetworkSignal", R.string.VoipWeakNetworkSignal), "weak_signal");
            } else {
                if (voIPColorsController.isWeakSignalActive()) {
                    voIPColorsController.hideWeakSignal();
                    scheduleStopAnimations();
                }
                centerNotificationsLayout.removeNotification("weak_signal");
            }
            centerNotificationsLayout.animateLayoutChanges();
        }
    }

    @Override
    public void onStartChangeAudioSettings() {
        voIPColorsController.pause(true);
    }

    @Override
    public void onAudioLevelsUpdated(int[] uids, float[] levels, boolean[] voice) {
        if (levels != null && levels.length > 0) {
            float amplitude = levels[0];
            voIPAvatarView.setAmplitude(amplitude);
        }
    }

    @Override
    public void isProximityNearChanged(boolean isProximityNear) {
        AndroidUtilities.cancelRunOnUIThread(stopAnimationsHandler);
        if (isProximityNear) {
            stopHeavyAnimations();
        } else {
            scheduleStopAnimations();
        }
    }

    @Override
    public boolean onRateCall(boolean needRate, long accessHash, long callId) {
        if (!needRate && shouldNavigateToRateCallLayout) {
            AndroidUtilities.runOnUIThread(() -> windowView.finish(), 200);
            return false;
        }

        if (shouldNavigateToRateCallLayout) {
            rateCallRequest = new TLRPC.TL_phone_setCallRating();
            rateCallRequest.comment = "";
            rateCallRequest.peer = new TLRPC.TL_inputPhoneCall();
            rateCallRequest.peer.access_hash = accessHash;
            rateCallRequest.peer.id = callId;
            rateCallRequest.user_initiative = false;
            startTransitionToRateCall();
        }

        return shouldNavigateToRateCallLayout;
    }

    @Override
    public void onAudioSettingsChanged() {
        updateButtons(true);
        voIPColorsController.resume();
    }

    @Override
    public void onMediaStateUpdated(int audioState, int videoState) {
        previousState = currentState;
        if (videoState == Instance.VIDEO_STATE_ACTIVE && !isVideoCall) {
            isVideoCall = true;
        }
        updateViewState();
    }

    @Override
    public void onCameraSwitch(boolean isFrontFace) {
        previousState = currentState;
        updateViewState();
    }

    @Override
    public void onVideoAvailableChange(boolean isAvailable) {
        previousState = currentState;
        if (isAvailable && !isVideoCall) {
            isVideoCall = true;
        }
        updateViewState();
    }

    @Override
    public void onScreenOnChange(boolean screenOn) {

    }

    public View createView(Context context) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        accessibilityManager = ContextCompat.getSystemService(context, AccessibilityManager.class);

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && lastInsets != null) {
                    canvas.drawRect(0, 0, getMeasuredWidth(), lastInsets.getSystemWindowInsetTop(), overlayPaint);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && lastInsets != null) {
                    canvas.drawRect(0, getMeasuredHeight() - lastInsets.getSystemWindowInsetBottom(), getMeasuredWidth(), getMeasuredHeight(), overlayBottomPaint);
                }
            }

            float pressedX;
            float pressedY;
            boolean check;
            long pressedTime;

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                updateLastFragmentActionTime();
                return super.dispatchTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                /* === pinch to zoom === */
                if (!canZoomGesture && !isInPinchToZoomTouchMode && !zoomStarted && ev.getActionMasked() != MotionEvent.ACTION_DOWN) {
                    finishZoom();
                    return false;
                }
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    canZoomGesture = false;
                    isInPinchToZoomTouchMode = false;
                    zoomStarted = false;
                }
                VoIPTextureView currentTextureView = getFullscreenTextureView();

                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                    if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        AndroidUtilities.rectTmp.set(currentTextureView.getX(), currentTextureView.getY(), currentTextureView.getX() + currentTextureView.getMeasuredWidth(), currentTextureView.getY() + currentTextureView.getMeasuredHeight());
                        AndroidUtilities.rectTmp.inset((currentTextureView.getMeasuredHeight() * currentTextureView.scaleTextureToFill - currentTextureView.getMeasuredHeight()) / 2, (currentTextureView.getMeasuredWidth() * currentTextureView.scaleTextureToFill - currentTextureView.getMeasuredWidth()) / 2);
                        if (!GroupCallActivity.isLandscapeMode) {
                            AndroidUtilities.rectTmp.top = Math.max(AndroidUtilities.rectTmp.top, ActionBar.getCurrentActionBarHeight());
                            AndroidUtilities.rectTmp.bottom = Math.min(AndroidUtilities.rectTmp.bottom, currentTextureView.getMeasuredHeight() - AndroidUtilities.dp(90));
                        } else {
                            AndroidUtilities.rectTmp.top = Math.max(AndroidUtilities.rectTmp.top, ActionBar.getCurrentActionBarHeight());
                            AndroidUtilities.rectTmp.right = Math.min(AndroidUtilities.rectTmp.right, currentTextureView.getMeasuredWidth() - AndroidUtilities.dp(90));
                        }
                        canZoomGesture = AndroidUtilities.rectTmp.contains(ev.getX(), ev.getY());
                        if (!canZoomGesture) {
                            finishZoom();
                        }
                    }
                    if (canZoomGesture && !isInPinchToZoomTouchMode && ev.getPointerCount() == 2) {
                        pinchStartDistance = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0));
                        pinchStartCenterX = pinchCenterX = (ev.getX(0) + ev.getX(1)) / 2.0f;
                        pinchStartCenterY = pinchCenterY = (ev.getY(0) + ev.getY(1)) / 2.0f;
                        pinchScale = 1f;

                        pointerId1 = ev.getPointerId(0);
                        pointerId2 = ev.getPointerId(1);
                        isInPinchToZoomTouchMode = true;
                    }
                } else if (ev.getActionMasked() == MotionEvent.ACTION_MOVE && isInPinchToZoomTouchMode) {
                    int index1 = -1;
                    int index2 = -1;
                    for (int i = 0; i < ev.getPointerCount(); i++) {
                        if (pointerId1 == ev.getPointerId(i)) {
                            index1 = i;
                        }
                        if (pointerId2 == ev.getPointerId(i)) {
                            index2 = i;
                        }
                    }
                    if (index1 == -1 || index2 == -1) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                        finishZoom();
                    } else {
                        pinchScale = (float) Math.hypot(ev.getX(index2) - ev.getX(index1), ev.getY(index2) - ev.getY(index1)) / pinchStartDistance;
                        if (pinchScale > 1.005f && !zoomStarted) {
                            pinchStartDistance = (float) Math.hypot(ev.getX(index2) - ev.getX(index1), ev.getY(index2) - ev.getY(index1));
                            pinchStartCenterX = pinchCenterX = (ev.getX(index1) + ev.getX(index2)) / 2.0f;
                            pinchStartCenterY = pinchCenterY = (ev.getY(index1) + ev.getY(index2)) / 2.0f;
                            pinchScale = 1f;
                            pinchTranslationX = 0f;
                            pinchTranslationY = 0f;
                            getParent().requestDisallowInterceptTouchEvent(true);
                            zoomStarted = true;
                            isInPinchToZoomTouchMode = true;
                        }

                        float newPinchCenterX = (ev.getX(index1) + ev.getX(index2)) / 2.0f;
                        float newPinchCenterY = (ev.getY(index1) + ev.getY(index2)) / 2.0f;

                        float moveDx = pinchStartCenterX - newPinchCenterX;
                        float moveDy = pinchStartCenterY - newPinchCenterY;
                        pinchTranslationX = -moveDx / pinchScale;
                        pinchTranslationY = -moveDy / pinchScale;
                        invalidate();
                    }
                } else if ((ev.getActionMasked() == MotionEvent.ACTION_UP || (ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP && checkPointerIds(ev)) || ev.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                    finishZoom();
                }
                fragmentView.invalidate();

                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        pressedX = ev.getX();
                        pressedY = ev.getY();
                        check = true;
                        pressedTime = System.currentTimeMillis();
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        check = false;
                        break;
                    case MotionEvent.ACTION_UP:
                        if (check) {
                            float dx = ev.getX() - pressedX;
                            float dy = ev.getY() - pressedY;
                            long currentTime = System.currentTimeMillis();
                            if (dx * dx + dy * dy < touchSlop * touchSlop && currentTime - pressedTime < 300 && currentTime - lastContentTapTime > 300) {
                                lastContentTapTime = System.currentTimeMillis();
                                if (emojiExpanded) {
                                    expandEmoji(false);
                                } else if (canHideUI) {
                                    showUi(!uiVisible);
                                    previousState = currentState;
                                    updateViewState();
                                }
                            }
                            check = false;
                        }
                        break;
                }
                return canZoomGesture || check;
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == callingUserPhotoView && (currentUserIsVideo || callingUserIsVideo)) {
                    return false;
                }
                if (
                        child == callingUserPhotoView ||
                                child == callingUserTextureView ||
                                (child == currentUserCameraFloatingLayout && currentUserCameraIsFullscreen)
                ) {
                    if (zoomStarted || zoomBackAnimator != null) {
                        canvas.save();
                        canvas.scale(pinchScale, pinchScale, pinchCenterX, pinchCenterY);
                        canvas.translate(pinchTranslationX, pinchTranslationY);
                        boolean b = super.drawChild(canvas, child, drawingTime);
                        canvas.restore();
                        return b;
                    }
                }
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        frameLayout.setClipToPadding(false);
        frameLayout.setClipChildren(false);
        frameLayout.setBackgroundColor(0xff000000);
        updateSystemBarColors();
        fragmentView = frameLayout;
        frameLayout.setFitsSystemWindows(true);

        voIPBackground = new VoIPBackground(context);
        voIPBackground.setVoIPColorsController(voIPColorsController);
        voIPColorsController.setBackgroundView(voIPBackground);
        voIPColorsController.start();
        frameLayout.addView(voIPBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        if (Build.VERSION.SDK_INT >= 21) {
            colorsSwitchImageView = new ImageView(context);
            colorsSwitchImageView.setVisibility(View.GONE);
        }

        callingUserPhotoView = new BackupImageView(context) {

            int blackoutColor = ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.3f));

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                canvas.drawColor(blackoutColor);
            }
        };
        callingUserTextureView = new VoIPTextureView(context, false, true, false, false);
        callingUserTextureView.renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        callingUserTextureView.renderer.setEnableHardwareScaler(true);
        callingUserTextureView.renderer.setRotateTextureWithScreen(true);
        callingUserTextureView.scaleType = VoIPTextureView.SCALE_TYPE_FIT;
        //     callingUserTextureView.attachBackgroundRenderer();

        frameLayout.addView(callingUserPhotoView);
        frameLayout.addView(callingUserTextureView);


        final BackgroundGradientDrawable gradientDrawable = new BackgroundGradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF1b354e, 0xFF255b7d});
        final BackgroundGradientDrawable.Sizes sizes = BackgroundGradientDrawable.Sizes.ofDeviceScreen(BackgroundGradientDrawable.Sizes.Orientation.PORTRAIT);
        gradientDrawable.startDithering(sizes, new BackgroundGradientDrawable.ListenerAdapter() {
            @Override
            public void onAllSizesReady() {
                callingUserPhotoView.invalidate();
            }
        });
        overlayBackground = new VoIPOverlayBackground(context);
        overlayBackground.setVisibility(View.GONE);

        callingUserPhotoView.getImageReceiver().setDelegate((imageReceiver, set, thumb, memCache) -> {
            ImageReceiver.BitmapHolder bmp = imageReceiver.getBitmapSafe();
            if (bmp != null) {
                overlayBackground.setBackground(bmp);
            }
        });


        currentUserCameraFloatingLayout = new VoIPFloatingLayout(context);
        currentUserCameraFloatingLayout.setDelegate((progress, value) -> currentUserTextureView.setScreenshareMiniProgress(progress, value));
        currentUserCameraFloatingLayout.setRelativePosition(1f, 1f);
        currentUserCameraIsFullscreen = true;
        currentUserTextureView = new VoIPTextureView(context, true, false);
        currentUserTextureView.renderer.setIsCamera(true);
        currentUserTextureView.renderer.setUseCameraRotation(true);
        currentUserCameraFloatingLayout.setOnTapListener(view -> {
            if (currentUserIsVideo && callingUserIsVideo && System.currentTimeMillis() - lastContentTapTime > 500) {
                AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
                hideUiRunnableWaiting = false;
                lastContentTapTime = System.currentTimeMillis();
                callingUserMiniFloatingLayout.setRelativePosition(currentUserCameraFloatingLayout);
                currentUserCameraIsFullscreen = true;
                cameraForceExpanded = true;
                previousState = currentState;
                updateViewState();
            }
        });
        currentUserTextureView.renderer.setMirror(true);
        currentUserCameraFloatingLayout.addView(currentUserTextureView);

        callingUserMiniFloatingLayout = new VoIPFloatingLayout(context);
        callingUserMiniFloatingLayout.alwaysFloating = true;
        callingUserMiniFloatingLayout.setFloatingMode(true, false);
        callingUserMiniTextureRenderer = new TextureViewRenderer(context);
        callingUserMiniTextureRenderer.setEnableHardwareScaler(true);
        callingUserMiniTextureRenderer.setIsCamera(false);
        callingUserMiniTextureRenderer.setFpsReduction(30);
        callingUserMiniTextureRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);

        View backgroundView = new View(context);
        backgroundView.setBackgroundColor(0xff1b1f23);
        callingUserMiniFloatingLayout.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        callingUserMiniFloatingLayout.addView(callingUserMiniTextureRenderer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        callingUserMiniFloatingLayout.setOnTapListener(view -> {
            if (cameraForceExpanded && System.currentTimeMillis() - lastContentTapTime > 500) {
                AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
                hideUiRunnableWaiting = false;
                lastContentTapTime = System.currentTimeMillis();
                currentUserCameraFloatingLayout.setRelativePosition(callingUserMiniFloatingLayout);
                currentUserCameraIsFullscreen = false;
                cameraForceExpanded = false;
                previousState = currentState;
                updateViewState();
            }
        });
        callingUserMiniFloatingLayout.setVisibility(View.GONE);

        frameLayout.addView(currentUserCameraFloatingLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        frameLayout.addView(callingUserMiniFloatingLayout);
        frameLayout.addView(overlayBackground);


        bottomShadow = new View(context);
        bottomShadow.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.4f))}));
        bottomShadow.setAlpha(0f);
        frameLayout.addView(bottomShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 140, Gravity.BOTTOM));

        topShadow = new View(context);
        topShadow.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.4f)), Color.TRANSPARENT}));
        topShadow.setAlpha(0f);
        frameLayout.addView(topShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 140, Gravity.TOP));

        hideEmoji = new TextView(context);
        hideEmoji.setText(LocaleController.getString("CallEmojiHide", R.string.CallEmojiHide));
        hideEmoji.setTextColor(0xffffffff);
        hideEmoji.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        hideEmoji.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        hideEmoji.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
        hideEmoji.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), getDarkAlphaColor(hideEmoji)));
        hideEmoji.setOnClickListener(v -> {
            MessagesController.getGlobalMainSettings().edit().putBoolean("call_emoji_hint_shown", true).apply();
            tapToEmojiTooltip.hide();
            lastContentTapTime = System.currentTimeMillis();
            expandEmoji(false);
        });
        hideEmoji.setVisibility(View.GONE);
        frameLayout.addView(hideEmoji, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

        emojiContainer = new LinearLayout(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setVisibleToUser(emojiLoaded);
            }
        };
        emojiContainer.setOrientation(LinearLayout.VERTICAL);
        emojiContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        emojiContainer.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(24), 0x00000000));

        emojiLayout = new LinearLayout(context);
        emojiLayout.setOrientation(LinearLayout.HORIZONTAL);
        emojiLayout.setPadding(0, 0, 0, AndroidUtilities.dp(16));
        emojiLayout.setClipToPadding(false);

        emojiLayout.setOnClickListener(view -> {
            if (System.currentTimeMillis() - lastContentTapTime < 500) {
                return;
            }
            MessagesController.getGlobalMainSettings().edit().putBoolean("call_emoji_hint_shown", true).apply();
            tapToEmojiTooltip.hide();
            lastContentTapTime = System.currentTimeMillis();
            expandEmoji(!emojiExpanded);
        });

        emojiRationalTextView = new TextView(context);
        emojiRationalTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("CallEmojiKeyTooltip", R.string.CallEmojiKeyTooltip, UserObject.getFirstName(callingUser))));
        emojiRationalTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        emojiRationalTextView.setTextColor(Color.WHITE);
        emojiRationalTextView.setGravity(Gravity.CENTER);
        emojiRationalTextView.setVisibility(View.GONE);

        emojiRationalTitleTextView = new TextView(context);
        emojiRationalTitleTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("CallEmojiKeyTooltipTitle", R.string.CallEmojiKeyTooltipTitle)));
        emojiRationalTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        emojiRationalTitleTextView.setTextColor(Color.WHITE);
        emojiRationalTitleTextView.setGravity(Gravity.CENTER);
        emojiRationalTitleTextView.setVisibility(View.GONE);

        for (int i = 0; i < 4; i++) {
            emojiViews[i] = new BackupImageView(context);
            emojiViews[i].setAspectFit(true);
            emojiLayout.addView(emojiViews[i], LayoutHelper.createLinear(24, 24, i == 0 ? 0 : 6, 0, 0, 0));
        }

        emojiContainer.addView(emojiLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));
        emojiContainer.addView(emojiRationalTitleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 16, 0, 16, 0));
        emojiContainer.addView(emojiRationalTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 16, 16, 16, 16));
        frameLayout.addView(emojiContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 40, 16, 40, 0));

        rateCallLayout = new LinearLayout(context);
        rateCallLayout.setOrientation(LinearLayout.VERTICAL);
        rateCallLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        rateCallLayout.setVisibility(View.INVISIBLE);

        TextView rateCallTitle = new TextView(context);
        rateCallTitle.setText(LocaleController.getString("VoipRateCallTitle", R.string.VoipRateCallTitle));
        rateCallTitle.setTextColor(0xffffffff);
        rateCallTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        rateCallTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        rateCallLayout.addView(rateCallTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

        TextView rateCallDescription = new TextView(context);
        rateCallDescription.setText(LocaleController.getString("VoipRateCallDescription", R.string.VoipRateCallDescription));
        rateCallDescription.setTextColor(0xffffffff);
        rateCallDescription.setGravity(Gravity.CENTER_HORIZONTAL);
        rateCallDescription.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        rateCallLayout.addView(rateCallDescription, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 16, 4, 16, 0));

        rateCallStarsLayout = new LinearLayout(context);
        rateCallStarsLayout.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 5; i++) {
            RLottieImageView rLottieImageView = new RLottieImageView(context);
            rLottieImageView.setTag(i + 1);
            rLottieImageView.setAnimation(R.raw.call_star, 32, 32);
            rLottieImageView.setProgress(0f);
            rLottieImageView.setOnClickListener(v -> {
                setCallRating((int) v.getTag());
            });
            rateCallStarsLayout.addView(rLottieImageView, LayoutHelper.createLinear(32, 32, i == 0 ? 0 : 6, 0, 0, 0));
        }

        rateCallLayout.addView(rateCallStarsLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 16));
        frameLayout.addView(rateCallLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 50, 346, 50, 0));
        statusLayout = new LinearLayout(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                final VoIPService service = VoIPService.getSharedInstance();
                final CharSequence callingUserTitleText = callingUserTitle.getText();
                if (service != null && !TextUtils.isEmpty(callingUserTitleText)) {
                    final StringBuilder builder = new StringBuilder(callingUserTitleText);

                    builder.append(", ");
                    if (service.privateCall != null && service.privateCall.video) {
                        builder.append(LocaleController.getString("VoipInVideoCallBranding", R.string.VoipInVideoCallBranding));
                    } else {
                        builder.append(LocaleController.getString("VoipInCallBranding", R.string.VoipInCallBranding));
                    }

                    final long callDuration = service.getCallDuration();
                    if (callDuration > 0) {
                        builder.append(", ");
                        builder.append(LocaleController.formatDuration((int) (callDuration / 1000)));
                    }

                    info.setText(builder);
                }
            }
        };
        statusLayout.setOrientation(LinearLayout.VERTICAL);
        statusLayout.setFocusable(true);
        statusLayout.setFocusableInTouchMode(true);

        voIPAvatarView = new VoIPAvatarView(context, callingUser);
        voIPAvatarView.setIsPulsing(true);
        voIPAvatarView.setIsWaving(true);
        voIPAvatarView.setVisibility(View.GONE);

        callingUserTitle = new TextView(context);
        callingUserTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        CharSequence name = ContactsController.formatName(callingUser.first_name, callingUser.last_name);
        name = Emoji.replaceEmoji(name, callingUserTitle.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
        callingUserTitle.setText(name);
        callingUserTitle.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
        callingUserTitle.setTextColor(Color.WHITE);
        callingUserTitle.setGravity(Gravity.CENTER_HORIZONTAL);
        callingUserTitle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        statusLayout.addView(callingUserTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6));

        statusTextView = new VoIPStatusTextView(context);
        ViewCompat.setImportantForAccessibility(statusTextView, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        statusLayout.addView(statusTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6));

        statusLayout.setClipChildren(false);
        statusLayout.setClipToPadding(false);
        statusLayout.setPadding(0, 0, 0, AndroidUtilities.dp(15));

        frameLayout.addView(voIPAvatarView, LayoutHelper.createFrame(VoIPAvatarView.WAVE_BIG_MAX, VoIPAvatarView.WAVE_BIG_MAX, Gravity.CENTER_HORIZONTAL, 0, 90, 0, 0));
        frameLayout.addView(statusLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 69, 0, 0));

        buttonsLayout = new VoIPButtonsLayout(context);
        for (int i = 0; i < 4; i++) {
            bottomButtons[i] = new VoIPToggleButton2(context);
            bottomButtons[i].setVisibility(View.GONE);
            buttonsLayout.addView(bottomButtons[i]);
        }

        acceptDeclineButtons[0] = new VoIPToggleButton2(context, 56f, 50);
        acceptDeclineButtons[0].setOnClickListener(v -> {
            v.getLocationInWindow(lastAcceptButtonLocation);
            acceptDeclineButtons[1].getLocationInWindow(lastDeclineButtonLocation);
            if (currentState != VoIPService.STATE_WAITING_INCOMING) {
                lastAcceptButtonLocation[1] += AndroidUtilities.dp(88);
                lastDeclineButtonLocation[1] += AndroidUtilities.dp(88);
            }
            isAcceptButtonClicked = true;
            needAnimateFromAcceptButton = true;
            acceptDeclineButtons[0].setShowWaves(false);
            acceptDeclineButtons[0].stopLottieAnimation();

            if (currentState != VoIPService.STATE_BUSY) {
                startRevealAnimation(AndroidUtilities.dp(56), new Point(lastAcceptButtonLocation[0] + AndroidUtilities.dp(28), lastAcceptButtonLocation[1] + AndroidUtilities.dp(28)));
            }
            acceptCall();
        });
        acceptDeclineButtons[0].setIsRepeatAnimation(true);
        acceptDeclineButtons[0].setShowWaves(true);
        acceptDeclineButtons[0].setLottieData(R.raw.call_accept, Color.WHITE, 0xFF40C749, LocaleController.getString("AcceptCall", R.string.AcceptCall), false, new String[] {"Shape Layer 2.**", "Shape Layer 1.**", "Call Accept Outlines.**",});
        acceptDeclineButtons[0].setVisibility(View.GONE);

        acceptDeclineButtons[1] = new VoIPToggleButton2(context, 56f, 50);
        acceptDeclineButtons[1].setOnClickListener(v -> declineCall());
        acceptDeclineButtons[1].setLottieData(R.raw.call_decline, Color.WHITE, 0xFFF01D2C, LocaleController.getString("DeclineCall", R.string.DeclineCall), false, new String[] {"Call Decline Outlines.**"});
        acceptDeclineButtons[1].setVisibility(View.GONE);

        frameLayout.addView(buttonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
        frameLayout.addView(acceptDeclineButtons[0], LayoutHelper.createFrame(80, 80, Gravity.BOTTOM | Gravity.START, 42, 0, 0, 54));
        frameLayout.addView(acceptDeclineButtons[1], LayoutHelper.createFrame(80, 80, Gravity.BOTTOM | Gravity.END, 0, 0, 42, 54));

        backIcon = new ImageView(context);
        backIcon.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.3f))));
        backIcon.setImageResource(R.drawable.msg_call_minimize);
        backIcon.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        backIcon.setContentDescription(LocaleController.getString("Back", R.string.Back));
        frameLayout.addView(backIcon, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.LEFT));

        backIcon.setOnClickListener(view -> {
            if (!lockOnScreen) {
                onBackPressed();
            }
        });
        if (windowView.isLockOnScreen()) {
            backIcon.setVisibility(View.GONE);
        }

        notificationsLayout = new VoIPNotificationsLayout(context);
        notificationsLayout.setGravity(Gravity.BOTTOM);
        notificationsLayout.setOnViewsUpdated(() -> {
            previousState = currentState;
            updateViewState();
        });
        frameLayout.addView(notificationsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.BOTTOM, 16, 0, 16, 0));

        centerNotificationsLayout = new VoIPNotificationsLayout(context);
        centerNotificationsLayout.setGravity(Gravity.TOP);
        centerNotificationsLayout.setOnViewsUpdated(() -> {
            previousState = currentState;
            updateViewState();
        });
        frameLayout.addView(centerNotificationsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.TOP, 16, 360, 16, 0));

        tapToVideoTooltip = new HintView(context, 4);
        tapToVideoTooltip.setText(LocaleController.getString("TapToTurnCamera", R.string.TapToTurnCamera));
        frameLayout.addView(tapToVideoTooltip, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 19, 0, 19, 8));
        tapToVideoTooltip.setBottomOffset(AndroidUtilities.dp(4));
        tapToVideoTooltip.setVisibility(View.GONE);

        tapToEmojiTooltip = new HintView(context, 10, true);
        tapToEmojiTooltip.setText(LocaleController.getString("CallEmojiKeyTooltipSmall", R.string.CallEmojiKeyTooltipSmall));
        frameLayout.addView(tapToEmojiTooltip, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));
        tapToEmojiTooltip.setBottomOffset(AndroidUtilities.dp(12));
        tapToEmojiTooltip.setVisibility(View.GONE);

        updateViewState();

        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            if (!isVideoCall) {
                isVideoCall = service.privateCall != null && service.privateCall.video;
            }
            initRenderers();
        }

        return frameLayout;
    }

    private void acceptCall() {
        if (currentState == VoIPService.STATE_BUSY) {
            Intent intent = new Intent(activity, VoIPService.class);
            intent.putExtra("user_id", callingUser.id);
            intent.putExtra("is_outgoing", true);
            intent.putExtra("start_incall_activity", false);
            intent.putExtra("video_call", isVideoCall);
            intent.putExtra("can_video_call", isVideoCall);
            intent.putExtra("account", currentAccount);
            try {
                activity.startService(intent);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, 101);
            } else {
                if (VoIPService.getSharedInstance() != null) {
                    VoIPService.getSharedInstance().acceptIncomingCall();
                    if (currentUserIsVideo) {
                        VoIPService.getSharedInstance().requestVideoCall(false);
                    }
                }
            }
        }
    }

    private void declineCall() {
        if (currentState == VoIPService.STATE_BUSY) {
            windowView.finish();
        } else {
            if (VoIPService.getSharedInstance() != null) {
                VoIPService.getSharedInstance().declineIncomingCall();
            }
        }
    }

    private boolean checkPointerIds(MotionEvent ev) {
        if (ev.getPointerCount() < 2) {
            return false;
        }
        if (pointerId1 == ev.getPointerId(0) && pointerId2 == ev.getPointerId(1)) {
            return true;
        }
        if (pointerId1 == ev.getPointerId(1) && pointerId2 == ev.getPointerId(0)) {
            return true;
        }
        return false;
    }

    private VoIPTextureView getFullscreenTextureView() {
        if (callingUserIsVideo) {
            return callingUserTextureView;
        }
        return currentUserTextureView;
    }

    private void finishZoom() {
        if (zoomStarted) {
            zoomStarted = false;
            zoomBackAnimator = ValueAnimator.ofFloat(1f, 0);

            float fromScale = pinchScale;
            float fromTranslateX = pinchTranslationX;
            float fromTranslateY = pinchTranslationY;
            zoomBackAnimator.addUpdateListener(valueAnimator -> {
                float v = (float) valueAnimator.getAnimatedValue();
                pinchScale = fromScale * v + 1f * (1f - v);
                pinchTranslationX = fromTranslateX * v;
                pinchTranslationY = fromTranslateY * v;
                fragmentView.invalidate();
            });

            zoomBackAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    zoomBackAnimator = null;
                    pinchScale = 1f;
                    pinchTranslationX = 0;
                    pinchTranslationY = 0;
                    fragmentView.invalidate();
                }
            });
            zoomBackAnimator.setDuration(TRANSITION_DURATION);
            zoomBackAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            zoomBackAnimator.start();
        }
        canZoomGesture = false;
        isInPinchToZoomTouchMode = false;
    }

    private void initRenderers() {
        currentUserTextureView.renderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), new RendererCommon.RendererEvents() {
            @Override
            public void onFirstFrameRendered() {
                AndroidUtilities.runOnUIThread(() -> updateViewState());
            }

            @Override
            public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {

            }

        });
        callingUserTextureView.renderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), new RendererCommon.RendererEvents() {
            @Override
            public void onFirstFrameRendered() {
                AndroidUtilities.runOnUIThread(() -> updateViewState());
            }

            @Override
            public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {

            }

        }, EglBase.CONFIG_PLAIN, new GlRectDrawer());

        callingUserMiniTextureRenderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), null);
    }

    public void switchToPip() {
        if (isFinished || !AndroidUtilities.checkInlinePermissions(activity) || instance == null) {
            return;
        }
        isFinished = true;
        if (VoIPService.getSharedInstance() != null) {
            int h = instance.windowView.getMeasuredHeight();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                h -= instance.lastInsets.getSystemWindowInsetBottom();
            }
            VoIPPiPView.show(instance.activity, instance.currentAccount, instance.windowView.getMeasuredWidth(), h, VoIPPiPView.ANIMATION_ENTER_TYPE_TRANSITION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                VoIPPiPView.topInset = instance.lastInsets.getSystemWindowInsetTop();
                VoIPPiPView.bottomInset = instance.lastInsets.getSystemWindowInsetBottom();
            }
        }
        if (VoIPPiPView.getInstance() == null) {
            return;
        }

        backIcon.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        emojiContainer.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        statusLayout.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        buttonsLayout.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        bottomShadow.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        topShadow.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        callingUserMiniFloatingLayout.animate().alpha(0).setDuration(350)
            .setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        notificationsLayout.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT)
            .start();
        centerNotificationsLayout.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT)
            .start();

        VoIPPiPView.switchingToPip = true;
        switchingToPip = true;
        Animator animator = createPiPTransition(false);
        animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, null);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                VoIPPiPView.getInstance().windowView.setAlpha(1f);
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex);
                    VoIPPiPView.getInstance().onTransitionEnd();
                    currentUserCameraFloatingLayout.setCornerRadius(-1f);
                    callingUserTextureView.renderer.release();
                    currentUserTextureView.renderer.release();
                    callingUserMiniTextureRenderer.release();
                    destroy();
                    windowView.finishImmediate();
                    VoIPPiPView.switchingToPip = false;
                    switchingToPip = false;
                    instance = null;
                }, 200);
            }
        });
        animator.setDuration(350);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.start();
    }

    public void startTransitionFromPiP() {
        enterFromPiP = true;
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null && service.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE) {
            callingUserTextureView.setStub(VoIPPiPView.getInstance().callingUserTextureView);
            currentUserTextureView.setStub(VoIPPiPView.getInstance().currentUserTextureView);
        }
        windowView.setAlpha(0f);
        updateViewState();
        switchingToPip = true;
        VoIPPiPView.switchingToPip = true;
        VoIPPiPView.prepareForTransition();
        animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, null);
        AndroidUtilities.runOnUIThread(() -> {
            windowView.setAlpha(1f);
            Animator animator = createPiPTransition(true);

            backIcon.setAlpha(0f);
            hideEmoji.setAlpha(0f);
            emojiContainer.setAlpha(0f);
            statusLayout.setAlpha(0f);
            buttonsLayout.setAlpha(0f);
            bottomShadow.setAlpha(0f);
            topShadow.setAlpha(0f);
            notificationsLayout.setAlpha(0f);
            centerNotificationsLayout.setAlpha(0f);
            callingUserPhotoView.setAlpha(0f);

            currentUserCameraFloatingLayout.switchingToPip = true;
            AndroidUtilities.runOnUIThread(() -> {
                VoIPPiPView.switchingToPip = false;
                VoIPPiPView.finish();
                backIcon.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                emojiContainer.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .start();
                statusLayout.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .start();
                buttonsLayout.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .start();
                if (currentUserIsVideo || callingUserIsVideo) {
                    bottomShadow.animate().alpha(1f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT)
                        .start();
                    topShadow.animate().alpha(1f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT)
                        .start();
                }
                notificationsLayout.animate().alpha(1f).setDuration(350)
                    .setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                if (!currentUserIsVideo && !callingUserIsVideo) {
                    centerNotificationsLayout.animate().alpha(1f).setDuration(350)
                        .setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                }
                callingUserPhotoView.animate().alpha(1f).setDuration(350)
                    .setInterpolator(CubicBezierInterpolator.DEFAULT).start();

                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex);
                        currentUserCameraFloatingLayout.setCornerRadius(-1f);
                        switchingToPip = false;
                        currentUserCameraFloatingLayout.switchingToPip = false;
                        previousState = currentState;
                        updateViewState();
                    }
                });
                animator.setDuration(350);
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.start();
            }, 32);
        }, 32);

    }

    public Animator createPiPTransition(boolean enter) {
        currentUserCameraFloatingLayout.animate().cancel();
        float toX = VoIPPiPView.getInstance().windowLayoutParams.x + VoIPPiPView.getInstance().xOffset;
        float toY = VoIPPiPView.getInstance().windowLayoutParams.y + VoIPPiPView.getInstance().yOffset;

        float cameraFromX = currentUserCameraFloatingLayout.getX();
        float cameraFromY = currentUserCameraFloatingLayout.getY();
        float cameraFromScale = currentUserCameraFloatingLayout.getScaleX();
        boolean animateCamera = true;

        float callingUserFromX = 0;
        float callingUserFromY = 0;
        float callingUserFromScale = 1f;
        float callingUserToScale, callingUserToX, callingUserToY;
        float cameraToScale, cameraToX, cameraToY;

        float pipScale = VoIPPiPView.isExpanding() ? 0.4f : 0.25f;
        callingUserToScale = pipScale;
        callingUserToX = toX - (callingUserTextureView.getMeasuredWidth() - callingUserTextureView.getMeasuredWidth() * callingUserToScale) / 2f;
        callingUserToY = toY - (callingUserTextureView.getMeasuredHeight() - callingUserTextureView.getMeasuredHeight() * callingUserToScale) / 2f;
        if (callingUserIsVideo) {
            int currentW = currentUserCameraFloatingLayout.getMeasuredWidth();
            if (currentUserIsVideo && currentW != 0) {
                cameraToScale = (windowView.getMeasuredWidth() / (float) currentW) * pipScale * 0.4f;
                cameraToX = toX - (currentUserCameraFloatingLayout.getMeasuredWidth() - currentUserCameraFloatingLayout.getMeasuredWidth() * cameraToScale) / 2f +
                    VoIPPiPView.getInstance().parentWidth * pipScale - VoIPPiPView.getInstance().parentWidth * pipScale * 0.4f - AndroidUtilities.dp(4);
                cameraToY = toY - (currentUserCameraFloatingLayout.getMeasuredHeight() - currentUserCameraFloatingLayout.getMeasuredHeight() * cameraToScale) / 2f +
                    VoIPPiPView.getInstance().parentHeight * pipScale - VoIPPiPView.getInstance().parentHeight * pipScale * 0.4f - AndroidUtilities.dp(4);
            } else {
                cameraToScale = 0;
                cameraToX = 1f;
                cameraToY = 1f;
                animateCamera = false;
            }
        } else {
            cameraToScale = pipScale;
            cameraToX = toX - (currentUserCameraFloatingLayout.getMeasuredWidth() - currentUserCameraFloatingLayout.getMeasuredWidth() * cameraToScale) / 2f;
            cameraToY = toY - (currentUserCameraFloatingLayout.getMeasuredHeight() - currentUserCameraFloatingLayout.getMeasuredHeight() * cameraToScale) / 2f;
        }

        float cameraCornerRadiusFrom = callingUserIsVideo ? AndroidUtilities.dp(4) : 0;
        float cameraCornerRadiusTo = AndroidUtilities.dp(4) * 1f / cameraToScale;

        float fromCameraAlpha = 1f;
        float toCameraAlpha = 1f;
        if (callingUserIsVideo) {
            fromCameraAlpha = VoIPPiPView.isExpanding() ? 1f : 0f;
        }

        if (enter) {
            if (animateCamera) {
                currentUserCameraFloatingLayout.setScaleX(cameraToScale);
                currentUserCameraFloatingLayout.setScaleY(cameraToScale);
                currentUserCameraFloatingLayout.setTranslationX(cameraToX);
                currentUserCameraFloatingLayout.setTranslationY(cameraToY);
                currentUserCameraFloatingLayout.setCornerRadius(cameraCornerRadiusTo);
                currentUserCameraFloatingLayout.setAlpha(fromCameraAlpha);
            }
            callingUserTextureView.setScaleX(callingUserToScale);
            callingUserTextureView.setScaleY(callingUserToScale);
            callingUserTextureView.setTranslationX(callingUserToX);
            callingUserTextureView.setTranslationY(callingUserToY);
            callingUserTextureView.setRoundCorners(AndroidUtilities.dp(6) * 1f / callingUserToScale);

            callingUserPhotoView.setAlpha(0f);
            callingUserPhotoView.setScaleX(callingUserToScale);
            callingUserPhotoView.setScaleY(callingUserToScale);
            callingUserPhotoView.setTranslationX(callingUserToX);
            callingUserPhotoView.setTranslationY(callingUserToY);
        }
        ValueAnimator animator = ValueAnimator.ofFloat(enter ? 1f : 0, enter ? 0 : 1f);

        enterTransitionProgress = enter ? 0f : 1f;
        updateSystemBarColors();

        boolean finalAnimateCamera = animateCamera;
        float finalFromCameraAlpha = fromCameraAlpha;
        animator.addUpdateListener(valueAnimator -> {
            float v = (float) valueAnimator.getAnimatedValue();
            enterTransitionProgress = 1f - v;
            updateSystemBarColors();

            if (finalAnimateCamera) {
                float cameraScale = cameraFromScale * (1f - v) + cameraToScale * v;
                currentUserCameraFloatingLayout.setScaleX(cameraScale);
                currentUserCameraFloatingLayout.setScaleY(cameraScale);
                currentUserCameraFloatingLayout.setTranslationX(cameraFromX * (1f - v) + cameraToX * v);
                currentUserCameraFloatingLayout.setTranslationY(cameraFromY * (1f - v) + cameraToY * v);
                currentUserCameraFloatingLayout.setCornerRadius(cameraCornerRadiusFrom * (1f - v) + cameraCornerRadiusTo * v);
                currentUserCameraFloatingLayout.setAlpha(toCameraAlpha * (1f - v) + finalFromCameraAlpha * v);
            }

            float callingUserScale = callingUserFromScale * (1f - v) + callingUserToScale * v;
            callingUserTextureView.setScaleX(callingUserScale);
            callingUserTextureView.setScaleY(callingUserScale);
            float tx = callingUserFromX * (1f - v) + callingUserToX * v;
            float ty = callingUserFromY * (1f - v) + callingUserToY * v;

            callingUserTextureView.setTranslationX(tx);
            callingUserTextureView.setTranslationY(ty);
            callingUserTextureView.setRoundCorners(v * AndroidUtilities.dp(4) * 1 / callingUserScale);
            if (!currentUserCameraFloatingLayout.measuredAsFloatingMode) {
                currentUserTextureView.setScreenshareMiniProgress(v, false);
            }

            callingUserPhotoView.setScaleX(callingUserScale);
            callingUserPhotoView.setScaleY(callingUserScale);
            callingUserPhotoView.setTranslationX(tx);
            callingUserPhotoView.setTranslationY(ty);
            callingUserPhotoView.setAlpha(1f - v);
        });
        return animator;
    }

    private ValueAnimator emojiAnimator;

    private void expandEmoji(boolean expanded) {
        if (!emojiLoaded || emojiExpanded == expanded || !uiVisible) {
            return;
        }
        emojiExpanded = expanded;
        if (emojiAnimator != null) {
            emojiAnimator.cancel();
        }

        boolean allEmojiAnimated = true;
        for (int i = 0; i < 4; i++) {
            if (reactions[i] == null || reactions[i].center_icon == null) {
                allEmojiAnimated = false;
                break;
            }
        }
        boolean finalAllEmojiAnimated = allEmojiAnimated;

        if (voIPAvatarView.getTag() != null) {
            voIPAvatarView.setPivotX(voIPAvatarView.getMeasuredWidth() / 2f);
            voIPAvatarView.setPivotY(voIPAvatarView.getMeasuredHeight());
        }

        if (expanded) {
            AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
            hideUiRunnableWaiting = false;

            if (emojiRationalTitleTextView.getVisibility() != View.VISIBLE) {
                emojiRationalTitleTextView.setVisibility(View.VISIBLE);
                emojiRationalTitleTextView.setAlpha(0f);
            }
            if (emojiRationalTextView.getVisibility() != View.VISIBLE) {
                emojiRationalTextView.setVisibility(View.VISIBLE);
                emojiRationalTextView.setAlpha(0f);
            }

            if (hideEmoji.getVisibility() != View.VISIBLE) {
                hideEmoji.setVisibility(View.VISIBLE);
                hideEmoji.setAlpha(0f);
            }

            statusLayout.setTranslationY(statusLayoutAnimateToOffset);
        } else {
            if (voIPAvatarView.getTag() != null) {
                voIPAvatarView.setAlpha(0);
                voIPAvatarView.setScaleX(0);
                voIPAvatarView.setScaleY(0);
                statusLayout.setAlpha(1f);
            } else {
                statusLayout.setAlpha(0);
                statusLayout.setTranslationY(statusLayoutAnimateToOffset);
            }
        }
        float emojiDefaultSize = AndroidUtilities.dp(24);
        float emojiExpandedSize = AndroidUtilities.dp(40);
        float scale2 = emojiExpandedSize / emojiDefaultSize;

        emojiAnimator = ValueAnimator.ofFloat(expanded ? 0f : 1f, expanded ? 1f : 0);
        emojiAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();

            float emojiLayoutScale = 1 + (scale2 - 1) * progress;
            emojiLayout.setScaleX(emojiLayoutScale);
            emojiLayout.setScaleY(emojiLayoutScale);
            emojiLayout.setPadding(0, (int) (AndroidUtilities.dp(32) * progress), 0, (int) (AndroidUtilities.dp(16) + AndroidUtilities.dp(16) * progress));

            emojiRationalTitleTextView.setAlpha(progress);
            emojiRationalTextView.setAlpha(progress);
            int bgColor = ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.12f * progress));
            emojiContainer.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(24), bgColor));
            emojiContainer.setTranslationY(AndroidUtilities.dp(92) * progress);

            if (voIPAvatarView.getTag() != null) {
                voIPAvatarView.setAlpha(1f - progress);
                voIPAvatarView.setScaleX(1f - progress);
                voIPAvatarView.setScaleY(1f - progress);
                statusLayout.setTranslationY(statusLayoutAnimateToOffset + AndroidUtilities.dp(48) * progress);
            } else {
                statusLayout.setAlpha(1f - progress);
            }

            centerNotificationsLayout.setTranslationY(AndroidUtilities.dp(48) * progress);

            hideEmoji.setAlpha(progress);
        });

        emojiAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                emojiAnimator = null;
                if (voIPAvatarView.getTag() != null) {
                    voIPAvatarView.setAlpha(expanded ? 0 : 1f);
                    voIPAvatarView.setScaleX(expanded ? 0 : 1f);
                    voIPAvatarView.setScaleY(expanded ? 0 : 1f);
                }

                if (expanded) {
                    if (finalAllEmojiAnimated) {
                        for (int i = 0; i < 4; i++) {
                            if (emojiViews[i].getImageReceiver().getLottieAnimation() != null) {
                                emojiViews[i].getImageReceiver().getLottieAnimation().setCurrentFrame(0, false);
                                emojiViews[i].getImageReceiver().getLottieAnimation().setAutoRepeatCount(2);
                                emojiViews[i].getImageReceiver().getLottieAnimation().start();
                            }
                        }
                    }

                    if (voIPAvatarView.getTag() != null) {
                        statusLayout.setTranslationY(statusLayoutAnimateToOffset + AndroidUtilities.dp(48));
                        statusLayout.setAlpha(1f);
                    } else {
                        statusLayout.setAlpha(0f);
                    }
                } else {
                    if (finalAllEmojiAnimated) {
                        for (int i = 0; i < 4; i++) {
                            if (emojiViews[i].getImageReceiver().getLottieAnimation() != null) {
                                emojiViews[i].getImageReceiver().getLottieAnimation().setAutoRepeatCount(1);
                                emojiViews[i].getImageReceiver().getLottieAnimation().start();
                            }
                        }

                    }
                    emojiRationalTitleTextView.setVisibility(View.GONE);
                    emojiRationalTextView.setVisibility(View.GONE);
                    hideEmoji.setVisibility(View.GONE);

                    if (voIPAvatarView.getTag() != null) {
                        voIPAvatarView.setVisibility(View.VISIBLE);
                    }
                    statusLayout.setAlpha(1f);
                    statusLayout.setTranslationY(statusLayoutAnimateToOffset);

                    VoIPService service = VoIPService.getSharedInstance();
                    if (canHideUI && !hideUiRunnableWaiting && service != null && !service.isMicMute()) {
                        AndroidUtilities.runOnUIThread(hideUIRunnable, 3000);
                        hideUiRunnableWaiting = true;
                    }
                }
            }
        });

        emojiAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        emojiAnimator.setDuration(300).start();
    }

    private void updateViewState() {
        if (isFinished || switchingToPip) {
            return;
        }
        lockOnScreen = false;
        boolean animated = previousState != -1;
        boolean animatedButtons = previousState != -1 || currentState == VoIPService.STATE_REQUESTING;
        boolean showAcceptDeclineView = false;
        boolean showTimer = false;
        boolean showReconnecting = false;
        boolean showCallingAvatarMini = true;
        int statusLayoutOffset;
        VoIPService service = VoIPService.getSharedInstance();
        switch (currentState) {
            case VoIPService.STATE_WAITING_INCOMING:
                showAcceptDeclineView = true;
                lockOnScreen = true;

                acceptDeclineButtons[0].setShowWaves(true);
                acceptDeclineButtons[0].setIsRepeatAnimation(true);
                acceptDeclineButtons[0].setLottieData(R.raw.call_accept, Color.WHITE, 0xFF40C749, LocaleController.getString("AcceptCall", R.string.AcceptCall), false, new String[] {"Shape Layer 2.**", "Shape Layer 1.**", "Call Accept Outlines.**",});
                acceptDeclineButtons[0].startLottieAnimation();

                voIPAvatarView.setIsPulsing(true);
                if (service != null && service.privateCall.video) {
                    statusTextView.setText(LocaleController.getString("VoipInVideoCallBranding", R.string.VoipInVideoCallBranding), true, animated);
                } else {
                    statusTextView.setText(LocaleController.getString("VoipInCallBranding", R.string.VoipInCallBranding), true, animated);
                }
                acceptDeclineButtons[0].setTranslationY(-AndroidUtilities.dp(60));
                acceptDeclineButtons[1].setTranslationY(-AndroidUtilities.dp(60));
                break;
            case VoIPService.STATE_WAIT_INIT:
            case VoIPService.STATE_WAIT_INIT_ACK:
                statusTextView.setText(LocaleController.getString("VoipConnecting", R.string.VoipConnecting), true, animated);
                break;
            case VoIPService.STATE_EXCHANGING_KEYS:
                statusTextView.setText(LocaleController.getString("VoipExchangingKeys", R.string.VoipExchangingKeys), true, animated);
                break;
            case VoIPService.STATE_WAITING:
                statusTextView.setText(LocaleController.getString("VoipWaiting", R.string.VoipWaiting), true, animated);
                break;
            case VoIPService.STATE_RINGING:
                statusTextView.setText(LocaleController.getString("VoipRinging", R.string.VoipRinging), true, animated);
                break;
            case VoIPService.STATE_REQUESTING:
                statusTextView.setText(LocaleController.getString("VoipRequesting", R.string.VoipRequesting), true, animated);
                voIPAvatarView.setIsPulsing(true);
                voIPAvatarView.setIsWaving(true);
                break;
            case VoIPService.STATE_HANGING_UP:
                break;
            case VoIPService.STATE_BUSY:
                showAcceptDeclineView = true;
                statusTextView.setText(LocaleController.getString("VoipBusy", R.string.VoipBusy), false, animated);
                voIPAvatarView.setIsWaving(false);
                voIPAvatarView.setIsPulsing(false);

                acceptDeclineButtons[0].stopLottieAnimation();
                acceptDeclineButtons[0].setShowWaves(false);
                acceptDeclineButtons[0].setIsRepeatAnimation(false);
                acceptDeclineButtons[0].setLottieData(R.raw.call_accept, Color.WHITE, 0xFF40C749, LocaleController.getString("RetryCall", R.string.RetryCall), false, new String[] {"Shape Layer 2.**", "Shape Layer 1.**", "Call Accept Outlines.**",});
                isAcceptButtonClicked = false;
                needAnimateFromAcceptButton = false;
                currentUserIsVideo = false;
                callingUserIsVideo = false;
                break;
            case VoIPService.STATE_ESTABLISHED:
            case VoIPService.STATE_RECONNECTING:
                updateKeyView(animated);
                showTimer = true;
                if (currentState == VoIPService.STATE_RECONNECTING) {
                    showReconnecting = true;
                }
                voIPAvatarView.setIsPulsing(false);
                voIPAvatarView.setIsWaving(true);
                if (currentState == VoIPService.STATE_ESTABLISHED) {
                    if (!callingUserIsVideo && !currentUserIsVideo) {
                        if (isOutgoing || !isAcceptButtonClicked) {
                            startRevealAnimation(AndroidUtilities.dp(VoIPAvatarView.WAVE_MAX_RADIUS), new Point((int) voIPAvatarView.getX() + AndroidUtilities.dp(VoIPAvatarView.WAVE_BIG_MAX) / 2, (int) voIPAvatarView.getY() + AndroidUtilities.dp(VoIPAvatarView.WAVE_BIG_MAX) / 2));
                        }
                    }
                }
                break;
            case VoIPService.STATE_ENDED:
                currentUserTextureView.saveCameraLastBitmap();
                if (!shouldNavigateToRateCallLayout) {
                    AndroidUtilities.runOnUIThread(() -> windowView.finish(), 200);
                }
                break;
            case VoIPService.STATE_FAILED:
                statusTextView.setText(LocaleController.getString("VoipFailed", R.string.VoipFailed), false, animated);
                voIPAvatarView.setIsWaving(false);
                voIPAvatarView.setIsPulsing(false);
                final VoIPService voipService = VoIPService.getSharedInstance();
                final String lastError = voipService != null ? voipService.getLastError() : Instance.ERROR_UNKNOWN;
                if (!TextUtils.equals(lastError, Instance.ERROR_UNKNOWN)) {
                    if (TextUtils.equals(lastError, Instance.ERROR_INCOMPATIBLE)) {
                        final String name = ContactsController.formatName(callingUser.first_name, callingUser.last_name);
                        final String message = LocaleController.formatString("VoipPeerIncompatible", R.string.VoipPeerIncompatible, name);
                        showErrorDialog(AndroidUtilities.replaceTags(message));
                    } else if (TextUtils.equals(lastError, Instance.ERROR_PEER_OUTDATED)) {
                        if (isVideoCall) {
                            final String name = UserObject.getFirstName(callingUser);
                            final String message = LocaleController.formatString("VoipPeerVideoOutdated", R.string.VoipPeerVideoOutdated, name);
                            boolean[] callAgain = new boolean[1];
                            AlertDialog dlg = new DarkAlertDialog.Builder(activity)
                                .setTitle(LocaleController.getString("VoipFailed", R.string.VoipFailed))
                                .setMessage(AndroidUtilities.replaceTags(message))
                                .setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), (dialogInterface, i) -> windowView.finish())
                                .setPositiveButton(LocaleController.getString("VoipPeerVideoOutdatedMakeVoice", R.string.VoipPeerVideoOutdatedMakeVoice), (dialogInterface, i) -> {
                                    callAgain[0] = true;
                                    currentState = VoIPService.STATE_BUSY;
                                    Intent intent = new Intent(activity, VoIPService.class);
                                    intent.putExtra("user_id", callingUser.id);
                                    intent.putExtra("is_outgoing", true);
                                    intent.putExtra("start_incall_activity", false);
                                    intent.putExtra("video_call", false);
                                    intent.putExtra("can_video_call", false);
                                    intent.putExtra("account", currentAccount);
                                    try {
                                        activity.startService(intent);
                                    } catch (Throwable e) {
                                        FileLog.e(e);
                                    }
                                })
                                .show();
                            dlg.setCanceledOnTouchOutside(true);
                            dlg.setOnDismissListener(dialog -> {
                                if (!callAgain[0]) {
                                    windowView.finish();
                                }
                            });
                        } else {
                            final String name = UserObject.getFirstName(callingUser);
                            final String message = LocaleController.formatString("VoipPeerOutdated", R.string.VoipPeerOutdated, name);
                            showErrorDialog(AndroidUtilities.replaceTags(message));
                        }
                    } else if (TextUtils.equals(lastError, Instance.ERROR_PRIVACY)) {
                        final String name = ContactsController.formatName(callingUser.first_name, callingUser.last_name);
                        final String message = LocaleController.formatString("CallNotAvailable", R.string.CallNotAvailable, name);
                        showErrorDialog(AndroidUtilities.replaceTags(message));
                    } else if (TextUtils.equals(lastError, Instance.ERROR_AUDIO_IO)) {
                        showErrorDialog("Error initializing audio hardware");
                    } else if (TextUtils.equals(lastError, Instance.ERROR_LOCALIZED)) {
                        windowView.finish();
                    } else if (TextUtils.equals(lastError, Instance.ERROR_CONNECTION_SERVICE)) {
                        showErrorDialog(LocaleController.getString("VoipErrorUnknown", R.string.VoipErrorUnknown));
                    } else {
                        AndroidUtilities.runOnUIThread(() -> windowView.finish(), 1000);
                    }
                } else {
                    AndroidUtilities.runOnUIThread(() -> windowView.finish(), 1000);
                }
                voIPAvatarView.setIsPulsing(false);
                break;
        }
        if (previewDialog != null) {
            return;
        }

        if (service != null) {
            callingUserIsVideo = service.getRemoteVideoState() == Instance.VIDEO_STATE_ACTIVE;
            currentUserIsVideo = service.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE || service.getVideoState(false) == Instance.VIDEO_STATE_PAUSED;
            if (currentUserIsVideo && !isVideoCall) {
                isVideoCall = true;
            }
        }

        if (animated) {
            currentUserCameraFloatingLayout.saveRelativePosition();
            callingUserMiniFloatingLayout.saveRelativePosition();
        }

        if (callingUserIsVideo) {
            if (!switchingToPip) {
                callingUserPhotoView.setAlpha(1f);
            }
            if (animated) {
                callingUserTextureView.animate().alpha(1f).setDuration(250).start();
            } else {
                callingUserTextureView.animate().cancel();
                callingUserTextureView.setAlpha(1f);
            }
            if (!callingUserTextureView.renderer.isFirstFrameRendered() && !enterFromPiP) {
                callingUserIsVideo = false;
            }
        }

        if (currentUserIsVideo || callingUserIsVideo) {
            showCallingAvatarMini = false;
        } else {
            callingUserPhotoView.setVisibility(View.VISIBLE);
            if (animated) {
                callingUserTextureView.animate().alpha(0f).setDuration(250).start();
            } else {
                callingUserTextureView.animate().cancel();
                callingUserTextureView.setAlpha(0f);
            }
        }

        if (!currentUserIsVideo || !callingUserIsVideo) {
            cameraForceExpanded = false;
        }

        boolean showCallingUserVideoMini = currentUserIsVideo && cameraForceExpanded;

        showCallingUserAvatarMini(showCallingAvatarMini, animated);
        statusLayoutOffset = voIPAvatarView.getTag() == null ? 0 : AndroidUtilities.dp(200);
        showAcceptDeclineView(showAcceptDeclineView, animated);
        windowView.setLockOnScreen(lockOnScreen || deviceIsLocked);
        boolean prevCanHideUi = canHideUI;
        canHideUI = (currentState == VoIPService.STATE_ESTABLISHED) && (currentUserIsVideo || callingUserIsVideo);
        if (!canHideUI && !uiVisible) {
            showUi(true);
        }
        if (prevCanHideUi != canHideUI) {
            if (!canHideUI) {
                scheduleStopAnimations();
            } else {
                stopHeavyAnimations();
            }
        }

        showShadows(uiVisible && (currentUserIsVideo || callingUserIsVideo));

        boolean canDisplayCentralNotification = !currentUserIsVideo && !callingUserIsVideo;
        if (animated) {
            centerNotificationsLayout.animate().alpha(canDisplayCentralNotification ? 1f : 0f).setDuration(150)
                .setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        } else {
            centerNotificationsLayout.setAlpha(canDisplayCentralNotification ? 1f : 0f);
        }

        if (uiVisible && canHideUI && !hideUiRunnableWaiting && service != null && !service.isMicMute()) {
            AndroidUtilities.runOnUIThread(hideUIRunnable, 3000);
            hideUiRunnableWaiting = true;
        } else if (service != null && service.isMicMute()) {
            AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
            hideUiRunnableWaiting = false;
        }

        if (animated) {
            if (lockOnScreen || !uiVisible) {
                if (backIcon.getVisibility() != View.VISIBLE) {
                    backIcon.setVisibility(View.VISIBLE);
                    backIcon.setAlpha(0f);
                }
                backIcon.animate().alpha(0f).start();
            } else {
                backIcon.animate().alpha(1f).start();
            }
            notificationsLayout.animate()
                .translationY(-AndroidUtilities.dp(16) - (uiVisible ? AndroidUtilities.dp(80) : 0)).setDuration(150)
                .setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        } else {
            if (!lockOnScreen) {
                backIcon.setVisibility(View.VISIBLE);
            }
            backIcon.setAlpha(lockOnScreen ? 0 : 1f);
            notificationsLayout.setTranslationY(-AndroidUtilities.dp(16) - (uiVisible ? AndroidUtilities.dp(80) : 0));
        }

        if (currentState != VoIPService.STATE_HANGING_UP && currentState != VoIPService.STATE_ENDED) {
            updateButtons(animatedButtons);
        }

        if (showTimer) {
            statusTextView.showTimer(animated);
        }

        statusTextView.showReconnect(showReconnecting, animated);

        if (!emojiExpanded) {
            if (animated) {
                if (statusLayoutOffset != statusLayoutAnimateToOffset) {
                    statusLayout.animate().translationY(statusLayoutOffset).setDuration(150)
                        .setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                }
            } else {
                statusLayout.setTranslationY(statusLayoutOffset);
            }
        }

        statusLayoutAnimateToOffset = statusLayoutOffset;
        overlayBackground.setShowBlackout(currentUserIsVideo || callingUserIsVideo, animated);
        canSwitchToPip = (currentState != VoIPService.STATE_ENDED && currentState != VoIPService.STATE_BUSY) && (currentUserIsVideo || callingUserIsVideo);

        int floatingViewsOffset;
        if (service != null) {
            if (currentUserIsVideo) {
                service.sharedUIParams.tapToVideoTooltipWasShowed = true;
            }
            currentUserTextureView.setIsScreencast(service.isScreencast());
            currentUserTextureView.renderer.setMirror(service.isFrontFaceCamera());
            service.setSinks(currentUserIsVideo && !service.isScreencast() ? currentUserTextureView.renderer : null, showCallingUserVideoMini ? callingUserMiniTextureRenderer : callingUserTextureView.renderer);

            if (animated) {
                notificationsLayout.beforeLayoutChanges();
            }
            if ((currentUserIsVideo || callingUserIsVideo) && (currentState == VoIPService.STATE_ESTABLISHED || currentState == VoIPService.STATE_RECONNECTING) && service.getCallDuration() > 500) {
                if (service.getRemoteAudioState() == Instance.AUDIO_STATE_MUTED) {
                    notificationsLayout.addNotification(LocaleController.formatString("VoipUserMicrophoneIsOff", R.string.VoipUserMicrophoneIsOff, UserObject.getFirstName(callingUser)), "muted");
                } else {
                    notificationsLayout.removeNotification("muted");
                }
                if (service.isMicMute()) {
                    notificationsLayout.addNotification(LocaleController.getString("VoipYouMicrophoneIsOff", R.string.VoipYouMicrophoneIsOff), "your_mic_muted");
                } else {
                    notificationsLayout.removeNotification("your_mic_muted");
                }
                if (service.getRemoteVideoState() == Instance.VIDEO_STATE_INACTIVE) {
                    notificationsLayout.addNotification(LocaleController.formatString("VoipUserCameraIsOff", R.string.VoipUserCameraIsOff, UserObject.getFirstName(callingUser)), "video");
                } else {
                    notificationsLayout.removeNotification("video");
                }
            } else {
                if (service.getRemoteAudioState() == Instance.AUDIO_STATE_MUTED) {
                    notificationsLayout.addNotification(LocaleController.formatString("VoipUserMicrophoneIsOff", R.string.VoipUserMicrophoneIsOff, UserObject.getFirstName(callingUser)), "muted");
                } else {
                    notificationsLayout.removeNotification("muted");
                }
                if (service.isMicMute()) {
                    notificationsLayout.addNotification(LocaleController.getString("VoipYouMicrophoneIsOff", R.string.VoipYouMicrophoneIsOff), "your_mic_muted");
                } else {
                    notificationsLayout.removeNotification("your_mic_muted");
                }
                notificationsLayout.removeNotification("video");
            }

            if (notificationsLayout.getChildCount() == 0 && callingUserIsVideo && service.privateCall != null && !service.privateCall.video && !service.sharedUIParams.tapToVideoTooltipWasShowed) {
                service.sharedUIParams.tapToVideoTooltipWasShowed = true;
                tapToVideoTooltip.showForView(bottomButtons[1], true);
            } else if (notificationsLayout.getChildCount() != 0) {
                tapToVideoTooltip.hide();
            }

            if (animated) {
                notificationsLayout.animateLayoutChanges();
            }
        }

        floatingViewsOffset = notificationsLayout.getChildrenHeight();

        callingUserMiniFloatingLayout.setBottomOffset(floatingViewsOffset, animated);
        currentUserCameraFloatingLayout.setBottomOffset(floatingViewsOffset, animated);
        currentUserCameraFloatingLayout.setUiVisible(uiVisible);
        callingUserMiniFloatingLayout.setUiVisible(uiVisible);

        if (currentUserIsVideo) {
            if (!callingUserIsVideo || cameraForceExpanded) {
                showFloatingLayout(STATE_FULLSCREEN, animated);
            } else {
                showFloatingLayout(STATE_FLOATING, animated);
            }
        } else {
            showFloatingLayout(STATE_GONE, animated);
        }

        if (showCallingUserVideoMini && callingUserMiniFloatingLayout.getTag() == null) {
            callingUserMiniFloatingLayout.setIsActive(true);
            if (callingUserMiniFloatingLayout.getVisibility() != View.VISIBLE) {
                callingUserMiniFloatingLayout.setVisibility(View.VISIBLE);
                callingUserMiniFloatingLayout.setAlpha(0f);
                callingUserMiniFloatingLayout.setPivotX(callingUserMiniFloatingLayout.getMeasuredWidth());
                callingUserMiniFloatingLayout.setPivotY(callingUserMiniFloatingLayout.getMeasuredHeight());
                callingUserMiniFloatingLayout.setScaleX(0f);
                callingUserMiniFloatingLayout.setScaleY(0f);
            }
            callingUserMiniFloatingLayout.animate().setListener(null).cancel();
            callingUserMiniFloatingLayout.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(250)
                .setInterpolator(CubicBezierInterpolator.DEFAULT).setStartDelay(50).start();
            callingUserMiniFloatingLayout.setTag(1);
        } else if (!showCallingUserVideoMini && callingUserMiniFloatingLayout.getTag() != null) {

            callingUserMiniFloatingLayout.setIsActive(false);
            callingUserMiniFloatingLayout.animate().alpha(0).scaleX(0f).scaleY(0f)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (callingUserMiniFloatingLayout.getTag() == null) {
                            callingUserMiniFloatingLayout.setVisibility(View.GONE);
                        }
                    }
                }).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            callingUserMiniFloatingLayout.setTag(null);
        }

        currentUserCameraFloatingLayout.restoreRelativePosition();
        callingUserMiniFloatingLayout.restoreRelativePosition();
    }

    private void showUi(boolean show) {
        if (uiVisibilityAnimator != null) {
            uiVisibilityAnimator.cancel();
        }

        if (!show && uiVisible) {
            backIcon.animate().alpha(0).translationY(-AndroidUtilities.dp(50)).setDuration(150)
                .setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            emojiContainer.animate().alpha(0).translationY(-AndroidUtilities.dp(50)).setDuration(150)
                .setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            statusLayout.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            centerNotificationsLayout.animate().alpha(0).setDuration(150)
                .setInterpolator(CubicBezierInterpolator.DEFAULT).start();

            buttonsLayout.animate().alpha(0).translationY(AndroidUtilities.dp(50)).setDuration(150)
                .setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
            hideUiRunnableWaiting = false;
            buttonsLayout.setEnabled(false);
        } else if (show && !uiVisible) {
            tapToVideoTooltip.hide();
            tapToEmojiTooltip.hide();
            backIcon.animate().alpha(1f).translationY(0).setDuration(150)
                .setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            emojiContainer.animate().alpha(1f).translationY(0).setDuration(150)
                .setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            if ((!currentUserIsVideo && !callingUserIsVideo) || !emojiExpanded) {
                statusLayout.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .start();
            }
            if (!currentUserIsVideo && !callingUserIsVideo) {
                centerNotificationsLayout.animate().alpha(1).setDuration(150)
                    .setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            }
            buttonsLayout.animate().alpha(1f).translationY(0).setDuration(150)
                .setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            buttonsLayout.setEnabled(true);
        }

        uiVisible = show;
        windowView.requestFullscreen(!show);
        notificationsLayout.animate().translationY(-AndroidUtilities.dp(16) - (uiVisible ? AndroidUtilities.dp(80) : 0))
            .setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
    }

    private void showShadows(boolean show) {
        if (show == shadowsVisible) {
            return;
        }

        if (show) {
            bottomShadow.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            topShadow.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            uiVisibilityAnimator = ValueAnimator.ofFloat(uiVisibilityAlpha, 1f);
        } else {
            bottomShadow.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            topShadow.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            uiVisibilityAnimator = ValueAnimator.ofFloat(uiVisibilityAlpha, 0);
        }

        uiVisibilityAnimator.addUpdateListener(statusbarAnimatorListener);
        uiVisibilityAnimator.setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT);
        uiVisibilityAnimator.start();

        shadowsVisible = show;
    }

    private void showFloatingLayout(int state, boolean animated) {
        if (currentUserCameraFloatingLayout.getTag() == null || (int) currentUserCameraFloatingLayout.getTag() != STATE_FLOATING) {
            currentUserCameraFloatingLayout.setUiVisible(uiVisible);
        }
        if (!animated && cameraShowingAnimator != null) {
            cameraShowingAnimator.removeAllListeners();
            cameraShowingAnimator.cancel();
        }
        if (state == STATE_GONE) {
            if (animated) {
                if (currentUserCameraFloatingLayout.getTag() != null && (int) currentUserCameraFloatingLayout.getTag() != STATE_GONE) {
                    if (cameraShowingAnimator != null) {
                        cameraShowingAnimator.removeAllListeners();
                        cameraShowingAnimator.cancel();
                    }
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(
                        ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.ALPHA, currentUserCameraFloatingLayout.getAlpha(), 0)
                    );
                    if (currentUserCameraFloatingLayout.getTag() != null && (int) currentUserCameraFloatingLayout.getTag() == STATE_FLOATING) {
                        animatorSet.playTogether(
                            ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_X, currentUserCameraFloatingLayout.getScaleX(), 0.7f),
                            ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_Y, currentUserCameraFloatingLayout.getScaleX(), 0.7f)
                        );
                    }
                    cameraShowingAnimator = animatorSet;
                    cameraShowingAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            currentUserCameraFloatingLayout.setTranslationX(0);
                            currentUserCameraFloatingLayout.setTranslationY(0);
                            currentUserCameraFloatingLayout.setScaleY(1f);
                            currentUserCameraFloatingLayout.setScaleX(1f);
                            currentUserCameraFloatingLayout.setVisibility(View.GONE);
                        }
                    });
                    cameraShowingAnimator.setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT);
                    cameraShowingAnimator.setStartDelay(50);
                    cameraShowingAnimator.start();
                }
            } else {
                currentUserCameraFloatingLayout.setVisibility(View.GONE);
            }
        } else {
            boolean switchToFloatAnimated = animated;
            if (currentUserCameraFloatingLayout.getTag() == null || (int) currentUserCameraFloatingLayout.getTag() == STATE_GONE) {
                switchToFloatAnimated = false;
            }
            if (animated) {
                if (currentUserCameraFloatingLayout.getTag() != null && (int) currentUserCameraFloatingLayout.getTag() == STATE_GONE) {
                    if (currentUserCameraFloatingLayout.getVisibility() == View.GONE) {
                        currentUserCameraFloatingLayout.setAlpha(0f);
                        currentUserCameraFloatingLayout.setScaleX(0.7f);
                        currentUserCameraFloatingLayout.setScaleY(0.7f);
                        currentUserCameraFloatingLayout.setVisibility(View.VISIBLE);
                    }
                    if (cameraShowingAnimator != null) {
                        cameraShowingAnimator.removeAllListeners();
                        cameraShowingAnimator.cancel();
                    }
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(
                        ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.ALPHA, 0.0f, 1f),
                        ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_X, 0.7f, 1f),
                        ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_Y, 0.7f, 1f)
                    );
                    cameraShowingAnimator = animatorSet;
                    cameraShowingAnimator.setDuration(150).start();
                }
            } else {
                currentUserCameraFloatingLayout.setVisibility(View.VISIBLE);
            }
            if ((currentUserCameraFloatingLayout.getTag() == null || (int) currentUserCameraFloatingLayout.getTag() != STATE_FLOATING) && currentUserCameraFloatingLayout.relativePositionToSetX < 0) {
                currentUserCameraFloatingLayout.setRelativePosition(1f, 1f);
                currentUserCameraIsFullscreen = true;
            }
            currentUserCameraFloatingLayout.setFloatingMode(state == STATE_FLOATING, switchToFloatAnimated);
            currentUserCameraIsFullscreen = state != STATE_FLOATING;
        }
        currentUserCameraFloatingLayout.setTag(state);
    }

    private void showCallingUserAvatarMini(boolean show, boolean animated) {
        if (animated) {
            if (show && voIPAvatarView.getTag() == null) {
                voIPAvatarView.animate().setListener(null).cancel();
                voIPAvatarView.setVisibility(View.VISIBLE);
                voIPAvatarView.setAlpha(0);
                if (!emojiExpanded) {
                    voIPAvatarView.setTranslationY(-AndroidUtilities.dp(VoIPAvatarView.WAVE_MAX_RADIUS));
                    voIPAvatarView.setScaleX(1);
                    voIPAvatarView.setScaleY(1);
                    voIPAvatarView.animate().alpha(1f).translationY(0).setDuration(150)
                        .setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                } else {
                    voIPAvatarView.setTranslationY(0);
                }
            } else if (!show && voIPAvatarView.getTag() != null) {
                voIPAvatarView.animate().setListener(null).cancel();
                voIPAvatarView.animate().alpha(0).translationY(-AndroidUtilities.dp(VoIPAvatarView.WAVE_MAX_RADIUS))
                    .setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            voIPAvatarView.setVisibility(View.GONE);
                        }
                    }).start();
            }
        } else {
            voIPAvatarView.animate().setListener(null).cancel();
            voIPAvatarView.setTranslationY(0);
            voIPAvatarView.setAlpha(emojiExpanded ? 0f : 1f);
            voIPAvatarView.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        voIPAvatarView.setTag(show ? 1 : null);
    }

    private void updateKeyView(boolean animated) {
        if (emojiLoaded) {
            return;
        }
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }
        byte[] auth_key = null;
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            buf.write(service.getEncryptionKey());
            buf.write(service.getGA());
            auth_key = buf.toByteArray();
        } catch (Exception checkedExceptionsAreBad) {
            FileLog.e(checkedExceptionsAreBad, false);
        }
        if (auth_key == null) {
            return;
        }
        byte[] sha256 = Utilities.computeSHA256(auth_key, 0, auth_key.length);
        emoji = EncryptionKeyEmojifier.emojifyForCall(sha256);
        for (int i = 0; i < 4; i++) {
            Emoji.preloadEmoji(emoji[i]);
            Emoji.EmojiDrawable drawable = Emoji.getEmojiDrawable(emoji[i]);
            if (drawable != null) {
                drawable.setBounds(0, 0, AndroidUtilities.dp(24), AndroidUtilities.dp(24));
                drawable.preload();
                emojiViews[i].setContentDescription(emoji[i]);
            }
            emojiDrawables[i] = drawable;
        }
        checkEmojiLoaded(animated);
    }

    private void checkEmojiLoaded(boolean animated) {
        int count = 0;
        int reactionsCount = 0;

        // try to load reactions
        HashMap<String, TLRPC.TL_availableReaction> reactionsMap = MediaDataController.getInstance(currentAccount)
            .getReactionsMap();
        for (int i = 0; i < 4; i++) {
            TLRPC.TL_availableReaction react = reactionsMap.get(emoji[i]);
            if (react != null) {
                reactionsCount++;
                SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(react.static_icon, Theme.key_windowBackgroundGray, 1.0f);
                emojiViews[i].setImage(ImageLocation.getForDocument(react.activate_animation), ACTIVATE_ANIMATION_FILTER, "tgs", svgThumb, react);
                emojiViews[i].getImageReceiver().setFileLoadingPriority(1);
                emojiViews[i].getImageReceiver().setAllowStartLottieAnimation(false);
                emojiViews[i].setVisibility(View.GONE);
                reactions[i] = react;
                if (emojiViews[i].getImageReceiver().hasImageLoaded()) {
                    if (emojiViews[i].getImageReceiver().getLottieAnimation() != null) {
                        emojiViews[i].getImageReceiver().setAutoRepeat(0);
                        emojiViews[i].getImageReceiver().getLottieAnimation().setCurrentFrame(0, false);
                        emojiViews[i].getImageReceiver().getLottieAnimation().stop();
                    }
                    count++;
                }
            }
        }

        if (reactionsCount == 4 && count != 4) {
            // wait when reactions are loaded
            return;
        }

        // load emoji if can't load 4 reactions
        if (count != 4) {
            count = 0;
            for (int i = 0; i < 4; i++) {
                emojiViews[i].getImageReceiver().clearImage();
                reactions[i] = null;
                Emoji.EmojiDrawable drawable = emojiDrawables[i];
                if (drawable != null && drawable.isLoaded()) {
                    emojiViews[i].setImageDrawable(drawable);
                    emojiViews[i].setVisibility(View.GONE);
                    count++;
                }
            }
        }

        if (count == 4) {
            emojiLoaded = true;
            for (int i = 0; i < 4; i++) {
                if (emojiViews[i].getVisibility() != View.VISIBLE) {
                    emojiViews[i].setVisibility(View.VISIBLE);
                    if (animated) {
                        emojiViews[i].setPivotX(emojiViews[i].getMeasuredWidth() / 2f);
                        emojiViews[i].setPivotY(emojiViews[i].getMeasuredHeight() / 2f);
                        emojiViews[i].setScaleX(0f);
                        emojiViews[i].setScaleY(0f);
                        emojiViews[i].animate().scaleX(1f).scaleY(1f).setDuration(200).setListener(
                            new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    VoIPService service = VoIPService.getSharedInstance();
                                    if (service != null && !service.sharedUIParams.tapToEmojiTooltipWasShowed && !MessagesController.getGlobalMainSettings()
                                        .getBoolean("call_emoji_hint_shown", false)) {
                                        service.sharedUIParams.tapToEmojiTooltipWasShowed = true;
                                        MessagesController.getGlobalMainSettings()
                                            .getBoolean("call_emoji_hint_shown", false);
                                        tapToEmojiTooltip.showForView(emojiLayout, true);
                                    }
                                }
                            }
                        ).start();
                    }
                }
            }
        }
    }

    private void showAcceptDeclineView(boolean show, boolean animated) {
        for (int i = 0; i < 2; i++) {
            VoIPToggleButton2 button = acceptDeclineButtons[i];
            if (!animated) {
                button.setVisibility(show ? View.VISIBLE : View.GONE);
                if (show && i == 0) {
                    button.startLottieAnimation();
                }
            } else {
                if (show && button.getTag() == null) {
                    button.animate().setListener(null).cancel();
                    if (button.getVisibility() == View.GONE) {
                        button.setVisibility(View.VISIBLE);
                        button.setAlpha(0);
                    }
                    int finalI = i;
                    button.animate().alpha(1f).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (finalI == 0) {
                                button.startLottieAnimation();
                            }
                        }
                    });
                }
                if (!show && button.getTag() != null) {
                    button.animate().setListener(null).cancel();
                    button.animate().setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            button.setVisibility(View.GONE);
                        }
                    }).alpha(0f);
                }
            }

            button.setEnabled(show);
            button.setTag(show ? 1 : null);
        }
    }

    private void updateButtons(boolean animated) {
        boolean isAllButtonsVisibleBefore = true;
        for (int i = 0; i < 4; i++) {
            if (bottomButtons[i].getVisibility() != View.VISIBLE) {
                isAllButtonsVisibleBefore = false;
                break;
            }
        }

        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }
        if (animated) {
            TransitionSet transitionSet = new TransitionSet();
            Visibility visibility = new Visibility() {
                @Override
                public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                    ValueAnimator animator = ValueAnimator.ofFloat(0, 1f);
//                    animator.addUpdateListener(animation -> {
//                        float progress = (float) animation.getAnimatedValue();
//                        view.setTranslationY(AndroidUtilities.dp(100) * (1 - progress));
//                        view.setScaleX(progress);
//                        view.setScaleY(progress);
//                    });
//                    if (view instanceof VoIPToggleButton2) {
//                        view.setTranslationY(AndroidUtilities.dp(100));
//                        view.setPivotX(view.getMeasuredWidth() / 2f);
//                        view.setPivotY(view.getMeasuredHeight() / 2f);
//                        view.setScaleX(0f);
//                        view.setScaleY(0f);
//                        animator.setStartDelay(((VoIPToggleButton2) view).animationDelay);
//                    }
                    return animator;
                }

                @Override
                public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                    return ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.getTranslationY(), AndroidUtilities.dp(100));
                }
            };
            transitionSet
                .addTransition(visibility.setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT))
                .addTransition(new ChangeBounds().setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT));
            transitionSet.excludeChildren(VoIPToggleButton2.class, true);
            TransitionManager.beginDelayedTransition(buttonsLayout, transitionSet);
        }

        if (currentState == VoIPService.STATE_WAITING_INCOMING || currentState == VoIPService.STATE_BUSY) {
            if (service.privateCall != null && service.privateCall.video && currentState == VoIPService.STATE_WAITING_INCOMING) {
                if (!service.isScreencast() && (currentUserIsVideo || callingUserIsVideo)) {
                    setFrontalCameraAction(bottomButtons[0], service, animated);
                } else {
                    setSpeakerPhoneAction(bottomButtons[0], service, animated);
                }
                setVideoAction(bottomButtons[1], service, animated);
                setMicrophoneAction(bottomButtons[2], service, animated);
            } else {
                bottomButtons[0].setVisibility(View.GONE);
                bottomButtons[1].setVisibility(View.GONE);
                bottomButtons[2].setVisibility(View.GONE);
            }
            bottomButtons[3].setVisibility(View.GONE);
        } else {
            if (instance == null) {
                return;
            }
            if (!service.isScreencast() && (currentUserIsVideo || callingUserIsVideo)) {
                setFrontalCameraAction(bottomButtons[0], service, animated);
            } else {
                setSpeakerPhoneAction(bottomButtons[0], service, animated);
            }
            setVideoAction(bottomButtons[1], service, animated);
            setMicrophoneAction(bottomButtons[2], service, animated);

            bottomButtons[3].setLottieData(R.raw.call_decline, Color.WHITE, 0xFFF01D2C, LocaleController.getString("VoipEndCall", R.string.VoipEndCall), animated, new String[] {"Call Decline Outlines.**"});
            bottomButtons[3].setChecked(false);
            bottomButtons[3].setOnClickListener(view -> {
                if (VoIPService.getSharedInstance() != null) {
                    shouldNavigateToRateCallLayout = currentState == VoIPService.STATE_ESTABLISHED || currentState == VoIPService.STATE_RECONNECTING;
                    VoIPService.getSharedInstance().hangUp();
                }
            });
        }

        int animationDelay = 0;
        for (int i = 0; i < 4; i++) {
            if (bottomButtons[i].getVisibility() == View.VISIBLE) {
                bottomButtons[i].animationDelay = animationDelay;
                animationDelay += 16;
            }
        }

        boolean isAllButtonsVisibleAfter = true;
        for (int i = 0; i < 4; i++) {
            if (bottomButtons[i].getVisibility() != View.VISIBLE) {
                isAllButtonsVisibleAfter = false;
                break;
            }
        }

        if (!isAllButtonsVisibleBefore && isAllButtonsVisibleAfter) {
            if (needAnimateFromAcceptButton) {
                startAnimationFromAcceptButton();
            } else {
                for (int i = 0; i < 4; i++) {
                    VoIPToggleButton2 button = bottomButtons[i];
                    button.setTranslationY(AndroidUtilities.dp(100));
                    button.setPivotX(AndroidUtilities.dp(62) / 2f);
                    button.setPivotY(AndroidUtilities.dp(62) / 2f);
                    button.setScaleX(0f);
                    button.setScaleY(0f);

                    ValueAnimator animator = ValueAnimator.ofFloat(0, 1f);
                    animator.addUpdateListener(animation -> {
                        float progress = (float) animation.getAnimatedValue();
                        button.setTranslationY(AndroidUtilities.dp(100) * (1 - progress));
                        button.setScaleX(progress);
                        button.setScaleY(progress);
                    });
                    animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                    animator.setStartDelay(button.animationDelay);
                    animator.setDuration(300);
                    animator.start();
                }
            }
        }
    }

    private void startAnimationFromAcceptButton() {
        for (int i = 0; i < 3; i++) {
            VoIPToggleButton2 button = bottomButtons[i];
            button.post(() -> {
                int[] coord = new int[2];
                button.getLocationInWindow(coord);
                ValueAnimator animator = ValueAnimator.ofFloat(0, 1f);
                float finalX = button.getX();
                animator.addUpdateListener(animation -> {
                    float progress = (float) animation.getAnimatedValue();
                    button.setTranslationX((lastAcceptButtonLocation[0] - finalX) * (1 - progress));
                    button.setTranslationY(-(coord[1] - lastAcceptButtonLocation[1]) * (1 - progress));
                    float scaleProgress = progress < 0.5f ? 0.8f + 0.1f * (1 - progress) : 0.8f + 0.1f * progress;
                    button.setScaleX(scaleProgress);
                    button.setScaleY(scaleProgress);
                    button.setAlpha(progress);
                });
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.setDuration(400);
                animator.start();
            });
        }

        VoIPToggleButton2 button = bottomButtons[3];
        button.post(() -> {
            int[] coord = new int[2];
            button.getLocationInWindow(coord);
            ValueAnimator animator = ValueAnimator.ofFloat(0, 1f);
            float finalX = button.getX();
            animator.addUpdateListener(animation -> {
                float progress = (float) animation.getAnimatedValue();
                button.setTranslationX((lastDeclineButtonLocation[0] - finalX) * (1 - progress));
                button.setTranslationY(-(coord[1] - lastDeclineButtonLocation[1]) * (1 - progress));
                float scaleProgress = progress < 0.5f ? 0.8f + 0.1f * (1 - progress) : 0.8f + 0.1f * progress;
                button.setScaleX(scaleProgress);
                button.setScaleY(scaleProgress);
                button.setAlpha(progress);
            });
            animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            animator.setDuration(400);
            animator.start();
        });
        needAnimateFromAcceptButton = false;
    }

    private void setMicrophoneAction(VoIPToggleButton2 bottomButton, VoIPService service, boolean animated) {
        if (service.isMicMute()) {
            bottomButton.setLottieData(R.raw.call_mute, getDarkColor(bottomButton), Color.WHITE, LocaleController.getString("VoipUnmute", R.string.VoipUnmute), animated, new String[] {"Top 2.**", "Call Unmute Outlines.**", "Call Mute Outlines.**"});
            bottomButton.setChecked(true);
        } else {
            bottomButton.setLottieData(R.raw.call_unmute, Color.WHITE, getLightColor(bottomButton), LocaleController.getString("VoipMute", R.string.VoipMute), animated, new String[] {"Top 3.**", "Call Unmute Outlines.**", "Call Mute Outlines.**"});
            bottomButton.setChecked(false);
        }
        currentUserCameraFloatingLayout.setMuted(service.isMicMute(), animated);
        bottomButton.setOnClickListener(view -> {
            final VoIPService serviceInstance = VoIPService.getSharedInstance();
            if (serviceInstance != null) {
                final boolean micMute = !serviceInstance.isMicMute();
                if (accessibilityManager.isTouchExplorationEnabled()) {
                    final String text;
                    if (micMute) {
                        text = LocaleController.getString("AccDescrVoipMicOff", R.string.AccDescrVoipMicOff);
                    } else {
                        text = LocaleController.getString("AccDescrVoipMicOn", R.string.AccDescrVoipMicOn);
                    }
                    view.announceForAccessibility(text);
                }
                serviceInstance.setMicMute(micMute, false, true);
                previousState = currentState;
                updateViewState();
            }
        });
    }

    private void setVideoAction(VoIPToggleButton2 bottomButton, VoIPService service, boolean animated) {
        boolean isVideoAvailable;
        if (currentUserIsVideo || callingUserIsVideo) {
            isVideoAvailable = true;
        } else {
            isVideoAvailable = service.isVideoAvailable();
        }
        if (isVideoAvailable) {
            if (currentUserIsVideo) {
                if (service.isScreencast()) {
                    bottomButton.setStaticData(R.drawable.calls_sharescreen, Color.WHITE, getLightColor(bottomButton), LocaleController.getString("VoipStopVideo", R.string.VoipStopVideo), false);
                    bottomButton.setChecked(false);
                } else {
                    bottomButton.setLottieData(R.raw.video_start, Color.WHITE, getLightColor(bottomButton), LocaleController.getString("VoipStopVideo", R.string.VoipStopVideo), animated, new String[] {"Top 3.**", "slash.**", "base.**"});
                    bottomButton.setChecked(false);
                }
            } else {
                bottomButton.setLottieData(R.raw.video_stop, getDarkColor(bottomButton), Color.WHITE, LocaleController.getString("VoipStartVideo", R.string.VoipStartVideo), animated, new String[] {"Top 2.**", "slash.**", "base.**"});
                bottomButton.setChecked(true);
            }
            bottomButton.setOnClickListener(view -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(new String[] {Manifest.permission.CAMERA}, 102);
                } else {
                    if (Build.VERSION.SDK_INT < 21 && service.privateCall != null && !service.privateCall.video && !callingUserIsVideo && !service.sharedUIParams.cameraAlertWasShowed) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                        builder.setMessage(LocaleController.getString("VoipSwitchToVideoCall", R.string.VoipSwitchToVideoCall));
                        builder.setPositiveButton(LocaleController.getString("VoipSwitch", R.string.VoipSwitch), (dialogInterface, i) -> {
                            service.sharedUIParams.cameraAlertWasShowed = true;
                            toggleCameraInput();
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        builder.create().show();
                    } else {
                        toggleCameraInput();
                    }
                }
            });
            bottomButton.setEnabled(true);
        } else {
            bottomButton.setLottieData(R.raw.video_stop, Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.12f)), LocaleController.getString("VoipStartVideo", R.string.VoipStartVideo), animated, new String[] {"Top 2.**", "slash.**", "base.**"});
            bottomButton.setChecked(false);
            bottomButton.setOnClickListener(null);
            bottomButton.setEnabled(false);
        }
    }

    private void setSpeakerPhoneAction(VoIPToggleButton2 bottomButton, VoIPService service, boolean animated) {
        if (service.isBluetoothOn()) {
            bottomButton.setLottieData(R.raw.speaker_to_bt, Color.WHITE, getLightColor(bottomButton), LocaleController.getString("VoipAudioRoutingBluetooth", R.string.VoipAudioRoutingBluetooth), animated, new String[] {"Top 2.**", "Bluetooth Outlines.**", "wave2.**", "wave1.**", "Speaker Base.**"});
            bottomButton.setChecked(false);
        } else if (service.isSpeakerphoneOn()) {
            bottomButton.setLottieData(R.raw.bt_to_speaker, getDarkColor(bottomButton), Color.WHITE, LocaleController.getString("VoipSpeaker", R.string.VoipSpeaker), animated, new String[] {"Top 3.**", "Bluetooth Outlines.**", "wave2.**", "wave1.**", "Speaker Base.**"});
            bottomButton.setChecked(true);
        } else {
            bottomButton.setLottieData(R.raw.bt_to_speaker, Color.WHITE, getLightColor(bottomButton), LocaleController.getString("VoipSpeaker", R.string.VoipSpeaker), animated, new String[] {"Top 3.**", "Bluetooth Outlines.**", "wave2.**", "wave1.**", "Speaker Base.**"});
            bottomButton.setChecked(false);
        }
        bottomButton.setCheckableForAccessibility(true);
        bottomButton.setEnabled(true);
        bottomButton.setOnClickListener(view -> {
            if (VoIPService.getSharedInstance() != null) {
                VoIPService.getSharedInstance().toggleSpeakerphoneOrShowRouteSheet(activity, false);
            }
        });
    }

    private void setFrontalCameraAction(VoIPToggleButton2 bottomButton, VoIPService service, boolean animated) {
        if (!currentUserIsVideo) {
            bottomButton.setEnabled(false);
            bottomButton.setLottieData(R.raw.call_camera_flip, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.5f)), ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.12f)), LocaleController.getString("VoipFlip", R.string.VoipFlip), animated, new String[] {"Camera Flip Outlines.**"});
            bottomButton.setChecked(false);
            bottomButton.setOnClickListener(null);
        } else {
            bottomButton.setEnabled(true);
            if (!service.isFrontFaceCamera()) {
                bottomButton.setLottieData(R.raw.call_camera_flip, getDarkColor(bottomButton), Color.WHITE, LocaleController.getString("VoipFlip", R.string.VoipFlip), animated, new String[] {"Camera Flip Outlines.**"});
                bottomButton.setChecked(true);
            } else {
                bottomButton.setLottieData(R.raw.call_camera_flip, Color.WHITE, getLightColor(bottomButton), LocaleController.getString("VoipFlip", R.string.VoipFlip), animated, new String[] {"Camera Flip Outlines.**"});
                bottomButton.setChecked(false);
            }
            bottomButton.setOnClickListener(view -> {
                final VoIPService serviceInstance = VoIPService.getSharedInstance();
                if (serviceInstance != null) {
                    if (accessibilityManager.isTouchExplorationEnabled()) {
                        final String text;
                        if (service.isFrontFaceCamera()) {
                            text = LocaleController.getString("AccDescrVoipCamSwitchedToBack", R.string.AccDescrVoipCamSwitchedToBack);
                        } else {
                            text = LocaleController.getString("AccDescrVoipCamSwitchedToFront", R.string.AccDescrVoipCamSwitchedToFront);
                        }
                        view.announceForAccessibility(text);
                    }
                    serviceInstance.switchCamera();
                }
            });
        }
    }

    public void onScreenCastStart() {
        if (previewDialog == null) {
            return;
        }
        previewDialog.dismiss(true, true);
    }

    private void toggleCameraInput() {
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            if (accessibilityManager.isTouchExplorationEnabled()) {
                final String text;
                if (!currentUserIsVideo) {
                    text = LocaleController.getString("AccDescrVoipCamOn", R.string.AccDescrVoipCamOn);
                } else {
                    text = LocaleController.getString("AccDescrVoipCamOff", R.string.AccDescrVoipCamOff);
                }
                fragmentView.announceForAccessibility(text);
            }
            if (!currentUserIsVideo) {
                if (Build.VERSION.SDK_INT >= 21) {
                    if (previewDialog == null) {
                        service.createCaptureDevice(false);
                        if (!service.isFrontFaceCamera()) {
                            service.switchCamera();
                        }
                        windowView.setLockOnScreen(true);
                        previewDialog = new PrivateVideoPreviewDialog(fragmentView.getContext(), false, true) {
                            @Override
                            public void onDismiss(boolean screencast, boolean apply) {
                                previewDialog = null;
                                VoIPService service = VoIPService.getSharedInstance();
                                windowView.setLockOnScreen(false);
                                if (apply) {
                                    currentUserIsVideo = true;
                                    if (service != null && !screencast) {
                                        service.requestVideoCall(false);
                                        service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE);
                                    }
                                } else {
                                    if (service != null) {
                                        service.setVideoState(false, Instance.VIDEO_STATE_INACTIVE);
                                    }
                                }
                                previousState = currentState;
                                updateViewState();
                            }
                        };
                        if (lastInsets != null) {
                            previewDialog.setBottomPadding(lastInsets.getSystemWindowInsetBottom());
                        }

                        int[] cameraButtonLocation = new int[2];
                        bottomButtons[1].getLocationInWindow(cameraButtonLocation);
                        fragmentView.addView(previewDialog);
                        previewDialog.show(cameraButtonLocation, AndroidUtilities.dp(68), fragmentView.getMeasuredWidth(), fragmentView.getMeasuredHeight());
                    }
                    return;
                } else {
                    currentUserIsVideo = true;
                    if (!service.isSpeakerphoneOn()) {
                        VoIPService.getSharedInstance().toggleSpeakerphoneOrShowRouteSheet(activity, false);
                    }
                    service.requestVideoCall(false);
                    service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE);
                }
            } else {
                currentUserTextureView.saveCameraLastBitmap();
                service.setVideoState(false, Instance.VIDEO_STATE_INACTIVE);
                if (Build.VERSION.SDK_INT >= 21) {
                    service.clearCamera();
                }
            }
            previousState = currentState;
            updateViewState();
        }
    }

    public static void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (instance != null) {
            instance.onRequestPermissionsResultInternal(requestCode, permissions, grantResults);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void onRequestPermissionsResultInternal(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 101) {
            if (VoIPService.getSharedInstance() == null) {
                windowView.finish();
                return;
            }
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                VoIPService.getSharedInstance().acceptIncomingCall();
            } else {
                if (!activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    VoIPService.getSharedInstance().declineIncomingCall();
                    VoIPHelper.permissionDenied(activity, () -> windowView.finish(), requestCode);
                    return;
                }
            }
        }
        if (requestCode == 102) {
            if (VoIPService.getSharedInstance() == null) {
                windowView.finish();
                return;
            }
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toggleCameraInput();
            }
        }
    }

    private void updateSystemBarColors() {
        overlayPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.4f * uiVisibilityAlpha * enterTransitionProgress)));
        overlayBottomPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * (0.4f) * uiVisibilityAlpha * enterTransitionProgress)));
        if (fragmentView != null) {
            fragmentView.invalidate();
        }
    }

    public static void onPause() {
        if (instance != null) {
            instance.onPauseInternal();
        }
        if (VoIPPiPView.getInstance() != null) {
            VoIPPiPView.getInstance().onPause();
        }
    }

    public static void onResume() {
        if (instance != null) {
            instance.onResumeInternal();
        }
        if (VoIPPiPView.getInstance() != null) {
            VoIPPiPView.getInstance().onResume();
        }
    }

    public void onPauseInternal() {
        PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);

        boolean screenOn;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            screenOn = pm.isInteractive();
        } else {
            screenOn = pm.isScreenOn();
        }

        boolean hasPermissionsToPip = AndroidUtilities.checkInlinePermissions(activity);

        if (canSwitchToPip && hasPermissionsToPip) {
            int h = instance.windowView.getMeasuredHeight();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                h -= instance.lastInsets.getSystemWindowInsetBottom();
            }
            VoIPPiPView.show(instance.activity, instance.currentAccount, instance.windowView.getMeasuredWidth(), h, VoIPPiPView.ANIMATION_ENTER_TYPE_SCALE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                VoIPPiPView.topInset = instance.lastInsets.getSystemWindowInsetTop();
                VoIPPiPView.bottomInset = instance.lastInsets.getSystemWindowInsetBottom();
            }
        }

        if (currentUserIsVideo && (!hasPermissionsToPip || !screenOn)) {
            VoIPService service = VoIPService.getSharedInstance();
            if (service != null) {
                service.setVideoState(false, Instance.VIDEO_STATE_PAUSED);
            }
        }
        stopHeavyAnimations();
    }

    public void onResumeInternal() {
        if (VoIPPiPView.getInstance() != null) {
            VoIPPiPView.finish();
        }
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            if (service.getVideoState(false) == Instance.VIDEO_STATE_PAUSED) {
                service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE);
            }
            updateViewState();
        } else {
            windowView.finish();
        }
        scheduleStopAnimations();
        deviceIsLocked = ((KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode();
    }

    private void showErrorDialog(CharSequence message) {
        if (activity.isFinishing()) {
            return;
        }
        AlertDialog dlg = new DarkAlertDialog.Builder(activity)
            .setTitle(LocaleController.getString("VoipFailed", R.string.VoipFailed))
            .setMessage(message)
            .setPositiveButton(LocaleController.getString("OK", R.string.OK), null)
            .show();
        dlg.setCanceledOnTouchOutside(true);
        dlg.setOnDismissListener(dialog -> windowView.finish());
    }

    @SuppressLint("InlinedApi")
    private void requestInlinePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AlertsCreator.createDrawOverlayPermissionDialog(activity, (dialogInterface, i) -> {
                if (windowView != null) {
                    windowView.finish();
                }
            }).show();
        }
    }

    private void updateLastFragmentActionTime() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFragmentActionTime < 500) {
            return;
        }
        scheduleStopAnimations();
        lastFragmentActionTime = System.currentTimeMillis();
    }

    private void scheduleStopAnimations() {
        if (isHeavyAnimationsStopped) {
            startHeavyAnimations();
        }
        AndroidUtilities.cancelRunOnUIThread(stopAnimationsHandler);
        AndroidUtilities.runOnUIThread(stopAnimationsHandler, 10000);
    }

    private void startRevealAnimation(float startRadius, Point point) {
        if (!isRevealAnimationShown && Build.VERSION.SDK_INT >= 21) {
            isRevealAnimationShown = true;
            float revealEndRadius = (float) Math.hypot(
                AndroidUtilities.displaySize.x,
                AndroidUtilities.displaySize.y
            );

            try {
                Bitmap bitmap = AndroidUtilities.snapshotView(fragmentView);
                windowView.removeView(colorsSwitchImageView);
                windowView.addView(colorsSwitchImageView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                colorsSwitchImageView.setImageBitmap(bitmap);
                colorsSwitchImageView.setVisibility(View.VISIBLE);
                voIPColorsController = new VoIPColorsController(VoIPColorsController.FLAG_BLUE_VIOLET | VoIPColorsController.FLAG_BLUE_GREEN | VoIPColorsController.FLAG_GREEN, VoIPColorsController.FLAG_GREEN);
                voIPColorsController.setListener(this);
                voIPBackground.setVoIPColorsController(voIPColorsController);
                voIPColorsController.setBackgroundView(voIPBackground);
                voIPColorsController.start();

                Animator revealAnimator = ViewAnimationUtils.createCircularReveal(
                    fragmentView,
                    point.x,
                    point.y,
                    startRadius,
                    revealEndRadius
                );
                revealAnimator.setDuration(400);
                revealAnimator.setInterpolator(Easings.easeInOutQuad);
                revealAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        colorsSwitchImageView.setImageDrawable(null);
                        windowView.removeView(colorsSwitchImageView);
                    }
                });
                revealAnimator.start();
            } catch (Throwable e) {
                try {
                    colorsSwitchImageView.setImageDrawable(null);
                    windowView.removeView(colorsSwitchImageView);
                } catch (Exception e2) {
                    FileLog.e(e2);
                }
            }
        }
    }

    @Override
    public void onColorsChanged() {
        for (int i = 0; i < 3; i++) {
            if (bottomButtons[i] != null && bottomButtons[i].isEnabled()) {
                if (bottomButtons[i].getChecked()) {
                    bottomButtons[i].updateIconColor(getDarkColor(bottomButtons[i]));
                    bottomButtons[i].updateBackgroundColor(Color.WHITE);
                } else {
                    bottomButtons[i].updateBackgroundColor(getLightColor(bottomButtons[i]));
                    bottomButtons[i].updateIconColor(Color.WHITE);
                }
            }
        }

        if (tapToEmojiTooltip != null && tapToEmojiTooltip.getVisibility() == View.VISIBLE) {
            int darkColor = getDarkAlphaColor(tapToEmojiTooltip);
            tapToEmojiTooltip.setBackgroundColor(darkColor);
        }

        if (tapToVideoTooltip != null && tapToVideoTooltip.getVisibility() == View.VISIBLE) {
            int darkColor = getDarkAlphaColor(tapToVideoTooltip);
            tapToVideoTooltip.setBackgroundColor(darkColor);
        }

        if (emojiExpanded && hideEmoji != null && hideEmoji.getVisibility() == View.VISIBLE) {
            hideEmoji.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), getDarkAlphaColor(hideEmoji)));
        }

        if (notificationsLayout != null && notificationsLayout.getChildCount() != 0) {
            for (int i = 0; i < notificationsLayout.getChildCount(); i++) {
                View child = notificationsLayout.getChildAt(i);
                if (child instanceof VoIPNotificationsLayout.NotificationView) {
                    ((VoIPNotificationsLayout.NotificationView) child).updateBackgroundColor(getDarkAlphaColor(child));
                }
            }
        }

        if (centerNotificationsLayout != null && centerNotificationsLayout.getChildCount() != 0) {
            for (int i = 0; i < centerNotificationsLayout.getChildCount(); i++) {
                View child = centerNotificationsLayout.getChildAt(i);
                if (child instanceof VoIPNotificationsLayout.NotificationView) {
                    ((VoIPNotificationsLayout.NotificationView) child).updateBackgroundColor(getDarkAlphaColor(child));
                }
            }
        }

        if (rateCallLayout != null && rateCallLayout.getVisibility() == View.VISIBLE) {
            rateCallLayout.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(24), getDarkAlphaColor(rateCallLayout)));
        }

        if (expandableCloseRateButton != null && expandableCloseRateButton.getVisibility() == View.VISIBLE) {
            float parentWidth = (float) fragmentView.getMeasuredWidth();
            float parentHeight = (float) fragmentView.getMeasuredHeight();
            int color = voIPColorsController.getDarkColor(
                parentWidth,
                parentHeight,
                parentWidth / 2,
                expandableCloseRateButtonCoords[1]
            );
            expandableCloseRateButton.setTextColor(color);
        }
    }

    private int getLightColor(View view) {
        if (currentUserIsVideo || callingUserIsVideo) {
            return ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.12f));
        } else {
            float parentWidth = (float) fragmentView.getMeasuredWidth();
            float parentHeight = (float) fragmentView.getMeasuredHeight();
            view.getLocationOnScreen(viewLocation);
            viewLocation[0] += view.getMeasuredWidth() / 2f + view.getTranslationX();
            viewLocation[1] += view.getMeasuredHeight() / 2f + view.getTranslationY();
            return voIPColorsController.getLightColor(
                parentWidth,
                parentHeight,
                viewLocation[0],
                viewLocation[1]
            );
        }
    }

    private int getDarkColor(View view) {
        if (currentUserIsVideo || callingUserIsVideo) {
            return Color.BLACK;
        } else {
            float parentWidth = (float) fragmentView.getMeasuredWidth();
            float parentHeight = (float) fragmentView.getMeasuredHeight();
            view.getLocationOnScreen(viewLocation);
            viewLocation[0] += view.getMeasuredWidth() / 2f + view.getTranslationX();
            viewLocation[1] += view.getMeasuredHeight() / 2f + view.getTranslationY();
            return voIPColorsController.getDarkColor(
                parentWidth,
                parentHeight,
                viewLocation[0],
                viewLocation[1]
            );
        }
    }

    private int getDarkAlphaColor(View view) {
        return ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.12f));
    }

    public void startTransitionToRateCall() {
        if (animatorToRateCall != null) {
            animatorToRateCall.cancel();
        }
        if (notificationsLayout != null && notificationsLayout.getVisibility() != View.GONE) {
            notificationsLayout.setVisibility(View.GONE);
        }
        if (centerNotificationsLayout != null && centerNotificationsLayout.getVisibility() != View.GONE) {
            centerNotificationsLayout.setVisibility(View.GONE);
        }
        currentUserCameraFloatingLayout.setVisibility(View.GONE);
        callingUserMiniFloatingLayout.setVisibility(View.GONE);
        voIPAvatarView.setIsWaving(false);

        if (expandableCloseRateButton == null) {
            expandableCloseRateButton = new VoIPToggleButton2(fragmentView.getContext());
            expandableCloseRateButton.needAnimateToWideButton = true;
            expandableCloseRateButton.setOnClickListener(v -> AndroidUtilities.runOnUIThread(() -> windowView.finish(), 200));
            fragmentView.addView(expandableCloseRateButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 52, Gravity.TOP, 0, 0, 0, 0));
        }

        expandableCloseRateButton.setLottieData(R.raw.call_decline, Color.WHITE, 0xFFF01D2C, LocaleController.getString("VoipEndCall", R.string.VoipEndCall), false, new String[] {"Call Decline Outlines.**"});

        bottomButtons[3].getLocationInWindow(expandableCloseRateButtonCoords);
        expandableCloseRateButton.setY(expandableCloseRateButtonCoords[1]);
        expandableCloseRateButton.setTextData(Color.WHITE, LocaleController.getString("Close", R.string.Close), expandableCloseRateButtonCoords[0] + bottomButtons[3].getMeasuredWidth() / 2, expandableCloseRateButtonCoords[1]);

        for (int i = 0; i < 4; i++) {
            bottomButtons[i].animate().alpha(0).scaleX(0.5f).scaleY(0.5f).setDuration(50).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        }
        if (emojiExpanded) {
            emojiContainer.setVisibility(View.GONE);
            hideEmoji.setVisibility(View.GONE);
        }

        callingUserTitle.setText(LocaleController.getString("VoipCallEnded", R.string.VoipCallEnded));

        rateCallLayout.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(24), getDarkAlphaColor(rateCallLayout)));
        rateCallLayout.setVisibility(View.VISIBLE);
        rateCallLayout.setAlpha(0f);
        rateCallLayout.setScaleX(0.7f);
        rateCallLayout.setScaleY(0.7f);
        rateCallLayout.setPivotX(rateCallLayout.getMeasuredWidth() / 2f);
        rateCallLayout.setPivotY(rateCallLayout.getMeasuredHeight());

        statusTextView.showCallEnded(true);

        for (int i = 0; i < 5; i++) {
            View child = rateCallStarsLayout.getChildAt(i);
            child.setTranslationY(AndroidUtilities.dp(4) * i);
            child.setScaleX(0.2f);
            child.setScaleY(0.2f);
        }
        showFloatingLayout(STATE_GONE, true);
        boolean wasAvatarHidden = voIPAvatarView.getVisibility() == View.GONE || voIPAvatarView.getTag() == null || voIPAvatarView.getAlpha() == 0f;
        if (wasAvatarHidden) {
            voIPAvatarView.animate().setListener(null).cancel();
            voIPAvatarView.setVisibility(View.VISIBLE);
            voIPAvatarView.setAlpha(0);
            voIPAvatarView.setScaleX(1f);
            voIPAvatarView.setScaleY(1f);
            voIPAvatarView.setTranslationY(-AndroidUtilities.dp(VoIPAvatarView.WAVE_MAX_RADIUS));
            voIPAvatarView.setTag(1);
        }
        voIPAvatarView.setPivotX(AndroidUtilities.dp(VoIPAvatarView.WAVE_MAX_RADIUS) / 2f);
        voIPAvatarView.setPivotY(AndroidUtilities.dp(VoIPAvatarView.WAVE_MAX_RADIUS) / 2f);

        boolean animateCallingUserTextureView = callingUserTextureView.getVisibility() == View.VISIBLE && callingUserTextureView.getAlpha() > 0f;
        boolean animateCurrentUserTextureView = currentUserTextureView.getVisibility() == View.VISIBLE && currentUserTextureView.getAlpha() > 0f;

        long duration = 300;
        statusLayout.animate().alpha(1f).translationY(AndroidUtilities.dp(176)).setDuration(duration)
            .setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        rateCallLayout.animate().alpha(1).scaleX(1f).scaleY(1f).setDuration(duration)
            .setInterpolator(CubicBezierInterpolator.DEFAULT).start();

        animatorToRateCall = ValueAnimator.ofFloat(0f, 1f);
        animatorToRateCall.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();

            for (int i = 0; i < 4; i++) {
                emojiViews[i].setPivotX(emojiViews[i].getMeasuredWidth() / 2f);
                emojiViews[i].setPivotY(emojiViews[i].getMeasuredHeight() / 2f);
                emojiViews[i].setScaleX(1 - progress);
                emojiViews[i].setScaleY(1 - progress);
            }

            for (int i = 0; i < 5; i++) {
                View child = rateCallStarsLayout.getChildAt(i);
                child.setTranslationY((AndroidUtilities.dp(4) * i) * (1 - progress));
                child.setScaleX(0.2f + 0.8f * progress);
                child.setScaleY(0.2f + 0.8f * progress);
            }


            if (wasAvatarHidden) {
                voIPAvatarView.setAlpha(progress);
                voIPAvatarView.setTranslationY(-AndroidUtilities.dp(16) - AndroidUtilities.dp(VoIPAvatarView.WAVE_MAX_RADIUS) * (1 - progress));
            } else {
                float avatarScale;
                if (progress < 0.5f) {
                    avatarScale = 1 - 0.1f * progress;
                } else {
                    avatarScale = 1 - 0.1f * (1 - progress);
                }
                voIPAvatarView.setScaleX(avatarScale);
                voIPAvatarView.setScaleY(avatarScale);
                voIPAvatarView.setTranslationY(-AndroidUtilities.dp(16) * progress);
            }

            float videoViewsProgress = (1 - progress) / 2;
            if (animateCallingUserTextureView) {
                callingUserTextureView.setAlpha(videoViewsProgress);
            }
            if (animateCurrentUserTextureView) {
                currentUserTextureView.setAlpha(videoViewsProgress);
            }
        });
        animatorToRateCall.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animatorToRateCall = null;
                callingUserTextureView.setVisibility(View.GONE);
                currentUserTextureView.setVisibility(View.GONE);

                if (ratingAnimationImageView == null) {
                    ratingAnimationImageView = new RLottieImageView(fragmentView.getContext());
                    ratingAnimationImageView.setAnimation(R.raw.call_star_big, 128, 128);
                    ratingAnimationImageView.setVisibility(View.INVISIBLE);
                    fragmentView.addView(ratingAnimationImageView, LayoutHelper.createFrame(128, 128));
                }
            }
        });

        animatorToRateCall.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animatorToRateCall.setDuration(duration);
        animatorToRateCall.start();
    }

    private void setCallRating(int rating) {
        if (animatorCallRatingStars != null) {
            animatorCallRatingStars.cancel();
        }

        for (int i = 0; i < 5; i++) {
            rateCallStarsLayout.getChildAt(i).setEnabled(false);
        }

        if (rateCallRequest != null) {
            rateCallRequest.rating = rating;
            ConnectionsManager.getInstance(currentAccount).sendRequest(rateCallRequest, (response, error) -> {
                if (response instanceof TLRPC.TL_updates) {
                    TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
                    MessagesController.getInstance(currentAccount).processUpdates(updates, false);
                }
            });
        }

        if (rating >= 4) {
            int[] clickedViewLocation = new int[2];
            View clickedView = rateCallStarsLayout.getChildAt(rating - 1);
            clickedView.getLocationInWindow(clickedViewLocation);
            ratingAnimationImageView.setX(clickedViewLocation[0] + clickedView.getMeasuredWidth() / 2f - AndroidUtilities.dp(128) / 2f);
            ratingAnimationImageView.setY(clickedViewLocation[1] + clickedView.getMeasuredHeight() / 2f - AndroidUtilities.dp(128) / 2f);

            clickedView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            AndroidUtilities.runOnUIThread(() -> {
                ratingAnimationImageView.setVisibility(View.VISIBLE);
                ratingAnimationImageView.setProgress(0f);
                ratingAnimationImageView.playAnimation();
                clickedView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }, 250);
        }

        animatorCallRatingStars = ValueAnimator.ofFloat(0f, 1f);
        animatorCallRatingStars.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            for (int i = 0; i < rating; i++) {
                View child = rateCallStarsLayout.getChildAt(i);
                if (child instanceof RLottieImageView) {
                    ((RLottieImageView) child).getAnimatedDrawable().setProgress(progress);
                }
                child.setPivotX(child.getMeasuredWidth() / 2f);
                child.setPivotY(child.getMeasuredHeight() / 2f);

                float scale = 0.7f + 0.3f * (progress < 0.5f ? (1 - progress) : progress);
                child.setScaleX(scale);
                child.setScaleY(scale);
            }
        });
        animatorCallRatingStars.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animatorCallRatingStars = null;
            }
        });
        animatorCallRatingStars.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animatorCallRatingStars.setDuration(500);
        animatorCallRatingStars.start();

        AndroidUtilities.runOnUIThread(() -> {
            windowView.finish();
        }, rating >= 4 ? 1950 : 700);
    }

    private void startHeavyAnimations() {
        voIPColorsController.resume();
        voIPAvatarView.resume();
        isHeavyAnimationsStopped = false;
    }
    private void stopHeavyAnimations() {
        voIPColorsController.pause(false);
        voIPAvatarView.pause();
        isHeavyAnimationsStopped = true;
    }
}
