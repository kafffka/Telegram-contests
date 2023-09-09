package org.telegram.ui.Components.Paint;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.Paint.Views.PaintTextOptionsView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class PersistColorPalette {

    private final static List<Integer> DEFAULT_MODIFIABLE_COLORS = Arrays.asList(
            0xffD7A07C,
            0xffAC734C,
            0xff90512C,
            0xff532E1F,
            0xff818181
    );

    private final static List<Integer> PRESET_COLORS = Arrays.asList(
            0xff000000,
            0xffffffff,
            0xffff453a,
            0xffff8a00,
            0xffffd60a,
            0xff34c759,
            0xff63e6e2,
            0xff0a84ff,
            0xffbf5af2
    );

    public final static int MODIFIABLE_COLORS_COUNT = DEFAULT_MODIFIABLE_COLORS.size();
    public final static int PRESET_COLORS_COUNT = PRESET_COLORS.size();
    public final static int COLORS_COUNT = MODIFIABLE_COLORS_COUNT + PRESET_COLORS_COUNT;

    private final static int BRUSH_TEXT = -1;
    private static PersistColorPalette[] instances = new PersistColorPalette[UserConfig.MAX_ACCOUNT_COUNT];

    private final SharedPreferences mConfig;
    private final List<Integer> colors = new ArrayList<>(COLORS_COUNT);
    private final HashMap<Integer, Integer> brushColor = new HashMap<>(Brush.BRUSHES_LIST.size());
    private Integer hiddenColor = null;
    private List<Integer> pendingChange = new ArrayList<>(COLORS_COUNT);

    private int currentBrush;
    private int currentAlignment;
    private int currentTextType;
    private float currentWeight;
    private String currentTypeface;
    private boolean fillShapes;
    private boolean inTextMode;

    public PersistColorPalette(int currentUser) {
        mConfig = ApplicationLoader.applicationContext.getSharedPreferences("photo_color_palette_" + currentUser, Context.MODE_PRIVATE);
        currentBrush = mConfig.getInt("brush", 0);
        currentWeight = mConfig.getFloat("weight", .5f);
        currentTypeface = mConfig.getString("typeface", "roboto");
        currentAlignment = mConfig.getInt("text_alignment", PaintTextOptionsView.ALIGN_LEFT);
        currentTextType = mConfig.getInt("text_type", 0);
        fillShapes = mConfig.getBoolean("fill_shapes", false);

        loadColors();
    }

    public static PersistColorPalette getInstance(int currentAccount) {
        if (instances[currentAccount] == null) {
            instances[currentAccount] = new PersistColorPalette(currentAccount);
        }
        return instances[currentAccount];
    }

    public int getCurrentTextType() {
        return currentTextType;
    }

    public void setCurrentTextType(int currentTextType) {
        this.currentTextType = currentTextType;
        mConfig.edit().putInt("text_type", currentTextType).apply();
    }

    public void setInTextMode(boolean inTextMode) {
        if (this.inTextMode != inTextMode) {
            this.inTextMode = inTextMode;
            if (inTextMode) {
                setCurrentBrush(BRUSH_TEXT, false);
            } else {
                setCurrentBrush(mConfig.getInt("brush", 0), false);
            }
        }
    }

    public int getCurrentAlignment() {
        return currentAlignment;
    }

    public void setCurrentAlignment(int currentAlignment) {
        this.currentAlignment = currentAlignment;
        mConfig.edit().putInt("text_alignment", currentAlignment).apply();
    }

    public String getCurrentTypeface() {
        return currentTypeface;
    }

    public void setCurrentTypeface(String currentTypeface) {
        this.currentTypeface = currentTypeface;
        mConfig.edit().putString("typeface", currentTypeface).apply();
    }

    public float getWeight(String key, float defaultWeight) {
        return mConfig.getFloat("weight_" + key, defaultWeight);
    }

    public void setWeight(String key, float weight) {
        mConfig.edit().putFloat("weight_" + key, weight).apply();
    }

    public float getCurrentWeight() {
        return currentWeight;
    }

    public void setCurrentWeight(float currentWeight) {
        this.currentWeight = currentWeight;
        mConfig.edit().putFloat("weight", currentWeight).apply();
    }

    public int getCurrentBrush() {
        return currentBrush;
    }

    public void setCurrentBrush(int currentBrush) {
        setCurrentBrush(currentBrush, true);
    }

    public void setCurrentBrush(int currentBrush, boolean saveBrush) {
        this.currentBrush = currentBrush;
        if (saveBrush) {
            mConfig.edit().putInt("brush", currentBrush).apply();
        }

        Integer color = brushColor.get(currentBrush);
        if (color != null) {
            selectColor(color);
            saveColors();
        }
    }

    public boolean getFillShapes() {
        return fillShapes;
    }

    public void toggleFillShapes() {
        this.fillShapes = !this.fillShapes;
        mConfig.edit().putBoolean("fill_shapes", fillShapes).apply();
    }

    public void cleanup() {
        pendingChange.clear();
        pendingChange.addAll(DEFAULT_MODIFIABLE_COLORS);
        hiddenColor = null;
        SharedPreferences.Editor editor = mConfig.edit();
        for (int i = 0; i < Brush.BRUSHES_LIST.size(); i++) {
            editor.remove("brush_color_" + i);
        }
        editor.remove("brush_color_" + BRUSH_TEXT);
        brushColor.clear();
        editor.apply();

        saveColors();
    }

    private void checkIndex(int i) {
        if (i < 0 || i >= COLORS_COUNT) {
            throw new IndexOutOfBoundsException("Color palette index should be in range 0 ... " + COLORS_COUNT);
        }
    }

    public int getColor(int index) {
        checkIndex(index);
        if (index >= colors.size()) {
            if (index < MODIFIABLE_COLORS_COUNT) {
                return DEFAULT_MODIFIABLE_COLORS.get(index);
            } else {
                return PRESET_COLORS.get(index - MODIFIABLE_COLORS_COUNT);
            }
        }
        return colors.get(index);
    }

    public int getCurrentColor() {
        return (colors.size() > 0) ? colors.get(0) : DEFAULT_MODIFIABLE_COLORS.get(0);
    }

    public void selectColor(int color) {
        selectColor(color, true);
    }

    public void selectColor(int color, boolean updateBrush) {
        int i = colors.indexOf(color);
        if (i != -1) {
            selectColorIndex(i);
        } else {
            int prevColor = getCurrentColor();
            List<Integer> from = new ArrayList<>(pendingChange.isEmpty() ? colors : pendingChange);
            if (hiddenColor != null && hiddenColor == color) {
                hiddenColor = null;
            }
            if (hiddenColor != null && PRESET_COLORS.contains(prevColor) && !PRESET_COLORS.contains(color)) {
                from.add(hiddenColor);
                hiddenColor = null;
            }
            pendingChange.clear();
            pendingChange.add(color);
            for (int j = 0; j < from.size(); j++) {
                // don't add preset colors to the recent colors
                if (!PRESET_COLORS.contains(from.get(j))) {
                    pendingChange.add(from.get(j));
                }
            }
            if (pendingChange.size() < DEFAULT_MODIFIABLE_COLORS.size()) {
                for (int j = pendingChange.size(); j < DEFAULT_MODIFIABLE_COLORS.size(); ++j) {
                    pendingChange.add(DEFAULT_MODIFIABLE_COLORS.get(j));
                }
            } else if (pendingChange.size() > DEFAULT_MODIFIABLE_COLORS.size()) {
                if (PRESET_COLORS.contains(color)) {
                    hiddenColor = pendingChange.get(pendingChange.size() - 1);
                }
                pendingChange = pendingChange.subList(0, DEFAULT_MODIFIABLE_COLORS.size());
            }
            if (updateBrush) {
                brushColor.put(currentBrush, color);
            }
        }
    }

    public void selectColorIndex(int index) {
        selectColorIndex(index, true);
    }

    public void selectColorIndex(int index, boolean updateBrush) {
        int prevColor = getCurrentColor();
        int color = index < 0 || index >= colors.size() ? DEFAULT_MODIFIABLE_COLORS.get(index) : colors.get(index);

        List<Integer> from = new ArrayList<>(pendingChange.isEmpty() ? colors : pendingChange);
        if (hiddenColor != null && hiddenColor == color) {
            hiddenColor = null;
        }
        if (hiddenColor != null && PRESET_COLORS.contains(prevColor) && !PRESET_COLORS.contains(color)) {
            from.add(hiddenColor);
            hiddenColor = null;
        }
        pendingChange.clear();
        pendingChange.add(color);
        for (int i = 0; i < from.size(); i++) {
            // don't add preset colors to the recent colors
           if (from.get(i) != color && !PRESET_COLORS.contains(from.get(i))) {
                pendingChange.add(from.get(i));
            }
        }
        if (pendingChange.size() < DEFAULT_MODIFIABLE_COLORS.size()) {
            for (int j = pendingChange.size(); j < DEFAULT_MODIFIABLE_COLORS.size(); ++j) {
                pendingChange.add(DEFAULT_MODIFIABLE_COLORS.get(j));
            }
        } else if (pendingChange.size() > DEFAULT_MODIFIABLE_COLORS.size()) {
            if (PRESET_COLORS.contains(color)) {
                hiddenColor = pendingChange.get(pendingChange.size() - 1);
            } else {
                hiddenColor = null;
            }
            pendingChange = pendingChange.subList(0, DEFAULT_MODIFIABLE_COLORS.size());
        }
        if (updateBrush) {
            brushColor.put(currentBrush, color);
        }
    }

    private void loadColors() {
        for (int i = 0; i < MODIFIABLE_COLORS_COUNT; i++) {
            colors.add((int) mConfig.getLong("color_" + i, DEFAULT_MODIFIABLE_COLORS.get(i)));
        }
        for (int i = 0; i < PRESET_COLORS_COUNT; i++) {
            colors.add(PRESET_COLORS.get(i));
        }

        if (mConfig.contains("color_" + MODIFIABLE_COLORS_COUNT + 1)) {
            hiddenColor = (int) mConfig.getLong("color_" + MODIFIABLE_COLORS_COUNT + 1, 0);
        }

        for (int i = 0; i < Brush.BRUSHES_LIST.size(); i++) {
            int color = (int) mConfig.getLong("brush_color_" + i, colors.get(0));
            brushColor.put(i, color);
        }

        int color = (int) mConfig.getLong("brush_color_" + BRUSH_TEXT, colors.get(0));
        brushColor.put(BRUSH_TEXT, color);
    }

    public void resetCurrentColor() {
        setCurrentBrush(0);
    }

    public void saveColors() {
        if (pendingChange.isEmpty()) {
            return;
        }

        SharedPreferences.Editor editor = mConfig.edit();
        for (int i = 0; i < MODIFIABLE_COLORS_COUNT; i++) {
            editor.putLong("color_" + i, i < pendingChange.size() ? pendingChange.get(i) : (long) DEFAULT_MODIFIABLE_COLORS.get(i));
        }
        if (hiddenColor != null) {
            editor.putLong("color_" + MODIFIABLE_COLORS_COUNT + 1, hiddenColor);
        } else {
            editor.remove("color_" + MODIFIABLE_COLORS_COUNT + 1);
        }

        Integer currentBrushColor = brushColor.get(currentBrush);
        if (currentBrushColor != null) {
            editor.putLong("brush_color_" + currentBrush, currentBrushColor);
        }
        editor.apply();

        colors.clear();
        colors.addAll(pendingChange);
        colors.addAll(PRESET_COLORS);
        pendingChange.clear();
    }
}
