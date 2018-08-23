package com.amap.loc.diagnose.problem;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;

import com.amap.loc.diagnose.BuildConfig;
import com.amap.loc.diagnose.R;

import java.util.LinkedList;
import java.util.List;

public class DiagnoseActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnose);

        DiagnoseView diagnoseView = findViewById(R.id.activity_diagnose);
        diagnoseView.setDiagnoseViewCallback(new DiagnoseView.DiagnoseViewCallback() {
            @Override
            public void onBack() {
                finish();
            }
        });
        List<DiagnoseView.Diagnoser> checkCallables = new LinkedList<>();

        // 高于M的手机才检查运行时权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkCallables.add(new DefaultLocPermissionDiagnoser());
        }
        checkCallables.add(new DefaultLocNetDiagnoser());
        checkCallables.add(new DefaultLocationDiagnoser());
        diagnoseView.startDiagnose(checkCallables);
    }
}
