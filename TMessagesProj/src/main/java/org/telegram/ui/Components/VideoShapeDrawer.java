package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.animation.AnimationUtils;
import android.view.animation.OvershootInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.VideoShape;
import org.telegram.messenger.VideoShapeGeometry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Animated round video mask: maps the current (possibly morphing or live) {@link VideoShape} outline to bounds,
 * clips with it and draws progress along a ring that keeps a constant distance from the mask edge
 * (offset along normals with self-intersections cut, not a scaled copy). Paths are cached and reused,
 * nothing is allocated per frame. The ring is cached relative to the bounds' top-left, so moving bounds reuse it.
 */
public class VideoShapeDrawer {

    private static final int N = VideoShape.POINTS;
    private static final int R = VideoShape.RING_POINTS;
    private static final long MORPH_DURATION = 420;
    private static final float MORPH_OVERSHOOT_TENSION = 1.2f;
    /** Side of the cached outline path, which is scaled to the bounds, so resizing doesn't rebuild it. */
    private static final float UNIT = 1024;
    /** Ring rebuilds closer together than this (ms) are an animation, and use the cheap offset. */
    private static final long RING_ANIMATION_WINDOW = 100;

    private final Runnable invalidate;

    private int shape = VideoShape.CIRCLE;
    private float[] points = VideoShape.unitPoints(VideoShape.CIRCLE);
    private float[] morphFrom, morphTo, morphBuffer, liveBuffer, interactiveLive;
    private boolean interactive;
    private float morphProgress;
    private ValueAnimator animator;
    private int version;
    private long liveFrameTime = -1;
    private boolean live, liveActive;

    private final Path unitPath = new Path();
    private int unitPathVersion = -1;
    private final Path maskPath = new Path();
    private final RectF maskBounds = new RectF();
    private int maskVersion = -1;
    private final Matrix matrix = new Matrix();

    private final Path ringPath = new Path();
    private final PathMeasure ringMeasure = new PathMeasure();
    private float ringWidth = -1, ringHeight = -1, ringOffset, ringLength;
    private final float[] mapped = new float[N * 2];
    private final float[] ringPoints = new float[R * 2];
    private final float[] ringLengths = new float[R + 1];
    private int ringVersion = -1;
    private boolean ringExact;
    private long ringTime;

    private final Path progressPath = new Path();
    private float progressPathValue = -1;
    private final RectF tmpBounds = new RectF();

