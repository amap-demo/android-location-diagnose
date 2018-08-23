package com.amap.loc.diagnose.problem;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Process;

import com.amap.loc.diagnose.R;

import java.util.LinkedList;
import java.util.List;

public class DefaultLocPermissionDiagnoser implements DiagnoseView.Diagnoser {

    @Override
    public void prepare(Context context) {

    }

    @Override
    public void diagnose(Context context, DiagnoseView.DiagnoseFinishCallback finishCallback) {
        diagnosePermission(context, finishCallback);
    }

    @Override
    public int getIcon() {
        return R.drawable.permissions;
    }

    @Override
    public String getTitle() {
        return "权限检查";
    }

    // 检查权限
    private void diagnosePermission(Context context, final DiagnoseView.DiagnoseFinishCallback diagnoseFinishCallback) {
        if (diagnoseFinishCallback == null) {
            return;
        }
        // 权限：
        boolean internetState = isSelfPermissionGranted(context, Manifest.permission.INTERNET);
        boolean accessNetworkState = isSelfPermissionGranted(context, Manifest.permission.ACCESS_NETWORK_STATE);
        boolean wifiState = isSelfPermissionGranted(context, Manifest.permission.ACCESS_WIFI_STATE) &&
                isSelfPermissionGranted(context, Manifest.permission.CHANGE_WIFI_STATE);
        boolean blueToothState = isSelfPermissionGranted(context, Manifest.permission.BLUETOOTH) &&
                isSelfPermissionGranted(context, Manifest.permission.BLUETOOTH_ADMIN);

        // 涉及危险权限组
        boolean locationState = isSelfPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) &&
                isSelfPermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION) &&
                isSelfPermissionGranted(context, Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS);
        boolean externalStroateState = isSelfPermissionGranted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            externalStroateState = externalStroateState &&
                    isSelfPermissionGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE);
        }
//        boolean phoneState = isSelfPermissionGranted(context, Manifest.permission.READ_PHONE_STATE);
        final DiagnoseResultItem permissionItem = new DiagnoseResultItem();
        permissionItem.errorHint = "以下权限缺失可能会导致无法定位或影响定位准确性，请确保没有禁用这些权限";
        permissionItem.subItems = new LinkedList<>();
        permissionItem.subItems.add(new DiagnoseResultItem.SubItem(internetState,
                "缺少访问网络权限，会导致无法访问网络, 无法进行网络定位"));
        permissionItem.subItems.add(new DiagnoseResultItem.SubItem(accessNetworkState,
                "缺少检查网络连接信息权限，会导致无法判断当前网络连接状态，影响定位"));
        permissionItem.subItems.add(new DiagnoseResultItem.SubItem(wifiState,
                "缺少获取Wifi信息权限，会导致无法获取附近wifi信息，影响定位准确性"));
        permissionItem.subItems.add(new DiagnoseResultItem.SubItem(blueToothState,
                "缺少获取蓝牙信息权限，会导致无法获取附近蓝牙信息，影响室内定位准确性"));
        permissionItem.subItems.add(new DiagnoseResultItem.SubItem(locationState,
                "缺少定位权限权限，会导致无法进行GPS定位，影响定位准确性"));
        permissionItem.subItems.add(new DiagnoseResultItem.SubItem(externalStroateState,
                "缺少读写外部存储权限权限，会导致无法读写sd卡，影响定位sdk缓存数据的存取"));
//        permissionItem.subItems.add(new DiagnoseResultItem.SubItem(phoneState,
//                "缺少读取设备信息权限"));
        permissionItem.checkResult = isAllSubItemsOk(permissionItem.subItems) ?
                DiagnoseResultItem.CheckResult.Ok : DiagnoseResultItem.CheckResult.Error;
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                diagnoseFinishCallback.onDiagnoseFinish(permissionItem);
            }
        }, 1000);
    }

    private boolean isSelfPermissionGranted(Context context, String permission) {
        return checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private static int checkSelfPermission(Context context, String permission) {
        return context.checkPermission(permission, android.os.Process.myPid(), Process.myUid());
    }

    private boolean isAllSubItemsOk(List<DiagnoseResultItem.SubItem> subItems) {
        for (DiagnoseResultItem.SubItem subItem : subItems) {
            if (subItem.checkResult != DiagnoseResultItem.CheckResult.Ok) {
                return false;
            }
        }
        return true;
    }

}
