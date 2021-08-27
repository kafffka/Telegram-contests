package org.telegram.ui.Cells;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

import androidx.annotation.Keep;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.ChatTheme;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.ThemeActivity;
import org.telegram.ui.ThemeSetUrlActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class ChatThemesHorizontalListCell extends RecyclerListView implements NotificationCenter.NotificationCenterDelegate {

    private LinearLayoutManager horizontalLayoutManager;
    private HashMap<String, Theme.ThemeInfo> loadingThemes = new HashMap<>();
    private HashMap<Theme.ThemeInfo, String> loadingWallpapers = new HashMap<>();
    private ArrayList<ChatTheme> defaultThemes;
    private int noChatThemeBackgroundColor;
    private int noChatThemeBorderColor;
    private int userId;
    private String selectedEmoticon;

    private class ThemesListAdapter extends SelectionAdapter {

        private Context mContext;

        ThemesListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(ViewHolder holder) {
            return false;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(new InnerThemeView(mContext));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            InnerThemeView view = (InnerThemeView) holder.itemView;
            view.setTheme(defaultThemes.get(position), position == getItemCount() - 1, position == 0);
        }

        @Override
        public int getItemCount() {
            return defaultThemes.size();
        }
    }

    private class InnerThemeView extends View {

        private ChatTheme chatTheme;
        private Theme.ThemeInfo themeInfo;
        private RectF rect = new RectF();
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private boolean isLast;
        private boolean isFirst;

        private int inColor;
        private int outColor;
        private int backColor;
        private int checkColor;

        private int oldBackColor;

        private float accentState;
        private final ArgbEvaluator evaluator = new ArgbEvaluator();

        private Drawable backgroundDrawable;
        private Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private BitmapShader bitmapShader;
        private Matrix shaderMatrix = new Matrix();

        private StaticLayout noThemeStaticLayout;

        public InnerThemeView(Context context) {
            super(context);
            setWillNotDraw(false);
            textPaint.setTextSize(AndroidUtilities.dp(20));
            borderPaint.setStrokeWidth(AndroidUtilities.dpf2(2));
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeCap(Paint.Cap.ROUND);
            borderPaint.setStrokeJoin(Paint.Join.ROUND);
            bubblePaint.setColor(0xffffffff);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(70 + (isLast ? 16f : 4f) + (isFirst ? 16f : 4f)), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(113), MeasureSpec.EXACTLY));
        }

        private void applyTheme() {
            oldBackColor = backColor = themeInfo.getPreviewBackgroundColor();
            updateColors();
            bitmapShader = null;
            backgroundDrawable = null;
            if (themeInfo.previewBackgroundGradientColor1 != 0 && themeInfo.previewBackgroundGradientColor2 != 0) {
                final MotionBackgroundDrawable drawable = new MotionBackgroundDrawable(themeInfo.getPreviewBackgroundColor(), themeInfo.previewBackgroundGradientColor1, themeInfo.previewBackgroundGradientColor2, themeInfo.previewBackgroundGradientColor3, true);
                drawable.setPatternBitmap(themeInfo.patternIntensity, BitmapFactory.decodeFile(themeInfo.pathToWallpaper));
                drawable.setRoundRadius(AndroidUtilities.dp(6));
                backgroundDrawable = drawable;
            }

            if (themeInfo.getPreviewBackgroundColor() == 0 && themeInfo.previewParsed && backgroundDrawable == null) {
                backgroundDrawable = Theme.createDefaultWallpaper(100, 200);
                if (backgroundDrawable instanceof MotionBackgroundDrawable) {
                    ((MotionBackgroundDrawable) backgroundDrawable).setRoundRadius(AndroidUtilities.dp(6));
                }
            }
            invalidate();
        }

        public void setTheme(ChatTheme chatTheme, boolean last, boolean first) {
            this.chatTheme = chatTheme;
            isFirst = first;
            isLast = last;
            if (chatTheme == null) {
                return;
            }

            themeInfo = chatTheme.themeInfo;
            if (themeInfo != null && themeInfo.info != null) {
                Theme.ThemeAccent themeAccent = themeInfo.themeAccents.get(0);
                int messageInColor = Theme.getDefaultColor(Theme.key_chat_inBubble);

                float[] hsvTemp1 = Theme.getTempHsv(1);
                Color.colorToHSV(themeAccent.parentTheme.accentBaseColor, hsvTemp1);
                int color = Theme.getDefaultColor(Theme.key_chat_outBubble);
                int firstColor = themeAccent.myMessagesAccentColor != 0 ? themeAccent.myMessagesAccentColor : themeAccent.accentColor;
                int myMessagesAccent = Theme.getAccentColor(hsvTemp1, color, firstColor);

                themeInfo.setPreviewInColor(messageInColor);
                themeInfo.setPreviewOutColor(myMessagesAccent);
                themeInfo.setPreviewBackgroundColor((int) themeAccent.backgroundOverrideColor);
                themeInfo.previewBackgroundGradientColor1 = (int) themeAccent.backgroundGradientOverrideColor1;
                themeInfo.previewBackgroundGradientColor2 = (int) themeAccent.backgroundGradientOverrideColor2;
                themeInfo.previewBackgroundGradientColor3 = (int) themeAccent.backgroundGradientOverrideColor3;
                themeInfo.patternIntensity = (int) themeAccent.patternIntensity;
                themeInfo.pathToWallpaper = themeAccent.getPathToWallpaper().getAbsolutePath();
            }
            applyTheme();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (themeInfo != null && themeInfo.info != null && !themeInfo.themeLoaded) {
                String name = FileLoader.getAttachFileName(themeInfo.info.document);
                if (!loadingThemes.containsKey(name) && !loadingWallpapers.containsKey(themeInfo)) {
                    themeInfo.themeLoaded = true;
                    applyTheme();
                }
            }
        }

        public void updateCurrentThemeCheck() {
            invalidate();
        }

        void updateColors() {
            if (chatTheme == null) {
                return;
            }
            oldBackColor = backColor;
            Theme.ThemeAccent accent = themeInfo.getAccent(false);
            int accentColor;
            int backAccent;
            int gradientAccent;
            if (accent != null) {
                accentColor = accent.accentColor;
                int myAccentColor = accent.myMessagesAccentColor != 0 ? accent.myMessagesAccentColor : accentColor;
                int backgroundOverrideColor = (int) accent.backgroundOverrideColor;
                backAccent = backgroundOverrideColor != 0 ? backgroundOverrideColor : accentColor;
                gradientAccent = accent.myMessagesGradientAccentColor1 != 0 ? accent.myMessagesGradientAccentColor1 : myAccentColor;
            } else {
                accentColor = 0;
                backAccent = 0;
                gradientAccent = 0;
            }
            inColor = Theme.changeColorAccent(themeInfo, accentColor, themeInfo.getPreviewInColor());
            outColor = gradientAccent;
            backColor = Theme.changeColorAccent(themeInfo, backAccent, themeInfo.getPreviewBackgroundColor());
            checkColor = outColor;
            accentState = 1f;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int x = isFirst ? AndroidUtilities.dp(16f) : AndroidUtilities.dp(4f);
            int y = AndroidUtilities.dp(11);
            rect.set(x, y, x + AndroidUtilities.dp(70), y + AndroidUtilities.dp(96));

            boolean drawContent = themeInfo != null && themeInfo.info != null && themeInfo.themeLoaded && themeInfo.info.settings != null;
            boolean drawBorder = false;

            if (drawContent) {
                paint.setColor(blend(oldBackColor, backColor));
                if (backgroundDrawable != null) {
                    if (bitmapShader != null) {
                        BitmapDrawable bitmapDrawable = (BitmapDrawable) backgroundDrawable;
                        float bitmapW = bitmapDrawable.getBitmap().getWidth();
                        float bitmapH = bitmapDrawable.getBitmap().getHeight();
                        float scaleW = bitmapW / rect.width();
                        float scaleH = bitmapH / rect.height();

                        shaderMatrix.reset();
                        float scale = 1.0f / Math.min(scaleW, scaleH);
                        if (bitmapW / scaleH > rect.width()) {
                            bitmapW /= scaleH;
                            shaderMatrix.setTranslate(x - (bitmapW - rect.width()) / 2, y);
                        } else {
                            bitmapH /= scaleW;
                            shaderMatrix.setTranslate(x, y - (bitmapH - rect.height()) / 2);
                        }
                        shaderMatrix.preScale(scale, scale);
                        bitmapShader.setLocalMatrix(shaderMatrix);
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), bitmapPaint);
                    } else {
                        backgroundDrawable.setBounds((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
                        backgroundDrawable.draw(canvas);
                    }
                } else {
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), paint);
                }
                bubblePaint.setColor(outColor);
                rect.set(x + AndroidUtilities.dp(21), y + AndroidUtilities.dp(10), x + AndroidUtilities.dp(21 + 43), y + AndroidUtilities.dp(10 + 22));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), bubblePaint);
                bubblePaint.setColor(inColor);
                rect.set(x + AndroidUtilities.dp(6), y + AndroidUtilities.dp(36), x + AndroidUtilities.dp(6 + 43), y + AndroidUtilities.dp(36 + 22));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), bubblePaint);
                String name = themeInfo.emoticon;

                int width = (int) Math.ceil(textPaint.measureText(name));
                textPaint.setTextSize(AndroidUtilities.dp(20));
                textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                canvas.drawText(name, x + (AndroidUtilities.dp(70) - width) / 2f, y + AndroidUtilities.dp(84), textPaint);

                borderPaint.setColor(checkColor);
                drawBorder = themeInfo.emoticon.equals(selectedEmoticon);
            } else if (chatTheme == null) {
                paint.setColor(noChatThemeBackgroundColor);
                canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), paint);
                String name = "❌";
                textPaint.setTextSize(AndroidUtilities.dp(16));
                int width = (int) Math.ceil(textPaint.measureText(name));
                textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                canvas.drawText(name, x + (AndroidUtilities.dp(70) - width) / 2f, y + AndroidUtilities.dp(84), textPaint);

                canvas.save();
                StaticLayout textLayout = getNoThemeStaticLayout();
                canvas.translate(x + (AndroidUtilities.dp(70) - textLayout.getWidth()) / 2f, y + AndroidUtilities.dp(16));
                textLayout.draw(canvas);
                canvas.restore();

                borderPaint.setColor(noChatThemeBorderColor);
                drawBorder = selectedEmoticon == null;
            }
            if (drawBorder) {
                x = isFirst ? AndroidUtilities.dp(13) : AndroidUtilities.dp(1f);
                y = AndroidUtilities.dp(8);
                rect.set(x, y, x + AndroidUtilities.dp(76), y + AndroidUtilities.dp(102));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(9), AndroidUtilities.dp(9), borderPaint);
            }
        }

        private String getThemeName() {
            String name = themeInfo.getName();
            if (name.contains(" ")) {
                name = name.substring(0, name.indexOf(' '));
            }
            return name;
        }

        private StaticLayout getNoThemeStaticLayout() {
            if (noThemeStaticLayout != null) {
                return noThemeStaticLayout;
            }
            TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG + TextPaint.SUBPIXEL_TEXT_FLAG);
            textPaint.setColor(Theme.getColor(Theme.key_chat_emojiPanelTrendingDescription));
            textPaint.setTextSize(AndroidUtilities.dp(14));
            textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            noThemeStaticLayout = StaticLayoutEx.createStaticLayout2(LocaleController.getString("ChatNoTheme", R.string.ChatNoTheme), textPaint, AndroidUtilities.dp(52), Layout.Alignment.ALIGN_CENTER, 1f, 0f, true, TextUtils.TruncateAt.END, AndroidUtilities.dp(52), 2);
            return noThemeStaticLayout;
        }

        private int blend(int color1, int color2) {
            if (accentState == 1.0f) {
                return color2;
            } else {
                return (int) evaluator.evaluate(accentState, color1, color2);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setText(getThemeName());
            info.setClassName(Button.class.getName());
            info.setCheckable(true);
            info.setEnabled(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
            }
        }
    }

    public ChatThemesHorizontalListCell(Context context, ArrayList<ChatTheme> def, int userId) {
        super(context);

        this.userId = userId;
        setItems();
        setItemAnimator(null);
        setLayoutAnimation(null);
        horizontalLayoutManager = new LinearLayoutManager(context) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        setPadding(0, 0, 0, 0);
        setClipToPadding(false);
        horizontalLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        setLayoutManager(horizontalLayoutManager);
        setAdapter(new ThemesListAdapter(context));
        setOnItemClickListener((view1, position) -> {
            selectTheme(((InnerThemeView) view1).chatTheme);
            int left = view1.getLeft();
            int right = view1.getRight();
            if (left < 0) {
                smoothScrollBy(left - AndroidUtilities.dp(8), 0);
            } else if (right > getMeasuredWidth()) {
                smoothScrollBy(right - getMeasuredWidth(), 0);
            }
        });
    }

    private void setItems() {
        defaultThemes = ChatTheme.getCurrentChatThemes();
        if (ChatTheme.getChatThemeForUser(userId) != null) {
            ChatTheme currentChatTheme = ChatTheme.getChatThemeForUser(userId);
            selectedEmoticon = currentChatTheme.getEmoticon();
            noChatThemeBorderColor = Theme.getChatThemeColor(currentChatTheme, Theme.key_featuredStickers_addButton);
        } else {
            selectedEmoticon = null;
        }
        if (selectedEmoticon != null) {
            defaultThemes.add(0, null);
        }
        if (ChatTheme.previewIsDark) {
            noChatThemeBackgroundColor = 0x22ffffff;
        } else {
            noChatThemeBackgroundColor = 0xfff2f2f2;
        }
    }

    public void reloadItems() {
        if (getAdapter() != null) {
            setItems();
            getAdapter().notifyDataSetChanged();
        }
    }

    public void selectTheme(ChatTheme chatTheme) {
        if (chatTheme != null) {
            Theme.ThemeInfo themeInfo = chatTheme.themeInfo;
            if (themeInfo.info != null) {
                if (!themeInfo.themeLoaded) {
                    return;
                }
                if (!themeInfo.info.for_chat && themeInfo.info.document == null) {
                    return;
                }
            }
            if (!TextUtils.isEmpty(themeInfo.assetName)) {
                Theme.PatternsLoader.createLoader(false);
            }
            if (themeInfo == Theme.getCurrentChatTheme()) {
                return;
            } else {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightChatTheme, chatTheme, true);
            }
            selectedEmoticon = chatTheme.getEmoticon();
        } else {
            selectedEmoticon = null;
        }

        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View child = getChildAt(a);
            if (child instanceof InnerThemeView) {
                ((InnerThemeView) child).updateCurrentThemeCheck();
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (getParent() != null && getParent().getParent() != null) {
            getParent().getParent().requestDisallowInterceptTouchEvent(canScrollHorizontally(-1));
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override
    public void setBackgroundColor(int color) {
        super.setBackgroundColor(color);
        invalidateViews();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.fileLoadFailed);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.fileLoadFailed);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileLoaded) {
            String fileName = (String) args[0];
            File file = (File) args[1];
            Theme.ThemeInfo info = loadingThemes.get(fileName);
            if (info != null) {
                loadingThemes.remove(fileName);
                if (loadingWallpapers.remove(info) != null) {
                    Utilities.globalQueue.postRunnable(() -> {
                        info.badWallpaper = !info.createBackground(file, info.pathToWallpaper);
                        AndroidUtilities.runOnUIThread(() -> checkVisibleTheme(info));
                    });
                } else {
                    checkVisibleTheme(info);
                }
            }
        } else if (id == NotificationCenter.fileLoadFailed) {
            String fileName = (String) args[0];
            loadingThemes.remove(fileName);
        }
    }

    private void checkVisibleTheme(Theme.ThemeInfo info) {
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View child = getChildAt(a);
            if (child instanceof InnerThemeView) {
                InnerThemeView view = (InnerThemeView) child;
                if (view.themeInfo == info) {
                    view.themeInfo.themeLoaded = true;
                    view.applyTheme();
                }
            }
        }
    }
}
