package org.telegram.messenger;

/**
 * Runtime geometry for {@link VideoShape} outlines: rings, morphs, hit tests and the live blob (no Android
 * dependencies). The static outlines come precomputed from {@link VideoShapeOutlines}.
 */
public final class VideoShapeGeometry {

    private VideoShapeGeometry() {
    }

    private static float[] blobTable;

    /**
     * Living blob: polar outline sampled at fixed angles from top-centre clockwise, so it can be recomputed
     * every frame without resampling. Stays within the unit box.
     * r(a) = 0.5 * (0.86 + 0.065 sin(3a + 1.1 + 0.9t) + 0.04 cos(5a + 2.3 - 1.3t) + 0.03 sin(2a + 0.6t)), with the
     * harmonics split by angle addition so a frame costs six sin/cos in total, not per point.
     */
    static void blob(float[] out, int points, double timeSeconds) {
        final float[] table = blobTable(points);
        final double t = timeSeconds, p3 = 1.1 + t * 0.9, p5 = 2.3 - t * 1.3, p2 = t * 0.6;
        final float s3 = (float) (0.065 * Math.cos(p3)), c3 = (float) (0.065 * Math.sin(p3));
        final float c5 = (float) (0.04 * Math.cos(p5)), s5 = (float) (-0.04 * Math.sin(p5));
        final float s2 = (float) (0.03 * Math.cos(p2)), c2 = (float) (0.03 * Math.sin(p2));
        for (int i = 0, k = 0; i < points; i++, k += 8) {
            float r = 0.5f * (0.86f
                    + table[k + 4] * s3 + table[k + 5] * c3
                    + table[k + 7] * c5 + table[k + 6] * s5
                    + table[k + 2] * s2 + table[k + 3] * c2);
            out[i * 2] = 0.5f + table[k] * r;
            out[i * 2 + 1] = 0.5f - table[k + 1] * r;
        }
    }

