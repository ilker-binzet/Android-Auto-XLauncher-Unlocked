package com.google.android.projection.gearhead.xunlocked.recovery;

/** Sizing in pixels for a centered, aspect-preserving projection surface. */
final class ViewportSize {
    private static final int MAX_FILL_EDGE = 4096;
    private static final long MAX_FILL_PIXELS = 4L * 1024 * 1024;

    final int width;
    final int height;
    final boolean filled;

    private ViewportSize(int width, int height, boolean filled) {
        this.width = width;
        this.height = height;
        this.filled = filled;
    }

    static ViewportSize measure(int frameWidth, int frameHeight, int sourceWidth, int sourceHeight,
                                boolean fill) {
        if (frameWidth <= 0 || frameHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) return null;
        double horizontal = frameWidth / (double) sourceWidth;
        double vertical = frameHeight / (double) sourceHeight;
        if (fill) {
            double scale = Math.max(horizontal, vertical);
            long width = Math.round(sourceWidth * scale);
            long height = Math.round(sourceHeight * scale);
            if (width <= MAX_FILL_EDGE && height <= MAX_FILL_EDGE && width * height <= MAX_FILL_PIXELS) {
                return new ViewportSize(Math.max(frameWidth, (int) width), Math.max(frameHeight, (int) height), true);
            }
        }
        double scale = Math.min(horizontal, vertical);
        return new ViewportSize(Math.max(1, (int) Math.round(sourceWidth * scale)),
                Math.max(1, (int) Math.round(sourceHeight * scale)), false);
    }

    static boolean includes(float x, float y, int left, int top, int surfaceWidth, int surfaceHeight,
                            int frameWidth, int frameHeight) {
        return x >= 0 && x < surfaceWidth && y >= 0 && y < surfaceHeight
                && x + left >= 0 && x + left < frameWidth
                && y + top >= 0 && y + top < frameHeight;
    }
}
