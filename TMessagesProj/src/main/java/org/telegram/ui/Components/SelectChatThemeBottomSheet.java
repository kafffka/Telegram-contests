package org.telegram.ui.Components;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.ChatTheme;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ChatThemesHorizontalListCell;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;

public class SelectChatThemeBottomSheet extends BottomSheet {

    private int userId;
    private final ChatActivity chatActivity;

    private LinearLayout container;
    private TextView titleView;
    private RLottieDrawable sunDrawable;
    private ChatThemesHorizontalListCell cell;
    private TextView buttonTextView;

    public SelectChatThemeBottomSheet(ChatActivity chatActivity, TLRPC.User user) {
        super(chatActivity.getParentActivity(), true);
        setApplyBottomPadding(false);
        setDimBehindAlpha(128);
        ChatTheme.enableIsPreviewMode(user.id);
        this.userId = user.id;
        this.chatActivity = chatActivity;

        container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);

        FrameLayout frameLayout = new FrameLayout(getContext());

        titleView = new TextView(getContext());
        titleView.setText(LocaleController.getString("SelectTheme", R.string.SelectTheme));
        titleView.setTextColor(Theme.getChatThemeColor(chatActivity.getChatTheme(), Theme.key_dialogTextBlack));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        frameLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START, 16, 8, 0, 0));


        RLottieImageView darkThemeView = new RLottieImageView(getContext());

        final ChatTheme[] info = new ChatTheme[1];
        cell = new ChatThemesHorizontalListCell(getContext(), ChatTheme.chatThemes, userId) {
            @Override
            public void selectTheme(ChatTheme themeInfo) {
                super.selectTheme(themeInfo);
                info[0] = themeInfo;
                if (info[0] == null) {
                    buttonTextView.setText(LocaleController.getString("ResetChatTheme", R.string.ResetChatTheme));
                    darkThemeView.setVisibility(View.INVISIBLE);
                } else {
                    buttonTextView.setText(LocaleController.getString("ApplyChatTheme", R.string.ApplyChatTheme));
                    darkThemeView.setVisibility(View.VISIBLE);
                }
            }
        };
        cell.setBackgroundColor(Theme.getChatThemeColor(chatActivity.getChatTheme(), Theme.key_dialogBackground));

        sunDrawable = new RLottieDrawable(R.raw.sun_outline, "" + R.raw.sun_outline, AndroidUtilities.dp(28), AndroidUtilities.dp(28), true, null);
        if (!ChatTheme.previewIsDark) {
            sunDrawable.setCustomEndFrame(36);
        } else {
            sunDrawable.setCustomEndFrame(0);
            sunDrawable.setCurrentFrame(36);
        }
        sunDrawable.setPlayInDirectionOfCustomEndFrame(true);
        int sunColor = Theme.getChatThemeColor(chatActivity.getChatTheme(), Theme.key_featuredStickers_addButton);
        sunDrawable.beginApplyLayerColors();
        sunDrawable.setLayerColor("Sunny.**", sunColor);
        sunDrawable.setLayerColor("Path 6.**", sunColor);
        sunDrawable.setLayerColor("Path.**", sunColor);
        sunDrawable.setLayerColor("Path 5.**", sunColor);
        sunDrawable.setLayerColor("Path 10.**", sunColor);
        sunDrawable.setLayerColor("Path 11.**", sunColor);
        sunDrawable.commitApplyLayerColors();

        darkThemeView.setAnimation(sunDrawable);
        darkThemeView.setScaleType(ImageView.ScaleType.CENTER);
        darkThemeView.setOnClickListener(v -> {
            ChatTheme.switchPreviewIsDark();
            cell.reloadItems();
            ChatTheme theme = ChatTheme.getChatThemeForUser(userId);
            if (theme != null) {
                if (theme.isDark()) {
                    sunDrawable.setCustomEndFrame(36);
                } else {
                    sunDrawable.setCustomEndFrame(0);
                }
                darkThemeView.playAnimation();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightChatTheme, theme, true);

            }
        });
        if (chatActivity.getChatTheme() == null) {
            darkThemeView.setVisibility(View.INVISIBLE);
        }

        frameLayout.addView(darkThemeView, LayoutHelper.createFrame(44, 44, Gravity.CENTER_VERTICAL | Gravity.END, 0, 0, 8, 0));
        container.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        container.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 113, 0, 8, 0, 0));

        buttonTextView = new TextView(getContext()) {
            @Override
            public CharSequence getAccessibilityClassName() {
                return Button.class.getName();
            }
        };
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(Theme.getChatThemeColor(chatActivity.getChatTheme(), Theme.key_featuredStickers_buttonText));
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonTextView.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Theme.getChatThemeColor(chatActivity.getChatTheme(), Theme.key_featuredStickers_addButton), Theme.getChatThemeColor(chatActivity.getChatTheme(), Theme.key_featuredStickers_addButtonPressed)));
        buttonTextView.setText(LocaleController.getString("ApplyChatTheme", R.string.ApplyChatTheme));
        buttonTextView.setOnClickListener(v -> {
            ChatTheme previousChatTheme = ChatTheme.getPreviousChatThemeForUser(userId);
            if (info[0] != null && (previousChatTheme == null || !previousChatTheme.getEmoticon().equals(info[0].getEmoticon()))) {
                ChatTheme.isPreviewMode = false;
                if (ChatTheme.previewIsDark != Theme.isCurrentThemeDark()) {
                    ChatTheme.disableIsPreviewMode(userId);
                    ChatTheme changedChatTheme = ChatTheme.getChatThemeByEmoticon(info[0].getEmoticon());
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightChatTheme, changedChatTheme, false);
                } else {
                    ChatTheme.clearPreviousChatThemeForUser(userId);
                }
                MessagesController.getInstance(currentAccount).saveChatTheme(info[0], user);
            } else if (info[0] == null) {
                ChatTheme.isPreviewMode = false;
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightChatTheme, null, false);
                MessagesController.getInstance(currentAccount).saveChatTheme(null, user);
            }
            dismiss();
        });

        container.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 14, 16, 16));
        setCustomView(container);
        setBackgroundColor(Theme.getChatThemeColor(chatActivity.getChatTheme(), Theme.key_dialogBackground));
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();
        ThemeDescription.ThemeDescriptionDelegate descriptionDelegate = this::updateColors;
        arrayList.add(new ThemeDescription(container, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogBackground));
        arrayList.add(new ThemeDescription(cell, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogBackground));
        arrayList.add(new ThemeDescription(titleView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_dialogTextBlack));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_dialogBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_featuredStickers_addButton));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_featuredStickers_addButtonPressed));
        return arrayList;
    }

    private void updateColors() {
        int sunColor = Theme.getChatThemeColor(chatActivity.getChatTheme(), Theme.key_featuredStickers_addButton);
        sunDrawable.setLayerColor("Sunny.**", sunColor);
        sunDrawable.setLayerColor("Path 6.**", sunColor);
        sunDrawable.setLayerColor("Path.**", sunColor);
        sunDrawable.setLayerColor("Path 5.**", sunColor);
        sunDrawable.setLayerColor("Path 10.**", sunColor);
        sunDrawable.setLayerColor("Path 11.**", sunColor);
        buttonTextView.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Theme.getChatThemeColor(chatActivity.getChatTheme(), Theme.key_featuredStickers_addButton), Theme.getChatThemeColor(chatActivity.getChatTheme(), Theme.key_featuredStickers_addButtonPressed)));
        setBackgroundColor(Theme.getChatThemeColor(chatActivity.getChatTheme(), Theme.key_dialogBackground));
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (ChatTheme.isPreviewMode) {
            ChatTheme previousChatTheme = ChatTheme.getPreviousChatThemeForUser(userId);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightChatTheme, previousChatTheme, false);
        }
    }

    @Override
    protected boolean onContainerTouchEvent(MotionEvent event) {
        if (event != null) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            boolean touchInsideContainer = y >= containerView.getTop() && x >= containerView.getLeft() && x <= containerView.getRight();
            if (touchInsideContainer) {
                return false;
            } else {
                chatActivity.getFragmentView().dispatchTouchEvent(event);
                return true;
            }
        } else {
            return false;
        }
    }
}
