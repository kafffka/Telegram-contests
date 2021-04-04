package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.BaseCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.InstantCameraView;
import org.telegram.ui.Components.PipRoundVideoView;
import org.telegram.ui.Components.RecyclerListView;

public class MessageEnterTransition {

    float fromRadius;
    float progress;
    private final ValueAnimator animator;
    TextPaint messagePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    TextPaint timePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    ChatMessageCell messageView;
    Theme.MessageDrawable messageDrawable = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, true, false);
    Rect bounds = new Rect();
    Paint testPaint = new Paint();
    Drawable checkDrawable;
    final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    MessageObject message;

    private int animationType;
    private int duration;

    private int startXTime;
    private int endXTime;
    private float startXProgress;
    private float endXProgress;

    private int startYTime;
    private int endYTime;
    private float startYProgress;
    private float endYProgress;

    private int startBubbleShapeTime;
    private int endBubbleShapeTime;
    private float startBubbleShapeProgress;
    private float endBubbleShapeProgress;

    private int startTextScaleTime;
    private int endTextScaleTime;
    private float startTextScaleProgress;
    private float endTextScaleProgress;

    private int startColorChangeTime;
    private int endColorChangeTime;
    private float startColorChangeProgress;
    private float endColorChangeProgress;

    private int startStickerScaleTime;
    private int endStickerScaleTime;
    private float startStickerScaleProgress;
    private float endStickerScaleProgress;

    private int startEmojiScaleTime;
    private int endEmojiScaleTime;
    private float startEmojiScaleProgress;
    private float endEmojiScaleProgress;

    private int startTimeAppearsTime;
    private int endTimeAppearsTime;
    private float startTimeAppearsProgress;
    private float endTimeAppearsProgress;

    CubicBezierInterpolator xInterpolator;
    CubicBezierInterpolator yInterpolator;
    CubicBezierInterpolator bubbleShapeInterpolator;
    CubicBezierInterpolator textScaleInterpolator;
    CubicBezierInterpolator colorChangeInterpolator;
    CubicBezierInterpolator stickerScaleInterpolator;
    CubicBezierInterpolator emojiScaleInterpolator;
    CubicBezierInterpolator timeAppearsInterpolator;

    private float fromStickerX;
    private float fromStickerY;
    private float fromStickerWidth;
    private float fromStickerHeight;

    private Drawable replyDrawable;

    public MessageEnterTransition(FrameLayout containerView, ChatMessageCell messageView, ChatActivityEnterView chatActivityEnterView, RecyclerListView listView, MessageObject message, InstantCameraView instantCameraView) {
        this.messageView = messageView;
        this.message = message;
        this.fromStickerX = chatActivityEnterView.stickerX;
        this.fromStickerY = chatActivityEnterView.stickerY;
        this.fromStickerWidth = chatActivityEnterView.stickerWidth;
        this.fromStickerHeight = chatActivityEnterView.stickerHeight;


        chatActivityEnterView.stickerX = -1;
        chatActivityEnterView.stickerY = -1;
        chatActivityEnterView.stickerWidth = -1;
        chatActivityEnterView.stickerWidth = -1;
        animationType = getCurrentAnimationType();

        timePaint.setColor(Theme.chat_timePaint.getColor());
        timePaint.setTextSize(Theme.chat_timePaint.getTextSize());

        chatActivityEnterView.startMessageTransition();
        messageView.getTransitionParams().isRunningMessageEnterTransition = true;

        if (animationType == 1 || animationType == 3) {
            messageView.setSingleLineMessageTransitionInProgress(true);
        }
        if (message.isVoice()) {
            messageView.setVoiceTransitionInProgress(true);
        }
        if (message.isAnimatedEmoji()) {
            messageView.getTransitionParams().animateChange = true;
            messageView.getTransitionParams().animateDrawingTimeAlpha = true;
            messageView.getTransitionParams().animateChangeProgress = 0;
            messageView.setAnimatedEmojiTransitionInProgress(true);
        }

        if (message.isAnyKindOfSticker()) {
            messageView.getTransitionParams().animateChange = true;
            messageView.getTransitionParams().animateDrawingTimeAlpha = true;
            messageView.getTransitionParams().animateChangeProgress = 0;
            messageView.setStickerTransitionInProgress(true);
        }

        if (animationType == 7) {
            messageView.getTransitionParams().animateChange = true;
            messageView.getTransitionParams().animateDrawingTimeAlpha = true;
            messageView.getTransitionParams().animateChangeProgress = 0;
            messageView.setVideoTransitionInProgress(true);
        }

        if (message.isReply()) {
            messageView.getTransitionParams().animateReplay = true;

            replyDrawable = ContextCompat.getDrawable(containerView.getContext(), R.drawable.msg_panel_reply);
            replyDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_replyPanelIcons), PorterDuff.Mode.MULTIPLY));
        }


        if (animationType == 3) {
            messageView.getTransitionParams().animateLinkPreview = true;
        }
        fromRadius = chatActivityEnterView.getRecordCicle().drawingCircleRadius;

        TextPaint durationPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        durationPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        Rect durationTextBounds = new Rect();

        testPaint.setColor(0xffff0000);
        testPaint.setStrokeWidth(AndroidUtilities.dp(4));

        int messageId = messageView.getMessageObject().stableId;
        checkDrawable = Theme.chat_msgOutCheckDrawable.getConstantState().newDrawable();

        ChatActivityEnterView.RecordCircle recordCircle = chatActivityEnterView.getRecordCicle();
        recordCircle.voiceEnterTransitionInProgress = true;
        recordCircle.skipDraw = true;

        ChatActivityEnterView.RecordDot recordDot = chatActivityEnterView.getRecordDot();
        ChatActivityEnterView.TimerView recordTimerView = chatActivityEnterView.getRecordTimerView();
        recordDot.skipDraw = true;

        updateAnimationParams();


        if (animationType == 7) {
            PipRoundVideoView pipRoundVideoView = PipRoundVideoView.getInstance();
            if (pipRoundVideoView != null) {
                pipRoundVideoView.showTemporary(true);
            }

            ImageReceiver imageReceiver = messageView.getPhotoImage();
            messageView.getTransitionParams().ignoreAlpha = true;
            final InstantCameraView.InstantViewCameraContainer cameraContainer = instantCameraView.getCameraContainer();
            cameraContainer.setImageReceiver(imageReceiver);
            instantCameraView.cancelBlur();
            messageView.setAlpha(0.0f);
        }

        View view = new View(containerView.getContext()) {

            int i = 0;

            private final int xPositionMessageText = i++;
            private final int yPositionMessageText = i++;

            private final int xPositionMessageCell = i++;
            private final int x2PositionMessageCell = i++;
            private final int yPositionMessageCell = i++;
            private final int y2PositionMessageCell = i++;

            private final int xTimePosition = i++;
            private final int yTimePosition = i++;

            private final int xStatusPosition = i++;
            private final int yStatusPosition = i++;

            private final int cxPositionRecordCircle = i++;
            private final int cyPositionRecordCircle = i++;

            private final int cxPositionRecordDot = i++;
            private final int cyPositionRecordDot = i++;

            private final int xPositionWaveForm = i++;
            private final int cyPositionWaveForm = i++;

            private final int cyPositionDuration = i++;
            private final int xPositionDuration = i++;

            private final int emojiSizePosition = i++;

            private final int cxStickerPosition = i++;
            private final int cyStickerPosition = i++;
            private final int widthStickerPosition = i++;
            private final int heightStickerPosition = i++;

            private final int xVideoPosition = i++;
            private final int yVideoPosition = i++;
            private final int sizeVideoPosition = i++;

            private final int xReplyPosition = i++;
            private final int yReplyPosition = i++;
            private final int sizeReplyIconPosition = i++;
            private final int xReplyIconPosition = i++;
            private final int cyReplyIconPosition = i++;


            final float[] last = new float[i];
            final float[] from = new float[i];
            final float[] to = new float[i];

            final int[] durationLocation = new int[]{0, 0};
            int[] messageViewPosition = new int[2];

            Rect startMessageBounds = new Rect();

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float step1Time = 0.6f;
                float moveProgress = progress;
                float hideWavesProgress = progress > step1Time ? 1f : progress / step1Time;

                if (hasDefaultBackground() || message.isVoice()) {
                    from[x2PositionMessageCell] = getMeasuredWidth() + AndroidUtilities.dp(8);
                    if (message.textLayoutBlocks == null || message.textLayoutBlocks.isEmpty()) {
                        from[yPositionMessageCell] = getMeasuredHeight() - AndroidUtilities.dp(48) - getY();
                    } else {
                        chatActivityEnterView.messageEditText.getPaint().getTextBounds(message.messageText.toString(), 0, message.messageText.length(), startMessageBounds);
                        from[yPositionMessageCell] = getMeasuredHeight() - getY() - message.textLayoutBlocks.get(0).height - chatActivityEnterView.messageEditText.getPaddingBottom() - chatActivityEnterView.messageEditText.getPaddingTop();
                    }
                    from[y2PositionMessageCell] = getMeasuredHeight() - getY();
                    if (chatActivityEnterView.emojiViewVisible) {
                        from[yPositionMessageCell] -= chatActivityEnterView.keyboardHeight;
                        from[y2PositionMessageCell] -= chatActivityEnterView.keyboardHeight;
                    }
                }

                from[xTimePosition] = getMeasuredWidth() - getX() - AndroidUtilities.dp(60);
                from[yTimePosition] = getMeasuredHeight() - getY() - messageView.timeLayout.getHeight() - AndroidUtilities.dp(6);

                from[xStatusPosition] = getMeasuredWidth() - getX() - AndroidUtilities.dp(16);
                from[yStatusPosition] = from[yTimePosition];

                if (chatActivityEnterView.emojiViewVisible) {
                    from[yTimePosition] -= chatActivityEnterView.keyboardHeight;
                    from[yStatusPosition] -= chatActivityEnterView.keyboardHeight;
                }

                if (animationType == 3) {
                    from[yTimePosition] = from[yPositionMessageCell] + messageView.getLayoutHeight();
                    from[yStatusPosition] = from[yPositionMessageCell] + messageView.getLayoutHeight();
                }

                if ((animationType == 1 || animationType == 3) && message.textLayoutBlocks != null) {
                    from[xPositionMessageText] = AndroidUtilities.dp(52);
                    from[yPositionMessageText] = getMeasuredHeight() - getY() - message.textLayoutBlocks.get(0).height - chatActivityEnterView.messageEditText.getPaddingBottom();
                    if (chatActivityEnterView.emojiViewVisible) {
                        from[yPositionMessageText] -= chatActivityEnterView.keyboardHeight;
                    }
                }

                if (message.isAnimatedEmoji()) {
                    float extraY = chatActivityEnterView.emojiViewVisible ? chatActivityEnterView.keyboardHeight : 0;
                    from[emojiSizePosition] = AndroidUtilities.dp(24);
                    from[xPositionMessageText] = AndroidUtilities.dp(42);
                    from[yPositionMessageText] = getMeasuredHeight() - getY() - chatActivityEnterView.messageEditText.getPaddingBottom() - from[emojiSizePosition] - extraY;
                }

                if (message.isVoice()) {
                    recordTimerView.getLocationInWindow(durationLocation);
                    from[cxPositionRecordCircle] = recordCircle.drawingCx + recordCircle.getX() - getX();
                    from[cyPositionRecordCircle] = recordCircle.drawingCy + recordCircle.getY() - getY();
                    from[cxPositionRecordDot] = AndroidUtilities.dp(27f);
                    from[cyPositionRecordDot] = getMeasuredHeight() - getY() - AndroidUtilities.dp(24f);
                    from[xPositionDuration] = AndroidUtilities.dp(47f);
                    from[cyPositionDuration] = getMeasuredHeight() - getY() - AndroidUtilities.dp(48f) / 2f;

                    from[xPositionWaveForm] = getMeasuredWidth() / 2f - messageView.getSeekBarWaveform().getWidth() / 2f;//AndroidUtilities.dp(24);
                    from[cyPositionWaveForm] = getMeasuredHeight() - getY() - AndroidUtilities.dp(48f) / 2f - messageView.getSeekBarWaveform().getHeight() / 2f;
                }

                if (message.isAnyKindOfSticker()) {
                    from[yPositionMessageCell] = getMeasuredHeight() - AndroidUtilities.dp(48) - getY() - chatActivityEnterView.keyboardHeight;
                    from[cxStickerPosition] = fromStickerX;
                    from[cyStickerPosition] = fromStickerY - getY();
                    from[widthStickerPosition] = fromStickerWidth;
                    from[heightStickerPosition] = fromStickerHeight;
                }

                if (animationType == 7 && from[xVideoPosition] == 0f) {
                    org.telegram.ui.Components.Rect rect = instantCameraView.getCameraRect();
                    from[xVideoPosition] = rect.x;
                    from[yVideoPosition] = rect.y;
                    from[sizeVideoPosition] = rect.width;
                }

                if (message.isReply()) {
                    from[xReplyPosition] = AndroidUtilities.dp(52) + messageView.replyNameOffset - AndroidUtilities.dp(10 + (messageView.needReplyImage ? 44 : 0));
                    from[yReplyPosition] = from[yPositionMessageCell] - AndroidUtilities.dp(46);
                    from[yPositionMessageCell] -= AndroidUtilities.dp(54);

                    if (messageView.needReplyImage) {
                        from[xReplyPosition] += AndroidUtilities.dp(46);
                    }

                    from[sizeReplyIconPosition] = AndroidUtilities.dp(24);
                    from[xReplyIconPosition] = from[xPositionMessageCell] + AndroidUtilities.dp(14);
                    from[cyReplyIconPosition] = (from[yPositionMessageCell] + AndroidUtilities.dp(13) + from[sizeReplyIconPosition] / 2);
                }

                if (messageView.getMessageObject().stableId != messageId) {
                    System.arraycopy(last, 0, to, 0, to.length);
                } else {
                    if (hasDefaultBackground() || message.isVoice()) {
                        to[xPositionMessageCell] = messageView.getBackgroundDrawableLeft();
                        to[x2PositionMessageCell] = messageView.getBackgroundDrawableRight();
                        to[yPositionMessageCell] = messageView.getBackgroundDrawableTop() + messageView.getY() + listView.getY() - getY();
                        to[y2PositionMessageCell] = messageView.getBackgroundDrawableBottom() + messageView.getY() + listView.getY() - getY();
                    }

                    to[xTimePosition] = messageView.timeX + messageView.getX() + listView.getX() - getX();
                    to[yTimePosition] = messageView.timeY1 + messageView.getY() + listView.getY() - getY();

                    to[xStatusPosition] = messageView.statusDrawableX + messageView.getX() + listView.getX() - getX();
                    to[yStatusPosition] = to[yTimePosition] + AndroidUtilities.dp(1);

                    if (animationType == 1 || animationType == 3) {
                        to[xPositionMessageText] = messageView.textX + messageView.getX() + listView.getX() - getX() - message.textXOffset;
                        to[yPositionMessageText] = messageView.textY + messageView.getY() + listView.getY() - getY();
                    }

                    if (message.isAnimatedEmoji()) {
                        to[xPositionMessageText] = messageView.photoImage.getImageX() + messageView.getX() + listView.getX() - getX();
                        to[yPositionMessageText] = messageView.photoImage.getImageY2() + messageView.getY() + listView.getY() - getY() - messageView.photoHeight;
                        to[emojiSizePosition] = messageView.photoWidth;
                    }

                    if (message.isAnyKindOfSticker()) {
                        to[cxStickerPosition] = messageView.photoImage.getImageX() + messageView.getX() + listView.getX() - getX();
                        to[cyStickerPosition] = messageView.photoImage.getImageY() + messageView.getY() + listView.getY() - getY();
                        to[widthStickerPosition] = messageView.photoWidth;
                        to[heightStickerPosition] = messageView.photoHeight;
                    }

                    if (message.isVoice()) {
                        to[cxPositionRecordCircle] = messageView.getRadialProgress().getProgressRect().centerX() + messageView.getX() + listView.getX() - getX();
                        to[cyPositionRecordCircle] = messageView.getRadialProgress().getProgressRect().centerY() + messageView.getY() + listView.getY() - getY();

                        to[cxPositionRecordDot] = messageView.getChatDotPoint().x + messageView.getX() + listView.getX() - getX();
                        to[cyPositionRecordDot] = messageView.getChatDotPoint().y + messageView.getY() + listView.getY() - getY();

                        to[xPositionWaveForm] = messageView.seekBarX + AndroidUtilities.dp(13) + messageView.getX() + listView.getX() - getX();
                        to[cyPositionWaveForm] = messageView.seekBarY + messageView.getY() + listView.getY() - getY();

                        to[xPositionDuration] = messageView.getRecordTimerPoint().x + messageView.getX() + listView.getX() - getX();
                        to[cyPositionDuration] = messageView.getTransitionParams().lastDrawingBackgroundRect.top + messageView.getY() + listView.getY() - getY() + messageView.getRecordTimerPoint().y + messageView.getDurationLayout().getHeight() / 2f;
                    }
                    if (animationType == 7 && to[xVideoPosition] == 0) {
                        messageView.getLocationOnScreen(messageViewPosition);
                        messageViewPosition[0] += messageView.getPhotoImage().getImageX() - messageView.getAnimationOffsetX();
                        messageViewPosition[1] += messageView.getPhotoImage().getImageY() - messageView.getTranslationY();
                        to[xVideoPosition] = messageViewPosition[0];
                        to[yVideoPosition] = messageViewPosition[1];
                        to[sizeVideoPosition] = messageView.getPhotoImage().getImageWidth();
                    }

                    if (message.isReply()) {
                        to[xReplyPosition] = messageView.replyStartX + messageView.getX() + listView.getX() - getX();
                        to[yReplyPosition] = messageView.replyStartY + messageView.getY() + listView.getY() - getY();
                        to[sizeReplyIconPosition] = AndroidUtilities.dp(2);
                        to[xReplyIconPosition] = to[xReplyPosition];
                        to[cyReplyIconPosition] = (to[yPositionMessageCell] + AndroidUtilities.dp(13) + to[sizeReplyIconPosition] / 2);
                    }

                }

                System.arraycopy(to, 0, last, 0, to.length);

                float progress = CubicBezierInterpolator.DEFAULT.getInterpolation(moveProgress);

                float scale2 = (float) (endXTime - startXTime) / duration;
                float progressXScale = (moveProgress - (float) startXTime / duration) / scale2;
                float xProgress2 = xInterpolator.getInterpolation(progressXScale);
                if (moveProgress < (float) startXTime / duration) {
                    xProgress2 = 0;
                } else if (moveProgress > (float) endXTime / duration) {
                    xProgress2 = 1;
                }

                float yScale2 = (float) (endYTime - startYTime) / duration;
                float progressYScale = (moveProgress - (float) startYTime / duration) / yScale2;
                float yProgress2 = yInterpolator.getInterpolation(progressYScale);
                if (moveProgress < (float) startYTime / duration) {
                    yProgress2 = 0;
                } else if (moveProgress > (float) endYTime / duration) {
                    yProgress2 = 1;
                }
                float xMessageText = from[xPositionMessageText] * (1f - xProgress2) + to[xPositionMessageText] * xProgress2;
                float yMessageText = from[yPositionMessageText] * (1f - yProgress2) + to[yPositionMessageText] * yProgress2;

                float xMessageCell = to[xPositionMessageCell] * xProgress2;
                float x2MessageCell = from[x2PositionMessageCell] * (1f - xProgress2) + to[x2PositionMessageCell] * xProgress2;
                float yMessageCell = from[yPositionMessageCell] + (to[yPositionMessageCell] - from[yPositionMessageCell]) * yProgress2;
                float y2MessageCell = from[y2PositionMessageCell] + (to[y2PositionMessageCell] - from[y2PositionMessageCell]) * yProgress2;
                if (animationType == 3) {
                    y2MessageCell = yMessageCell + messageView.getLayoutHeight() - AndroidUtilities.dp(3);
                }

                float xTime = from[xTimePosition] * (1f - xProgress2) + to[xTimePosition] * xProgress2;
                float yTime = from[yTimePosition] * (1f - yProgress2) + to[yTimePosition] * yProgress2;

                float xCheckDrawable = from[xStatusPosition] * (1f - xProgress2) + to[xStatusPosition] * xProgress2;
                float yCheckDrawable = from[yStatusPosition] * (1f - yProgress2) + to[yStatusPosition] * yProgress2;

                float cxRecordCircle = from[cxPositionRecordCircle] * (1f - xProgress2) + to[cxPositionRecordCircle] * xProgress2;
                float cyRecordCircle = from[cyPositionRecordCircle] * (1f - yProgress2) + to[cyPositionRecordCircle] * yProgress2;
                float cxRecordDot = from[cxPositionRecordDot] * (1f - xProgress2) + to[cxPositionRecordDot] * xProgress2;
                float cyRecordDot = from[cyPositionRecordDot] * (1f - yProgress2) + to[cyPositionRecordDot] * yProgress2;
                float xDuration = from[xPositionDuration] * (1f - xProgress2) + to[xPositionDuration] * xProgress2;
                float yDuration = from[cyPositionDuration] * (1f - yProgress2) + to[cyPositionDuration] * yProgress2;
                float xWaveForm = from[xPositionWaveForm] * (1f - xProgress2) + to[xPositionWaveForm] * xProgress2;
                float cyWaveForm = from[cyPositionWaveForm] * (1f - yProgress2) + to[cyPositionWaveForm] * yProgress2;

                float toRadius = messageView.getRadialProgress().getProgressRect().height() / 2;
                float radius = fromRadius * (1f - progress) + toRadius * progress;
                float dotRadius = AndroidUtilities.dp(3) + AndroidUtilities.dp(2) * (1f - progress);


                float clipTop = listView.getY() - getY();
                if (chatActivityEnterView.emojiViewVisible || chatActivityEnterView.isKeyboardVisible()) {
                    clipTop += chatActivityEnterView.keyboardHeight;
                }

                if (getMeasuredHeight() > 0) {
                    canvas.saveLayerAlpha(0, clipTop, getMeasuredWidth(), getMeasuredHeight(), 255, canvas.ALL_SAVE_FLAG);
                } else {
                    canvas.save();
                }


                float timeAppearsScale = (float) (endTimeAppearsTime - startTimeAppearsTime) / duration;
                float progressTimeAppearsScale = (moveProgress - (float) startTimeAppearsTime / duration) / timeAppearsScale;
                float timeAppears = timeAppearsInterpolator.getInterpolation(progressTimeAppearsScale);
                if (moveProgress < (float) startTimeAppearsTime / duration) {
                    timeAppears = 0;
                } else if (moveProgress > (float) endTimeAppearsTime / duration) {
                    timeAppears = 1;
                }

                float colorChangeProgress = 0;
                if (animationType == 1 || animationType == 3 || animationType == 4 || animationType == 5 || animationType == 6) {
                    float colorChangeScale = (float) (endColorChangeTime - startColorChangeTime) / duration;
                    float progressColorChangeScale = (moveProgress - (float) startColorChangeTime / duration) / colorChangeScale;
                    colorChangeProgress = colorChangeInterpolator.getInterpolation(progressColorChangeScale);
                    if (moveProgress < (float) startColorChangeTime / duration) {
                        colorChangeProgress = 0;
                    } else if (moveProgress > (float) endColorChangeTime / duration) {
                        colorChangeProgress = 1;
                    }
                }

                if (hasDefaultBackground()) {
                    float bubbleShapeScale = (float) (endBubbleShapeTime - startBubbleShapeTime) / duration;
                    float progressBubbleShapeScale = (moveProgress - (float) startBubbleShapeTime / duration) / bubbleShapeScale;
                    float bubbleShapeProgress = bubbleShapeInterpolator.getInterpolation(progressBubbleShapeScale);
                    if (moveProgress < (float) startBubbleShapeTime / duration) {
                        bubbleShapeProgress = 0;
                    } else if (moveProgress > (float) endBubbleShapeTime / duration) {
                        bubbleShapeProgress = 1;
                    }
                    if (messageView.getCurrentBackgroundDrawable() != null) {
                        int oldColor = messageView.getCurrentBackgroundDrawable().getPaint().getColor();
                        int backgroundColor = ColorUtils.blendARGB(Theme.getColor(Theme.key_chat_messagePanelBackground), oldColor, colorChangeProgress);

                        drawBackground(canvas, xMessageCell, x2MessageCell, yMessageCell, y2MessageCell, backgroundColor, bubbleShapeProgress, timeAppears);
                        messageView.getCurrentBackgroundDrawable().getPaint().setColor(oldColor);
                    }
                }

                float settingsTextScale = (float) (endTextScaleTime - startTextScaleTime) / duration;
                float progressSettingsTextScale = (moveProgress - (float) startTextScaleTime / duration) / settingsTextScale;


                float settingsTextScaleValue = 0;
                if (animationType == 1 || animationType == 3 || animationType == 6) {
                    settingsTextScaleValue = textScaleInterpolator.getInterpolation(progressSettingsTextScale);
                    if (moveProgress < (float) startTextScaleTime / duration) {
                        settingsTextScaleValue = 0;
                    } else if (moveProgress > (float) endTextScaleTime / duration) {
                        settingsTextScaleValue = 1;
                    }
                }


                if (animationType == 1 || animationType == 3) {
                    float textScale = chatActivityEnterView.messageEditText.getTextSize() * (1f - settingsTextScaleValue) + message.textLayoutBlocks.get(0).textLayout.getPaint().getTextSize() * settingsTextScaleValue;

                    drawMessage(canvas, message, xMessageText, yMessageText, textScale);
                }

                if (message.isReply()) {
                    float replyX = from[xReplyPosition] * (1f - xProgress2) + to[xReplyPosition] * xProgress2;
                    float replyStartXOffset = -(from[xReplyPosition] * (1f - xProgress2));
                    float replyY = from[yReplyPosition] * (1f - yProgress2) + to[yReplyPosition] * yProgress2;

                    float replyNameTextSize = AndroidUtilities.dp(14) * (1f - settingsTextScaleValue) + Theme.chat_replyNamePaint.getTextSize() * settingsTextScaleValue;
                    float replyMessageTextSize = AndroidUtilities.dp(14) * (1f - settingsTextScaleValue) + Theme.chat_replyTextPaint.getTextSize() * settingsTextScaleValue;

                    int endReplyNameTextColor;
                    int endReplyMessageTextColor;
                    int endIconAndLineColor;
                    float animateReplayMessageOffsetY;
                    if (message.shouldDrawWithoutBackground()) {
                        endReplyNameTextColor = Theme.getColor(Theme.key_chat_stickerReplyNameText);
                        endReplyMessageTextColor = Theme.getColor(Theme.key_chat_stickerReplyMessageText);
                        endIconAndLineColor = Theme.getColor(Theme.key_chat_stickerReplyLine);
                        animateReplayMessageOffsetY = AndroidUtilities.dp(21.2f) - AndroidUtilities.dp(2) * (1f - settingsTextScaleValue);
                    } else {
                        endReplyNameTextColor = Theme.getColor(Theme.key_chat_outReplyNameText);
                        if (message.hasValidReplyMessageObject() && (message.replyMessageObject.type == 0 || !TextUtils.isEmpty(message.replyMessageObject.caption)) && !(message.replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame || message.replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice)) {
                            endReplyMessageTextColor = Theme.getColor(Theme.key_chat_outReplyMessageText);
                        } else {
                            endReplyMessageTextColor = Theme.getColor(Theme.key_chat_outReplyMediaMessageText);
                        }
                        endIconAndLineColor = Theme.getColor(Theme.key_chat_outReplyLine);
                        animateReplayMessageOffsetY = AndroidUtilities.dp(19) - AndroidUtilities.dp(2) * (1f - settingsTextScaleValue);
                    }

                    int replyNameTextColor = ColorUtils.blendARGB(Theme.getColor(Theme.key_chat_replyPanelName), endReplyNameTextColor, colorChangeProgress);
                    int replyNameMessageColor = ColorUtils.blendARGB(Theme.getColor(Theme.key_chat_replyPanelMessage), endReplyMessageTextColor, colorChangeProgress);
                    int replyLineColor = ColorUtils.blendARGB(Theme.getColor(Theme.key_chat_replyPanelIcons), endIconAndLineColor, colorChangeProgress);


                    messageView.getTransitionParams().animateReplyProgress = xProgress2;
                    messageView.getTransitionParams().animateReplayX = replyX;
                    messageView.getTransitionParams().animateReplayY = replyY;
                    messageView.getTransitionParams().animateReplayMessageOffsetY = animateReplayMessageOffsetY;
                    messageView.getTransitionParams().animateReplayNameTextSize = replyNameTextSize;
                    messageView.getTransitionParams().animateReplayMessageTextSize = replyMessageTextSize;
                    messageView.getTransitionParams().animateReplayNameTextColor = replyNameTextColor;
                    messageView.getTransitionParams().animateReplayMessageTextColor = replyNameMessageColor;
                    messageView.getTransitionParams().animateReplayLineColor = replyLineColor;
                    if (message.shouldDrawWithoutBackground()) {
                        messageView.drawReplyBackground(canvas, replyX, replyStartXOffset);
                    }
                    messageView.drawReply(canvas);

                    if (message.shouldDrawWithoutBackground()) {
                        if (xProgress2 <= 0.5f) {
                            float iconProgress = xProgress2 * 2;
                            float iconProgressY = yProgress2 * 2;
                            float toXIcon = from[xReplyPosition] * (1f - 0.5f) + to[xReplyPosition] * 0.5f;
                            float toYIcon = from[yReplyPosition] * (1f - 0.5f) + to[yReplyPosition] * 0.5f;
                            float xIcon = from[xReplyIconPosition] * (1f - iconProgress) + toXIcon * iconProgress;
                            float cyIcon = from[cyReplyIconPosition] * (1f - iconProgressY) + toYIcon * iconProgressY;
                            float sizeIcon = from[sizeReplyIconPosition] * (1f - iconProgress) + to[sizeReplyIconPosition] * iconProgress;
                            replyDrawable.setColorFilter(replyLineColor, PorterDuff.Mode.MULTIPLY);
                            replyDrawable.setBounds((int) xIcon, (int) (cyIcon - sizeIcon / 2), (int) (xIcon + sizeIcon), (int) (cyIcon + sizeIcon / 2));
                            replyDrawable.draw(canvas);
                        }
                    } else {
                        if (xProgress2 <= 0.5f) {
                            float iconProgress = xProgress2 * 2;
                            float toXIcon = from[xReplyPosition] * (1f - 0.5f) + to[xReplyPosition] * 0.5f;
                            float xIcon = from[xReplyIconPosition] * (1f - iconProgress) + toXIcon * iconProgress;
                            float cyIcon = from[cyReplyIconPosition] * (1f - yProgress2) + to[cyReplyIconPosition] * yProgress2;
                            float sizeIcon = from[sizeReplyIconPosition] * (1f - iconProgress) + to[sizeReplyIconPosition] * iconProgress;
                            replyDrawable.setColorFilter(replyLineColor, PorterDuff.Mode.MULTIPLY);
                            replyDrawable.setBounds((int) xIcon, (int) (cyIcon - sizeIcon / 2), (int) (xIcon + sizeIcon), (int) (cyIcon + sizeIcon / 2));
                            replyDrawable.draw(canvas);
                        }
                    }
                }

                if (animationType == 4) {
                    float emojiScale = (float) (endEmojiScaleTime - startEmojiScaleTime) / duration;
                    float progressEmojiScale = (moveProgress - (float) startEmojiScaleTime / duration) / emojiScale;
                    float emojiScaleValue = emojiScaleInterpolator.getInterpolation(progressEmojiScale);
                    if (moveProgress < (float) startEmojiScaleTime / duration) {
                        emojiScaleValue = 0;
                    } else if (moveProgress > (float) endEmojiScaleTime / duration) {
                        emojiScaleValue = 1;
                    }
                    float emojiSize1 = from[emojiSizePosition] * (1f - emojiScaleValue) + to[emojiSizePosition] * emojiScaleValue;
                    float scale = emojiSize1 / to[emojiSizePosition];

                    drawAnimatedEmoji(canvas, xMessageText, yMessageText, scale, timeAppears);
                } else if (animationType == 5) {
                    float stickerScale = (float) (endStickerScaleTime - startStickerScaleTime) / duration;
                    float progressStickerScale = (moveProgress - (float) startStickerScaleTime / duration) / stickerScale;
                    float stickerScaleValue = stickerScaleInterpolator.getInterpolation(progressStickerScale);
                    if (moveProgress < (float) startStickerScaleTime / duration) {
                        stickerScaleValue = 0;
                    } else if (moveProgress > (float) endStickerScaleTime / duration) {
                        stickerScaleValue = 1;
                    }
                    float scaleWidth = (from[widthStickerPosition] * (1f - stickerScaleValue) + to[widthStickerPosition] * stickerScaleValue) / to[widthStickerPosition];
                    float scaleHeight = (from[heightStickerPosition] * (1f - stickerScaleValue) + to[heightStickerPosition] * stickerScaleValue) / to[heightStickerPosition];

                    float cxSticker = from[cxStickerPosition] * (1f - xProgress2) + to[cxStickerPosition] * xProgress2;
                    if (moveProgress < (float) startXTime / duration) {
                        cxSticker = from[cxStickerPosition];
                    } else if (moveProgress > (float) endXTime / duration) {
                        cxSticker = to[cxStickerPosition];
                    }

                    float cySticker = from[cyStickerPosition] * (1f - yProgress2) + to[cyStickerPosition] * yProgress2;
                    if (moveProgress < (float) startYTime / duration) {
                        cySticker = from[cyStickerPosition];
                    } else if (moveProgress > (float) endYTime / duration) {
                        cySticker = to[cyStickerPosition];
                    }

                    drawSticker(canvas, cxSticker, cySticker, scaleWidth, scaleHeight, timeAppears);
                }

                if (message.isVoice() || animationType == 1 || animationType == 3) {
                    drawTime(canvas, messageView.timeLayout, xTime, yTime, timeAppears);
                    drawCheckDrawable(canvas, xCheckDrawable, yCheckDrawable, timeAppears);
                }

                if (message.isVoice()) {
                    float toMessageTextSize = messageView.getDurationLayout().getPaint().getTextSize() + (AndroidUtilities.dp(15) - messageView.getDurationLayout().getPaint().getTextSize()) * (1f - settingsTextScaleValue);

                    circlePaint.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_chat_messagePanelVoiceBackground), Theme.getColor(messageView.getRadialProgress().getCircleColorKey()), colorChangeProgress));
                    dotPaint.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_chat_recordedVoiceDot), Theme.chat_docBackPaint.getColor(), colorChangeProgress));
                    durationPaint.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_chat_recordTime), Theme.getColor(Theme.key_chat_outAudioDurationText), colorChangeProgress));

                    recordCircle.drawWaves(canvas, cxRecordCircle, cyRecordCircle, 1f - hideWavesProgress);

                    canvas.drawCircle(cxRecordCircle, cyRecordCircle, radius, circlePaint);
                    canvas.drawCircle(cxRecordDot, cyRecordDot, dotRadius, dotPaint);

                    // draw duration
                    canvas.save();
                    canvas.translate(xDuration, yDuration);
                    durationPaint.setTextSize(toMessageTextSize);
                    if (settingsTextScaleValue > 0.5) {
                        durationPaint.setTypeface(null);
                    }
                    durationPaint.getTextBounds(messageView.getDurationLayout().getText().toString(), 0, messageView.getDurationLayout().getText().length(), durationTextBounds);
                    canvas.drawText(messageView.getDurationLayout().getText(), 0, messageView.getDurationLayout().getText().length(), 0, durationTextBounds.height() / 2f, durationPaint);
                    canvas.restore();

                    canvas.save();
                    float scale = radius / toRadius;
                    canvas.scale(scale, scale, cxRecordCircle, cyRecordCircle);
                    canvas.translate(cxRecordCircle - messageView.getRadialProgress().getProgressRect().centerX(), cyRecordCircle - messageView.getRadialProgress().getProgressRect().centerY());
                    messageView.getRadialProgress().setOverrideAlpha(progress);
                    messageView.getRadialProgress().setDrawBackground(false);
                    messageView.getRadialProgress().draw(canvas);
                    messageView.getRadialProgress().setDrawBackground(true);
                    messageView.getRadialProgress().setOverrideAlpha(1f);
                    canvas.restore();

                    canvas.save();
                    canvas.translate(xWaveForm, cyWaveForm);
                    messageView.getSeekBarWaveform().draw(canvas, this);
                    canvas.restore();
                }

                if (animationType == 7) {
                    final InstantCameraView.InstantViewCameraContainer cameraContainer = instantCameraView.getCameraContainer();
                    cameraContainer.setPivotX(0);
                    cameraContainer.setPivotY(0);

                    float xVideo = (to[xVideoPosition] - from[xVideoPosition]) * xProgress2;
                    float yVideo = (to[yVideoPosition] - from[yVideoPosition]) * yProgress2;
                    float scale = to[sizeVideoPosition] / from[sizeVideoPosition];

                    cameraContainer.setScaleX(scale);
                    cameraContainer.setScaleY(scale);
                    cameraContainer.setTranslationX(xVideo);
                    cameraContainer.setTranslationY(yVideo);
                    instantCameraView.getSwitchButtonView().setAlpha((int) (1 - timeAppears));
                    instantCameraView.getPaint().setAlpha((int) (255 * (1 - timeAppears)));
                    instantCameraView.getMuteImageView().setAlpha((int) (1 - timeAppears));

                    drawTime(canvas, messageView.timeLayout, xTime, yTime, timeAppears);
                }
                //restore clipRect
                canvas.restore();
                if (message.isVoice()) {
                    recordCircle.drawIcon(canvas, (int) from[cxPositionRecordCircle], (int) from[cyPositionRecordCircle], 1f - moveProgress);

                    recordCircle.skipDraw = false;
                    canvas.save();
                    canvas.translate(recordCircle.getX() - getX(), recordCircle.getY() - getY());
                    recordCircle.draw(canvas);
                    canvas.restore();
                    recordCircle.skipDraw = true;
                }
            }


        };

        containerView.addView(view);

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(valueAnimator -> {
            progress = (float) valueAnimator.getAnimatedValue();
            view.invalidate();
        });


        animator.setInterpolator(new LinearInterpolator());
        animator.setDuration(duration);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (view.getParent() != null) {
                    messageView.setVoiceTransitionInProgress(false);
                    messageView.setSingleLineMessageTransitionInProgress(false);
                    messageView.setAnimatedEmojiTransitionInProgress(false);
                    messageView.setStickerTransitionInProgress(false);
                    messageView.setVideoTransitionInProgress(false);
                    messageView.getTransitionParams().isRunningMessageEnterTransition = false;
                    recordCircle.skipDraw = false;
                    recordDot.skipDraw = false;
                    if (message.isAnyKindOfSticker() || message.isAnimatedEmoji() || animationType == 7) {
                        messageView.getPhotoImage().setAlpha(1f);
                    }

                    if (animationType == 7) {
                        messageView.getTransitionParams().ignoreAlpha = false;
                        messageView.setAlpha(1);
                        instantCameraView.hideCamera(true);
                        instantCameraView.setVisibility(View.INVISIBLE);
                    }

                    messageView.getTransitionParams().resetAnimation();
                    messageView.getTransitionParams().animateReplayX = 0;
                    messageView.getTransitionParams().animateReplayY = 0;
                    messageView.invalidate();
                    containerView.removeView(view);
                }
            }
        });
    }

    public void start() {
        animator.start();
    }

    private int getCurrentAnimationType() {
        if (hasOnlyText()) {
            return 1;
        } else if (!message.isRestrictedMessage && message.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && message.messageOwner.media.webpage instanceof TLRPC.TL_webPage) {
            return 3;
        } else if (message.isAnimatedEmoji()) {
            return 4;
        } else if (message.isAnyKindOfSticker()) {
            return 5;
        } else if (message.isVoice()) {
            return 6;
        } else if (message.type == MessageObject.TYPE_ROUND_VIDEO) {
            return 7;
        }
        return -1;
    }

    private void updateAnimationParams() {
        duration = MessagesController.getGlobalMessageAnimationSettings().getInt("duration_type_" + animationType, 3000);

        if (animationType == 1 || animationType == 3 || animationType == 4 || animationType == 5 || animationType == 6 || animationType == 7) {
            startXTime = MessagesController.getGlobalMessageAnimationSettings().getInt("start_time_type_" + animationType + "_row_" + "xPosition", 0);
            endXTime = MessagesController.getGlobalMessageAnimationSettings().getInt("end_time_type_" + animationType + "_row_" + "xPosition", 0);
            startXProgress = MessagesController.getGlobalMessageAnimationSettings().getFloat("start_progress_type_" + animationType + "_row_" + "xPosition", 0);
            endXProgress = MessagesController.getGlobalMessageAnimationSettings().getFloat("end_progress_type_" + animationType + "_row_" + "xPosition", 0);

            startYTime = MessagesController.getGlobalMessageAnimationSettings().getInt("start_time_type_" + animationType + "_row_" + "yPosition", 0);
            endYTime = MessagesController.getGlobalMessageAnimationSettings().getInt("end_time_type_" + animationType + "_row_" + "yPosition", 0);
            startYProgress = MessagesController.getGlobalMessageAnimationSettings().getFloat("start_progress_type_" + animationType + "_row_" + "yPosition", 0);
            endYProgress = MessagesController.getGlobalMessageAnimationSettings().getFloat("end_progress_type_" + animationType + "_row_" + "yPosition", 0);

            startTimeAppearsTime = MessagesController.getGlobalMessageAnimationSettings().getInt("start_time_type_" + animationType + "_row_" + "timeAppears", 0);
            endTimeAppearsTime = MessagesController.getGlobalMessageAnimationSettings().getInt("end_time_type_" + animationType + "_row_" + "timeAppears", 0);
            startTimeAppearsProgress = MessagesController.getGlobalMessageAnimationSettings().getFloat("start_progress_type_" + animationType + "_row_" + "timeAppears", 0);
            endTimeAppearsProgress = MessagesController.getGlobalMessageAnimationSettings().getFloat("end_progress_type_" + animationType + "_row_" + "timeAppears", 0);

            xInterpolator = new CubicBezierInterpolator(startXProgress, 0, 1 - endXProgress, 1);
            yInterpolator = new CubicBezierInterpolator(startYProgress, 0, 1 - endYProgress, 1);
            timeAppearsInterpolator = new CubicBezierInterpolator(startTimeAppearsProgress, 0, 1 - endTimeAppearsProgress, 1);
        }

        if (animationType == 1 || animationType == 3 || animationType == 4 || animationType == 5 || animationType == 6) {
            startColorChangeTime = MessagesController.getGlobalMessageAnimationSettings().getInt("start_time_type_" + animationType + "_row_" + "colorChange", 0);
            endColorChangeTime = MessagesController.getGlobalMessageAnimationSettings().getInt("end_time_type_" + animationType + "_row_" + "colorChange", 0);
            startColorChangeProgress = MessagesController.getGlobalMessageAnimationSettings().getFloat("start_progress_type_" + animationType + "_row_" + "colorChange", 0);
            endColorChangeProgress = MessagesController.getGlobalMessageAnimationSettings().getFloat("end_progress_type_" + animationType + "_row_" + "colorChange", 0);

            colorChangeInterpolator = new CubicBezierInterpolator(startColorChangeProgress, 0, 1 - endColorChangeProgress, 1);
        }

        if (animationType == 1 || animationType == 3 || animationType == 6) {
            startBubbleShapeTime = MessagesController.getGlobalMessageAnimationSettings().getInt("start_time_type_" + animationType + "_row_" + "bubbleShape", 0);
            endBubbleShapeTime = MessagesController.getGlobalMessageAnimationSettings().getInt("end_time_type_" + animationType + "_row_" + "bubbleShape", 0);
            startBubbleShapeProgress = MessagesController.getGlobalMessageAnimationSettings().getFloat("start_progress_type_" + animationType + "_row_" + "bubbleShape", 0);
            endBubbleShapeProgress = MessagesController.getGlobalMessageAnimationSettings().getFloat("end_progress_type_" + animationType + "_row_" + "bubbleShape", 0);

            startTextScaleTime = MessagesController.getGlobalMessageAnimationSettings().getInt("start_time_type_" + animationType + "_row_" + "textScale", 0);
            endTextScaleTime = MessagesController.getGlobalMessageAnimationSettings().getInt("end_time_type_" + animationType + "_row_" + "textScale", 0);
            startTextScaleProgress = MessagesController.getGlobalMessageAnimationSettings().getFloat("start_progress_type_" + animationType + "_row_" + "textScale", 0);
            endTextScaleProgress = MessagesController.getGlobalMessageAnimationSettings().getFloat("end_progress_type_" + animationType + "_row_" + "textScale", 0);

            bubbleShapeInterpolator = new CubicBezierInterpolator(startBubbleShapeProgress, 0, 1 - endBubbleShapeProgress, 1);
            textScaleInterpolator = new CubicBezierInterpolator(startTextScaleProgress, 0, 1 - endTextScaleProgress, 1);
        }

        if (animationType == 4) {
            startEmojiScaleTime = MessagesController.getGlobalMessageAnimationSettings().getInt("start_time_type_" + animationType + "_row_" + "emojiScale", 0);
            endEmojiScaleTime = MessagesController.getGlobalMessageAnimationSettings().getInt("end_time_type_" + animationType + "_row_" + "emojiScale", 0);
            startEmojiScaleProgress = MessagesController.getGlobalMessageAnimationSettings().getFloat("start_progress_type_" + animationType + "_row_" + "emojiScale", 0);
            endEmojiScaleProgress = MessagesController.getGlobalMessageAnimationSettings().getFloat("end_progress_type_" + animationType + "_row_" + "emojiScale", 0);

            emojiScaleInterpolator = new CubicBezierInterpolator(startEmojiScaleProgress, 0, 1 - endEmojiScaleProgress, 1);
        }

        if (animationType == 5) {
            startStickerScaleTime = MessagesController.getGlobalMessageAnimationSettings().getInt("start_time_type_" + animationType + "_row_" + "stickerScale", 0);
            endStickerScaleTime = MessagesController.getGlobalMessageAnimationSettings().getInt("end_time_type_" + animationType + "_row_" + "stickerScale", 0);
            startStickerScaleProgress = MessagesController.getGlobalMessageAnimationSettings().getFloat("start_progress_type_" + animationType + "_row_" + "stickerScale", 0);
            endStickerScaleProgress = MessagesController.getGlobalMessageAnimationSettings().getFloat("end_progress_type_" + animationType + "_row_" + "stickerScale", 0);

            stickerScaleInterpolator = new CubicBezierInterpolator(startStickerScaleProgress, 0, 1 - endStickerScaleProgress, 1);
        }
    }

    private boolean hasDefaultBackground() {
        return !message.isAnimatedEmoji() && !message.isAnyKindOfSticker() && message.type != MessageObject.TYPE_ROUND_VIDEO;
    }

    private boolean hasOnlyText() {
        return message.type != MessageObject.TYPE_ROUND_VIDEO && !message.isAnimatedEmoji() && !message.isAnyKindOfSticker() && message.messageText.length() != 0 && !message.isVoice() &&
                !(!message.isRestrictedMessage && message.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && message.messageOwner.media.webpage instanceof TLRPC.TL_webPage);
    }

    private boolean hasTextOrAnimatedEmoji() {
        return message.isAnimatedEmoji() || (message.textLayoutBlocks != null && !message.textLayoutBlocks.isEmpty());
    }

    private void drawBackground(Canvas canvas, float x1, float x2, float y1, float y2, int backgroundColor, float bubbleShape, float timeAppearsAlpha) {
        if (messageView.getCurrentBackgroundDrawable() != null) {
            bounds.set((int) x1, (int) y1, (int) x2, (int) y2);
            messageView.getCurrentBackgroundDrawable().getPaint().setColor(backgroundColor);
            messageDrawable.setBounds(bounds);
            messageDrawable.setTop((int) y1, bounds.height(), messageView.getCurrentBackgroundDrawable().isTopNear, messageView.getCurrentBackgroundDrawable().isBottomNear);
            messageDrawable.draw(canvas, messageView.getCurrentBackgroundDrawable().getPaint(), bubbleShape);
        }
    }

    private RectF rectImage = new RectF();

    private void drawAnimatedEmoji(Canvas canvas, float x, float y, float scale, float timeAppearsAlpha) {
        if (messageView.photoImage.getBitmap() != null) {
            rectImage.set(x, y, x + messageView.photoWidth * scale, y + messageView.photoHeight * scale);
            canvas.drawBitmap(messageView.photoImage.getBitmap(), null, rectImage, null);
            messageView.getTransitionParams().animateChange = true;
            messageView.getTransitionParams().animateDrawingTimeAlpha = true;
            messageView.getTransitionParams().animateShouldDrawTimeOnMedia = true;
            messageView.getTransitionParams().animateChangeProgress = timeAppearsAlpha;
        }
    }

    private void drawSticker(Canvas canvas, float x, float y, float scaleWidth, float scaleHeight, float timeAppearsAlpha) {
        if (messageView.photoImage.getBitmap() != null) {
            rectImage.set(x, y, x + messageView.photoWidth * scaleWidth, y + messageView.photoHeight * scaleHeight);
            canvas.drawBitmap(messageView.photoImage.getBitmap(), null, rectImage, null);
            messageView.getTransitionParams().animateChange = true;
            messageView.getTransitionParams().animateDrawingTimeAlpha = true;
            messageView.getTransitionParams().animateShouldDrawTimeOnMedia = true;
            messageView.getTransitionParams().animateChangeProgress = timeAppearsAlpha;

        }
    }

    private void drawMessage(Canvas canvas, MessageObject messageObject, float x, float y, float textSize) {
        if (hasTextOrAnimatedEmoji()) {
            messagePaint.setTextSize(textSize);
            canvas.save();
            canvas.translate(x, y);
            messageObject.textLayoutBlocks.get(0).textLayout.draw(canvas, null, messagePaint, 0);
            canvas.restore();

            if (messageView.hasLinkPreview) {
                messageView.getTransitionParams().animateLinkPreviewX = AndroidUtilities.dp(1) + x;
                messageView.getTransitionParams().animateLinkPreviewY = messageView.textY + messageObject.textHeight + y;
                messageView.drawLinkPreview(canvas);
            }
        }
    }

    private void drawTime(Canvas canvas, StaticLayout timeLayout, float x, float y, float timeAppearsAlpha) {
        int oldAlpha = Theme.chat_timePaint.getAlpha();
        Theme.chat_timePaint.setAlpha((int) (255 * timeAppearsAlpha));
        canvas.save();
        canvas.translate(x, y);
        timeLayout.draw(canvas);
        canvas.restore();
        Theme.chat_timePaint.setAlpha(oldAlpha);
    }

    private void drawCheckDrawable(Canvas canvas, float x, float y, float timeAppearsAlpha) {
        BaseCell.setDrawableBounds(checkDrawable, x, y);
        checkDrawable.setAlpha((int) (255 * timeAppearsAlpha));
        checkDrawable.draw(canvas);
    }

}
