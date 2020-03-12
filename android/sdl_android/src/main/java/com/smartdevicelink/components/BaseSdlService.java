package com.smartdevicelink.components;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.DrawableRes;
import android.util.Log;
import android.util.SparseArray;

import com.smartdevicelink.managers.SdlManager;

public abstract class BaseSdlService extends Service implements ISdlService{

	private static final String TAG = "SDL Service";
	private static Integer serviceForegroundId = null;
	private static String serviceName = null;
	private static @DrawableRes Integer serviceIcon = null;
	private static String serviceTitle = null;

	public SparseArray<SdlManager> sdlManagerMap = new SparseArray<>();

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		Log.d(TAG, "onCreate");
		super.onCreate();

		serviceForegroundId = provideServiceForegroundId();
		serviceName = provideServiceName();
		serviceIcon = provideServiceIcon();
		serviceTitle = provideServiceNotificationTitle();

		processGlobalConstants();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			enterForeground();
		}
	}

	// Helper method to let the service enter foreground mode
	@SuppressLint("NewApi")
	public void enterForeground() {
		Log.d(TAG, "enterForground");

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(
					serviceForegroundId.toString(),
					serviceName,
					NotificationManager.IMPORTANCE_DEFAULT
			);
			NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			if (notificationManager != null) {
				notificationManager.createNotificationChannel(channel);
				Notification serviceNotification = new Notification.Builder(this, channel.getId())
						.setContentTitle(serviceTitle)
						.setSmallIcon(serviceIcon)
						.build();
				startForeground(serviceForegroundId, serviceNotification);
			}
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		configure();
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy");

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			stopForeground(true);
		}

		disposeSdlManagers();
		super.onDestroy();
	}

	@Override
	public void disposeSdlManagers() {
		Log.d(TAG, "disposing managers");

		for (int i = 0; i < sdlManagerMap.size(); i++) {
			SdlManager manager = sdlManagerMap.get(i);
			if (manager != null) {
				manager.dispose();
			}
		}
	}

	private void processGlobalConstants() {
		StringBuilder error = new StringBuilder();
		if (serviceForegroundId == null) {
			error.append("ApplicationId should not be null, please override method correctly \n");
		}
		if (serviceName == null) {
			error.append("ServiceName should not be null, please override method correctly \n");
		}

		if (serviceIcon == null) {
			error.append("ServiceIcon should not be null, please override method correctly \n");
		}

		if (serviceTitle == null) {
			error.append("ServiceTitle should not be null, please override method correctly");
		}

		if (error.length() != 0) {
			throw new RuntimeException(error.toString());
		}
	}
}