    /** sin, cos of a, 2a, 3a, 5a for every blob angle. */
    private static float[] blobTable(int points) {
        float[] table = blobTable;
        if (table != null && table.length == points * 8) {
            return table;
        }
        table = new float[points * 8];
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            table[i * 8] = (float) Math.sin(a);
            table[i * 8 + 1] = (float) Math.cos(a);
            table[i * 8 + 2] = (float) Math.sin(2 * a);
            table[i * 8 + 3] = (float) Math.cos(2 * a);
            table[i * 8 + 4] = (float) Math.sin(3 * a);
            table[i * 8 + 5] = (float) Math.cos(3 * a);
            table[i * 8 + 6] = (float) Math.sin(5 * a);
            table[i * 8 + 7] = (float) Math.cos(5 * a);
        }
        return blobTable = table;
    }

    /** Radius of the arcs that replace the kinks of a ring, relative to its offset (see {@link #offsetOutline}). */
    static final float CLOSING = 0.6f;
    private static final double JOIN_STEP = Math.toRadians(6);

    private static float[] offsetBuffer = new float[1024], ringBuffer = new float[512];
    private static float[] lengths = new float[1024];
    private static int[] links = new int[1024];
    private static int count;

    /**
     * Ring at a constant distance d from a closed clockwise outline (pixels): d > 0 outside, d < 0 inside.
     * A plain offset folds wherever a corner is tighter than d, and cutting the fold leaves a kink. So this is a
     * morphological closing (an opening for d < 0): offset by d + k with round joins and folds cut, then back by
     * k, which turns every kink into an arc of radius k and keeps the ring exactly d away everywhere else.
     * Output: outCount points evenly spaced by arc length, starting at top-centre (x of the first input point),
     * clockwise. Use more points than the outline has, so the arcs at tight corners stay round.
     */
    static synchronized void offsetOutline(float[] p, int n, float d, float[] out, int outCount) {
        final float centerX = p[0];
        if (d == 0) {
            resampleClosed(p, n, centerX, outCount, out);
            return;
        }
        final float k = d * CLOSING;
        if (ringBuffer.length < n * 2) {
            ringBuffer = new float[n * 2];
        }
        offsetRound(p, n, d + k);
        resampleClosed(offsetBuffer, cutLoops(offsetBuffer, count), centerX, n, ringBuffer);
        offsetRound(ringBuffer, n, -k);
        resampleClosed(offsetBuffer, cutLoops(offsetBuffer, count), centerX, outCount, out);
    }

    /**
     * Plain offset of every vertex along its normal, without the closing and loop cutting of {@link #offsetOutline}.
     * Exact for smooth outlines (the blob) and close enough for a frame of an animation; tight corners may fold.
     */
    static synchronized void offsetOutlineFast(float[] p, int n, float d, float[] out, int outCount) {
        if (ringBuffer.length < n * 2) {
            ringBuffer = new float[n * 2];
        }
        for (int i = 0; i < n; i++) {
            int a = (i - 1 + n) % n, b = (i + 1) % n;
            float tx = p[b * 2] - p[a * 2], ty = p[b * 2 + 1] - p[a * 2 + 1];
            float l = (float) Math.sqrt(tx * tx + ty * ty);
            float k = l <= 0 ? 0 : d / l;
            ringBuffer[i * 2] = p[i * 2] + ty * k;
            ringBuffer[i * 2 + 1] = p[i * 2 + 1] - tx * k;
        }
        resampleClosed(ringBuffer, n, p[0], outCount, out);
    }

    /** Offsets every vertex along its normal; corners that open up get a round join, folds are left for {@link #cutLoops}. */
    private static void offsetRound(float[] p, int n, float d) {
        count = 0;
        for (int i = 0; i < n; i++) {
            int a = (i - 1 + n) % n, b = (i + 1) % n;
            float vx = p[i * 2], vy = p[i * 2 + 1];
            float e1x = vx - p[a * 2], e1y = vy - p[a * 2 + 1];
            float e2x = p[b * 2] - vx, e2y = p[b * 2 + 1] - vy;
            float l1 = (float) Math.sqrt(e1x * e1x + e1y * e1y), l2 = (float) Math.sqrt(e2x * e2x + e2y * e2y);
            if (l1 <= 0 && l2 <= 0) {
                continue;
            }
            if (l1 <= 0) {
                e1x = e2x;
                e1y = e2y;
                l1 = l2;
            } else if (l2 <= 0) {
                e2x = e1x;
                e2y = e1y;
                l2 = l1;
            }
            e1x /= l1;
            e1y /= l1;
            e2x /= l2;
            e2y /= l2;
            float n1x = e1y, n1y = -e1x, n2x = e2y, n2y = -e2x;
            double phi = Math.atan2(e1x * e2y - e1y * e2x, e1x * e2x + e1y * e2y);
            if (Math.abs(phi) < JOIN_STEP) {
                float mx = n1x + n2x, my = n1y + n2y;
                float ml = (float) Math.sqrt(mx * mx + my * my);
                float scale = d / (float) Math.cos(phi / 2) / ml;
                append(vx + mx * scale, vy + my * scale);
            } else if (phi * d > 0) {
                int steps = (int) Math.ceil(Math.abs(phi) / JOIN_STEP);
                for (int s = 0; s <= steps; s++) {
                    double t = phi * s / steps, c = Math.cos(t), sn = Math.sin(t);
                    append(vx + (float) (n1x * c - n1y * sn) * d, vy + (float) (n1x * sn + n1y * c) * d);
                }
            } else {
                append(vx + n1x * d, vy + n1y * d);
                append(vx + n2x * d, vy + n2y * d);
            }
        }
    }

    private static void append(float x, float y) {
        if (offsetBuffer.length < (count + 1) * 2) {
            float[] grown = new float[offsetBuffer.length * 2];
            System.arraycopy(offsetBuffer, 0, grown, 0, count * 2);
            offsetBuffer = grown;
        }
        offsetBuffer[count * 2] = x;
        offsetBuffer[count * 2 + 1] = y;
        count++;
    }

    /** Removes the small loops of a closed polyline by joining the crossing segments at their intersection. Returns the new count. */
    private static int cutLoops(float[] p, int m) {
        if (m < 4) {
            return m;
        }
        if (links.length < m) {
            links = new int[m * 2];
        }
        final int[] next = links;
        for (int i = 0; i < m; i++) {
            next[i] = (i + 1) % m;
        }
        final int window = Math.max(8, m / 4);
        int alive = m, i = 0;
        for (int advanced = 0; advanced < m + window && alive > 3; ) {
            int i1 = next[i];
            float ax = p[i * 2], ay = p[i * 2 + 1], rx = p[i1 * 2] - ax, ry = p[i1 * 2 + 1] - ay;
            float minX = Math.min(ax, ax + rx), maxX = Math.max(ax, ax + rx), minY = Math.min(ay, ay + ry), maxY = Math.max(ay, ay + ry);
            int found = -1;
            float fx = 0, fy = 0;
            int j = next[i1];
            for (int w = 0; w < window && j != i && next[j] != i; w++) {
                int j1 = next[j];
                float cx = p[j * 2], cy = p[j * 2 + 1], ex = p[j1 * 2], ey = p[j1 * 2 + 1];
                if (Math.max(cx, ex) < minX || Math.min(cx, ex) > maxX || Math.max(cy, ey) < minY || Math.min(cy, ey) > maxY) {
                    j = j1;
                    continue;
                }
                float sx = ex - cx, sy = ey - cy;
                float den = rx * sy - ry * sx;
                if (Math.abs(den) > 1e-9f) {
                    float t = ((cx - ax) * sy - (cy - ay) * sx) / den;
                    float u = ((cx - ax) * ry - (cy - ay) * rx) / den;
                    if (t > 0 && t < 1 && u > 0 && u < 1) {
                        found = j;
                        fx = ax + rx * t;
                        fy = ay + ry * t;
                        break;
                    }
                }
                j = j1;
            }
            if (found >= 0) {
                for (int r = i1; r != found; r = next[r]) {
                    alive--;
                }
                p[found * 2] = fx;
                p[found * 2 + 1] = fy;
                next[i] = found;
            } else {
                i = i1;
                advanced++;
            }
        }
        // Compact through a scratch buffer, in ring order.
        if (lengths.length < alive * 2) {
            lengths = new float[alive * 2];
        }
        int start = i, k = 0;
        do {
            lengths[k * 2] = p[i * 2];
            lengths[k * 2 + 1] = p[i * 2 + 1];
            k++;
            i = next[i];
        } while (i != start && k < alive);
        System.arraycopy(lengths, 0, p, 0, k * 2);
        return k;
    }

    /**
     * Resamples a closed polyline to evenly spaced points, starting where it crosses x = centerX left to right
     * at its top, so progress along any ring starts at top-centre.
     */
    private static void resampleClosed(float[] p, int m, float centerX, int points, float[] out) {
        int start = 0;
        float startX = p[0], startY = Float.MAX_VALUE;
        for (int i = 0; i < m; i++) {
            int j = (i + 1) % m;
            float x0 = p[i * 2], x1 = p[j * 2];
            if (x0 <= centerX && x1 > centerX) {
                float t = (centerX - x0) / (x1 - x0);
                float y = p[i * 2 + 1] + (p[j * 2 + 1] - p[i * 2 + 1]) * t;
                if (y < startY) {
                    startY = y;
                    startX = centerX;
                    start = i;
                }
            }
        }
        if (startY == Float.MAX_VALUE) {
            startY = p[1];
        }
        if (lengths.length < m + 2) {
            lengths = new float[(m + 2) * 2];
        }
        // Sequence: start point, p[start + 1], ..., p[start + m] (== p[start]), start point again.
        lengths[0] = 0;
        float px = startX, py = startY;
        for (int t = 1; t <= m + 1; t++) {
            float x, y;
            if (t <= m) {
                int idx = (start + t) % m;
                x = p[idx * 2];
                y = p[idx * 2 + 1];
            } else {
                x = startX;
                y = startY;
            }
            final float dx = x - px, dy = y - py;
            lengths[t] = lengths[t - 1] + (float) Math.sqrt(dx * dx + dy * dy);
            px = x;
            py = y;
        }
        final float total = lengths[m + 1];
        int seg = 0;
        for (int i = 0; i < points; i++) {
            float target = total * i / points;
            while (seg < m && lengths[seg + 1] < target) {
                seg++;
            }
            float ax, ay, bx, by;
            if (seg == 0) {
                ax = startX;
                ay = startY;
            } else {
                int idx = (start + seg) % m;
                ax = p[idx * 2];
                ay = p[idx * 2 + 1];
            }
            if (seg + 1 <= m) {
                int idx = (start + seg + 1) % m;
                bx = p[idx * 2];
                by = p[idx * 2 + 1];
            } else {
                bx = startX;
                by = startY;
            }
            float len = lengths[seg + 1] - lengths[seg];
            float t = len <= 0 ? 0 : (target - lengths[seg]) / len;
            out[i * 2] = ax + (bx - ax) * t;
            out[i * 2 + 1] = ay + (by - ay) * t;
        }
    }

    /**
     * Point-by-point interpolation between two outlines (t may overshoot past 0..1). Overshoot extrapolates
     * past the target, so the result is scaled back about the centre to stay within the unit box (otherwise the
     * clip would cut it flat).
     */
    public static void morph(float[] from, float[] to, float t, float[] out) {
        final int n = Math.min(from.length, to.length);
        float minX = 0, minY = 0, maxX = 1, maxY = 1;
        for (int i = 0; i < n; i += 2) {
            float x = out[i] = from[i] + (to[i] - from[i]) * t;
            float y = out[i + 1] = from[i + 1] + (to[i + 1] - from[i + 1]) * t;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        if (minX < 0 || minY < 0 || maxX > 1 || maxY > 1) {
            float scale = Math.min(Math.min(0.5f / (0.5f - minX), 0.5f / (maxX - 0.5f)), Math.min(0.5f / (0.5f - minY), 0.5f / (maxY - 0.5f)));
            for (int i = 0; i < n; i++) {
                out[i] = 0.5f + (out[i] - 0.5f) * scale;
            }
        }
    }

    /** Whether a unit-box point lies inside a closed outline (even-odd ray casting). */
    public static boolean contains(float[] points, float x, float y) {
        final int n = points.length / 2;
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = points[i * 2], yi = points[i * 2 + 1], xj = points[j * 2], yj = points[j * 2 + 1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** Zoom that keeps the whole outline within the inscribed circle of the video (radius 0.5). */
    static float zoomToFitCircle(float[] points, float margin) {
        double max = 0;
        for (int i = 0; i < points.length / 2; i++) {
            max = Math.max(max, Math.hypot(points[i * 2] - 0.5, points[i * 2 + 1] - 0.5));
        }
        return (float) Math.max(1, max / 0.5 * (1 + margin));
    }
}
