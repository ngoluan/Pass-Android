/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package luan.com.flippit;

import android.app.ActivityManager;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import luan.com.flippit.utilities.Callback;
import luan.com.flippit.utilities.CopyClipboard;
import luan.com.flippit.utilities.CopyService;
import luan.com.flippit.utilities.FileCallback;
import luan.com.flippit.utilities.HistoryInterface;

/**
 * This {@code IntentService} does the actual handling of the GCM message.
 * {@code GcmBroadcastReceiver} (a {@code WakefulBroadcastReceiver}) holds a
 * partial wake lock for this service while the service does its work. When the
 * service is finished, it calls {@code completeWakefulIntent()} to release the
 * wake lock.
 */
public class GcmIntentService extends IntentService {
    public static final int NOTIFICATION_ID = 1;
    static public NotificationManager mNotificationManager;
    static Context mContext = null;
    static NotificationCompat.Builder mBuilder = null;
    static SharedPreferences mPrefs = null;
    static ActivityManager activityManager;

    public GcmIntentService() {
        super("GcmIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        mContext = this;
        mPrefs = getSharedPreferences(mContext.getPackageName(),
                Context.MODE_PRIVATE);
        mNotificationManager = (NotificationManager)
                this.getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new NotificationCompat.Builder(this);
        activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);

        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);

        String message = intent.getStringExtra("message");
        String messageType = gcm.getMessageType(intent);

