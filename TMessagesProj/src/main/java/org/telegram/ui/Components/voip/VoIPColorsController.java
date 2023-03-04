package org.telegram.ui.Components.voip;

import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Bitmaps;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;

public class VoIPColorsController {
    public interface Listener {
        void onColorsChanged();
    }

    public static final int FLAG_BLUE_VIOLET = 1;
    public static final int FLAG_BLUE_GREEN = 1 << 1;
    public static final int FLAG_GREEN = 1 << 2;

    private static final float PHASE_DURATION_MS = 2000f;
    private static final float COLOR_CHANGE_DURATION_MS = 8000f;
    private static final float COlOR_CHANGE_DURATION_FAST_MS = 500f;
    private static final float LIGHT_DARK_COlORS_INVALIDATE_TIME_MS = 10f;
    private static final int PHASES_COUNT = 8;
    private static final float ALL_PHASES_DURATION = PHASES_COUNT * PHASE_DURATION_MS;

    private static final int SIGNAL_STABLE = 0;
    private static final int SIGNAL_WEAK = 1;
    private static final int SIGNAL_TO_STABLE = 2;
    private static final int SIGNAL_TO_NEXT_MAIN_COLOR = 3;
    private int signal = SIGNAL_STABLE;

    private Listener listener;
    private View backgroundView;
    private boolean isRunning;
    private boolean isPaused;
    private Bitmap bitmap;
    private Bitmap bitmapLight;
    private Bitmap bitmapDark;
    private int[][] currentColors;
    private int[][] prevColors;
    private int phase;
    private float phaseProgress;
    private float colorsProgress;
    private float changeSignalProgress;
    private long startTime;
    private long lastPauseTime;
    private int startColorPosition;
    private int endColorPosition;
    private float currentColorPhase;

    private int[] colorsBlueVioletMain = new int[] {0xff8148EC, 0xffB456D8, 0xff20A4D7, 0xff3F8BEA};
    private int[] colorsBlueGreenMain = new int[] {0xff3B7AF1, 0xff4576E9, 0xff08B0A3, 0xff17AAE4};
    private int[] colorsGreenMain = new int[] {0xff07BA63, 0xff07A9AC, 0xffA9CC66, 0xff5AB147};
    private int[] colorsOrangeRedMain = new int[] {0xffE7618F, 0xffE86958, 0xffDB904C, 0xffDE7238};


    private int[] colorsBlueVioletLight = new int[] {0xff9258FD, 0xffD664FF, 0xff2DC0F9, 0xff57A1FF};
    private int[] colorsBlueGreenLight = new int[] {0xff5FABFF, 0xff558BFF, 0xff04DCCC, 0xff28C2FF};
    private int[] colorsGreenLight = new int[] {0xff09E279, 0xff00D2D5, 0xffC7EF60, 0xff6DD957};
    private int[] colorsOrangeRedLight = new int[] {0xffFF82A5, 0xffFF7866, 0xffFEB055, 0xffFF8E51};


    private int[] colorsBlueVioletDark = new int[] {0xff6A2BDD, 0xffA736D0, 0xff0F95C9, 0xff287AE1};
    private int[] colorsBlueGreenDark = new int[] {0xff2C6ADF, 0xff2D60D6, 0xff009595, 0xff0291C9};
    private int[] colorsGreenDark = new int[] {0xff01934C, 0xff008B8E, 0xff8FBD37, 0xff319D27};
    private int[] colorsOrangeRedDark = new int[] {0xffE6306F, 0xffE23F29, 0xffC77616, 0xffD75A16};
    private int orderedColorsCount;
    private ArrayList<int[][]> orderedColors;


    private int[][] colorsBlueViolet = new int[][] {
        colorsBlueVioletMain, colorsBlueVioletLight, colorsBlueVioletDark
    };
    private int[][] colorsBlueGreen = new int[][] {
        colorsBlueGreenMain, colorsBlueGreenLight, colorsBlueGreenDark
    };
    private int[][] colorsGreen = new int[][] {
        colorsGreenMain, colorsGreenLight, colorsGreenDark
    };
    private int[][] colorsOrangeRed = new int[][] {
        colorsOrangeRedMain, colorsOrangeRedLight, colorsOrangeRedDark
    };

    private long lastLightDarkColorsInvalidateTime;
    private long changeSignalTime;

    private final int bitmapWidth = 60;
    private final int bitmapHeight = 80;

