package com.google.android.projection.gearhead.xunlocked.recovery;

import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/** Owns the size preference and input validity for one recovered projection surface. */
public final class ProjectionViewport {
    private final FrameLayout frame;
    private final SurfaceView surface;
    private int phoneWidth;
    private int phoneHeight;
    private int frameWidth;
    private int frameHeight;
    private boolean fillPortrait;
    private boolean fillLandscape;
    private long changedAt;
    private boolean sizing;

    ProjectionViewport(FrameLayout frame) {
        this.frame = frame;
        surface = (SurfaceView) frame.getChildAt(0);
        surface.setTag(this);
        frame.setClipChildren(true);
        frame.setClipToPadding(true);
    }

    // Calls originate from the original renderer, after obtaining the phone size.
    public static void resize(SurfaceView surface, Point phone) {
        ProjectionViewport output = from(surface);
        if (output == null || phone == null || phone.x <= 0 || phone.y <= 0) return;
        if (output.phoneWidth != phone.x || output.phoneHeight != phone.y) {
            output.phoneWidth = phone.x;
            output.phoneHeight = phone.y;
            output.changedAt = SystemClock.uptimeMillis();
        }
        output.layoutOutput();
    }

    static ProjectionViewport from(View surface) {
        Object state = surface.getTag();
        return state instanceof ProjectionViewport ? (ProjectionViewport) state : null;
    }

    public static boolean accepts(View surface, MotionEvent event) {
        ProjectionViewport output = from(surface);
        if (output == null || output.sizing || event.getPointerCount() != 1
                || event.getDownTime() <= output.changedAt || surface.getVisibility() != View.VISIBLE) return false;
        return ViewportSize.includes(event.getX(), event.getY(), surface.getLeft(), surface.getTop(),
                surface.getWidth(), surface.getHeight(), output.frame.getWidth(), output.frame.getHeight());
    }

    public static void surfaceSized(SurfaceView surface, int width, int height) {
        ProjectionViewport output = from(surface);
        if (output == null) return;
        ViewGroup.LayoutParams layout = surface.getLayoutParams();
        if (width == layout.width && height == layout.height) output.sizing = false;
    }

    boolean select(boolean fill) {
        if (phoneWidth < phoneHeight) fillPortrait = fill;
        else fillLandscape = fill;
        return layoutOutput();
    }

    boolean hasSource() { return phoneWidth > 0 && phoneHeight > 0; }

    boolean fillRequested() {
        return phoneWidth < phoneHeight ? fillPortrait : fillLandscape;
    }

    boolean canFill() {
        ViewportSize size = ViewportSize.measure(frame.getWidth(), frame.getHeight(), phoneWidth, phoneHeight, true);
        return size != null && size.filled;
    }

    private boolean layoutOutput() {
        int width = frame.getWidth();
        int height = frame.getHeight();
        if (frameWidth != width || frameHeight != height) {
            frameWidth = width;
            frameHeight = height;
            frame.setClipBounds(new Rect(0, 0, width, height));
            changedAt = SystemClock.uptimeMillis();
        }
        ViewportSize size = ViewportSize.measure(width, height, phoneWidth,
                phoneHeight, fillRequested());
        if (size == null) return false;
        ViewGroup.LayoutParams old = surface.getLayoutParams();
        if (old.width != size.width || old.height != size.height) {
            sizing = true;
            changedAt = SystemClock.uptimeMillis();
            surface.setLayoutParams(new FrameLayout.LayoutParams(size.width, size.height, Gravity.CENTER));
        }
        return size.filled;
    }
}
