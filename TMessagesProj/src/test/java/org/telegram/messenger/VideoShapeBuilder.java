package org.telegram.messenger;

/**
 * Builds the {@link VideoShape} outlines that {@link VideoShapeOutlinesTest} writes into {@link VideoShapeOutlines}.
 * Shapes are described in a unit box (y goes down) and tuned by eye; tweak the constants freely, then rerun the test.
 * Test-only: the app ships the generated table, not this code.
 */
final class VideoShapeBuilder {

    static final int DENSE = 4096;

    private static final float SQUIRCLE_EXPONENT = 4.5f;

    private static final float[] TREE = {
            0.50f, 0.00f,
            0.78f, 0.33f, 0.66f, 0.33f,
            0.90f, 0.62f, 0.76f, 0.62f,
            1.00f, 0.88f, 0.60f, 0.88f,
            0.60f, 1.00f, 0.40f, 1.00f,
            0.40f, 0.88f, 0.00f, 0.88f,
            0.24f, 0.62f, 0.10f, 0.62f,
            0.34f, 0.33f, 0.22f, 0.33f
    };

    private VideoShapeBuilder() {
    }

    /** Dense closed outline for a shape plus whether it may be stretched to fill the box. */
    static float[] build(int shape, int points) {
        float[] dense;
        boolean stretch = false;
        switch (shape) {
            case VideoShape.HEART:
                dense = heart();
                stretch = true;
                break;
            case VideoShape.BLOB:
                float[] out = new float[points * 2];
                VideoShapeGeometry.blob(out, points, 0);
                return out;
            case VideoShape.TREE:
                dense = roundedPolygon(TREE, 0.07f, 0.035f, 0.07f);
                stretch = true;
                break;
            case VideoShape.SQUIRCLE:
                dense = superellipse(SQUIRCLE_EXPONENT);
                stretch = true;
                break;
            case VideoShape.STAR:
                dense = roundedPolygon(star(5, 0.5f, 0.3f), 0.07f, 0.045f, 0.045f);
                break;
            case VideoShape.HEXAGON:
                dense = roundedPolygon(star(6, 0.5f, 0.5f), 0.09f, 0.09f, 0.09f);
                break;
            case VideoShape.FLOWER:
                dense = polar(t -> 0.5 * (0.7 + 0.3 * petal(Math.cos(3 * t))));
                break;
            case VideoShape.CLOUD:
                dense = smoothUnion(0.5f, 0.6f, 0.05f, new float[][]{
                        circle(0.50f, 0.42f, 0.28f),
                        circle(0.24f, 0.59f, 0.20f),
                        circle(0.76f, 0.59f, 0.20f),
                        polygon(0.05f, 0.24f, 0.60f, 0.76f, 0.60f, 0.76f, 0.74f, 0.24f, 0.74f),
                });
                break;
            case VideoShape.CAT:
                dense = smoothUnion(0.5f, 0.6f, 0.06f, new float[][]{
                        ellipse(0.50f, 0.60f, 0.46f, 0.37f),
                        polygon(0.05f, 0.12f, 0.46f, 0.18f, 0.12f, 0.40f, 0.30f),
                        polygon(0.05f, 0.60f, 0.30f, 0.82f, 0.12f, 0.88f, 0.46f),
                });
                break;
            case VideoShape.GHOST:
                dense = smoothUnion(0.5f, 0.55f, 0.06f, new float[][]{
                        circle(0.50f, 0.30f, 0.28f),
                        polygon(0.04f, 0.26f, 0.32f, 0.74f, 0.32f, 0.84f, 0.84f, 0.16f, 0.84f),
                        capsule(0.34f, 0.50f, 0.07f, 0.37f, 0.07f),
                        capsule(0.66f, 0.50f, 0.93f, 0.37f, 0.07f),
                        circle(0.24f, 0.86f, 0.10f),
                        circle(0.50f, 0.87f, 0.10f),
                        circle(0.76f, 0.86f, 0.10f),
                });
                break;
            default:
                dense = superellipse(2f);
                stretch = true;
                break;
        }
        return resample(normalize(dense, stretch), points);
    }

    private interface Polar {
        double r(double t);
    }