    public VoIPColorsController(int colorsFlag, int startColorFlag) {
        this.currentColors = new int[3][4];
        this.prevColors = new int[3][4];
        this.bitmap = createBitmap();
        this.bitmapLight = createBitmap();
        this.bitmapDark = createBitmap();

        orderedColorsCount = 0;
        if ((colorsFlag & FLAG_BLUE_VIOLET) == FLAG_BLUE_VIOLET) {
            orderedColorsCount++;
        }
        if ((colorsFlag & FLAG_BLUE_GREEN) == FLAG_BLUE_GREEN) {
            orderedColorsCount++;
        }
        if ((colorsFlag & FLAG_GREEN) == FLAG_GREEN) {
            orderedColorsCount += 2;
        }
        orderedColors = new ArrayList(orderedColorsCount);
        if (startColorFlag == FLAG_BLUE_VIOLET) {
            orderedColors.add(colorsBlueViolet);
            orderedColors.add(colorsBlueGreen);
            if (orderedColorsCount > 2) {
                orderedColors.add(colorsGreen);
                orderedColors.add(colorsBlueGreen);
            }
        }
        if (startColorFlag == FLAG_GREEN) {
            startColorPosition = 2;
            orderedColors.add(colorsGreen);
            orderedColors.add(colorsBlueGreen);
            orderedColors.add(colorsBlueViolet);
            orderedColors.add(colorsBlueGreen);
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }


    public void invalidate() {
        if (!isRunning) {
            return;
        }
        long newTime = SystemClock.elapsedRealtime();
        long dt = newTime - startTime;
        phase = (int) Math.floor((dt % ALL_PHASES_DURATION) / PHASE_DURATION_MS);
        phaseProgress = 1f - (dt % PHASE_DURATION_MS) / PHASE_DURATION_MS;

        if (signal == SIGNAL_WEAK || signal == SIGNAL_TO_NEXT_MAIN_COLOR) {
            changeSignalProgress = (newTime - changeSignalTime) / COlOR_CHANGE_DURATION_FAST_MS;
            if (changeSignalProgress > 1f) {
                changeSignalProgress = 1f;
                if (signal == SIGNAL_TO_NEXT_MAIN_COLOR) {
                    lastPauseTime = (long) (SystemClock.elapsedRealtime());
                    isPaused = true;
                    isRunning = false;
                    return;
                }
            }
        } else {
            float fullColorsPhases = dt % (COLOR_CHANGE_DURATION_MS * (orderedColorsCount));
            currentColorPhase = fullColorsPhases / COLOR_CHANGE_DURATION_MS;
            startColorPosition = (int) Math.floor(currentColorPhase);
            if (startColorPosition >= orderedColorsCount || startColorPosition < 0) {
                startColorPosition = 0;
            }
            endColorPosition = startColorPosition + 1;
            if (endColorPosition == orderedColorsCount) {
                endColorPosition = 0;
            }
            if (signal == SIGNAL_TO_STABLE) {
                changeSignalProgress = (newTime - changeSignalTime) / COlOR_CHANGE_DURATION_FAST_MS;
                if (changeSignalProgress > 1) {
                    signal = SIGNAL_STABLE;
                }
            }
            colorsProgress = (dt % COLOR_CHANGE_DURATION_MS) / COLOR_CHANGE_DURATION_MS;
        }
        updateCurrentColors();
        if (bitmap != null) {
            Utilities.generateGradient(bitmap, true, phase, phaseProgress, bitmap.getWidth(), bitmap.getHeight(), bitmap.getRowBytes(), currentColors[0]);
            if (backgroundView != null) {
                backgroundView.invalidate();
            }
        }

        if (newTime - lastLightDarkColorsInvalidateTime > LIGHT_DARK_COlORS_INVALIDATE_TIME_MS) {
            if (bitmapLight != null && bitmapDark != null) {
                Utilities.generateGradient(bitmapLight, true, phase, phaseProgress, bitmapLight.getWidth(), bitmapLight.getHeight(), bitmapLight.getRowBytes(), currentColors[1]);
                Utilities.generateGradient(bitmapDark, true, phase, phaseProgress, bitmapDark.getWidth(), bitmapDark.getHeight(), bitmapDark.getRowBytes(), currentColors[2]);
                if (listener != null) {
                    listener.onColorsChanged();
                }
            }
            lastLightDarkColorsInvalidateTime = newTime;
        }
    }

    private void updateCurrentColors() {
        int[][] colorsStart;
        int[][] colorsEnd;

        if (signal == SIGNAL_WEAK || signal == SIGNAL_TO_NEXT_MAIN_COLOR) {

            colorsStart = prevColors;
            colorsEnd = signal == SIGNAL_WEAK ? colorsOrangeRed : orderedColors.get(currentColorPhase < 0.5f ? startColorPosition : endColorPosition);

            for (int i = 0; i < 3; i++) {
                currentColors[i][0] = AndroidUtilities.getOffsetColor(colorsStart[i][0], colorsEnd[i][0], changeSignalProgress, 255f);
                currentColors[i][1] = AndroidUtilities.getOffsetColor(colorsStart[i][1], colorsEnd[i][1], changeSignalProgress, 255f);
                currentColors[i][2] = AndroidUtilities.getOffsetColor(colorsStart[i][2], colorsEnd[i][2], changeSignalProgress, 255f);
                currentColors[i][3] = AndroidUtilities.getOffsetColor(colorsStart[i][3], colorsEnd[i][3], changeSignalProgress, 255f);
            }
        } else {
            colorsStart = orderedColors.get(startColorPosition);
            colorsEnd = orderedColors.get(endColorPosition);

            for (int i = 0; i < 3; i++) {
                currentColors[i][0] = AndroidUtilities.getOffsetColor(colorsStart[i][0], colorsEnd[i][0], colorsProgress, 255f);
                currentColors[i][1] = AndroidUtilities.getOffsetColor(colorsStart[i][1], colorsEnd[i][1], colorsProgress, 255f);
                currentColors[i][2] = AndroidUtilities.getOffsetColor(colorsStart[i][2], colorsEnd[i][2], colorsProgress, 255f);
                currentColors[i][3] = AndroidUtilities.getOffsetColor(colorsStart[i][3], colorsEnd[i][3], colorsProgress, 255f);
            }

            if (signal == SIGNAL_TO_STABLE) {
                colorsStart = prevColors;
                colorsEnd = currentColors;

                for (int i = 0; i < 3; i++) {
                    currentColors[i][0] = AndroidUtilities.getOffsetColor(colorsStart[i][0], colorsEnd[i][0], changeSignalProgress, 255f);
                    currentColors[i][1] = AndroidUtilities.getOffsetColor(colorsStart[i][1], colorsEnd[i][1], changeSignalProgress, 255f);
                    currentColors[i][2] = AndroidUtilities.getOffsetColor(colorsStart[i][2], colorsEnd[i][2], changeSignalProgress, 255f);
                    currentColors[i][3] = AndroidUtilities.getOffsetColor(colorsStart[i][3], colorsEnd[i][3], changeSignalProgress, 255f);
                }
            }

        }

    }

    public void setBackgroundView(View view) {
        this.backgroundView = view;
    }

    public void start() {
        startTime = SystemClock.elapsedRealtime() - 1000;
        isRunning = true;
        isPaused = false;
        invalidate();
    }

    public void resume() {
        long newTime = SystemClock.elapsedRealtime();
        if (signal == SIGNAL_TO_NEXT_MAIN_COLOR) {
            if (isPaused) {
                long pauseDt = newTime - lastPauseTime;
                startTime += pauseDt;
                isPaused = false;
                isRunning = true;
                setSignalToStable();
            } else {
                signal = SIGNAL_STABLE;
            }
            invalidate();
        } else {
            if (isPaused) {
                long pauseDt = newTime - lastPauseTime;
                startTime += pauseDt;
                isPaused = false;
                isRunning = true;
                invalidate();
            }
        }
    }

    public void pause(boolean fast) {
        if (fast || signal == SIGNAL_WEAK) {
            lastPauseTime = SystemClock.elapsedRealtime();
            isPaused = true;
            isRunning = false;
        } else {
            changeSignalTime = SystemClock.elapsedRealtime();
            for (int i = 0; i < 3; i++) {
                System.arraycopy(currentColors[i], 0, prevColors[i], 0, 4);
            }
            signal = SIGNAL_TO_NEXT_MAIN_COLOR;
        }
    }

    public void showWeakSignal() {
        if (signal == SIGNAL_WEAK) {
            return;
        }
        changeSignalTime = SystemClock.elapsedRealtime();
        for (int i = 0; i < 3; i++) {
            System.arraycopy(currentColors[i], 0, prevColors[i], 0, 4);
        }
        signal = SIGNAL_WEAK;
    }

    public boolean isWeakSignalActive() {
        return signal == SIGNAL_WEAK;
    }

    public void hideWeakSignal() {
        if (signal == SIGNAL_WEAK) {
            setSignalToStable();
        }
    }

    private void setSignalToStable() {
        changeSignalTime = SystemClock.elapsedRealtime();
        for (int i = 0; i < 3; i++) {
            System.arraycopy(currentColors[i], 0, prevColors[i], 0, 4);
        }
        signal = SIGNAL_TO_STABLE;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getLightColor(float parentWidth, float parentHeight, float x, float y) {
        int color = 0;
        if (bitmapLight != null && parentWidth != 0 && parentHeight != 0) {
            int scaledX = (int) ((bitmapWidth / parentWidth) * x);
            int scaledY = (int) ((bitmapHeight / parentHeight) * y);
            if (scaledX >= 0 && scaledX < bitmapWidth && scaledY >= 0 && scaledY < bitmapHeight) {
                color = bitmapLight.getPixel(scaledX, scaledY);
            }
        }
        return color;
    }

    public int getDarkColor(float parentWidth, float parentHeight, float x, float y) {
        int color = 0;
        if (bitmapDark != null && parentWidth != 0 && parentHeight != 0) {
            int scaledX = (int) ((bitmapWidth / parentWidth) * x);
            int scaledY = (int) ((bitmapHeight / parentHeight) * y);
            if (scaledX >= 0 && scaledX < bitmapWidth && scaledY >= 0 && scaledY < bitmapHeight) {
                color = bitmapDark.getPixel(scaledX, scaledY);
            }
        }
        return color;
    }

    private Bitmap createBitmap() {
        return Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
    }

    public float getBitmapWidth() {
        return bitmapWidth;
    }

    public float getBitmapHeight() {
        return bitmapHeight;
    }
}
