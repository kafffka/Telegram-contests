package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.tgnet.TLRPC;
import org.telegram.utils.settings.SharedSettings;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Render-only shapes for round video messages. Every outline is a closed polyline of {@link #POINTS}
 * samples in a unit box, evenly spaced by arc length, starting at top-centre and running clockwise,
 * so shapes can be morphed point by point and progress can be drawn along any of them.
 */
public final class VideoShape {

    public static final int CIRCLE = 0, HEART = 1, TREE = 2, SQUIRCLE = 3, STAR = 4, HEXAGON = 5, FLOWER = 6,
            CLOUD = 7, BLOB = 8, CAT = 9, GHOST = 10;
    public static final int COUNT = 11;
    public static final int POINTS = 256;

    /**
     * Order used by the picker: basic, organic, playful. TREE is retired: not offered any more, but its id and
     * outline stay so messages already sent with it still render.
     */
    public static final int[] ORDER = {
            CIRCLE, SQUIRCLE, HEART, STAR, HEXAGON, FLOWER, CLOUD, BLOB, CAT, GHOST
    };

    /** Points of a progress ring, more than the outline so tight corners stay round (see {@link #offsetOutline}). */
    public static final int RING_POINTS = POINTS * 3;

    /**
     * Ring around (d > 0) or inside (d < 0) a pixel outline at a constant distance from it, with kinks rounded.
     * Fills all of out, evenly spaced, from top-centre clockwise.
     */
    public static void offsetOutline(float[] points, float d, float[] out) {
        VideoShapeGeometry.offsetOutline(points, points.length / 2, d, out, out.length / 2);
    }

    /** Like {@link #offsetOutline}, but a plain offset along normals: cheap, for outlines that change every frame. */
    public static void offsetOutlineFast(float[] points, float d, float[] out) {
        VideoShapeGeometry.offsetOutlineFast(points, points.length / 2, d, out, out.length / 2);
    }

    /** Shapes whose outline changes over time (see {@link #livePoints}). */
    public static boolean isLive(int shape) {
        return shape == BLOB;
    }

    /** Writes the outline of a live shape at the given time (seconds, shared clock so all blobs move alike). */
    public static void livePoints(int shape, double timeSeconds, float[] out) {
        if (shape == BLOB) {
            VideoShapeGeometry.blob(out, POINTS, timeSeconds);
        }
    }

    /**
     * Prototype hack (watermark): official clients composite a blurred ring plus a "TELEGRAM" text/logo
     * watermark into the corners of every round video, outside the inscribed circle (client side, see
     * InstantCameraVideoEncoderOverlayHelper and RoundVideoOverlayRenderer). Non-circle masks reveal it.
     * - Our own recordings: both encoders skip the composition unless
     *   {@link SharedSettings#roundVideoShapeComposition} is on, so the file is the plain square frame.
     * - Incoming videos already have it baked in, so shaped incoming videos are zoomed in until the whole
     *   outline fits the clean inscribed circle ({@link #contentZoom}). This crops a bit of the picture.
     */
    public static final boolean ZOOM_INCOMING_TO_CLEAN_CIRCLE = true;
    private static final float ZOOM_MARGIN = 0.02f;

    private static final String PREF_KEY = "videoMessageShape";

    private static final float[][] cache = new float[COUNT][];
    /**
     * Shapes of own round videos by account_dialog_message, until the server can carry the shape (see the v2 plan,
     * E1). Persisted so they survive restarts; lost on reinstall or on another device.
     */
    private static final HashMap<String, Integer> registry = new HashMap<>();
    private static final String REGISTRY_PREFS = "videoshapes";
    /**
     * Entries kept at most; past it the one with the lowest sent message id goes, a rough stand-in for the oldest.
     * Messages still sending have negative local ids and are kept.
     */
    private static final int REGISTRY_MAX = 1000;
    private static boolean registryLoaded;

    private VideoShape() {
    }

    public static boolean isValid(int shape) {
        return shape >= 0 && shape < COUNT;
    }

    /** Returns cached unit outline: x0, y0, x1, y1, ... ({@link #POINTS} pairs). Do not modify. */
    public static float[] unitPoints(int shape) {
        if (!isValid(shape)) {
            shape = CIRCLE;
        }
        float[] points = cache[shape];
        if (points == null) {
            synchronized (cache) {
                points = cache[shape];
                if (points == null) {
                    points = shape < VideoShapeOutlines.DATA.length ? decodeOutline(VideoShapeOutlines.DATA[shape]) : null;
                    if (points == null) {
                        points = circlePoints();
                    }
                    cache[shape] = points;
                }
            }
        }
        return points;
    }

    /** Plain circle, only if a table entry is broken (VideoShapeOutlinesTest keeps the table in sync). */
    private static float[] circlePoints() {
        final float[] out = new float[POINTS * 2];
        for (int i = 0; i < POINTS; i++) {
            final double a = 2 * Math.PI * i / POINTS;
            out[i * 2] = (float) (0.5 + 0.5 * Math.sin(a));
            out[i * 2 + 1] = (float) (0.5 - 0.5 * Math.cos(a));
        }
        return out;
    }

    /** Outline from {@link VideoShapeOutlines}, or null when the entry doesn't fit {@link #POINTS}. */
    static float[] decodeOutline(String data) {
        if (data == null || data.length() != POINTS * 2 * 4) {
            return null;
        }
        final float[] out = new float[POINTS * 2];
        for (int i = 0, c = 0; i < out.length; i++) {
            int v = 0;
            for (int j = 0; j < 4; j++, c++) {
                final int digit = Character.digit(data.charAt(c), 16);
                if (digit < 0) {
                    return null;
                }
                v = v << 4 | digit;
            }
            out[i] = v / 65535f;
        }
        return out;
    }

    private static final float[] zoomCache = new float[COUNT];

    /** Content scale for a message so composited corners stay hidden behind its mask, see the hack above. */
    public static float contentZoom(MessageObject messageObject) {
        if (!ZOOM_INCOMING_TO_CLEAN_CIRCLE || messageObject == null || messageObject.messageOwner == null) {
            return 1f;
        }
        int shape = messageObject.videoShape;
        if (!isValid(shape) || shape == CIRCLE || messageObject.messageOwner.out) {
            return 1f;
        }
        if (zoomCache[shape] == 0) {
            zoomCache[shape] = VideoShapeGeometry.zoomToFitCircle(unitPoints(shape), ZOOM_MARGIN);
        }
        return zoomCache[shape];
    }

    /** Localised shape name, for accessibility. */
    public static String getLabel(int shape) {
        switch (shape) {
            case HEART: return LocaleController.getString(R.string.VideoShapeHeart);
            case TREE: return LocaleController.getString(R.string.VideoShapeTree);
            case SQUIRCLE: return LocaleController.getString(R.string.VideoShapeSquare);
            case STAR: return LocaleController.getString(R.string.VideoShapeStar);
            case HEXAGON: return LocaleController.getString(R.string.VideoShapeHexagon);
            case FLOWER: return LocaleController.getString(R.string.VideoShapeFlower);
            case CLOUD: return LocaleController.getString(R.string.VideoShapeCloud);
            case BLOB: return LocaleController.getString(R.string.VideoShapeBlob);
            case CAT: return LocaleController.getString(R.string.VideoShapeCat);
            case GHOST: return LocaleController.getString(R.string.VideoShapeGhost);
            default: return LocaleController.getString(R.string.VideoShapeCircle);
        }
    }

    /** Whether the account may use shapes other than {@link #CIRCLE}. Every premium check for shapes goes here. */
    public static boolean hasPremium(int account) {
        return UserConfig.getInstance(account).isPremium();
    }

    /**
     * Shape a new recording starts with: the remembered one, or {@link #CIRCLE} when the account can't use it
     * (premium expired, or a free account on a multi-account device). The remembered shape is kept, so a premium
     * account that shares the device doesn't lose its choice.
     */
    public static int effective(int account) {
        return hasPremium(account) ? fromPrefs() : CIRCLE;
    }

    /** Shape actually sent: guards against a premium status change racing the send. */
    public static int forSend(int account, int shape) {
        return isValid(shape) && (shape == CIRCLE || hasPremium(account)) ? shape : CIRCLE;
    }

    /** The remembered shape, or {@link #CIRCLE} if it isn't offered any more (see {@link #ORDER}). */
    public static int fromPrefs() {
        int shape = MessagesController.getGlobalMainSettings().getInt(PREF_KEY, CIRCLE);
        for (int offered : ORDER) {
            if (offered == shape) {
                return shape;
            }
        }
        return CIRCLE;
    }

    public static void saveToPrefs(int shape) {
        MessagesController.getGlobalMainSettings().edit().putInt(PREF_KEY, shape).apply();
    }

    public static int randomFor(long dialogId, int messageId) {
        long h = messageId * 0x9E3779B97F4A7C15L ^ dialogId;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return ORDER[(int) ((h & 0x7fffffffL) % ORDER.length)];
    }

    /**
     * Shape for a freshly created message object: the registry for own videos; incoming ones are circles, or random
     * with {@link SharedSettings#roundVideoRandomIncomingShapes} (debug, to see every shape without a second phone).
     */
    public static int resolve(int account, TLRPC.Message message) {
        if (message == null || !MessageObject.isRoundVideoMessage(message)) {
            return CIRCLE;
        }
        long dialogId = MessageObject.getDialogId(message);
        if (message.out) {
            synchronized (registry) {
                loadRegistry();
                Integer shape = registry.get(key(account, dialogId, message.id));
                return shape == null ? CIRCLE : shape;
            }
        }
        return SharedSettings.roundVideoRandomIncomingShapes.get() ? randomFor(dialogId, message.id) : CIRCLE;
    }

    public static void remember(int account, long dialogId, int messageId, int shape) {
        final String key = key(account, dialogId, messageId);
        String evicted = null;
        synchronized (registry) {
            loadRegistry();
            if (shape == CIRCLE) {
                registry.remove(key);
            } else {
                registry.put(key, shape);
                if (registry.size() > REGISTRY_MAX) {
                    evicted = lowestMessageIdKey();
                    registry.remove(evicted);
                }
            }
        }
        SharedPreferences.Editor editor = registryPrefs().edit();
        if (shape == CIRCLE) {
            editor.remove(key);
        } else {
            editor.putInt(key, shape);
        }
        if (evicted != null) {
            editor.remove(evicted);
        }
        editor.apply();
    }

    /** Call with the registry lock held. */
    private static String lowestMessageIdKey() {
        String lowest = null;
        long lowestId = Long.MAX_VALUE;
        for (String key : registry.keySet()) {
            try {
                final long id = Long.parseLong(key.substring(key.lastIndexOf('_') + 1));
                if (lowest == null || id >= 0 && (lowestId < 0 || id < lowestId)) {
                    lowestId = id;
                    lowest = key;
                }
            } catch (Exception ignore) {
                return key;
            }
        }
        return lowest;
    }

    /** Forgets the shapes of an account's messages, on logout. */
    public static void clearAccount(int account) {
        final String prefix = account + "_";
        final SharedPreferences.Editor editor = registryPrefs().edit();
        synchronized (registry) {
            loadRegistry();
            final Iterator<String> iterator = registry.keySet().iterator();
            while (iterator.hasNext()) {
                final String key = iterator.next();
                if (key.startsWith(prefix)) {
                    iterator.remove();
                    editor.remove(key);
                }
            }
        }
        editor.apply();
    }

    public static void onMessageIdChanged(int account, long dialogId, int oldId, int newId) {
        if (oldId == newId) {
            return;
        }
        synchronized (registry) {
            loadRegistry();
            if (registry.isEmpty()) {
                return;
            }
        }
        final String oldKey = key(account, dialogId, oldId), newKey = key(account, dialogId, newId);
        Integer shape;
        synchronized (registry) {
            shape = registry.remove(oldKey);
            if (shape != null) {
                registry.put(newKey, shape);
            }
        }
        if (shape != null) {
            registryPrefs().edit().remove(oldKey).putInt(newKey, shape).apply();
        }
    }

    private static SharedPreferences registryPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(REGISTRY_PREFS, Context.MODE_PRIVATE);
    }

    /** Call with the registry lock held. */
    private static void loadRegistry() {
        if (registryLoaded) {
            return;
        }
        registryLoaded = true;
        try {
            for (Map.Entry<String, ?> entry : registryPrefs().getAll().entrySet()) {
                if (entry.getValue() instanceof Integer && isValid((Integer) entry.getValue())) {
                    registry.put(entry.getKey(), (Integer) entry.getValue());
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static String key(int account, long dialogId, int messageId) {
        return account + "_" + dialogId + "_" + messageId;
    }
}