    private static float[] polar(Polar f) {
        float[] out = new float[DENSE * 2];
        for (int i = 0; i < DENSE; i++) {
            double a = -Math.PI / 2 + 2 * Math.PI * i / DENSE;
            double r = f.r(a + Math.PI / 2);
            out[i * 2] = (float) (Math.cos(a) * r);
            out[i * 2 + 1] = (float) (Math.sin(a) * r);
        }
        return out;
    }

    private static final double PETAL_EPSILON = 0.02, PETAL_POWER = 0.275;

    /** |c|^0.55 with the cusp at c = 0 rounded off (sqrt(c^2 + e) instead of |c|), rescaled to 0..1. */
    private static double petal(double c) {
        double min = Math.pow(PETAL_EPSILON, PETAL_POWER), max = Math.pow(1 + PETAL_EPSILON, PETAL_POWER);
        return (Math.pow(c * c + PETAL_EPSILON, PETAL_POWER) - min) / (max - min);
    }

    private static float[] superellipse(float n) {
        float[] out = new float[DENSE * 2];
        float e = 2f / n;
        for (int i = 0; i < DENSE; i++) {
            double a = -Math.PI / 2 + 2 * Math.PI * i / DENSE;
            double c = Math.cos(a), s = Math.sin(a);
            out[i * 2] = (float) (Math.signum(c) * Math.pow(Math.abs(c), e));
            out[i * 2 + 1] = (float) (Math.signum(s) * Math.pow(Math.abs(s), e));
        }
        return out;
    }

    /** Right half of an emoji-like heart as cubic segments (start, then c1, c2, end per segment); mirrored. */
    private static final float[] HEART_HALF = {
            0.50f, 0.24f,
            0.56f, 0.08f, 0.66f, 0.02f, 0.77f, 0.02f,
            0.90f, 0.02f, 1.00f, 0.13f, 1.00f, 0.30f,
            1.00f, 0.60f, 0.66f, 0.84f, 0.50f, 0.98f,
    };

    private static float[] heart() {
        final int segments = (HEART_HALF.length - 2) / 6;
        final int steps = DENSE / 2 / segments;
        final int half = segments * steps;
        float[] out = new float[half * 4];
        int k = 0;
        for (int seg = 0; seg < segments; seg++) {
            int o = seg * 6;
            float x0 = HEART_HALF[o], y0 = HEART_HALF[o + 1];
            float x1 = HEART_HALF[o + 2], y1 = HEART_HALF[o + 3];
            float x2 = HEART_HALF[o + 4], y2 = HEART_HALF[o + 5];
            float x3 = HEART_HALF[o + 6], y3 = HEART_HALF[o + 7];
            for (int j = 0; j < steps; j++) {
                float t = j / (float) steps, u = 1 - t;
                out[k++] = u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3;
                out[k++] = u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3;
            }
        }
        for (int i = 0; i < half; i++) {
            int src = (half - 1 - i) * 2;
            out[k++] = 1f - out[src];
            out[k++] = out[src + 1];
        }
        return out;
    }

    /** Regular star polygon starting at the top; inner == outer gives a regular polygon with 2x vertices. */
    private static float[] star(int rays, float outer, float inner) {
        if (inner == outer) {
            float[] out = new float[rays * 2];
            for (int i = 0; i < rays; i++) {
                double a = -Math.PI / 2 + 2 * Math.PI * i / rays;
                out[i * 2] = (float) (0.5 + Math.cos(a) * outer);
                out[i * 2 + 1] = (float) (0.5 + Math.sin(a) * outer);
            }
            return out;
        }
        float[] out = new float[rays * 4];
        for (int i = 0; i < rays * 2; i++) {
            double a = -Math.PI / 2 + Math.PI * i / rays;
            float r = i % 2 == 0 ? outer : inner;
            out[i * 2] = (float) (0.5 + Math.cos(a) * r);
            out[i * 2 + 1] = (float) (0.5 + Math.sin(a) * r);
        }
        return out;
    }

