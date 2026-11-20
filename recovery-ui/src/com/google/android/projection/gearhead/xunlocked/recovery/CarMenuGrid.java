package com.google.android.projection.gearhead.xunlocked.recovery;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Native presentation for the recovered vehicle activity. It owns no projection or input state. */
public final class CarMenuGrid extends GridView {
    private static final ThreadPoolExecutor ICON_WORKER = new ThreadPoolExecutor(
            0, 1, 20, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    private final Palette palette;

    public CarMenuGrid(Context context) {
        super(context);
        palette = new Palette(context);
        setNumColumns(AUTO_FIT);
        setColumnWidth(dp(context, 220));
        setStretchMode(STRETCH_COLUMN_WIDTH);
        setHorizontalSpacing(dp(context, 12));
        setVerticalSpacing(dp(context, 12));
        setPadding(dp(context, 20), dp(context, 8), dp(context, 20), dp(context, 12));
        setClipToPadding(false);
        setSelector(feedback(context, palette));
        setDrawSelectorOnTop(true);
    }

    public static ListAdapter adapter(Context context, List<String> packages, List<String> labels) {
        return new AppAdapter(context, packages, labels);
    }

    public static View wrap(LinearLayout original, int readyText) {
        return new Shell(original, readyText);
    }

    private static int dp(Context context, int n) {
        return Math.round(context.getResources().getDisplayMetrics().density * n);
    }

    private static GradientDrawable shape(Context context, int color, int outline) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context, 12));
        if (outline != Color.TRANSPARENT) shape.setStroke(dp(context, 2), outline);
        return shape;
    }

    private static StateListDrawable feedback(Context context, Palette palette) {
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[]{android.R.attr.state_pressed}, shape(context, palette.pressed, palette.accent));
        drawable.addState(new int[]{android.R.attr.state_focused}, shape(context, Color.TRANSPARENT, palette.accent));
        drawable.addState(new int[]{android.R.attr.state_selected}, shape(context, Color.TRANSPARENT, palette.accent));
        drawable.addState(new int[0], shape(context, Color.TRANSPARENT, Color.TRANSPARENT));
        return drawable;
    }

    private static final class AppAdapter extends BaseAdapter {
        private final Context context;
        private final PackageManager packagesManager;
        private final List<String> packages;
        private final List<String> labels;
        private final int iconSize;
        private final Bitmap fallback;
        private final LruCache<String, Bitmap> icons = new LruCache<>(48);

        AppAdapter(Context context, List<String> packages, List<String> labels) {
            this.context = context;
            this.packagesManager = context.getPackageManager();
            this.packages = new ArrayList<>(packages);
            this.labels = new ArrayList<>(labels);
            this.iconSize = Math.min(192, dp(context, 56));
            this.fallback = rasterize(packagesManager.getDefaultActivityIcon());
        }

        @Override public int getCount() { return labels.size(); }
        @Override public String getItem(int position) { return labels.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convert, ViewGroup parent) {
            Tile tile = convert instanceof Tile ? (Tile) convert : new Tile(context);
            String packageName = packages.get(position);
            tile.label.setText(getItem(position));
            tile.setTag(packageName);
            Bitmap cached = icons.get(packageName);
            tile.icon.setImageBitmap(cached == null ? fallback : cached);
            if (cached == null) {
                ICON_WORKER.execute(() -> {
                    Bitmap bitmap = loadIcon(packageName);
                    icons.put(packageName, bitmap);
                    tile.post(() -> {
                        if (packageName.equals(tile.getTag())) tile.icon.setImageBitmap(bitmap);
                    });
                });
            }
            return tile;
        }

        private Bitmap loadIcon(String packageName) {
            try {
                return rasterize(packagesManager.getApplicationIcon(packageName));
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                return fallback;
            }
        }

        private Bitmap rasterize(Drawable drawable) {
            Bitmap result = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
            int width = Math.max(1, drawable.getIntrinsicWidth());
            int height = Math.max(1, drawable.getIntrinsicHeight());
            float scale = (float) iconSize / Math.max(width, height);
            int scaledWidth = Math.round(width * scale);
            int scaledHeight = Math.round(height * scale);
            int x = (iconSize - scaledWidth) / 2;
            int y = (iconSize - scaledHeight) / 2;
            drawable.setBounds(x, y, x + scaledWidth, y + scaledHeight);
            drawable.draw(new Canvas(result));
            return result;
        }
    }

    private static final class Tile extends LinearLayout {
        final ImageView icon;
        final TextView label;

        Tile(Context context) {
            super(context);
            Palette palette = new Palette(context);
            setLayoutParams(new AbsListView.LayoutParams(-1, -2));
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(context, 88));
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
            icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LayoutParams iconParams = new LayoutParams(dp(context, 56), dp(context, 56));
            iconParams.setMarginEnd(dp(context, 14));
            addView(icon, iconParams);
            label = new TextView(context);
            label.setTextSize(22);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            label.setTextColor(palette.text);
            label.setMaxLines(2);
            label.setEllipsize(TextUtils.TruncateAt.END);
            addView(label, new LayoutParams(0, -2, 1));
        }
    }

    /** Fits the existing surface and controls without moving any attached surface between parents. */
    private static final class Shell extends ViewGroup {
        private final Context context;
        private final LinearLayout header;
        private final TextView status;
        private final LinearLayout controls;
        private final FrameLayout content;
        private final CarMenuGrid grid;
        private final String ready;
        private final CharSequence menuLabel;
        private final CharSequence endLabel;
        private boolean rail;
        private boolean showingContent;
        private boolean arranged;

        Shell(LinearLayout original, int readyText) {
            super(original.getContext());
            context = getContext();
            Palette palette = new Palette(context);
            status = (TextView) original.getChildAt(0);
            controls = (LinearLayout) original.getChildAt(1);
            content = (FrameLayout) original.getChildAt(2);
            grid = (CarMenuGrid) content.getChildAt(1);
            ready = context.getString(readyText);
            menuLabel = ((Button) controls.getChildAt(0)).getText();
            endLabel = ((Button) controls.getChildAt(3)).getText();
            original.removeAllViews();
            setBackgroundColor(palette.canvas);
            header = new LinearLayout(context);
            header.setOrientation(LinearLayout.VERTICAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(context, 20), dp(context, 10), dp(context, 20), dp(context, 4));
            TextView brand = new TextView(context);
            brand.setText("Car App Hub");
            brand.setTextSize(26);
            brand.setTextColor(palette.text);
            brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            brand.setAccessibilityHeading(true);
            int logo = context.getResources().getIdentifier("ic_brand_mark", "drawable",
                    "com.google.android.projection.gearhead.xunlocked");
            if (logo != 0) {
                Drawable mark = context.getDrawable(logo);
                if (mark != null) {
                    mark.mutate().setTint(palette.accent);
                    mark.setBounds(0, 0, dp(context, 36), dp(context, 36));
                    brand.setCompoundDrawablesRelative(mark, null, null, null);
                    brand.setCompoundDrawablePadding(dp(context, 12));
                }
            }
            header.addView(brand);
            status.setTextColor(palette.muted);
            status.setPadding(0, dp(context, 4), 0, 0);
            status.setTextSize(16);
            status.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
            header.addView(status);
            content.setBackgroundColor(Color.BLACK);
            grid.setBackgroundColor(palette.canvas);
            controls.setBackgroundColor(palette.surface);
            for (int i = 0; i < controls.getChildCount(); i++) {
                Button button = (Button) controls.getChildAt(i);
                button.setAllCaps(false);
                button.setStateListAnimator(null);
                button.setMinimumWidth(0);
                button.setMinWidth(0);
                button.setMinimumHeight(0);
                button.setMinHeight(0);
                button.setPadding(dp(context, 8), 0, dp(context, 8), 0);
                button.setGravity(Gravity.CENTER);
                button.setTextColor(i == 0 ? palette.accent : palette.text);
                button.setTextSize(18);
                button.setBackground(feedback(context, palette));
            }
            addView(header);
            addView(controls);
            addView(content);
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int height = MeasureSpec.getSize(heightSpec);
            boolean contentOpened = grid.getVisibility() != VISIBLE;
            boolean sideRail = contentOpened && width >= dp(context, 660) && height >= dp(context, 336);
            if (!arranged || showingContent != contentOpened || rail != sideRail) {
                arranged = true;
                showingContent = contentOpened;
                rail = sideRail;
                arrangeControls();
            }
            if (showingContent) {
                header.setVisibility(GONE);
            } else {
                header.setVisibility(VISIBLE);
                status.setVisibility(TextUtils.equals(status.getText(), ready) ? GONE : VISIBLE);
            }
            int headerHeight = 0;
            if (!showingContent) {
                header.measure(exactly(width), MeasureSpec.makeMeasureSpec(height / 2, MeasureSpec.AT_MOST));
                headerHeight = header.getMeasuredHeight();
            }
            int bar = rail ? dp(context, 104) : dp(context, 80);
            controls.measure(exactly(rail ? bar : width), exactly(rail ? height : Math.min(bar, height)));
            content.measure(exactly(rail ? Math.max(0, width - bar) : width),
                    exactly(rail ? height : Math.max(0, height - headerHeight - controls.getMeasuredHeight())));
            setMeasuredDimension(width, height);
        }

        private void arrangeControls() {
            controls.setOrientation(rail ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            controls.setGravity(rail ? Gravity.TOP : Gravity.CENTER_VERTICAL);
            boolean turkish = "tr".equals(context.getResources().getConfiguration().getLocales().get(0).getLanguage());
            for (int i = 0; i < controls.getChildCount(); i++) {
                Button button = (Button) controls.getChildAt(i);
                button.setVisibility((showingContent ? i != 2 : i >= 2) ? VISIBLE : GONE);
                button.setLayoutParams(rail ? new LinearLayout.LayoutParams(-1, dp(context, 80))
                        : new LinearLayout.LayoutParams(0, -1, 1));
            }
            Button menu = (Button) controls.getChildAt(0);
            menu.setText(showingContent ? (turkish ? "Menü" : "Menu") : menuLabel);
            Button end = (Button) controls.getChildAt(3);
            end.setContentDescription(endLabel);
            end.setText(rail ? (turkish ? "Bitir" : "Stop") : endLabel);
        }

        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left;
            int height = bottom - top;
            if (rail) {
                int bar = controls.getMeasuredWidth();
                controls.layout(0, 0, bar, height);
                content.layout(bar, 0, width, height);
            } else {
                int headerBottom = showingContent ? 0 : header.getMeasuredHeight();
                int barTop = height - controls.getMeasuredHeight();
                if (!showingContent) header.layout(0, 0, width, headerBottom);
                content.layout(0, headerBottom, width, Math.max(headerBottom, barTop));
                controls.layout(0, barTop, width, height);
            }
        }

        private static int exactly(int size) {
            return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
        }
    }

    private static final class Palette {
        final int canvas, surface, text, muted, accent, pressed;

        Palette(Context context) {
            boolean night = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    != Configuration.UI_MODE_NIGHT_NO;
            canvas = night ? 0xff101619 : 0xfff4f3ed;
            surface = night ? 0xff1a2226 : 0xffeaeae3;
            text = night ? 0xffeff1ea : 0xff192226;
            muted = night ? 0xffaab6ba : 0xff566268;
            accent = night ? 0xfff2b85d : 0xff85510a;
            pressed = night ? 0x30f2b85d : 0x3085510a;
        }
    }
}
