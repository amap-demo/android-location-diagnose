package com.amap.loc.diagnose.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.amap.loc.diagnose.permission.PermissionHelper;

/**
 * 针对6.0以上系统的权限检查组件，展示当前缺少的权限并申请
 *
 * 基础的权限检查应该在第一个Activity中，也就是闪屏Activity中检查，这个Activity就是检查权限的闪屏Activity
 *
 * 闪屏Activity通常会在确认基础权限全部获取后，执行一些初始化操作，然后等待闪屏倒计时完成后切换到主界面，
 */
public class PermissionActivity extends Activity {

    private static final int REQUEST_CODE_SETTINGS = 1;

    private PermissionHelper permissionHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        permissionHelper = PermissionHelper.createForLocation(this);
        permissionHelper.setOnPermissionGranted(new PermissionHelper.OnPermissionGranted() {
            @Override
            public void onPermissionGranted() {
                Intent intent = new Intent(PermissionActivity.this, AfterPermissionActivity.class);
                startActivity(intent);
                finish();
            }
        });

        setContentView(R.layout.activity_permission);
        findViewById(R.id.activity_permission_test).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                permissionHelper.requestPermission(REQUEST_CODE_SETTINGS);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_SETTINGS) {
            permissionHelper.onRequestPermissionsResult(permissions, grantResults);
        }
    }

}