    /** Polygon (clockwise) with every corner replaced by a quadratic curve; the first vertex uses its own radius. */
    private static float[] roundedPolygon(float[] polygon, float firstRadius, float radius, float concaveRadius) {
        final int n = polygon.length / 2;
        final int steps = 24;
        float[] out = new float[n * (steps + 1) * 2];
        int k = 0;
        for (int i = 0; i < n; i++) {
            float px = polygon[((i - 1 + n) % n) * 2], py = polygon[((i - 1 + n) % n) * 2 + 1];
            float cx = polygon[i * 2], cy = polygon[i * 2 + 1];
            float nx = polygon[((i + 1) % n) * 2], ny = polygon[((i + 1) % n) * 2 + 1];
            float l1 = (float) Math.hypot(px - cx, py - cy), l2 = (float) Math.hypot(nx - cx, ny - cy);
            boolean concave = (cx - px) * (ny - cy) - (cy - py) * (nx - cx) < 0;
            float r = Math.min(i == 0 ? firstRadius : concave ? concaveRadius : radius, Math.min(l1, l2) * 0.45f);
            float ax = cx + (px - cx) / l1 * r, ay = cy + (py - cy) / l1 * r;
            float bx = cx + (nx - cx) / l2 * r, by = cy + (ny - cy) / l2 * r;
            for (int j = 0; j <= steps; j++) {
                float t = j / (float) steps, u = 1 - t;
                out[k++] = u * u * ax + 2 * u * t * cx + t * t * bx;
                out[k++] = u * u * ay + 2 * u * t * cy + t * t * by;
            }
        }
        return out;
    }

    // Smooth union of signed distance fields, traced by casting rays from an origin inside the shape (the union
    // must be star-shaped from that origin). Smoothing k rounds every joint, so there are no sharp notches for
    // the progress ring to fold on. Primitive encoding: [type, ...params].
    private static final float ELLIPSE = 1, POLYGON = 2, CAPSULE = 3;

    private static float[] circle(float cx, float cy, float r) {
        return new float[]{ELLIPSE, cx, cy, r, r};
    }

    private static float[] ellipse(float cx, float cy, float a, float b) {
        return new float[]{ELLIPSE, cx, cy, a, b};
    }

    /** Polygon (any winding) with corners rounded by r: the outline grows by r. */
    private static float[] polygon(float r, float... vertices) {
        float[] out = new float[vertices.length + 2];
        out[0] = POLYGON;
        out[1] = r;
        System.arraycopy(vertices, 0, out, 2, vertices.length);
        return out;
    }

    private static float[] capsule(float ax, float ay, float bx, float by, float r) {
        return new float[]{CAPSULE, ax, ay, bx, by, r};
    }

    private static float[] smoothUnion(float ox, float oy, float k, float[][] primitives) {
        float[] out = new float[DENSE * 2];
        for (int i = 0; i < DENSE; i++) {
            double a = -Math.PI / 2 + 2 * Math.PI * i / DENSE;
            double dx = Math.cos(a), dy = Math.sin(a);
            // Sphere-trace inward from outside the box to the outermost crossing, then refine by bisection.
            double t = 1.2, prev = t;
            double d = field(primitives, k, ox + dx * t, oy + dy * t);
            while (d > 1e-5 && t > 0) {
                prev = t;
                t -= Math.max(d * 0.9, 1e-3);
                d = field(primitives, k, ox + dx * t, oy + dy * t);
            }
            double lo = Math.max(t, 0), hi = prev;
            for (int j = 0; j < 24 && d < 0; j++) {
                double mid = (lo + hi) / 2;
                if (field(primitives, k, ox + dx * mid, oy + dy * mid) > 0) {
                    hi = mid;
                } else {
                    lo = mid;
                }
            }
            out[i * 2] = (float) (ox + dx * lo);
            out[i * 2 + 1] = (float) (oy + dy * lo);
        }
        return out;
    }

    private static double field(float[][] primitives, double k, double x, double y) {
        double d = Double.MAX_VALUE;
        for (float[] p : primitives) {
            double e = sdf(p, x, y);
            if (d == Double.MAX_VALUE) {
                d = e;
            } else {
                double h = Math.max(k - Math.abs(d - e), 0) / k;
                d = Math.min(d, e) - h * h * k / 4;
            }
        }
        return d;
    }

