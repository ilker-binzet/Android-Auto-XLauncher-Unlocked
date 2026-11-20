package com.google.android.projection.gearhead.xunlocked.recovery;

/** Portable regression checks for the live viewport, without Android framework stubs. */
public final class ViewportSizeCheck {
    public static void main(String[] args) {
        size("landscape fit", ViewportSize.measure(1000, 600, 1920, 1080, false), 1000, 563, false);
        size("landscape fill", ViewportSize.measure(1000, 600, 1920, 1080, true), 1067, 600, true);
        size("portrait fit", ViewportSize.measure(1000, 600, 1080, 1920, false), 338, 600, false);
        size("portrait fill", ViewportSize.measure(1000, 600, 1080, 1920, true), 1000, 1778, true);
        size("vertical head unit, landscape phone", ViewportSize.measure(600, 1000, 1920, 1080, true),
                1778, 1000, true);
        size("4K safety limit", ViewportSize.measure(3840, 2160, 1080, 2400, true), 972, 2160, false);
        require(ViewportSize.measure(0, 600, 1080, 1920, false) == null, "unmeasured frame");
        require(ViewportSize.measure(1000, 600, 1080, 0, true) == null, "unreported source height");
        require(ViewportSize.includes(500, 889, 0, -589, 1000, 1778, 1000, 600), "visible crop center");
        require(!ViewportSize.includes(500, 588, 0, -589, 1000, 1778, 1000, 600), "above crop");
        require(!ViewportSize.includes(500, 1189, 0, -589, 1000, 1778, 1000, 600), "below crop");
        require(!ViewportSize.includes(-1, 300, 331, 0, 338, 600, 1000, 600), "fit side bar");
        require(!ViewportSize.includes(Float.NaN, 1, 0, 0, 100, 100, 100, 100), "invalid coordinate");
        System.out.println("13 viewport checks passed");
    }

    private static void size(String label, ViewportSize size, int width, int height, boolean filled) {
        require(size != null && size.width == width && size.height == height && size.filled == filled, label);
    }

    private static void require(boolean result, String label) {
        if (!result) throw new AssertionError(label);
    }
}
