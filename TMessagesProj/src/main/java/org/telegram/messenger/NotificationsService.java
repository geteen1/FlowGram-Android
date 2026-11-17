/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

public class NotificationsService extends Service {

    @Override
    public void onCreate() {
        super.onCreate();
        flowStartForeground();
        ApplicationLoader.postInitApplication();
    }

    private static final int FLOW_NOTIFICATION_ID = 0x464C4F57; // "FLOW"
    private static final String FLOW_CHANNEL = "flowgram_background";

    /**
     * FlowGram: without Firebase push the app only gets messages while its connection is alive.
     * A plain background service is killed by Android 8+ within a minute, so run as a foreground
     * service with a silent, minimised notification (like other mods do).
     */
    private void flowStartForeground() {
        if (!FlowConfig.backgroundWork || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(FLOW_CHANNEL) == null) {
                NotificationChannel channel = new NotificationChannel(FLOW_CHANNEL, "Работа в фоне", NotificationManager.IMPORTANCE_MIN);
                channel.setDescription("FlowGram держит соединение, чтобы сообщения приходили, когда приложение свёрнуто");
                channel.setShowBadge(false);
                nm.createNotificationChannel(channel);
            }
            Intent open = new Intent(this, org.telegram.ui.LaunchActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(this, FLOW_CHANNEL)
                    .setSmallIcon(R.drawable.notification)
                    .setContentTitle("FlowGram работает в фоне")
                    .setContentText("Сообщения приходят, даже когда приложение закрыто")
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .setShowWhen(false)
                    .build();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(FLOW_NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(FLOW_NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(FLOW_NOTIFICATION_ID, n);
            }
        } catch (Throwable e) {
            // e.g. ForegroundServiceStartNotAllowedException when restarted from the background on Android 12+
            FileLog.e(e);
            try {
                stopSelf(); // never leave a startForegroundService() without startForeground(): Android would kill the app
            } catch (Throwable ignore) {
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onDestroy() {
        super.onDestroy();
        SharedPreferences preferences = MessagesController.getGlobalNotificationsSettings();
        if (preferences.getBoolean("pushService", true)) {
            Intent intent = new Intent("org.telegram.start");
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
        }
    }
}
