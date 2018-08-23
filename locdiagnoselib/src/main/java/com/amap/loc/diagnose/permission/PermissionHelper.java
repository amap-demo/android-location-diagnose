package com.amap.loc.diagnose.permission;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.loc.diagnose.R;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 用于简化权限申请及检查相关系统设置的帮助类，使用Android 6.0的运行时权限检查API来检查和申请权限，以及检查设备是否开启
 * 了定位开关。
 *
 * 若设备低于6.0，没有运行时权限API，将不会进行权限检查，直接认为权限已获取。
 *
 *
 * 集成方法：
 * 1、创建PermissionHelper：
 *
 * 如果仅需要检查定位相关权限及定位开关是否开启，可以直接使用{@link #createForLocation(Activity)}创建对象；
 *
 * 如果需要检查除定位权限以外的其他权限，请使用构造函数自行构造一个PermissionHelper对象，传入要检查的权限及其提示名称的
 * Map（注意每个危险权限组内的权限只需传入一个即可，因为组内任意权限被授权后，整个组的所有权限会同时被授权，若传入多个同组
 * 权限，后传入的会被忽略），以及是否需要检查定位开关；
 *
 * 2、调用{@link #setOnPermissionGranted(OnPermissionGranted)}方法设置权限成功获取的回调；
 * 3、调用{@link #requestPermission(int)}调起权限检查；
 * 4、重写你的Activity的{@link Activity#onRequestPermissionsResult(int, String[], int[])}方法，在该方法中
 * 调用PermissionHelper对象的{@link #onRequestPermissionsResult(String[], int[])}方法。
 *
 */
public class PermissionHelper {

    public interface OnPermissionGranted {
        void onPermissionGranted();
    }

    public interface OnPermissionDenied {
        /**
         * 定位权限及开关检查失败回调，当定位相关权限获取失败，或设置中的位置开关未开启时，该回调会被调用
         *
         * 在不指定该回调的情况下，默认会在无权限时弹出引导设置权限弹框，在位置开关关闭时弹出引导打开位置开关弹框
         *
         * @param deniedPermissionHintMap 未授予的权限，当该参数存在且不为空时，有未成功授予的权限
         * @param isLocationConfigEnabled 设置中的位置开关是否打开，若checkLocationEnabled参数未指定，则不检
         *                                查位置开关，该参数一定是true，注意仅API 19及以上系统能够检查该开关
         */
        void onPermissionDenied(Map<String, String> deniedPermissionHintMap, boolean isLocationConfigEnabled);
    }

    private Activity activity;

    private OnPermissionGranted onPermissionGranted;
    private OnPermissionDenied onPermissionDenied;

    private Map<String, String> permissionHintMap;
    private boolean checkLocationEnabled = false;

    /**
     * 创建一个检查定位相关权限和定位开关的PermissionHelper对象
     * @param activity 当前Activity
     * @return 检查定位相关权限和定位开关的PermissionHelper对象
     */
    public static PermissionHelper createForLocation(Activity activity) {
        Map<String, String> permissionHintMap = new HashMap<>();
        permissionHintMap.put(Manifest.permission.ACCESS_FINE_LOCATION, "定位");
        permissionHintMap.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, "存储");
        permissionHintMap.put(Manifest.permission.READ_PHONE_STATE, "读取设备信息");
        return new PermissionHelper(activity, permissionHintMap, true);
    }

    /**
     * 构造函数，在这里指定要检查的权限，以及是否需要检查设备的定位开关是否打开
     * @param activity 当前Activity，用于调起权限申请
     * @param permissionHintMap 要申请的权限及权限名称列表，权限名称会在获取权限失败时显示在提示对话框中
     * @param checkLocationEnabled 是否需要检查设备定位开关
     */
    public PermissionHelper(Activity activity, Map<String, String> permissionHintMap, boolean checkLocationEnabled) {
        permissionHintMap = removeDuplicationPermission(permissionHintMap);
        this.activity = activity;
        this.permissionHintMap = permissionHintMap;
        this.checkLocationEnabled = checkLocationEnabled;
    }

    private boolean checkLocationConfigEnabled() {
        if (checkLocationEnabled) {
            // 考虑到大部分App可能还未target到API 28，这里使用反射调用API 28开始提供的检查定位开关的方法
            if (Build.VERSION.SDK_INT >= 28) {
                boolean isLocationEnabled = true;
                LocationManager locationManager = (LocationManager)activity.getSystemService(Context.LOCATION_SERVICE);
                try {
                    Method isLocationEnabledMethod = locationManager.getClass().getMethod("isLocationEnabled", new Class[]{});
                    isLocationEnabled = (boolean) isLocationEnabledMethod.invoke(locationManager);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return isLocationEnabled;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                int locationMode = Settings.Secure.getInt(activity.getContentResolver(),
                        Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_HIGH_ACCURACY);
                boolean isLocationEnabled = locationMode != Settings.Secure.LOCATION_MODE_OFF;
                return isLocationEnabled;
            } else {
                LocationManager locationManager = (LocationManager)activity.getSystemService(Context.LOCATION_SERVICE);
                List<String> allProviders = locationManager.getAllProviders();
                if (allProviders == null || allProviders.isEmpty()) {
                    return false;
                }
                String usableProviders = Settings.Secure.getString(activity.getContentResolver(),
                        Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
                boolean isLocationEnabled = !usableProviders.isEmpty();
                return isLocationEnabled;
            }
        } else {
            return true;
        }
    }

    // 确保permissionHintMap中不会有多个属于同一个权限组的权限
    private Map<String, String> removeDuplicationPermission(Map<String, String> permissionHintMap) {
        if (permissionHintMap == null) {
            return null;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return permissionHintMap;
        }
        List<String> permissionGroups = new LinkedList<>();
        List<String> duplicatePermissions = new LinkedList<>();
        for (String permission : permissionHintMap.keySet()) {
            String group = getPermissionGroup(permission);
            if (!permissionGroups.contains(group)) {
                permissionGroups.add(group);
            } else {
                duplicatePermissions.add(permission);
            }
        }
        for (String duplicatePermission : duplicatePermissions) {
            permissionHintMap.remove(duplicatePermission);
        }
        return permissionHintMap;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private String getPermissionGroup(String permission) {
        switch (permission) {
            case Manifest.permission.READ_CALENDAR:
            case Manifest.permission.WRITE_CALENDAR:
                return Manifest.permission_group.CALENDAR;
            case Manifest.permission.CAMERA:
                return Manifest.permission_group.CAMERA;
            case Manifest.permission.READ_CONTACTS:
            case Manifest.permission.WRITE_CONTACTS:
            case Manifest.permission.GET_ACCOUNTS:
                return Manifest.permission_group.CONTACTS;
            case Manifest.permission.ACCESS_FINE_LOCATION:
            case Manifest.permission.ACCESS_COARSE_LOCATION:
                return Manifest.permission_group.LOCATION;
            case Manifest.permission.RECORD_AUDIO:
                return Manifest.permission_group.MICROPHONE;
            case Manifest.permission.READ_PHONE_STATE:
            case Manifest.permission.CALL_PHONE:
            case Manifest.permission.READ_CALL_LOG:
            case Manifest.permission.WRITE_CALL_LOG:
            case Manifest.permission.USE_SIP:
            case Manifest.permission.PROCESS_OUTGOING_CALLS:
                return Manifest.permission_group.PHONE;
            case Manifest.permission.BODY_SENSORS:
                return Manifest.permission_group.SENSORS;
            case Manifest.permission.SEND_SMS:
            case Manifest.permission.RECEIVE_SMS:
            case Manifest.permission.READ_SMS:
            case Manifest.permission.RECEIVE_WAP_PUSH:
            case Manifest.permission.RECEIVE_MMS:
                return Manifest.permission_group.SMS;
            case Manifest.permission.READ_EXTERNAL_STORAGE:
            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                return Manifest.permission_group.STORAGE;
        }
        return "";
    }

    /**
     * 指定当权限及定位开关检查无问题的回调
     * @param onPermissionGranted 当权限及定位开关检查无问题的回调
     */
    public void setOnPermissionGranted(OnPermissionGranted onPermissionGranted) {
        this.onPermissionGranted = onPermissionGranted;
    }

    /**
     * 设置定权限或定位开关检查失败（有权限未获取或定位开关未打开）时的回调
     * @param onPermissionDenied
     */
    public void setOnPermissionDenied(OnPermissionDenied onPermissionDenied) {
        this.onPermissionDenied = onPermissionDenied;
    }

    /**
     * 发起权限检查和申请
     * 若当前系统低于android M或所有权限均已获取，就不会弹出权限申请弹框，而是会直接回调权限获取成功
     * @param requestCode 标识权限申请请求的唯一id，应该在Activity的权限结果回调中检查该id
     */
    public void requestPermission(int requestCode) {
        if (permissionHintMap == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> missingPermissions = new LinkedList<>();
            for (String permission : permissionHintMap.keySet()) {
                if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(permission);
                }
            }
            if (missingPermissions.isEmpty()) {
                if (checkLocationConfigEnabled()) {
                    if (onPermissionGranted != null) {
                        onPermissionGranted.onPermissionGranted();
                    }
                } else {
                    if (onPermissionDenied != null) {
                        onPermissionDenied.onPermissionDenied(Collections.<String, String>emptyMap(), false);
                    } else {
                        onPermissionFail(activity, new String[]{}, false);
                    }
                }
            } else {
                activity.requestPermissions(missingPermissions.toArray(new String[0]), requestCode);
            }
        } else {
            if (checkLocationConfigEnabled()) {
                if (onPermissionGranted != null) {
                    onPermissionGranted.onPermissionGranted();
                }
            } else {
                if (onPermissionDenied != null) {
                    onPermissionDenied.onPermissionDenied(Collections.<String, String>emptyMap(), false);
                } else {
                    onPermissionFail(activity, new String[]{}, false);
                }
            }
        }
    }

    /**
     * 发起权限申请后，用于接收权限结果回调
     *
     * 如果权限申请全部通过，会回调权限获取成功
     *
     * 如果有任何权限没有申请通过，默认情况下会弹出对话框提示哪些权限没有获取，并引导用户去设置页授予权限，若想自己处理
     * 权限申请失败的情况，可以使用{@link #setOnPermissionDenied(OnPermissionDenied)}方法设置权限获取失败回
     * 调，设置了该回调后，权限申请失败时将不再弹出默认对话框
     *
     * @param permissions Activity的onRequestPermissionsResult方法回调的参数
     * @param grantResults Activity的onRequestPermissionsResult方法回调的参数
     */
    public void onRequestPermissionsResult(String[] permissions, int[] grantResults) {
        if (permissionHintMap == null) {
            return;
        }
        List<String> failPermissions = new LinkedList<>();
        for (int i = 0; i < grantResults.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                failPermissions.add(permissions[i]);
            }
        }
        boolean permissionOk = failPermissions.isEmpty();
        boolean locationConfigOk = checkLocationConfigEnabled();

        if (permissionOk && locationConfigOk) {
            onPermissionGranted();
        } else {
            if (onPermissionDenied != null) {
                Map<String, String> deniedPermissionHintMap = new HashMap<>();
                for (String per : failPermissions) {
                    deniedPermissionHintMap.put(per, permissionHintMap.get(per));
                }
                onPermissionDenied.onPermissionDenied(deniedPermissionHintMap, locationConfigOk);
            } else {
                String[] fails = new String[failPermissions.size()];
                for (int i = 0; i < failPermissions.size(); i++) {
                    fails[i] = permissionHintMap.get(failPermissions.get(i));
                }
                onPermissionFail(activity, fails, locationConfigOk);
            }
        }
    }

    private void onPermissionGranted() {
        if (onPermissionGranted != null) {
            onPermissionGranted.onPermissionGranted();
        }
    }

    private void onPermissionFail(final Activity activity, String[] fails, boolean isLocationConfigEnabled) {
        final Dialog dialog = new Dialog(activity, R.style.dialog);
        WindowManager windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        Point size = new Point();
        DisplayMetrics dm = new DisplayMetrics();
        Display display = windowManager.getDefaultDisplay();
        display.getMetrics(dm);
        size.x = display.getWidth();
        size.y = display.getHeight();
        View v = LayoutInflater.from(activity).inflate(R.layout.view_permission_fail, null, false);

        String desc;
        View.OnClickListener okClickListener;
        if (fails.length > 0) {
            StringBuilder failStrBuilder = new StringBuilder();
            for (String s : fails) {
                failStrBuilder.append(s).append("、");
            }
            failStrBuilder.deleteCharAt(failStrBuilder.length() - 1);
            String failPermissionStr = failStrBuilder.toString();
            desc = "需要" + failPermissionStr + "权限";
            okClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                    intent.setData(uri);
                    activity.startActivity(intent);
                    dialog.dismiss();
                }
            };
        } else {
            desc = "需要开启定位开关";
            okClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent myIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        activity.startActivity(myIntent);
                    } catch (ActivityNotFoundException e) {
                        e.printStackTrace();
                        Toast.makeText(activity, "无法开启定位设置页面", Toast.LENGTH_SHORT).show();
                    }
                    dialog.dismiss();
                }
            };
        }

        ((TextView)v.findViewById(R.id.view_permission_desc)).setText(desc);
        v.findViewById(R.id.view_permission_config).setOnClickListener(okClickListener);
        v.findViewById(R.id.view_permission_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.setContentView(v);
        WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.width = (int) (size.x - (dm.density * 36 + 0.5f));
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.gravity = Gravity.CENTER;
        dialog.show();
    }

}
