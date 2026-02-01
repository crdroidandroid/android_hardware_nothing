/*
 * Copyright (C) 2024-2025 LunarisAOSP
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

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.Manager.SettingsManager;
import co.aospa.glyph.Manager.StatusManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProgressService extends Service {

    private static final String TAG = "GlyphProgressService";
    private static final boolean DEBUG = true;

    public static final String ACTION_PROGRESS_NOTIFICATION = "co.aospa.glyph.PROGRESS_NOTIFICATION";
    public static final String ACTION_PROGRESS_REMOVED = "co.aospa.glyph.PROGRESS_REMOVED";
    public static final String EXTRA_PACKAGE_NAME = "package_name";
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";
    public static final String EXTRA_PROGRESS = "progress";
    public static final String EXTRA_MAX = "max";

    private HandlerThread thread;
    private Handler mThreadHandler;
    private Context mContext;

    private MediaSessionManager mMediaSessionManager;

    private Map<String, ProgressInfo> mActiveProgress = new HashMap<>();
    private int mLastDisplayedProgress = -1;
    private String mLastDisplayedKey = null;

    private int mLastMusicProgress = 0;

    private Runnable mProgressChecker;
    private Runnable mMusicProgressChecker;
    private Runnable dismissProgress = new Runnable() {
        @Override
        public void run() {
            AnimationManager.dismissProgress(mContext);
            StatusManager.setProgressAnimationActive(false);
        }
    };

    private static class ProgressInfo {
        String packageName;
        int notificationId;
        int progress;
        int max;
        long lastUpdate;

        ProgressInfo(String pkg, int id, int prog, int maximum) {
            this.packageName = pkg;
            this.notificationId = id;
            this.progress = prog;
            this.max = maximum;
            this.lastUpdate = System.currentTimeMillis();
        }

        int getProgressPercent() {
            if (max <= 0) return 0;
            return (int) ((progress * 100L) / max);
        }

        String getKey() {
            return packageName + ":" + notificationId;
        }
    }

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "Creating service");

        mContext = this;

        thread = new HandlerThread("ProgressService");
        thread.start();
        Looper looper = thread.getLooper();
        mThreadHandler = new Handler(looper);

        mMediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);

        IntentFilter progressFilter = new IntentFilter();
        progressFilter.addAction(ACTION_PROGRESS_NOTIFICATION);
        progressFilter.addAction(ACTION_PROGRESS_REMOVED);
        
        try {
            registerReceiver(mProgressReceiver, progressFilter, Context.RECEIVER_NOT_EXPORTED);
            if (DEBUG) Log.d(TAG, "BroadcastReceiver registered");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register BroadcastReceiver", e);
        }

        startProgressMonitoring();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        if (SettingsManager.isGlyphProgressMusicEnabled()) {
            if (DEBUG) Log.d(TAG, "Starting music progress monitoring");
            startMusicProgressMonitoring();
        } else {
            stopMusicProgressMonitoring();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service");
        try {
            unregisterReceiver(mProgressReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }
        stopProgressMonitoring();
        stopMusicProgressMonitoring();
        mThreadHandler.post(dismissProgress);
        thread.quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startProgressMonitoring() {
        if (DEBUG) Log.d(TAG, "Starting progress monitoring");
        mProgressChecker = new Runnable() {
            @Override
            public void run() {
                checkProgressNotifications();
                mThreadHandler.postDelayed(this, 500);
            }
        };
        mThreadHandler.post(mProgressChecker);
    }

    private void stopProgressMonitoring() {
        if (mProgressChecker != null) {
            mThreadHandler.removeCallbacks(mProgressChecker);
        }
    }

    private void checkProgressNotifications() {
        if (!SettingsManager.isGlyphProgressEnabled()) return;

        try {
            long currentTime = System.currentTimeMillis();
            mActiveProgress.entrySet().removeIf(entry -> 
                currentTime - entry.getValue().lastUpdate > 5000);

            ProgressInfo currentProgress = null;
            for (ProgressInfo info : mActiveProgress.values()) {
                if (currentProgress == null || info.getProgressPercent() > currentProgress.getProgressPercent()) {
                    currentProgress = info;
                }
            }

            if (currentProgress != null) {
                int progress = currentProgress.getProgressPercent();
                String key = currentProgress.getKey();

                if (!key.equals(mLastDisplayedKey) || Math.abs(progress - mLastDisplayedProgress) >= 3) {
                    if (DEBUG) Log.d(TAG, "Progress: " + progress + "% (" + currentProgress.packageName + ")");
                    
                    mLastDisplayedProgress = progress;
                    mLastDisplayedKey = key;

                    if (!StatusManager.isVolumeAnimationActive() && 
                        (!SettingsManager.isGlyphProgressMusicEnabled() || mLastMusicProgress == 0)) {
                        playProgressAnimation(progress, 1);
                    }
                }
            } else {
                if (mLastDisplayedProgress != -1) {
                    mLastDisplayedProgress = -1;
                    mLastDisplayedKey = null;
                    dismissProgressIfNeeded();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking progress notifications", e);
        }
    }

    private void startMusicProgressMonitoring() {
        if (!SettingsManager.isGlyphProgressMusicEnabled()) return;

        mMusicProgressChecker = new Runnable() {
            @Override
            public void run() {
                checkMusicProgress();
                mThreadHandler.postDelayed(this, 1000);
            }
        };
        mThreadHandler.post(mMusicProgressChecker);
    }

    private void stopMusicProgressMonitoring() {
        if (mMusicProgressChecker != null) {
            mThreadHandler.removeCallbacks(mMusicProgressChecker);
            if (StatusManager.getProgressType() == 2) {
                mThreadHandler.post(dismissProgress);
            }
        }
    }

    private void checkMusicProgress() {
        if (!SettingsManager.isGlyphProgressMusicEnabled()) return;

        try {
            MediaController controller = getActiveMediaController();
            if (controller != null && controller.getPlaybackState() != null) {
                PlaybackState state = controller.getPlaybackState();

                if (state.getState() == PlaybackState.STATE_PLAYING) {
                    long position = state.getPosition();
                    long duration = controller.getMetadata() != null ?
                            controller.getMetadata().getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) : 0;

                    if (duration > 0) {
                        int progress = (int) ((position * 100L) / duration);
                        progress = Math.min(100, Math.max(0, progress));

                        if (Math.abs(progress - mLastMusicProgress) >= 3) {
                            if (DEBUG) Log.d(TAG, "Music progress: " + progress + "%");
                            mLastMusicProgress = progress;

                            if (!StatusManager.isVolumeAnimationActive() && mLastDisplayedProgress == -1) {
                                playProgressAnimation(progress, 2);
                            }
                        }
                    }
                } else {
                    if (mLastMusicProgress > 0) {
                        mLastMusicProgress = 0;
                        dismissProgressIfNeeded();
                    }
                }
            } else {
                if (mLastMusicProgress > 0) {
                    mLastMusicProgress = 0;
                    dismissProgressIfNeeded();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking music progress", e);
        }
    }

    private MediaController getActiveMediaController() {
        try {
            ComponentName notificationListener = new ComponentName(mContext, 
                co.aospa.glyph.Services.NotificationService.class);
            
            List<MediaController> controllers = mMediaSessionManager.getActiveSessions(notificationListener);

            if (controllers == null || controllers.isEmpty()) {
                return null;
            }

            for (MediaController controller : controllers) {
                PlaybackState state = controller.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    return controller;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting active media controller", e);
        }
        return null;
    }

    private void playProgressAnimation(int progress, int progressType) {
        if (mThreadHandler.hasCallbacks(dismissProgress)) {
            mThreadHandler.removeCallbacks(dismissProgress);
        }

        mThreadHandler.post(() -> {
            AnimationManager.playProgress(mContext, progress, progressType, false);
            StatusManager.setProgressAnimationActive(true);
        });
    }

    private void dismissProgressIfNeeded() {
        if (mLastDisplayedProgress == -1 && mLastMusicProgress == 0) {
            if (mThreadHandler.hasCallbacks(dismissProgress)) {
                mThreadHandler.removeCallbacks(dismissProgress);
            }
            mThreadHandler.post(dismissProgress);
        }
    }

    private final BroadcastReceiver mProgressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (ACTION_PROGRESS_NOTIFICATION.equals(action)) {
                String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
                int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);
                int progress = intent.getIntExtra(EXTRA_PROGRESS, 0);
                int max = intent.getIntExtra(EXTRA_MAX, 100);

                if (packageName != null && notificationId != -1) {
                    ProgressInfo info = new ProgressInfo(packageName, notificationId, progress, max);
                    mActiveProgress.put(info.getKey(), info);
                    
                    if (DEBUG) Log.d(TAG, "Progress stored: " + info.getKey() + " = " + info.getProgressPercent() + "%");
                }
            } else if (ACTION_PROGRESS_REMOVED.equals(action)) {
                String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
                int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);

                if (packageName != null && notificationId != -1) {
                    String key = packageName + ":" + notificationId;
                    mActiveProgress.remove(key);
                    
                    if (DEBUG) Log.d(TAG, "Progress removed: " + key);
                    
                    if (key.equals(mLastDisplayedKey)) {
                        mLastDisplayedProgress = -1;
                        mLastDisplayedKey = null;
                        dismissProgressIfNeeded();
                    }
                }
            }
        }
    };
}
