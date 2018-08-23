package com.amap.loc.diagnose.problem;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import com.amap.loc.diagnose.R;

import java.util.LinkedList;
import java.util.List;

public class DiagnoseView extends FrameLayout {

    public interface DiagnoseFinishCallback {
        /**
         * 检查完成后的回调方法，必须在主线程被调用
         * @param diagnoseResultItem 检查结果信息
         */
        void onDiagnoseFinish(DiagnoseResultItem diagnoseResultItem);
    }

    public interface Diagnoser {
        /**
         * 某些检测，如定位，因为涉及到GPS检测，可能需要耗时10s以上，为了优化体验，这种检测应该尽早启动，这种检测的启动
         * 时机可以放在该prepare方法中，在diagnose中只需等待检测完成后回调Callback
         *
         * 耗时5s以内的检测通常不会引起等待焦虑，不需要提早启动
         *
         * @param context 传入的Context，用于检测权限等
         */

        void prepare(Context context);
        /**
         * 执行检测操作，当位于该Diagnoser以前的其他Diagnoser调用finishCallback回调后，接下来就会调用
         * 该Diagnoser的diagnose方法，同时使用getIcon和getTitle方法获取本检测项的图标和标题，在检测中
         * 及检测结果界面显示
         *
         * 有的检测可能属于耗时操作，需要在异步线程里进行检测，并在检测完成后在主线程中回调finishCallback
         *
         * 有的检测可能不耗时，可以直接在主线程中完成
         *
         * @param context 传入的Context，用于检测权限等
         * @param finishCallback 检测结果回调，检测结束后，必须在主线程中调用该回调
         */
        void diagnose(Context context, DiagnoseFinishCallback finishCallback);

        /**
         * 检测项的图标，必须是纯色透明背景图，组件会将其转为白色显示在"检测中"界面，并在结果界面使用原图作为结果项图标
         * @return 图标资源id
         */
        int getIcon();

        /**
         * 检测项的名字，如"权限检查"、"定位检查"，组件会在"检测中"界面和检测结果界面展示该名字
         * @return 检测项的名字
         */
        String getTitle();
    }

    public interface DiagnoseViewCallback {
        void onBack();
    }

    private DiagnoseViewCallback diagnoseViewCallback;

    private DiagnoseRadarView radarView;
    private DiagnoseResultView resultView;

    private List<Diagnoser> diagnosers;
    private List<DiagnoseResultItem> diagnoseResults = new LinkedList<>();
    private int checkIndex;
    private long latestCheckTime;
    private boolean destroyed = false;
    private Handler handler = new Handler();

    public DiagnoseView(Context context) {
        super(context);
        init();
    }

    public DiagnoseView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.view_diagnose, this);
        radarView = findViewById(R.id.activity_diagnose_radar);
        resultView = findViewById(R.id.activity_diagnose_result);

        OnClickListener backListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (diagnoseViewCallback != null) {
                    diagnoseViewCallback.onBack();
                }
            }
        };
        radarView.findViewById(R.id.view_diagnose_radar_back).setOnClickListener(backListener);
        resultView.findViewById(R.id.view_diagnose_result_back).setOnClickListener(backListener);
    }

    public void startDiagnose(List<Diagnoser> diagnosers) {
        if (diagnosers == null || diagnosers.isEmpty()) {
            return;
        }
        this.diagnosers = diagnosers;
        for (Diagnoser diagnoser : diagnosers) {
            diagnoser.prepare(getContext());
        }
        radarView.initStatus(diagnosers);
        startDiagnose();
    }

    public void setDiagnoseViewCallback(DiagnoseViewCallback diagnoseViewCallback) {
        this.diagnoseViewCallback = diagnoseViewCallback;
    }

    private void startDiagnose() {
        check();
    }

    private void check() {
        if (destroyed) {
            return;
        }
        if (checkIndex >= diagnosers.size()) {
            onCheckFinish();
            return;
        }
        latestCheckTime = System.currentTimeMillis();
        radarView.onStatusChange(checkIndex);
        Diagnoser current = diagnosers.get(checkIndex);
        current.diagnose(getContext(), diagnoseFinishCallback);
    }

    private void onCheckFinish() {
        resultView.setVisibility(View.VISIBLE);
        resultView.setData(diagnoseResults);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            resultView.setTranslationY(radarView.getHeight());
            resultView.setAlpha(0f);
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(500);
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        float curr = (float) animation.getAnimatedValue();
                        float offset = radarView.getHeight() * curr;
                        radarView.setTranslationY(-offset);
                        radarView.setAlpha(1f - curr);
                        resultView.setTranslationY(radarView.getHeight() - offset);
                        resultView.setAlpha(curr);
                    }
                }
            });
            va.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        destroyed = true;
    }


    private DiagnoseFinishCallback diagnoseFinishCallback = new DiagnoseFinishCallback() {
        @Override
        public void onDiagnoseFinish(DiagnoseResultItem diagnoseResultItem) {
            if (destroyed) {
                return;
            }
            Diagnoser d = diagnosers.get(checkIndex);
            diagnoseResultItem.icon = d.getIcon();
            diagnoseResultItem.title = d.getTitle();
            diagnoseResults.add(diagnoseResultItem);
            long curr = System.currentTimeMillis();
            long target = latestCheckTime + 2000;   // 最快2s以后再进行下一项检查
            checkIndex++;
            handler.postDelayed(runCheckRunnable, Math.max(0, target - curr));  // 如果已经超过1s，直接进行下一项检查
        }
    };

    private Runnable runCheckRunnable = new Runnable() {
        @Override
        public void run() {
            check();
        }
    };
}