    private static Paint shadowPaint, shadowBitmapPaint;
    private static final int SHADOW_CACHE_SIZE = 8;
    private static final float SHADOW_BITMAP_SCALE = 0.5f;
    private static final Map<Long, Bitmap> shadowCache = new LinkedHashMap<Long, Bitmap>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Bitmap> eldest) {
            return size() > SHADOW_CACHE_SIZE;
        }
    };

    public VideoShapeDrawer(Runnable invalidate) {
        this.invalidate = invalidate;
    }

    public int getShape() {
        return shape;
    }

    public boolean isAnimating() {
        return animator != null || interactive;
    }

    /** True only when settled on a circle, so callers can keep their legacy circle code paths. */
    public boolean isCircle() {
        return shape == VideoShape.CIRCLE && animator == null && !interactive;
    }

    /**
     * Whether a live shape (blob) moves. Off by default; turning it on or off morphs between the rest pose and the
     * moving outline. Power saving keeps it at rest.
     */
    public void setLive(boolean live) {
        if (this.live == live) {
            return;
        }
        this.live = live;
        if (VideoShape.isLive(shape) && invalidate != null) {
            invalidate.run();
        }
    }

    /** Whether small copies (replies, chat list) may move: not on low-end devices, where a list of them redraws every frame. */
    public static boolean isLiveThumbnailAllowed() {
        return SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_LOW;
    }

    private boolean liveAllowed() {
        return live && !LiteMode.isPowerSaverApplied();
    }

    /** Whether the outline changes every frame by itself, so nothing derived from it is worth making exact. */
    private boolean isOutlineMoving() {
        return animator != null || liveActive;
    }

    /**
     * Shows the outline t of the way from one shape to another without animating, so it can follow a finger.
     * {@link #getShape} reports the nearer of the two. End with {@link #finishInteractive} or {@link #setShape}.
     */
    public void setInteractiveMorph(int from, int to, float t) {
        if (!VideoShape.isValid(from)) {
            from = VideoShape.CIRCLE;
        }
        if (!VideoShape.isValid(to)) {
            to = VideoShape.CIRCLE;
        }
        cancelAnimator();
        if (morphBuffer == null) {
            morphFrom = new float[N * 2];
            morphBuffer = new float[N * 2];
        }
        interactive = true;
        liveActive = false;
        shape = t < 0.5f ? from : to;
        VideoShapeGeometry.morph(pointsOf(from), pointsOf(to), t, morphBuffer);
        points = morphBuffer;
        version++;
        if (invalidate != null) {
            invalidate.run();
        }
    }

    /** Settles an interactive morph exactly on a shape. */
    public void finishInteractive(int newShape) {
        if (!VideoShape.isValid(newShape)) {
            newShape = VideoShape.CIRCLE;
        }
        interactive = false;
        shape = newShape;
        points = settledPoints();
        version++;
        if (invalidate != null) {
            invalidate.run();
        }
    }

    private float[] pointsOf(int s) {
        if (!VideoShape.isLive(s) || !liveAllowed()) {
            return VideoShape.unitPoints(s);
        }
        if (interactiveLive == null) {
            interactiveLive = new float[N * 2];
        }
        VideoShape.livePoints(s, AnimationUtils.currentAnimationTimeMillis() / 1000.0, interactiveLive);
        return interactiveLive;
    }

    /** Outline the current shape settles on: moving for a live shape, the rest pose otherwise. */
    private float[] settledPoints() {
        liveActive = VideoShape.isLive(shape) && liveAllowed();
        return liveActive ? updateLiveBuffer(true) : VideoShape.unitPoints(shape);
    }

    private void cancelAnimator() {
        if (animator != null) {
            animator.removeAllListeners();
            animator.cancel();
            animator = null;
        }
    }

    public void setShape(int newShape, boolean animated) {
        if (!VideoShape.isValid(newShape)) {
            newShape = VideoShape.CIRCLE;
        }
        if (newShape == shape && !interactive) {
            return;
        }
        shape = newShape;
        interactive = false;
        cancelAnimator();
        if (!animated) {
            points = settledPoints();
            version++;
            if (invalidate != null) {
                invalidate.run();
            }
            return;
        }
        startMorph(settledPoints());
    }

    private void startMorph(float[] target) {
        cancelAnimator();
        if (morphBuffer == null) {
            morphFrom = new float[N * 2];
            morphBuffer = new float[N * 2];
        }
        System.arraycopy(points, 0, morphFrom, 0, N * 2);
        morphTo = target;
        morphProgress = 0;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(a -> {
            morphProgress = (float) a.getAnimatedValue();
            applyMorph();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation != animator) {
                    return;
                }
                animator = null;
                points = morphTo;
                version++;
                if (invalidate != null) {
                    invalidate.run();
                }
            }
        });
        animator.setDuration(MORPH_DURATION);
        animator.setInterpolator(LiteMode.isPowerSaverApplied() ? CubicBezierInterpolator.DEFAULT : new OvershootInterpolator(MORPH_OVERSHOOT_TENSION));
        animator.start();
    }

    private void applyMorph() {
        VideoShapeGeometry.morph(morphFrom, morphTo, morphProgress, morphBuffer);
        points = morphBuffer;
        version++;
        if (invalidate != null) {
            invalidate.run();
        }
    }

    private float[] updateLiveBuffer(boolean force) {
        final long time = AnimationUtils.currentAnimationTimeMillis();
        if (liveBuffer == null) {
            liveBuffer = new float[N * 2];
            force = true;
        }
        if (force || time != liveFrameTime) {
            liveFrameTime = time;
            VideoShape.livePoints(shape, time / 1000.0, liveBuffer);
        }
        return liveBuffer;
    }

    /** Live shapes (blob) change every frame while they are drawn and allowed to move; keeps the owner redrawing. */
    private void updateLive() {
        if (!VideoShape.isLive(shape) || interactive) {
            return;
        }
        final boolean active = liveAllowed();
        if (active != liveActive) {
            liveActive = active;
            startMorph(active ? updateLiveBuffer(true) : VideoShape.unitPoints(shape));
            return;
        }
        if (!liveActive) {
            return;
        }
        final long before = liveFrameTime;
        updateLiveBuffer(false);
        if (before == liveFrameTime) {
            return;
        }
        if (animator == null) {
            points = liveBuffer;
            version++;
        } else {
            morphTo = liveBuffer;
            applyMorph();
        }
        if (invalidate != null) {
            invalidate.run();
        }
    }

    /** Current outline mapped to bounds. The returned path is reused, don't modify it. */
    public Path getPath(RectF bounds) {
        updateLive();
        if (maskVersion != version || !maskBounds.equals(bounds)) {
            if (unitPathVersion != version) {
                unitPathVersion = version;
                unitPath.rewind();
                unitPath.moveTo(points[0] * UNIT, points[1] * UNIT);
                for (int i = 1; i < N; i++) {
                    unitPath.lineTo(points[i * 2] * UNIT, points[i * 2 + 1] * UNIT);
                }
                unitPath.close();
            }
            maskVersion = version;
            maskBounds.set(bounds);
            matrix.setScale(bounds.width() / UNIT, bounds.height() / UNIT);
            matrix.postTranslate(bounds.left, bounds.top);
            unitPath.transform(matrix, maskPath);
        }
        return maskPath;
    }

    /** Whether a point is inside the current outline mapped to the bounds, so taps in empty corners go through. */
    public boolean contains(float left, float top, float right, float bottom, float x, float y) {
        final float w = right - left, h = bottom - top;
        if (w <= 0 || h <= 0) {
            return false;
        }
        return VideoShapeGeometry.contains(points, (x - left) / w, (y - top) / h);
    }

    public Path getPath(float left, float top, float right, float bottom) {
        tmpBounds.set(left, top, right, bottom);
        return getPath(tmpBounds);
    }

    public void clip(Canvas canvas, RectF bounds) {
        canvas.clipPath(getPath(bounds));
    }

    public void clip(Canvas canvas, float left, float top, float right, float bottom) {
        canvas.clipPath(getPath(left, top, right, bottom));
    }

    /**
     * Soft shadow under the mask, same look as {@link org.telegram.ui.ActionBar.RoundVideoShadow}. A settled shape
     * draws a cached bitmap; only an outline that changes every frame (morph, swipe, moving blob) blurs a path.
     */
    public void drawShadow(Canvas canvas, RectF bounds, int alpha) {
        updateLive();
        final float w = bounds.width(), h = bounds.height();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (isOutlineMoving() || interactive) {
            if (shadowPaint == null) {
                shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                shadowPaint.setShadowLayer(AndroidUtilities.dp(4), 0, 0, 0x5f000000);
            }
            shadowPaint.setColor(0xff000000);
            shadowPaint.setAlpha(alpha);
            final float scale = (w - AndroidUtilities.dp(2)) / w;
            canvas.save();
            canvas.scale(scale, scale, bounds.centerX(), bounds.centerY());
            canvas.drawPath(getPath(bounds), shadowPaint);
            canvas.restore();
            return;
        }
        final int step = AndroidUtilities.dp(16);
        final int columns = Math.max(1, Math.round(w / step)), rows = Math.max(1, Math.round(h / step));
        final Bitmap bitmap = getShadowBitmap(shape, columns * step, rows * step);
        if (bitmap == null) {
            return;
        }
        if (shadowBitmapPaint == null) {
            shadowBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        }
        shadowBitmapPaint.setColor(0xff000000);
        shadowBitmapPaint.setAlpha(alpha);
        final float pad = shadowPadding();
        final float padX = pad * w / (columns * step), padY = pad * h / (rows * step);
        tmpBounds.set(bounds.left - padX, bounds.top - padY, bounds.right + padX, bounds.bottom + padY);
        canvas.drawBitmap(bitmap, null, tmpBounds, shadowBitmapPaint);
    }

    private static float shadowPadding() {
        return AndroidUtilities.dp(10);
    }

    /**
     * Shadow of a settled shape at a reference size, around (not under) the outline, as an alpha mask at half
     * resolution. Shared by all drawers; nearby sizes reuse it scaled.
     */
    private static Bitmap getShadowBitmap(int shape, int width, int height) {
        final long key = (long) shape << 40 | (long) width << 20 | height;
        Bitmap bitmap = shadowCache.get(key);
        if (bitmap != null) {
            return bitmap;
        }
        final float s = SHADOW_BITMAP_SCALE, pad = shadowPadding(), inset = AndroidUtilities.dp(1);
        try {
            bitmap = Bitmap.createBitmap((int) Math.ceil((width + pad * 2) * s), (int) Math.ceil((height + pad * 2) * s), Bitmap.Config.ALPHA_8);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
        final float[] p = VideoShape.unitPoints(shape);
        final float left = (pad + inset) * s, top = (pad + inset) * s, w = (width - inset * 2) * s, h = (height - inset * 2) * s;
        final Path path = new Path();
        path.moveTo(left + p[0] * w, top + p[1] * h);
        for (int i = 1; i < N; i++) {
            path.lineTo(left + p[i * 2] * w, top + p[i * 2 + 1] * h);
        }
        path.close();
        final Canvas canvas = new Canvas(bitmap);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xff000000);
        paint.setShadowLayer(AndroidUtilities.dp(4) * s, 0, 0, 0x5f000000);
        canvas.drawPath(path, paint);
        paint.clearShadowLayer();
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawPath(path, paint);
        shadowCache.put(key, bitmap);
        return bitmap;
    }

    /**
     * Ring around (offset > 0) or inside (offset < 0) the mask of the given bounds, at a constant distance
     * from its edge. Its kinks are already rounded, so the paint's default join is fine; paints this feature owns
     * use a round one.
     */
    public void drawTrack(Canvas canvas, RectF bounds, float offset, Paint paint) {
        updateRing(bounds.width(), bounds.height(), offset);
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.drawPath(ringPath, paint);
        canvas.restore();
    }

    /** Draws the ring from top-centre clockwise up to progress (0..1) of its length. */
    public void drawProgress(Canvas canvas, RectF bounds, float offset, float progress, Paint paint) {
        if (progress <= 0) {
            return;
        }
        updateRing(bounds.width(), bounds.height(), offset);
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        if (progress >= 1f) {
            canvas.drawPath(ringPath, paint);
        } else {
            if (progressPathValue != progress) {
                progressPathValue = progress;
                progressPath.rewind();
                ringMeasure.getSegment(0, ringLength * progress, progressPath, true);
            }
            canvas.drawPath(progressPath, paint);
        }
        canvas.restore();
    }

    public void getPointAt(RectF bounds, float offset, float progress, PointF out) {
        updateRing(bounds.width(), bounds.height(), offset);
        progress = progress - (float) Math.floor(progress);
        final float target = ringLengths[R] * progress;
        int i = 0;
        while (i < R - 1 && ringLengths[i + 1] < target) {
            i++;
        }
        int j = (i + 1) % R;
        float len = ringLengths[i + 1] - ringLengths[i];
        float t = len <= 0 ? 0 : (target - ringLengths[i]) / len;
        out.x = bounds.left + ringPoints[i * 2] + (ringPoints[j * 2] - ringPoints[i * 2]) * t;
        out.y = bounds.top + ringPoints[i * 2 + 1] + (ringPoints[j * 2 + 1] - ringPoints[i * 2 + 1]) * t;
    }

    /** Progress (0..1) of the ring point closest to (x, y). */
    public float progressForTouch(RectF bounds, float offset, float x, float y) {
        updateRing(bounds.width(), bounds.height(), offset);
        x -= bounds.left;
        y -= bounds.top;
        float best = Float.MAX_VALUE, bestLength = 0;
        for (int i = 0; i < R; i++) {
            int j = (i + 1) % R;
            float ax = ringPoints[i * 2], ay = ringPoints[i * 2 + 1];
            float dx = ringPoints[j * 2] - ax, dy = ringPoints[j * 2 + 1] - ay;
            float len2 = dx * dx + dy * dy;
            float t = len2 <= 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / len2));
            float px = ax + dx * t - x, py = ay + dy * t - y;
            float d = px * px + py * py;
            if (d < best) {
                best = d;
                bestLength = ringLengths[i] + (ringLengths[i + 1] - ringLengths[i]) * t;
            }
        }
        float total = ringLengths[R];
        return total <= 0 ? 0 : Math.min(1f, bestLength / total);
    }

    /**
     * Rebuilds the ring when the outline, size or offset changed. While they change frame after frame (morph,
     * swipe, moving blob, a resize or offset animation) it uses the cheap plain offset, and once they settle the
     * next draw builds the exact one.
     */
    private void updateRing(float w, float h, float offset) {
        updateLive();
        final boolean changed = ringVersion != version || ringWidth != w || ringHeight != h || ringOffset != offset;
        if (!changed && (ringExact || isOutlineMoving())) {
            return;
        }
        final long now = AnimationUtils.currentAnimationTimeMillis();
        final boolean exact = !changed || !isOutlineMoving() && (now == ringTime || now - ringTime > RING_ANIMATION_WINDOW);
        ringVersion = version;
        ringWidth = w;
        ringHeight = h;
        ringOffset = offset;
        ringExact = exact;
        ringTime = now;
        for (int i = 0; i < N; i++) {
            mapped[i * 2] = points[i * 2] * w;
            mapped[i * 2 + 1] = points[i * 2 + 1] * h;
        }
        if (exact) {
            VideoShape.offsetOutline(mapped, offset, ringPoints);
        } else {
            VideoShape.offsetOutlineFast(mapped, offset, ringPoints);
        }
        ringLengths[0] = 0;
        for (int i = 0; i < R; i++) {
            int j = (i + 1) % R;
            float dx = ringPoints[j * 2] - ringPoints[i * 2], dy = ringPoints[j * 2 + 1] - ringPoints[i * 2 + 1];
            ringLengths[i + 1] = ringLengths[i] + (float) Math.sqrt(dx * dx + dy * dy);
        }
        ringPath.rewind();
        ringPath.moveTo(ringPoints[0], ringPoints[1]);
        for (int i = 1; i < R; i++) {
            ringPath.lineTo(ringPoints[i * 2], ringPoints[i * 2 + 1]);
        }
        ringPath.close();
        ringMeasure.setPath(ringPath, true);
        ringLength = ringMeasure.getLength();
        progressPathValue = -1;
        if (!exact && !isOutlineMoving() && invalidate != null) {
            invalidate.run();
        }
    }
}
