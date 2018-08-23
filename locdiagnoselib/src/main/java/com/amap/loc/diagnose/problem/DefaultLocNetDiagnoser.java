package com.amap.loc.diagnose.problem;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.amap.loc.diagnose.R;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/**
 * 为定位服务网络，分别检查能否连接到外部网络和定位服务
 */
public class DefaultLocNetDiagnoser implements DiagnoseView.Diagnoser {

    private static final String TAG = "DefLocNetDia";
    private static final boolean DEBUGFLAG = false;

    private static final int MSG_CONNECT_OUTER_FAIL = 1;
    private static final int MSG_CONNECT_AMAP_FAIL = 2;
    private static final int MSG_CONNECT_SLOW = 3;
    private static final int MSG_CONNECT_OK = 4;

    private Handler mainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CONNECT_OUTER_FAIL:
                    onResult(DiagnoseResultItem.checkError("网络异常，无法正常访问网络"));
                    break;
                case MSG_CONNECT_AMAP_FAIL:
                    onResult(DiagnoseResultItem.checkError("网络异常，无法正常连接到定位服务"));
                    break;
                case MSG_CONNECT_SLOW:
                    onResult(DiagnoseResultItem.checkError("网络异常，延迟过高"));
                    break;
                case MSG_CONNECT_OK:
                    onResult(DiagnoseResultItem.checkOk());
                    break;
            }
        }
    };

    private DiagnoseResultItem resultItem;

    private CheckNetworkTask checkNetworkTask;
    private DiagnoseView.DiagnoseFinishCallback finishCallback;

    @Override
    public void prepare(Context context) {
        checkNetworkTask = new CheckNetworkTask(mainHandler);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            checkNetworkTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            checkNetworkTask.execute();
        }
    }

    @Override
    public void diagnose(Context context, final DiagnoseView.DiagnoseFinishCallback finishCallback) {
        if (finishCallback == null) {
            return;
        }
        this.finishCallback = finishCallback;
        if (resultItem != null) {
            this.finishCallback.onDiagnoseFinish(resultItem);
        }
    }

    private void onResult(DiagnoseResultItem diagnoseResultItem) {
        resultItem = diagnoseResultItem;
        if (finishCallback != null) {
            finishCallback.onDiagnoseFinish(resultItem);
        }
    }

    @Override
    public int getIcon() {
        return R.drawable.network;
    }

    @Override
    public String getTitle() {
        return "网络连接";
    }

    private static void log(String log) {
        if (DEBUGFLAG) {
            Log.w(TAG, log);
        }
    }


    private static class CheckNetworkTask extends AsyncTask<Void, Void, Void> {

        private Handler mainHandler;

        public CheckNetworkTask(Handler mainHandler) {
            this.mainHandler = mainHandler;
        }

        @Override
        protected Void doInBackground(Void... voids) {

            boolean slowNetwork = false;
            long startTime = System.currentTimeMillis();
            boolean connectOuterOk = checkHttpConnection("https://www.taobao.com", true);
            if (!connectOuterOk) {
                mainHandler.sendEmptyMessage(MSG_CONNECT_OUTER_FAIL);
                return null;
            }

            long endTime = System.currentTimeMillis();
            if (endTime - startTime > 5 * 1000) {
                slowNetwork = true;
            }

            if (slowNetwork) {
                mainHandler.sendEmptyMessage(MSG_CONNECT_SLOW);
                return null;
            } else {
                mainHandler.sendEmptyMessage(MSG_CONNECT_OK);
                return null;
            }
        }

        private boolean checkHttpConnection(String urlStr, boolean ignoreServerError) {
            boolean networkOk = true;
            URL url = null;
            try {
                url = new URL(urlStr);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }

            InputStream stream = null;
            HttpURLConnection connection = null;
            String result = null;
            try {
                connection = (HttpURLConnection) url.openConnection();
                connection.setReadTimeout(10 * 1000);
                connection.setConnectTimeout(10 * 1000);
                connection.setRequestMethod("GET");
                connection.setDoInput(true);
                connection.setInstanceFollowRedirects(true);
                connection.setUseCaches(false);
                connection.connect();
                log("connected");
                int responseCode = connection.getResponseCode();
                // is client error?
                if (responseCode >= 400 && responseCode < 500) {
                    throw new IOException("HTTP error code: " + responseCode);
                }
                // is server error?
                if (!ignoreServerError && responseCode >= 500 && responseCode < 600) {
                    throw new IOException("HTTP error code: " + responseCode);
                }
                stream = connection.getInputStream();
                log("input stream");
                if (stream != null) {
                    // can read succeed?
                    result = readStream(stream, 500);
                    log("read, result: >>>" + result);
                }
            } catch (Exception e) {
                e.printStackTrace();
                networkOk = false;
            } finally {
                closeSilently(stream);
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return networkOk;
        }

        /**
         * Converts the contents of an InputStream to a String.
         */
        private String readStream(InputStream stream, int maxReadSize)
                throws IOException, UnsupportedEncodingException {
            Reader reader = null;
            reader = new InputStreamReader(stream, "UTF-8");
            char[] rawBuffer = new char[maxReadSize];
            int readSize;
            StringBuffer buffer = new StringBuffer();
            while (((readSize = reader.read(rawBuffer)) != -1) && maxReadSize > 0) {
                if (readSize > maxReadSize) {
                    readSize = maxReadSize;
                }
                buffer.append(rawBuffer, 0, readSize);
                maxReadSize -= readSize;
            }
            return buffer.toString();
        }

        private void closeSilently(Closeable closeable) {
            if (closeable == null) {
                return;
            }
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}