        if (!extras.isEmpty()) {
            if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
                //sendNotification("Send error: " + extras.toString());
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
                //sendNotification("Deleted messages on server: " + extras.toString());
                // If it's a regular GCM message, do some work.
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
                manageNotification(message);
            }
        }
        GcmBroadcastReceiver.completeWakefulIntent(intent);
    }

    private void manageNotification(String content) {
        JSONObject data = null;
        String msg = null;
        String fileName = null;
        String msgId = null;
        try {
            data = new JSONObject(content);

            Log.i(MyActivity.TAG, getClass().getName() + ": " + "Received full message: " + data);

            msg = data.getString("message");
            msg = java.net.URLDecoder.decode(msg, "UTF-8");

            fileName = data.getString("fileName");

            msgId = data.getString("messageId");

            String type = GeneralUtilities.typeOfMessage(fileName);

            Log.i(MyActivity.TAG, getClass().getName() + ": " + "Type:" + type);

            if (type.contains("image") || type.equals("file")) {
                fileNotification(fileName, msg, msgId);
            } else {
                String extraUrl = searchForExtra(msg);

                if (!extraUrl.equals("")) {
                    String extraType = extraMIMEOnline(extraUrl);
                    extraDialog(msg, extraUrl, extraType);
                } else if (msg.contains("newDevice") == true) {
                    newDevice(msg);
                } else {
                    textNotification(msg);
                }

            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private void extraDialog(String msg, String extraFilename, String extraType) {
        Log.i(MyActivity.TAG, getClass().getName() + ": " + "Extra notification.");

        String url = msg;
        Intent intentWeb = new Intent(Intent.ACTION_VIEW);
        intentWeb.setData(Uri.parse(url));
        PendingIntent pendingWeb = PendingIntent.getActivity(mContext, 0, intentWeb, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent intentCopy = new Intent(mContext, CopyService.class);
        intentCopy.putExtra("msg", msg);
        PendingIntent pendingCopy = PendingIntent.getService(mContext, 0, intentCopy, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder mBuilder = GeneralUtilities.createNotificationBuilder(mContext);
        mBuilder.setStyle(new NotificationCompat.BigTextStyle()
                .bigText(msg))
                .setTicker("Rich message")
                .addAction(R.drawable.send_white, "Open", pendingWeb)
                .addAction(R.drawable.copy_white, "Copy", pendingCopy)
                .setContentText("Rich message");
        mNotificationManager.cancel(1);
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
        UpdateHistory updateHistory = new UpdateHistory();
        updateHistory.updateHistory(mContext);
    }

    private void textNotification(String msg) {
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MyActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

        Log.i(MyActivity.TAG, getClass().getName() + ": " + "Text message.");

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.action_icon)
                        .setContentTitle("FlippIt")
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(msg))
                        .setTicker("Copied to clipboard")
                        .setContentText(msg);

        mBuilder.setContentIntent(contentIntent);
        mNotificationManager.cancel(1);
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
        new CopyClipboard(msg, mContext);
        UpdateHistory updateHistory = new UpdateHistory();
        updateHistory.updateHistory(mContext);
    }

    private void fileNotification(final String fileName, String msg, String msgId) {
        Log.i(MyActivity.TAG, getClass().getName() + ": " + "File transfer.");

        mBuilder.setContentTitle("FlippIt")
                .setContentText("Downloading...")
                .setSmallIcon(R.drawable.action_icon);
        String email = mPrefs.getString("email", "");

        String url = GeneralUtilities.SERVER_PATH + "uploads/" + email + "/" + fileName;

        Bundle extras = new Bundle();
        extras.putString("email", email);
        extras.putString("filename", fileName);
        extras.putString("msg", msg);
        extras.putString("msgId", msgId);

        DownloadFiles downloadFiles = new DownloadFiles(mContext);
        Callback fileCallback = new FileCallback(mContext);
        downloadFiles.getFileFromServer_v2(url, extras, fileCallback);
    }

    private String searchForExtra(String message) {
        int start = -1;
        int end = 0;
        String urlStr = "";
        if (message.indexOf("http") > -1) {
            start = message.indexOf("http");
        } else if (message.indexOf("https") > -1) {
            start = message.indexOf("https");
        } else if (message.indexOf("www") > -1) {
            start = message.indexOf("www");
        }
        Log.i(MyActivity.TAG, getClass().getName() + ": " + " start : " + start);
        if (start > -1) {
            end = message.indexOf(" ", start);
            if (end == -1) {
                end = message.length();
            }
            urlStr = message.substring(start, end);
            Log.i(MyActivity.TAG, getClass().getName() + ": " + " Extra content in message detected: " + urlStr);
        }
        return urlStr;
    }

    private String extraMIMEOnline(String urlStr) {
        String contentType = "";
        try {
            URL url = new URL(urlStr);
            HttpURLConnection connection = null;
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.connect();
            contentType = connection.getContentType();
            Log.i(MyActivity.TAG, getClass().getName() + ": " + "Extra content type: " + contentType);
            if (contentType != null) {
                contentType = "web";
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return contentType;
    }

    private void newDevice(String msg) {
        String email = mPrefs.getString("email", "");
        Log.i(MyActivity.TAG, mContext.getClass().getName() + ": " + "New device detected");

        Callback deviceCallback = new IntentDeviceCallback(mContext);
        GeneralUtilities.getDevices(email, deviceCallback);
    }

    public class IntentDeviceCallback extends HistoryInterface {

        public IntentDeviceCallback(Context context) {
            super(context);
        }

        @Override
        public void callBackProgress(int progress) {

        }

        @Override
        public void callBackFinish(Bundle extras) {
            String msg = extras.getString("msg");
            String regID = mPrefs.getString("registration_id", "");
            ArrayList<DeviceItem> deviceItems = new ArrayList<DeviceItem>();
            try {
                JSONArray devices = new JSONArray(msg);
                deviceItems.clear();
                for (int i = 0; i < devices.length(); i++) {
                    JSONObject device = devices.getJSONObject(i);
                    if (!device.getString("targetID").equals(regID)) {
                        deviceItems.add(new DeviceItem(device.getString("name"), device.getString("type"), device.getString("targetID")));
                    }
                }
                SharedPreferences.Editor editor = mPrefs.edit();
                editor.putString("targetDevices", devices.toString());
                editor.commit();

                NotificationCompat.Builder mBuilder =
                        new NotificationCompat.Builder(mContext)
                                .setSmallIcon(R.drawable.action_icon)
                                .setContentTitle("FlippIt")
                                .setTicker("New device registered")
                                .setContentText("New device registered.");
                mNotificationManager.cancel(1);
                mNotificationManager.notify(1, mBuilder.build());
                Log.i(MyActivity.TAG, mContext.getClass().getName() + ": " + "Device added.");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void callBackFinish(ArrayList<HistoryItem> historyItems) {

        }
    }
}

