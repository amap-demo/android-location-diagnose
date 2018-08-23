package com.amap.loc.diagnose.problem;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.amap.loc.diagnose.BuildConfig;
import com.amap.loc.diagnose.R;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * 定位相关检测
 * GPS:开关、星数、能否GPS定位
 * WIFI：开关、列表
 * 基站：主基站、周边基站、基站列表、sim卡状态
 *
 * 网络检测不在这里做
 * 网络：通不通、速度(指延时，从发送到返回的时间，不是上下行速度)、ping域名
 */
public class DefaultLocationDiagnoser implements DiagnoseView.Diagnoser {


//  异常整理
//
//  统一预先处理无定位权限情况：
//  gps: 无定位权限，GPS信息无法获取
//  ap: 无定位权限，无法进行基站定位
//  wifi: 无定位权限，无法进行wifi定位
//
//  gps:
//  1. gps provider不可用					无法进行GPS定位，GPS定位开关关闭
//  2. gps定位30s未回调					无法进行GPS定位，GPS定位超时，卫星数：XXX【警告，不计入错误数量，但需要显示】
//  3. gps定位正常回调						【正常，不显示】
//
//  ap:
//  1. 飞行模式开启										无法进行基站定位，飞行模式开启
//  2. 基站信息由于任何原因无法获取，且SIM卡状态不是READY		无法进行基站定位，sim卡异常，（后面附带原始原因，<未获取到基站信息/系统错误>）
//  3. 获取到基站信息为0个 								    无法进行基站定位，未获取到基站信息
//  4. Telephony获取基站信息调用抛异常						无法进行基站定位，系统错误
//  5. 基站信息正常获取，有至少一个							【正常，不显示】
//
//  wifi：
//  1. wifi无热点							无法通过wifi定位，无wifi热点
//  2. wifi仅1个热点且无基站				无法通过wifi定位，wifi热点过少
//  3. wifi被关闭							无法通过wifi定位，wifi关闭
//  4. wifi相关调用抛出异常					无法通过wifi定位，系统错误
//  5. wifi热点扫描成功，热点多于1个			【正常，不显示】


    private static final boolean DEBUGFLAG = false;
    private static final String TAG = "DefaultLocDia";

    private static final int MSG_GPS_TIMEOUT = 1;
    private static final int MSG_WIFI_SCAN_TIMEOUT = 2;
    private static final int MSG_DIAGNOSE_FINISH = 100;

    private DiagnoseResultItem result;
    private DiagnoseResultItem.SubItem gpsItem;
    private DiagnoseResultItem.SubItem wifiItem;
    private DiagnoseResultItem.SubItem apItem;

    private LocationManager locationManager;
    private GpsStatus gpsStatus;
    private int satellitesNum;
    private WifiManager wifiManager;

    private boolean addedGpsListeners = false;
    private DiagnoseView.DiagnoseFinishCallback diagnoseFinishCallback;

