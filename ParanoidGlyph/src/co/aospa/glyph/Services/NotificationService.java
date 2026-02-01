/*
 * Copyright (C) 2022-2024 Paranoid Android
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

package co.aospa.glyph.Services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.android.internal.util.ArrayUtils;

import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.Manager.EssentialLedManager;
import co.aospa.glyph.Manager.SettingsManager;
import co.aospa.glyph.Manager.StatusManager;

public class NotificationService extends NotificationListenerService
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "GlyphNotification";
    private static final boolean DEBUG = true;

    private Context mContext;

    private NotificationManager mNotificationManager;

    private ContentResolver mContentResolver;
    private SettingObserver mSettingObserver;

    private SharedPreferences mSharedPreferences;

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "Creating service");

        mContext = this;
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mContentResolver = getContentResolver();
        mSettingObserver = new SettingObserver();
        mSettingObserver.register(mContentResolver);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        onNotificationUpdated();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service");
        AnimationManager.stopEssential();
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        mSettingObserver.unregister(mContentResolver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn){
        if (Constants.CONTEXT == null) return;
        if (DEBUG) Log.d(TAG, "onNotificationPosted: " + sbn.getPackageName());
        
        if (SettingsManager.isGlyphProgressEnabled()) {
            checkProgressNotification(sbn);
        }
        
        if (!SettingsManager.isGlyphNotifsEnabled()) return;
        
        String packageName = sbn.getPackageName();
        String packageChannelID = sbn.getNotification().getChannelId();
        
        if (hasProgressBar(sbn.getNotification())) {
            if (DEBUG) Log.d(TAG, "Skipping progress notification for regular glyph: " + packageName);
            return;
        }
        
        int packageImportance = -1;
        boolean packageCanBypassDnd = false;
        int interruptionFilter = mNotificationManager.getCurrentInterruptionFilter();
        
        try {
            Context packageContext = createPackageContext(packageName, 0);
            NotificationManager packageNotificationManager = (NotificationManager) packageContext.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel packageChannel = packageNotificationManager.getNotificationChannel(packageChannelID);
            if (packageChannel != null) {
                packageImportance = packageChannel.getImportance();
                packageCanBypassDnd = packageChannel.canBypassDnd();
            }
        } catch (PackageManager.NameNotFoundException e) {}
        
        if (DEBUG) Log.d(TAG, "onNotificationPosted: package:" + packageName + " | channel id: " + packageChannelID + " | importance: " + packageImportance + " | can bypass dnd: " + packageCanBypassDnd);
        
        if (SettingsManager.isGlyphNotifsAppEnabled(packageName)
                        && !sbn.isOngoing()
                        && !ArrayUtils.contains(Constants.APPS_TO_IGNORE, packageName)
                        && !ArrayUtils.contains(Constants.NOTIFS_TO_IGNORE, packageName + ":" + packageChannelID)
                        && (packageImportance >= NotificationManager.IMPORTANCE_DEFAULT || packageImportance == -1)
                        && (interruptionFilter <= NotificationManager.INTERRUPTION_FILTER_ALL || packageCanBypassDnd)) {
            AnimationManager.playCsv(mContext, SettingsManager.getGlyphNotifsAnimation());
        }
        
        if (SettingsManager.isGlyphNotifsAppEssential(packageName)
                        && !sbn.isOngoing()
                        && !ArrayUtils.contains(Constants.APPS_TO_IGNORE, packageName)
                        && !ArrayUtils.contains(Constants.NOTIFS_TO_IGNORE, packageName + ":" + packageChannelID)
                        && (packageImportance >= NotificationManager.IMPORTANCE_DEFAULT || packageImportance == -1)
                        && (interruptionFilter <= NotificationManager.INTERRUPTION_FILTER_ALL || packageCanBypassDnd)
                        && mNotificationManager.isNotificationPolicyAccessGranted()) {
            AnimationManager.playEssentialForApp(mContext, packageName);
        }
    }

    private boolean hasProgressBar(Notification notification) {
        if (notification == null || notification.extras == null) return false;
        
        int progress = notification.extras.getInt(Notification.EXTRA_PROGRESS, -1);
        int maxProgress = notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX, -1);
        
        return progress >= 0 && maxProgress > 0;
    }

    private void checkProgressNotification(StatusBarNotification sbn) {
        try {
            Notification notification = sbn.getNotification();
            if (notification == null || notification.extras == null) return;

            int progress = notification.extras.getInt(Notification.EXTRA_PROGRESS, -1);
            int maxProgress = notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX, -1);
            boolean indeterminate = notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, true);

            if (DEBUG) {
                Log.d(TAG, "Checking progress: pkg=" + sbn.getPackageName() + 
                        ", progress=" + progress + "/" + maxProgress + 
                        ", indeterminate=" + indeterminate + 
                        ", ongoing=" + sbn.isOngoing());
            }

            if (progress >= 0 && maxProgress > 0 && !indeterminate) {
                Intent intent = new Intent(ProgressService.ACTION_PROGRESS_NOTIFICATION);
                intent.putExtra(ProgressService.EXTRA_PACKAGE_NAME, sbn.getPackageName());
                intent.putExtra(ProgressService.EXTRA_NOTIFICATION_ID, sbn.getId());
                intent.putExtra(ProgressService.EXTRA_PROGRESS, progress);
                intent.putExtra(ProgressService.EXTRA_MAX, maxProgress);
                sendBroadcast(intent);

                if (DEBUG) {
                    Log.d(TAG, ">>> BROADCAST SENT: Progress notification detected: " + 
                            sbn.getPackageName() + " - " + progress + "/" + maxProgress);
                }
            } else {
                if (DEBUG) {
                    Log.d(TAG, "Not broadcasting: invalid progress or indeterminate");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking progress notification", e);
        }
    }

    private void checkProgressNotificationRemoved(StatusBarNotification sbn) {
        try {
            Notification notification = sbn.getNotification();
            if (notification == null || notification.extras == null) return;

            int progress = notification.extras.getInt(Notification.EXTRA_PROGRESS, -1);
            int maxProgress = notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX, -1);

            if (progress >= 0 && maxProgress > 0) {
                Intent intent = new Intent(ProgressService.ACTION_PROGRESS_REMOVED);
                intent.putExtra(ProgressService.EXTRA_PACKAGE_NAME, sbn.getPackageName());
                intent.putExtra(ProgressService.EXTRA_NOTIFICATION_ID, sbn.getId());
                sendBroadcast(intent);

                if (DEBUG) {
                    Log.d(TAG, ">>> BROADCAST SENT: Progress notification removed: " + sbn.getPackageName());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking progress notification removal", e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn){
        if (DEBUG) Log.d(TAG, "onNotificationRemoved: package:" + sbn.getPackageName() + " | channel id: " + sbn.getNotification().getChannelId());
        
        if (SettingsManager.isGlyphProgressEnabled()) {
            checkProgressNotificationRemoved(sbn);
        }
        
        onNotificationUpdated();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preference, String key) {
        if (key.equals("glyph_settings_notifs_sub_essential")) {
            if (DEBUG) Log.d(TAG, "onSharedPreferenceChanged: glyph_settings_notifs_sub_essential");
            onNotificationUpdated();
        }
    }

    private void onNotificationUpdated() {
        if (DEBUG) Log.d(TAG, "onNotificationUpdated");
        boolean playEssential = false;
        String essentialPackageName = null;
        if (SettingsManager.isGlyphNotifsEnabled()) {
            if (!mNotificationManager.isNotificationPolicyAccessGranted()) return;
            StatusBarNotification[] activeNotifications = getActiveNotifications();
            for (StatusBarNotification sbn : activeNotifications) {
                String packageName = sbn.getPackageName();
                String packageChannelID = sbn.getNotification().getChannelId();
                int packageImportance = -1;
                boolean packageCanBypassDnd = false;
                int interruptionFilter = mNotificationManager.getCurrentInterruptionFilter();
                try {
                    Context packageContext = createPackageContext(packageName, 0);
                    NotificationManager packageNotificationManager = (NotificationManager) packageContext.getSystemService(Context.NOTIFICATION_SERVICE);
                    NotificationChannel packageChannel = packageNotificationManager.getNotificationChannel(packageChannelID);
                    if (packageChannel != null) {
                        packageImportance = packageChannel.getImportance();
                        packageCanBypassDnd = packageChannel.canBypassDnd();
                    }
                } catch (PackageManager.NameNotFoundException e) {}
                if (DEBUG) Log.d(TAG, "onNotificationUpdated: package:" + packageName + " | channel id: " + packageChannelID + " | importance: " + packageImportance + " | can bypass dnd: " + packageCanBypassDnd);
                if (SettingsManager.isGlyphNotifsAppEssential(packageName)
                                && !sbn.isOngoing()
                                && !ArrayUtils.contains(Constants.APPS_TO_IGNORE, packageName)
                                && !ArrayUtils.contains(Constants.NOTIFS_TO_IGNORE, packageName + ":" + packageChannelID)
                                && (packageImportance >= NotificationManager.IMPORTANCE_DEFAULT || packageImportance == -1)
                                && (interruptionFilter <= NotificationManager.INTERRUPTION_FILTER_ALL || packageCanBypassDnd)) {
                    if (DEBUG) Log.d(TAG, "onNotificationUpdated: found essential notification | package:" + packageName);
                    playEssential = true;
                    essentialPackageName = packageName;
                    break;
                }
            }
        }
        if (playEssential && essentialPackageName != null) {
            AnimationManager.playEssentialForApp(mContext, essentialPackageName);
        } else {
            AnimationManager.stopEssential();
        }
    }

    private class SettingObserver extends ContentObserver {
        public SettingObserver() {
            super(new Handler());
        }

        public void register(ContentResolver cr) {
            cr.registerContentObserver(Settings.Secure.getUriFor(
                Constants.GLYPH_ENABLE), false, this);
            cr.registerContentObserver(Settings.Secure.getUriFor(
                Constants.GLYPH_NOTIFS_ENABLE), false, this);
        }

        public void unregister(ContentResolver cr) {
            cr.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            if (DEBUG) Log.d(TAG, "SettingObserver: onChange");
            onNotificationUpdated();
            super.onChange(selfChange);
        }
    }
}