    private static double sdf(float[] p, double x, double y) {
        if (p[0] == ELLIPSE) {
            double px = x - p[1], py = y - p[2], a = p[3], b = p[4];
            if (a == b) {
                return Math.hypot(px, py) - a;
            }
            double k0 = Math.hypot(px / a, py / b), k1 = Math.hypot(px / (a * a), py / (b * b));
            return k1 <= 0 ? -Math.min(a, b) : k0 * (k0 - 1) / k1;
        } else if (p[0] == CAPSULE) {
            return segmentDistance(x, y, p[1], p[2], p[3], p[4]) - p[5];
        } else {
            int n = (p.length - 2) / 2;
            double best = Double.MAX_VALUE;
            boolean inside = false;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                double xi = p[2 + i * 2], yi = p[3 + i * 2], xj = p[2 + j * 2], yj = p[3 + j * 2];
                best = Math.min(best, segmentDistance(x, y, xi, yi, xj, yj));
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                    inside = !inside;
                }
            }
            return (inside ? -best : best) - p[1];
        }
    }

    private static double segmentDistance(double x, double y, double ax, double ay, double bx, double by) {
        double ex = bx - ax, ey = by - ay;
        double len2 = ex * ex + ey * ey;
        double t = len2 <= 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * ex + (y - ay) * ey) / len2));
        return Math.hypot(x - ax - ex * t, y - ay - ey * t);
    }

    /** Fits into the unit box (stretched or uniformly centred), starts at top-centre, runs clockwise. */
    static float[] normalize(float[] p, boolean stretch) {
        int n = p.length / 2;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, p[i * 2]);
            maxX = Math.max(maxX, p[i * 2]);
            minY = Math.min(minY, p[i * 2 + 1]);
            maxY = Math.max(maxY, p[i * 2 + 1]);
        }
        float w = maxX - minX, h = maxY - minY;
        float sx = 1f / w, sy = 1f / h, ox = 0, oy = 0;
        if (!stretch) {
            sx = sy = 1f / Math.max(w, h);
            ox = (1f - w * sx) / 2f;
            oy = (1f - h * sy) / 2f;
        }
        for (int i = 0; i < n; i++) {
            p[i * 2] = ox + (p[i * 2] - minX) * sx;
            p[i * 2 + 1] = oy + (p[i * 2 + 1] - minY) * sy;
        }

        double area = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += p[i * 2] * p[j * 2 + 1] - p[j * 2] * p[i * 2 + 1];
        }
        if (area < 0) {
            for (int i = 0, j = n - 1; i < j; i++, j--) {
                float x = p[i * 2], y = p[i * 2 + 1];
                p[i * 2] = p[j * 2];
                p[i * 2 + 1] = p[j * 2 + 1];
                p[j * 2] = x;
                p[j * 2 + 1] = y;
            }
        }

        int start = 0;
        float startY = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float x0 = p[i * 2] - 0.5f, x1 = p[j * 2] - 0.5f;
            if (x0 <= 0 && x1 > 0) {
                float t = x0 == x1 ? 0 : -x0 / (x1 - x0);
                float y = p[i * 2 + 1] + (p[j * 2 + 1] - p[i * 2 + 1]) * t;
                if (y < startY) {
                    startY = y;
                    start = i;
                }
            }
        }
        float[] out = new float[(n + 1) * 2];
        out[0] = 0.5f;
        out[1] = startY;
        int k = 2;
        for (int i = 1; i <= n; i++) {
            int idx = (start + i) % n;
            out[k++] = p[idx * 2];
            out[k++] = p[idx * 2 + 1];
        }
        return out;
    }

    /** Resamples a closed polyline to evenly spaced points by arc length. */
    static float[] resample(float[] p, int points) {
        int n = p.length / 2;
        float[] cumulative = new float[n + 1];
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            cumulative[i + 1] = cumulative[i] + (float) Math.hypot(p[j * 2] - p[i * 2], p[j * 2 + 1] - p[i * 2 + 1]);
        }
        float total = cumulative[n];
        float[] out = new float[points * 2];
        int seg = 0;
        for (int i = 0; i < points; i++) {
            float target = total * i / points;
            while (seg < n - 1 && cumulative[seg + 1] < target) {
                seg++;
            }
            int j = (seg + 1) % n;
            float len = cumulative[seg + 1] - cumulative[seg];
            float t = len <= 0 ? 0 : (target - cumulative[seg]) / len;
            out[i * 2] = p[seg * 2] + (p[j * 2] - p[seg * 2]) * t;
            out[i * 2 + 1] = p[seg * 2 + 1] + (p[j * 2 + 1] - p[seg * 2 + 1]) * t;
        }
        return out;
    }
}