    private Handler mainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_GPS_TIMEOUT:
                    onGpsResult(DiagnoseResultItem.CheckResult.Warning, "无法进行GPS定位，" +
                            "GPS定位超时，卫星数：" + satellitesNum + ", 如果您在室内，请尝试到室外重新检测");
                    break;
                case MSG_WIFI_SCAN_TIMEOUT:
                    checkWifiScanResult();
                    break;
                case MSG_DIAGNOSE_FINISH:
                    clean();
                    if (diagnoseFinishCallback != null) {
                        diagnoseFinishCallback.onDiagnoseFinish(result);
                    }
                    break;
            }
        }
    };

    private LocationListener gpsListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            mainHandler.removeMessages(MSG_GPS_TIMEOUT);
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    onGpsResult(DiagnoseResultItem.CheckResult.Ok, "gps定位正常，卫星数：" + satellitesNum);
                }
            });
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            if (status == LocationProvider.OUT_OF_SERVICE) {
                if(DEBUGFLAG) {
                    Log.w(TAG, "GpsLocation | gps is outOfService  ");
                }
                satellitesNum = 0;
            }
        }

        @Override
        public void onProviderEnabled(String provider) {
            if(DEBUGFLAG) {
                if (LocationManager.GPS_PROVIDER.equalsIgnoreCase(provider)) {
                    Log.w(TAG, "GpsLocation | onProviderEnabled  ");
                }
            }
        }

        @Override
        public void onProviderDisabled(String provider) {
            if (LocationManager.GPS_PROVIDER.equalsIgnoreCase(provider)) {
                Log.w(TAG, "GpsLocation | onProviderDisabled  ");
                mainHandler.removeMessages(MSG_GPS_TIMEOUT);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onGpsResult(DiagnoseResultItem.CheckResult.Error, "无法进行GPS定位，GPS定位开关关闭");
                    }
                });
            }
        }
    };

    private GpsStatus.Listener statusListener = new GpsStatus.Listener() {
        @SuppressLint("MissingPermission")
        @Override
        public void onGpsStatusChanged(int event) {
            if(null == locationManager){
                return;
            }
            gpsStatus = locationManager.getGpsStatus(gpsStatus);
            switch (event) {
                case GpsStatus.GPS_EVENT_STARTED:
                    if(DEBUGFLAG) {
                        Log.w(TAG, "GpsLocation | status started ");
                    }
                    break;
                case GpsStatus.GPS_EVENT_STOPPED:
                    if(DEBUGFLAG) {
                        Log.w(TAG, "GpsLocation | status stopped ");
                    }
                    satellitesNum = 0;
                    break;
                case GpsStatus.GPS_EVENT_FIRST_FIX:
                    if(DEBUGFLAG) {
                        Log.w(TAG, "GpsLocation | first fix ");
                    }
                    break;
                // 周期的报告卫星状态
                case GpsStatus.GPS_EVENT_SATELLITE_STATUS :
                    Iterable<GpsSatellite> allSatellites;
                    int numOfSatellites = 0;
                    try {
                        if (null != gpsStatus) {
                            allSatellites = gpsStatus.getSatellites();
                            if (null != allSatellites) {
                                Iterator<GpsSatellite> iterator = allSatellites.iterator();
                                int maxSatellites = gpsStatus.getMaxSatellites();
                                while (iterator.hasNext() && numOfSatellites < maxSatellites) {
                                    GpsSatellite satellite = iterator.next();
                                    if (satellite.usedInFix()) {
                                        numOfSatellites++;
                                    }
                                }
                            }
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                    satellitesNum = numOfSatellites;
                    break;
                default :
                    break;
            }
        }
    };


    private static boolean airPlaneModeOn(Context ctx) {
        if (ctx == null) {
            return false;
        }
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                String strSystem = "android.provider.Settings$System";
                return isAirPlaneOn(ctx, strSystem);
            } else {
                String strGlobal = "android.provider.Settings$Global";
                return isAirPlaneOn(ctx, strGlobal);
            }
        } catch (Throwable e) {
        }
        if (DEBUGFLAG) {
            Log.w(TAG, "AIRPLANE_MODE e");
        }
        return false;
    }

    private static boolean isAirPlaneOn(Context ctx, String className)
            throws Throwable {
        ContentResolver cr = ctx.getContentResolver();
        Object obj = null;
        Class<?> cls = Class.forName(className);
        Field field = cls.getField("AIRPLANE_MODE_ON");
        field.setAccessible(true);
        obj = field.get(cls);
        String str = ((String) obj).toString();
        Object[] oa = new Object[2];
        oa[0] = cr;
        oa[1] = str;
        Class<?>[] ca = new Class[2];
        ca[0] = ContentResolver.class;
        ca[1] = String.class;
        Method method = cls.getDeclaredMethod("getInt", ca);
        method.setAccessible(true);
        obj = method.invoke(null, oa);
        return ((Integer) obj).intValue() == 1;
    }

    @Override
    public void prepare(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED ||
                        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                                != PackageManager.PERMISSION_GRANTED)) {
            onGpsResult(DiagnoseResultItem.CheckResult.Error, "无法进行GPS定位，无定位权限");
            onApResult(false, "无法进行基站定位，无定位权限");
            onWifiResult(false, "无法进行wifi定位，无定位权限");
            return;
        }

        // gps
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 0f,
                    gpsListener, mainHandler.getLooper());
            locationManager.addGpsStatusListener(statusListener);
            addedGpsListeners = true;
            mainHandler.sendEmptyMessageDelayed(MSG_GPS_TIMEOUT, 20 * 1000);
        } else {
            onGpsResult(DiagnoseResultItem.CheckResult.Error, "无法进行GPS定位，系统错误");
        }

        // ap
        if (airPlaneModeOn(context)) {
            onApResult(false, "飞行模式开启，无法获取基站信息进行基站定位");
        } else {
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager!= null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    try {
                        List<CellInfo> cellInfos = telephonyManager.getAllCellInfo();
                        if (cellInfos != null && cellInfos.size() > 0) {
                            onApResult(true, "基站信息正常，扫描到基站数量：" + cellInfos);
                        } else {
                            onApFail(telephonyManager, "未获取到基站信息");
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                        onApFail(telephonyManager, "系统错误");
                    }
                } else {
                    try {
                        CellLocation cellInfo = telephonyManager.getCellLocation();
                        if (cellInfo != null) {
                            onApResult(true, "基站信息正常，扫描到基站数量：" + 1);
                        } else {
                            onApFail(telephonyManager, "未获取到基站信息");
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                        onApFail(telephonyManager, "系统错误");
                    }
                }
            } else {
                onApResult(false, "系统错误，无法获取基站信息");
            }
        }

        // wifi
        // wifi检查时必须确保基站检查已经完成，因为有个逻辑是"如果没有基站且仅有1个wifi热点，就不定位"，需要在只有1个
        // wifi热点时，结合基站检查结果来决定wifi热点是否"过少"
        wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            int wifiState = wifiManager.getWifiState();
            switch (wifiState) {
                case WifiManager.WIFI_STATE_DISABLED:
                case WifiManager.WIFI_STATE_DISABLING:
                case WifiManager.WIFI_STATE_UNKNOWN:
                    onWifiResult(false, "无法进行wifi定位，wifi关闭");
                    return;
                default:
                    break;
            }
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            wifiManager.startScan();
            // 认为wifi扫描最多耗时3s，等待3s后检查wifi热点，此时的热点信息应该是实时的
            mainHandler.sendEmptyMessageDelayed(MSG_WIFI_SCAN_TIMEOUT, 3 * 1000);
        } else {
            onWifiResult(false, "无法进行wifi定位，系统错误");
        }
    }

    @Override
    public void diagnose(Context context, final DiagnoseView.DiagnoseFinishCallback finishCallback) {
        if (finishCallback == null) {
            return;
        }
        this.diagnoseFinishCallback = finishCallback;
        if (result != null) {
            mainHandler.sendEmptyMessage(MSG_DIAGNOSE_FINISH);
        }
    }

    private void checkWifiScanResult() {
        List<ScanResult> scanResults;
        try {
            scanResults = wifiManager.getScanResults();
        } catch (Throwable e) {
            e.printStackTrace();
            onWifiResult(false, "无法进行wifi定位，系统错误");
            return;
        }
        // ap信息一定在wifi之前获取，不可能走到else分支
        if (apItem != null) {
            if (scanResults == null || scanResults.isEmpty()) {
                onWifiResult(false, "无法进行wifi定位，无wifi热点");
            } else if ((apItem.checkResult != DiagnoseResultItem.CheckResult.Ok && scanResults.size() <= 1)) {
                onWifiResult(false, "无法进行wifi定位，wifi热点过少");
            } else {
                onWifiResult(true, "wifi定位正常");
            }
        } else {
            if (BuildConfig.DEBUG && DEBUGFLAG) {
                throw new AssertionError();
            }
            onWifiResult(false, "无法进行wifi定位，系统错误");
        }

    }

    private void clean() {
        if (locationManager != null && addedGpsListeners) {
            locationManager.removeUpdates(gpsListener);
            locationManager.removeGpsStatusListener(statusListener);
        }
    }

    /**
     * 有新的检查结果，检查是否所有检查都已经完成，如果完成就出结果，否则继续等待
     */
    private void checkResult() {
        if (wifiItem != null && apItem != null && gpsItem != null) {
            result = new DiagnoseResultItem();
            if (wifiItem.checkResult == DiagnoseResultItem.CheckResult.Error ||
                    apItem.checkResult == DiagnoseResultItem.CheckResult.Error ||
                    gpsItem.checkResult == DiagnoseResultItem.CheckResult.Error) {
                result.checkResult = DiagnoseResultItem.CheckResult.Error;
                result.errorHint = "可能无法定位";
            } else if (wifiItem.checkResult == DiagnoseResultItem.CheckResult.Warning ||
                    apItem.checkResult == DiagnoseResultItem.CheckResult.Warning ||
                    gpsItem.checkResult == DiagnoseResultItem.CheckResult.Warning) {
                result.checkResult = DiagnoseResultItem.CheckResult.Warning;
                result.errorHint = "警告";
            } else {
                result.checkResult = DiagnoseResultItem.CheckResult.Ok;
            }
            result.subItems = new LinkedList<>();
            result.subItems.add(gpsItem);
            result.subItems.add(wifiItem);
            result.subItems.add(apItem);
            mainHandler.sendEmptyMessage(MSG_DIAGNOSE_FINISH);
        } else {
            // not finished, wait...
            if (DEBUGFLAG) {
                Log.w(TAG, "check, gps: " + gpsItem +
                        ", wifi: " + wifiItem +
                        ", ap: " + apItem);
            }
        }
    }

    private void onGpsResult(DiagnoseResultItem.CheckResult checkResult, String desc) {
        if (gpsItem != null && DEBUGFLAG) {
            throw new AssertionError("gps: " + gpsItem);
        }
        if (gpsItem != null) {
            return;
        }
        if (locationManager != null && addedGpsListeners) {
            locationManager.removeGpsStatusListener(statusListener);
            locationManager.removeUpdates(gpsListener);
            addedGpsListeners = false;
        }
        gpsItem = new DiagnoseResultItem.SubItem(checkResult, desc);
        checkResult();
    }

    private void onWifiResult(boolean isOk, String desc) {
        if (wifiItem != null && DEBUGFLAG) {
            Log.e(TAG, "wifi: " + wifiItem + ", called again: isOk: " + isOk + ", desc: " + desc);
        }
        if (wifiItem != null) {
            return;
        }
        wifiItem = new DiagnoseResultItem.SubItem(isOk, desc);
        checkResult();
    }

    private void onApFail(TelephonyManager telephonyManager, String desc) {
        if (telephonyManager.getSimState() != TelephonyManager.SIM_STATE_READY) {
            onApResult(false, "无法进行基站定位，sim卡异常，" + desc);
        } else {
            onApResult(false, "无法进行基站定位，" + desc);
        }
    }

    private void onApResult(boolean isOk, String desc) {
        if (apItem != null && DEBUGFLAG) {
            throw new AssertionError("ap: " + apItem);
        }
        if (apItem != null) {
            return;
        }
        apItem = new DiagnoseResultItem.SubItem(isOk, desc);
        checkResult();
    }

    @Override
    public int getIcon() {
        return R.drawable.location;
    }

    @Override
    public String getTitle() {
        return "定位检查";
    }
}
