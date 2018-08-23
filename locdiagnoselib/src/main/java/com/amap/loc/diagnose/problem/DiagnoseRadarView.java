package com.amap.loc.diagnose.problem;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.amap.loc.diagnose.R;

import java.util.LinkedList;
import java.util.List;

public class DiagnoseRadarView extends FrameLayout {

    private static final int STATUS_MARGIN = 20;
    private static final int STATUS_ITEM_SPACING = 10;

    private View radarBg;
    private List<View> animationViews = new LinkedList<>();
    private LinearLayout statusContainer;
    private HorizontalScrollView statusScrollView;

    private int statusItemScrollPx;

    public DiagnoseRadarView(Context context) {
        super(context);
        init();
    }

    public DiagnoseRadarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.view_diagnose_radar, this);
        radarBg = findViewById(R.id.view_diagnose_radar_bg);
        statusContainer = findViewById(R.id.view_diagnose_radar_status_container);
        statusScrollView = findViewById(R.id.view_diagnose_radar_status_scroll);
        statusScrollView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // disable scroll view interaction
                return true;
            }
        });
    }

    public void initStatus(List<DiagnoseView.Diagnoser> diagnosers) {
        for (DiagnoseView.Diagnoser callable : diagnosers) {
            inflateStatusItem(callable.getTitle(), callable.getIcon());
        }

        // adjust status item view size
        WindowManager windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Point size = new Point();
        DisplayMetrics dm = new DisplayMetrics();
        Display display = windowManager.getDefaultDisplay();
        display.getMetrics(dm);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            display.getSize(size);
        } else {
            size.x = display.getWidth();
            size.y = display.getHeight();
        }
        int margin = (int) (STATUS_MARGIN * dm.density + 0.5f);
        int spacing = (int) (STATUS_ITEM_SPACING * dm.density + 0.5f);
        int width = size.x - 2 * margin;
        int childCount = statusContainer.getChildCount();
        statusItemScrollPx = width + spacing;
        for (int i = 0; i < childCount; i++) {
            View v = statusContainer.getChildAt(i);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
            lp.width = width;
            if (i == 0) {
                lp.leftMargin = margin;
                if (childCount == 2) {
                    lp.rightMargin = spacing;
                }
            } else if (i == childCount - 1) {
                lp.rightMargin = margin;
            } else {
                lp.leftMargin = spacing;
                lp.rightMargin = spacing;
            }
        }
    }

    private void inflateStatusItem(String title, int iconResId) {
        ViewGroup statusView = (ViewGroup) LayoutInflater.from(getContext())
                .inflate(R.layout.view_diagnose_radar_status, statusContainer, false);
        ((TextView) statusView.findViewById(R.id.view_diagnose_radar_status_status)).setText(title);
        Drawable drawable = getResources().getDrawable(iconResId);
        drawable.mutate().setColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_ATOP);
        ((ImageView) statusView.findViewById(R.id.view_diagnose_radar_status_icon)).setImageDrawable(drawable);
        RotateAnimation ra = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        ra.setInterpolator(new LinearInterpolator());
        ra.setRepeatCount(Animation.INFINITE);
        ra.setDuration(1500);
        statusView.findViewById(R.id.view_diagnose_radar_status_loading).startAnimation(ra);
        animationViews.add(statusView);
        statusContainer.addView(statusView);
    }

    public void onStatusChange(int status) {
        statusScrollView.smoothScrollTo(status * statusItemScrollPx, 0);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        RotateAnimation ra = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        ra.setInterpolator(new LinearInterpolator());
        ra.setRepeatCount(Animation.INFINITE);
        ra.setDuration(2000);
        radarBg.startAnimation(ra);
        animationViews.add(radarBg);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (View view : animationViews) {
            view.clearAnimation();
        }
        animationViews.clear();
    }
}
