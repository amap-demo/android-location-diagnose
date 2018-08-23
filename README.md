# android-loc-diagnose

高德地图定位检测组件。

1、帮助开发者检查定位权限及定位开关，并在缺少运行时权限或开关时给出引导；

2、提供定位异常检测组件，如果发现无法成功定位，可以使用该组件检查定位异常原因。


## 前述

- 组件不依赖高德定位sdk，但旨在和定位sdk配合使用，权限检查及异常检测都针对高德定位sdk进行。
- [高德官网申请key](https://lbs.amap.com/api/android-location-sdk/guide/create-project/get-key)
- [高德定位sdk介绍及开发指南](https://lbs.amap.com/api/android-location-sdk/locationsummary/)


## 使用方法

### 定位权限申请及定位开关检测

以下代码摘自Demo的PermissionActivity，作用是调起定位权限及定位开关检查，如果有权限且定位开关开启就跳转到新页面，如果无权限或定位开关关闭则给出引导对话框：
```
private static final int REQUEST_CODE_SETTINGS = 1;
private PermissionHelper permissionHelper;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // 使用createForLocation创建一个用于检查定位sdk运行时权限及定位开关的PermissionHelper对象
    // 如果需要
    permissionHelper = PermissionHelper.createForLocation(this);
    permissionHelper.setOnPermissionGranted(new PermissionHelper.OnPermissionGranted() {
        @Override
        public void onPermissionGranted() {
            Intent intent = new Intent(PermissionActivity.this, AfterPermissionActivity.class);
            startActivity(intent);
            finish();
        }
    });
    // 设置UI
    // ...

    // 调起权限申请
    permissionHelper.requestPermission(REQUEST_CODE_SETTINGS);
}

@Override
public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_CODE_SETTINGS) {
    	// 将权限申请结果告知PermissionHelper，继续处理
        permissionHelper.onRequestPermissionsResult(permissions, grantResults);
    }
}
```

### 定位异常检测

定位异常检测组件是个现成的Activity，只需在项目中的Manifest文件中声明DiagnoseActivity，即可调起进行定位异常检测。

在AndroidManifest.xml中声明Activity：
```
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="...">
    ...
    <application
    	...>

        <activity android:name="com.amap.loc.diagnose.problem.DiagnoseActivity"
            android:screenOrientation="portrait"/>

    </application>
</manifest>
```

调起定位检测Activity：
```
startActivity(new Intent(MainActivity.this, DiagnoseActivity.class));
```