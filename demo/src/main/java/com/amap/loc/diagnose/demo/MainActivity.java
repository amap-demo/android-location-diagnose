package com.amap.loc.diagnose.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.amap.loc.diagnose.problem.DiagnoseActivity;

public class MainActivity extends Activity {

    private View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.main_diagnose:
                    startActivity(new Intent(MainActivity.this, DiagnoseActivity.class));
                    break;
                case R.id.main_permission:
                    startActivity(new Intent(MainActivity.this, PermissionActivity.class));
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.main_permission).setOnClickListener(clickListener);
        findViewById(R.id.main_diagnose).setOnClickListener(clickListener);
    }
}
