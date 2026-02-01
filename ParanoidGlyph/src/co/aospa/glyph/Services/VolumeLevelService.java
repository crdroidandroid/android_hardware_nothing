/*
 * Copyright (C) 2023-2024 Paranoid Android
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
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.os.IBinder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.Manager.StatusManager;

public class VolumeLevelService extends Service {

    private static final String TAG = "GlyphVolumeLevelService";
    private static final boolean DEBUG = true;

    private HandlerThread thread;
    private Handler mThreadHandler;
    private VolumeChangeReceiver mVolumeChangeReceiver;

    private Context mContext;

    private AudioManager audioManager;
    private Runnable dismissVolume = new Runnable() {
        @Override
        public void run() {
            AnimationManager.dismissVolume(mContext);
        }
    };

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "Creating service");

        mContext = this;

        // Add a handler thread
        thread = new HandlerThread("VolumeLevelService");
        thread.start();
        Looper looper = thread.getLooper();
        mThreadHandler = new Handler(looper);

        audioManager = (AudioManager) getSystemService(AudioManager.class); 
        mVolumeChangeReceiver = new VolumeChangeReceiver();
        registerReceiver(mVolumeChangeReceiver, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service");
        unregisterReceiver(mVolumeChangeReceiver);
        thread.quit();
        if (StatusManager.isVolumeAnimationActive()) {
            AnimationManager.dismissVolume(mContext);
            StatusManager.setVolumeAnimationActive(false);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class VolumeChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())) {
                int streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1);
                int currentVolume = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1);
                int oldVolume = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1);

                // Only check streams which are shown in the volume panel
                if ((streamType >= 0 && streamType <= AudioSystem.NUM_STREAMS) && currentVolume >= 0 && oldVolume >= 0) {
                    int maxVolume = audioManager.getStreamMaxVolume(streamType);
                    int oldVolumePercent = (int) (Math.round(100D / maxVolume * oldVolume));
                    int currentVolumePercent = (int) (Math.round(100D / maxVolume * currentVolume));

                    if (oldVolumePercent != currentVolumePercent) {
                        if (mThreadHandler.hasCallbacks(dismissVolume)) {
                            mThreadHandler.removeCallbacks(dismissVolume);
                        }
                        if (DEBUG) {
                            Log.d(TAG, "Volume level changed for stream type " + streamType + 
                                  ": oldVolumePercent: " + oldVolumePercent + ", currentVolumePercent: " + currentVolumePercent);
                        }
                        mThreadHandler.post(() -> {
                            AnimationManager.playVolume(context, currentVolumePercent, false);
                        });
                        mThreadHandler.postDelayed(dismissVolume, 3000);
                    }
                }
            }
        }
    }
}
