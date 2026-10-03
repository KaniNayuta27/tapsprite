package com.tapsprite.agent;

/**
 * Pure coordinate math for 实时操控. No Android APIs, so JVM unit tests can run it.
 *
 * <p>The live encoder frame is in the <b>current logical</b> orientation (what the user
 * sees). That is the same space {@code AccessibilityService.dispatchGesture} uses, so
 * normalized (0,0) is the top-left of both the video and the physical display.
 * {@code physW}/{@code physH} must already be the current real size
 * (getRealMetrics / WindowMetrics), swapped for landscape. Encoder scaling
 * (1080×2400 → 576×1280) does not enter this function.
 *
 * <p>{@code rotationDeg} is {@code Display.getRotation() * 90} (0/90/180/270): the
 * rotation of the drawn graphics relative to the panel's natural orientation.
 * When {@code frameIsNatural} is false (the live path), rotation is validated but
 * does not remix axes — the frame was already produced in the logical orientation.
 * When {@code frameIsNatural} is true, {@code (nx, ny)} is on the natural panel
 * and is rotated into logical gesture pixels with the same mapping as AOSP
 * {@code CoordinateTransforms.transformPhysicalToLogicalCoordinates}.
 */
public final class LiveCoords {
    private LiveCoords() {
    }

    /** Nearest pixel on {@code [0, size-1]}. n=0 → 0, n=1 → size-1, n=0.5 → center. */
    public static int axisToPx(double n, int size) {
        if (size <= 1) {
            return 0;
        }
        if (Double.isNaN(n) || n < 0) {
            n = 0;
        } else if (n > 1) {
            n = 1;
        }
        long px = Math.round(n * (size - 1));
        if (px < 0) {
            return 0;
        }
        if (px > size - 1) {
            return size - 1;
        }
        return (int) px;
    }

    public static int normRotation(int rotationDeg) {
        int r = rotationDeg % 360;
        if (r < 0) {
            r += 360;
        }
        if (r != 0 && r != 90 && r != 180 && r != 270) {
            throw new IllegalArgumentException("rotation " + rotationDeg);
        }
        return r;
    }

    /**
     * Normalized point → dispatchGesture pixels.
     *
     * @param physW          current logical width (gesture space)
     * @param physH          current logical height
     * @param rotationDeg    0/90/180/270
     * @param frameIsNatural true only when the video buffer is still the natural panel
     */
    public static int[] toPhysical(double nx, double ny, int physW, int physH, int rotationDeg, boolean frameIsNatural) {
        if (physW < 1 || physH < 1) {
            throw new IllegalArgumentException("display size");
        }
        int rot = normRotation(rotationDeg);
        if (!frameIsNatural || rot == 0) {
            return new int[]{axisToPx(nx, physW), axisToPx(ny, physH)};
        }
        double[] logical = naturalNormToLogical(nx, ny, rot);
        return new int[]{axisToPx(logical[0], physW), axisToPx(logical[1], physH)};
    }

    /**
     * Natural-frame normalized point → logical normalized point.
     * ROTATION_90: (nx, ny) → (ny, 1-nx). ROTATION_270: (1-ny, nx). ROTATION_180: (1-nx, 1-ny).
     */
    public static double[] naturalNormToLogical(double nx, double ny, int rotationDeg) {
        if (Double.isNaN(nx) || nx < 0) {
            nx = 0;
        } else if (nx > 1) {
            nx = 1;
        }
        if (Double.isNaN(ny) || ny < 0) {
            ny = 0;
        } else if (ny > 1) {
            ny = 1;
        }
        switch (normRotation(rotationDeg)) {
            case 90:
                return new double[]{ny, 1.0 - nx};
            case 180:
                return new double[]{1.0 - nx, 1.0 - ny};
            case 270:
                return new double[]{1.0 - ny, nx};
            default:
                return new double[]{nx, ny};
        }
    }

    /**
     * Even encoder size. Long side ≤ {@code maxLong}, exact aspect kept as closely as even
     * integers allow. 1080×2400 with maxLong 1280 → 576×1280.
     */
    public static int[] encodeSize(int physW, int physH, int maxLong) {
        if (physW < 2) {
            physW = 2;
        }
        if (physH < 2) {
            physH = 2;
        }
        if (maxLong < 2) {
            maxLong = 2;
        }
        if ((maxLong & 1) != 0) {
            maxLong -= 1;
        }
        double scale = 1.0;
        int longSide = Math.max(physW, physH);
        if (longSide > maxLong) {
            scale = (double) maxLong / (double) longSide;
        }
        int w = nearestEven(physW * scale);
        int h = nearestEven(physH * scale);
        if (Math.max(w, h) > maxLong) {
            if (physW >= physH) {
                w = maxLong;
                h = nearestEven(maxLong * (physH / (double) physW));
            } else {
                h = maxLong;
                w = nearestEven(maxLong * (physW / (double) physH));
            }
        }
        if (w < 2) {
            w = 2;
        }
        if (h < 2) {
            h = 2;
        }
        if ((w & 1) != 0) {
            w -= 1;
        }
        if ((h & 1) != 0) {
            h -= 1;
        }
        if (w < 2) {
            w = 2;
        }
        if (h < 2) {
            h = 2;
        }
        return new int[]{w, h};
    }

    static int nearestEven(double v) {
        int r = (int) Math.round(v);
        if (r < 2) {
            return 2;
        }
        if ((r & 1) == 0) {
            return r;
        }
        double down = r - 1;
        double up = r + 1;
        return Math.abs(v - down) <= Math.abs(v - up) ? (int) down : (int) up;
    }
}
