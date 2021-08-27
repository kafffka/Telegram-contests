package org.telegram.ui.ActionBar;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.MotionBackgroundDrawable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class ChatTheme {

    public static HashMap<Integer, ChatTheme> previousUserChatThemesDict = new HashMap<>();
    public static HashMap<Integer, Boolean> hasPreviousUserChatThemesDict = new HashMap<>();
    public static ArrayList<ChatTheme> chatThemes = new ArrayList<>();
    private static HashMap<Integer, String> userChatThemesDict = new HashMap<>();
    public static boolean previewIsDark = Theme.isCurrentThemeDark();
    public static boolean isPreviewMode = false;

    public Theme.ThemeInfo themeInfo;
    public Theme.ThemeAccent themeAccent;
    private HashMap<String, Integer> currentColors;
    private boolean isDark;
    private String emoticon;
    private TLRPC.Theme tl_theme;
    private TLRPC.TL_chatTheme tl_parent;
    private Drawable chatWallpaper;
    public boolean loadedWallpaperBasedFile = false;
    public boolean isWallpaperMotion;
    private boolean isPatternWallpaper;
    private int patternIntensity;
    public Runnable wallpaperLoadTask;

    public ChatTheme(TLRPC.Theme tl_theme, TLRPC.TL_chatTheme tl_parent) {
        this.tl_theme = tl_theme;
        this.tl_parent = tl_parent;
        this.currentColors = new HashMap<>();
        this.isDark = tl_parent.dark_theme == tl_theme;
        this.emoticon = tl_parent.emoticon;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatTheme chatTheme = (ChatTheme) o;
        return isDark == chatTheme.isDark && isWallpaperMotion == chatTheme.isWallpaperMotion && isPatternWallpaper == chatTheme.isPatternWallpaper && patternIntensity == chatTheme.patternIntensity && Objects.equals(themeInfo, chatTheme.themeInfo) && Objects.equals(themeAccent, chatTheme.themeAccent) && Objects.equals(currentColors, chatTheme.currentColors) && emoticon.equals(chatTheme.emoticon) && tl_theme.equals(chatTheme.tl_theme) && tl_parent.equals(chatTheme.tl_parent) && Objects.equals(chatWallpaper, chatTheme.chatWallpaper) && Objects.equals(wallpaperLoadTask, chatTheme.wallpaperLoadTask);
    }

    @Override
    public int hashCode() {
        return Objects.hash(themeInfo, themeAccent, currentColors, isDark, emoticon, tl_theme, tl_parent, chatWallpaper, isWallpaperMotion, isPatternWallpaper, patternIntensity, wallpaperLoadTask);
    }

    public void fillColors() {
        currentColors.clear();
        themeAccent = themeInfo.getAccent(false);

        themeInfo.accentBaseColor = (int) themeAccent.accentColor;
        themeInfo.setPreviewBackgroundColor((int) themeAccent.backgroundOverrideColor);
        themeInfo.previewBackgroundGradientColor1 = (int) themeAccent.backgroundGradientOverrideColor1;
        themeInfo.previewBackgroundGradientColor2 = (int) themeAccent.backgroundGradientOverrideColor2;
        themeInfo.previewBackgroundGradientColor3 = (int) themeAccent.backgroundGradientOverrideColor3;
        themeInfo.patternIntensity = (int) themeAccent.patternIntensity;
        themeInfo.pathToWallpaper = themeAccent.getPathToWallpaper().getAbsolutePath();

        Theme.ThemeInfo parentThemeInfo = getBaseThemeInfo();
        HashMap<String, Integer> currentColorsNoAccent = Theme.getThemeFileValues(null, parentThemeInfo.assetName, null);
        currentColors.putAll(currentColorsNoAccent);
        themeAccent.fillAccentColors(currentColorsNoAccent, currentColors, parentThemeInfo, true);
        if (!isDark) {
            currentColors.put(Theme.key_dialogBackground, 0xffffffff);
            currentColors.put(Theme.key_dialogTextBlack, 0xff222222);

            currentColors.put(Theme.key_chat_messageTextOut, 0xff000000);
            currentColors.put(Theme.key_chat_messageTextIn, 0xff000000);
            currentColors.put(Theme.key_chat_outReplyMessageText, 0xff000000);
            currentColors.put(Theme.key_chat_inReplyMessageText, 0xff000000);
            currentColors.put(Theme.key_chat_inBubble, 0xffffffff);
        } else {
            currentColors.put(Theme.key_dialogBackground, currentColors.get(Theme.key_actionBarDefault));
        }

        int textColor;
        int subTextColor;
        int actionBarColor = getColor(Theme.key_actionBarDefault);
        boolean useBlackText = AndroidUtilities.computePerceivedBrightness(actionBarColor) > 0.705f;
        if (useBlackText) {
            textColor = 0xff212121;
            subTextColor = 0xff555555;
        } else {
            textColor = 0xffffffff;
            subTextColor = 0xffeeeeee;
        }
        currentColors.put(Theme.key_actionBarDefaultTitle, textColor);
        currentColors.put(Theme.key_actionBarDefaultSubtitle, subTextColor);
        currentColors.put(Theme.key_actionBarDefaultIcon, textColor);

        int messageInColor = getColor(Theme.key_chat_inBubble);
        float[] hsvTemp1 = Theme.getTempHsv(1);
        Color.colorToHSV(themeInfo.accentBaseColor, hsvTemp1);
        int outBubbleColor = getColor(Theme.key_chat_outBubble);
        int firstColor = themeAccent.myMessagesAccentColor != 0 ? themeAccent.myMessagesAccentColor : themeAccent.accentColor;
        int myMessagesAccent = Theme.getAccentColor(hsvTemp1, outBubbleColor, firstColor);
        themeInfo.setPreviewInColor(messageInColor);
        themeInfo.setPreviewOutColor(myMessagesAccent);
        loadChatWallpaper();
    }

    public String getEmoticon() {
        return emoticon;
    }

    public boolean isDark() {
        return isDark;
    }

    private Theme.ThemeInfo getBaseThemeInfo() {
        String key = Theme.getBaseThemeKey(themeInfo.info.settings);
        return Theme.getTheme(key);
    }

    public void loadChatWallpaper() {
        if (loadedWallpaperBasedFile || getEmoticon() == null || getEmoticon().isEmpty() || themeInfo == null) {
            return;
        }

        File wallpaperFile;
        boolean wallpaperMotion;
        Theme.ThemeAccent accent = themeInfo.getAccent(false);
        if (accent != null) {
            wallpaperFile = accent.getPathToWallpaper();
            wallpaperMotion = accent.patternMotion;
        } else {
            wallpaperFile = null;
            wallpaperMotion = false;
        }
        int intensity;
        Theme.OverrideWallpaperInfo overrideWallpaper = themeInfo.overrideWallpaper;
        if (overrideWallpaper != null) {
            intensity = (int) (overrideWallpaper.intensity * 100);
        } else {
            intensity = (int) (accent != null ? (accent.patternIntensity * 100) : themeInfo.patternIntensity);
        }

        Utilities.searchQueue.postRunnable(wallpaperLoadTask = () -> {
            boolean overrideTheme = overrideWallpaper != null;
            if (overrideWallpaper != null) {
                isWallpaperMotion = overrideWallpaper.isMotion;
                isPatternWallpaper = overrideWallpaper.color != 0 && !overrideWallpaper.isDefault() && !overrideWallpaper.isColor();
            } else {
                isWallpaperMotion = themeInfo.isMotion;
                isPatternWallpaper = themeInfo.patternBgColor != 0;
            }
            patternIntensity = intensity;
            if (!overrideTheme) {
                Integer backgroundColor = currentColors.get(Theme.key_chat_wallpaper);
                Integer gradientToColor3 = currentColors.get(Theme.key_chat_wallpaper_gradient_to3);
                if (gradientToColor3 == null) {
                    gradientToColor3 = 0;
                }
                Integer gradientToColor2 = currentColors.get(Theme.key_chat_wallpaper_gradient_to2);
                Integer gradientToColor1 = currentColors.get(Theme.key_chat_wallpaper_gradient_to1);
                if (wallpaperFile != null && wallpaperFile.exists()) {
                    try {
                        if (backgroundColor != null && gradientToColor1 != null && gradientToColor2 != null) {
                            MotionBackgroundDrawable motionBackgroundDrawable = new MotionBackgroundDrawable(backgroundColor, gradientToColor1, gradientToColor2, gradientToColor3, false);
                            motionBackgroundDrawable.setPatternBitmap(patternIntensity, BitmapFactory.decodeFile(wallpaperFile.getAbsolutePath()));
                            chatWallpaper = motionBackgroundDrawable;
                        } else {
                            chatWallpaper = Drawable.createFromPath(wallpaperFile.getAbsolutePath());
                        }
                        isWallpaperMotion = wallpaperMotion;
                        isPatternWallpaper = true;
                        loadedWallpaperBasedFile = true;
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                } else if (backgroundColor != null) {
                    if (gradientToColor1 != null && gradientToColor2 != null) {
                        chatWallpaper = new MotionBackgroundDrawable(backgroundColor, gradientToColor1, gradientToColor2, gradientToColor3, false);
                    }
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                wallpaperLoadTask = null;
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewChatWallpapper, this);
            });
        });
    }


    public Drawable getWallpaper() {
        return chatWallpaper;
    }

    public Integer getColor(String key) {
        Integer color = currentColors.get(key);
        if (color == null && !key.equals(Theme.key_chat_outBubbleGradient1) && !key.equals(Theme.key_chat_outBubbleGradient2) && !key.equals(Theme.key_chat_outBubbleGradient3)) {
            color = Theme.getColor(key);
        }
        return color;
    }

    public static void loadRemoteChatThemes(int currentAccount, boolean force) {
        Theme.loadRemoteChatThemes(currentAccount, force);
    }

    public static void setChatThemeForUser(int userId, String emoticon) {
        if (emoticon != null) {
            userChatThemesDict.put(userId, emoticon);
        }
    }

    public static ChatTheme getPreviousChatThemeForUser(int userId) {
        if (previousUserChatThemesDict.containsKey(userId)) {
            return previousUserChatThemesDict.get(userId);
        } else {
            return null;
        }
    }

    public static void clearPreviousChatThemeForUser(int userId) {
        previousUserChatThemesDict.remove(userId);
        hasPreviousUserChatThemesDict.remove(userId);
    }

    public static void enableIsPreviewMode(int userId) {
        isPreviewMode = true;
        resetPreviewIsDark();
        if (!previousUserChatThemesDict.containsKey(userId) && userChatThemesDict.containsKey(userId)) {
            previousUserChatThemesDict.put(userId, getChatThemeByEmoticon(userChatThemesDict.get(userId)));
        }
    }

    public static void disableIsPreviewMode(int userId) {
        isPreviewMode = false;
        if (previewIsDark != Theme.isCurrentThemeDark()) {
            resetPreviewIsDark();
        }
        previousUserChatThemesDict.remove(userId);
    }

    public static void clearChatThemeForUser(int userId) {
        userChatThemesDict.remove(userId);
    }

    public static ChatTheme getChatThemeForUser(int userId) {
        for (int i = 0; i < userChatThemesDict.size(); i++) {
            if (userChatThemesDict.containsKey(userId)) {
                return getChatThemeByEmoticon(userChatThemesDict.get(userId));
            }
        }
        return null;
    }

    public static ChatTheme getChatThemeByEmoticon(String emoticon) {
        ArrayList<ChatTheme> currentChatThemes = getCurrentChatThemes();
        for (int i = 0; i < currentChatThemes.size(); i++) {
            if (currentChatThemes.get(i).emoticon.equals(emoticon)) {
                return currentChatThemes.get(i);
            }
        }
        return null;
    }

    public static ArrayList<ChatTheme> getCurrentChatThemes() {
        ArrayList<ChatTheme> themes = new ArrayList<>();
        for (int i = 0; i < chatThemes.size(); i++) {
            if ((isDarkThemesRequired() && chatThemes.get(i).isDark) || (!isDarkThemesRequired() && !chatThemes.get(i).isDark)) {
                themes.add(chatThemes.get(i));
            }
        }
        return themes;
    }

    public static ArrayList<ChatTheme> getAllChatThemes() {
        return chatThemes;
    }

    public static ArrayList<ChatTheme> getDayChatThemes() {
        ArrayList<ChatTheme> themes = new ArrayList<>();
        for (int i = 0; i < chatThemes.size(); i++) {
            if (!chatThemes.get(i).isDark) {
                themes.add(chatThemes.get(i));
            }
        }
        return themes;
    }

    public static ArrayList<ChatTheme> getNightChatThemes() {
        ArrayList<ChatTheme> themes = new ArrayList<>();
        for (int i = 0; i < chatThemes.size(); i++) {
            if (chatThemes.get(i).isDark) {
                themes.add(chatThemes.get(i));
            }
        }
        return themes;
    }

    public static void switchPreviewIsDark() {
        previewIsDark = !previewIsDark;
    }

    public static void resetPreviewIsDark() {
        previewIsDark = Theme.isCurrentThemeDark();
    }

    public static void reloadAllWallpapers() {
        for (int i = 0; i < chatThemes.size(); i++) {
            chatThemes.get(i).loadChatWallpaper();
        }
    }

    private static boolean isDarkThemesRequired() {
        if (isPreviewMode) {
            return previewIsDark;
        } else {
            return Theme.isCurrentThemeDark();
        }
    }
}
