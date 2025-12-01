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

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.util.Log;

import co.aospa.glyph.Composer.GlyphComposerParser;
import co.aospa.glyph.Composer.GlyphPattern;
import co.aospa.glyph.Composer.GlyphSyncPlayer;
import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.Manager.SettingsManager;

public class CallReceiverService extends Service {

    private static final String TAG = "GlyphCallReceiverService";
    private static final boolean DEBUG = true;

    private AudioManager mAudioManager;
    private GlyphSyncPlayer mGlyphSyncPlayer;

    private HandlerThread thread;
    private Handler mThreadHandler;
    
    private boolean isComposerPatternPlaying = false;
    private long composerPatternStartTime = 0;

    private Runnable playCall = new Runnable() {
        @Override
        public void run() {
            playRingtoneWithGlyphSync();
        }
    };

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "Creating service");

        thread = new HandlerThread("CallReceiverService");
        thread.start();
        Looper looper = thread.getLooper();
        mThreadHandler = new Handler(looper);

        mAudioManager = getSystemService(AudioManager.class);
        mAudioManager.addOnModeChangedListener(cmd -> mThreadHandler.post(cmd), mAudioManagerOnModeChangedListener);
        mAudioManagerOnModeChangedListener.onModeChanged(mAudioManager.getMode());

        // Initialize Glyph Sync Player
        mGlyphSyncPlayer = new GlyphSyncPlayer(this);
        mGlyphSyncPlayer.setOnCompletionListener(() -> {
            if (DEBUG) Log.d(TAG, "Ringtone playback completed");
        });

        IntentFilter callReceiver = new IntentFilter();
        callReceiver.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        registerReceiver(mCallReceiver, callReceiver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service");
        this.unregisterReceiver(mCallReceiver);
        mAudioManager.removeOnModeChangedListener(mAudioManagerOnModeChangedListener);
        disableCallAnimation();
        
        if (mGlyphSyncPlayer != null) {
            mGlyphSyncPlayer.stop();
            mGlyphSyncPlayer = null;
        }
        
        thread.quit();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void enableCallAnimation() {
        if (DEBUG) Log.d(TAG, "enableCallAnimation");
        mThreadHandler.post(playCall);
    }

    private void disableCallAnimation() {
        if (DEBUG) Log.d(TAG, "disableCallAnimation");
        if (mThreadHandler.hasCallbacks(playCall))
            mThreadHandler.removeCallbacks(playCall);
        
        if (isComposerPatternPlaying) {
            isComposerPatternPlaying = false;
            mThreadHandler.removeCallbacksAndMessages(null);
            if (DEBUG) Log.d(TAG, "Stopped composer pattern playback");
        }
        
        AnimationManager.stopCall();
    }

    private void playRingtoneWithGlyphSync() {
        if (!SettingsManager.isGlyphComposerEnabled()) {
            if (DEBUG) Log.d(TAG, "Glyph Composer disabled, using standard animation");
            AnimationManager.playCall(SettingsManager.getGlyphCallAnimation());
            return;
        }

        Uri ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE);
        
        if (ringtoneUri == null) {
            if (DEBUG) Log.w(TAG, "No ringtone URI, falling back to standard animation");
            AnimationManager.playCall(SettingsManager.getGlyphCallAnimation());
            return;
        }

        if (DEBUG) Log.d(TAG, "Ringtone URI: " + ringtoneUri);

        String audioPath = getRealPathFromUri(ringtoneUri);
        
        if (audioPath != null) {
            String glyphPatternPath = GlyphComposerParser.getGlyphPatternPath(audioPath);
            
            if (glyphPatternPath != null) {
                GlyphPattern pattern = GlyphComposerParser.parseFromFile(glyphPatternPath);
                
                if (pattern != null && GlyphComposerParser.isValid(pattern)) {
                    if (DEBUG) Log.d(TAG, "Playing ringtone with Glyph Composer sync");
                    playGlyphPatternOnly(pattern);
                    return;
                }
            }
        }

        if (SettingsManager.useComposerFallback()) {
            if (DEBUG) Log.d(TAG, "No Glyph pattern found, using fallback animation");
            AnimationManager.playCall(SettingsManager.getGlyphCallAnimation());
        } else {
            if (DEBUG) Log.d(TAG, "No Glyph pattern found, fallback disabled, no animation");
        }
    }

    private void playGlyphPatternOnly(GlyphPattern pattern) {
        if (pattern == null || pattern.getFrames() == null) {
            return;
        }

        isComposerPatternPlaying = true;
        composerPatternStartTime = System.currentTimeMillis();
        
        new Handler(Looper.getMainLooper()).post(() -> {
            scheduleGlyphFrames(pattern, 0, composerPatternStartTime);
        });
    }

    private void scheduleGlyphFrames(GlyphPattern pattern, int frameIndex, long startTime) {
        if (!isComposerPatternPlaying) {
            if (DEBUG) Log.d(TAG, "Composer pattern stopped by user action");
            return;
        }
        
        if (frameIndex >= pattern.getFrames().size()) {
            if (DEBUG) Log.d(TAG, "All Glyph frames completed");
            isComposerPatternPlaying = false;
            return;
        }

        GlyphPattern.GlyphFrame frame = pattern.getFrames().get(frameIndex);
        long currentTime = System.currentTimeMillis() - startTime;
        long delay = frame.getTimestamp() - currentTime;

        if (delay < 0) delay = 0;

        mThreadHandler.postDelayed(() -> {
            if (!isComposerPatternPlaying) return;
            activateGlyphFrame(frame);
            scheduleGlyphFrames(pattern, frameIndex + 1, startTime);
        }, delay);
    }

    private void activateGlyphFrame(GlyphPattern.GlyphFrame frame) {
        if (frame == null || frame.getZones() == null) {
            return;
        }

        if (!AnimationManager.canPlayGlyphComposer()) {
            if (DEBUG) Log.d(TAG, "Cannot play frame, other animation active");
            return;
        }

        int brightness = scaleBrightness(frame.getBrightness());
        AnimationManager.playGlyphFrame(this, frame.getZones(), brightness, frame.getDuration());
    }

    private int scaleBrightness(int patternBrightness) {
        int userBrightness = SettingsManager.getGlyphBrightness();
        return (patternBrightness * userBrightness) / 100;
    }

    private String getRealPathFromUri(Uri uri) {
        if (uri == null) return null;

        if ("file".equals(uri.getScheme())) {
            return uri.getPath();
        }

        if ("content".equals(uri.getScheme())) {
            android.database.Cursor cursor = null;
            try {
                String[] projection = {android.provider.MediaStore.Audio.Media.DATA};
                cursor = getContentResolver().query(uri, projection, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA);
                    return cursor.getString(columnIndex);
                }
            } catch (Exception e) {
                if (DEBUG) Log.e(TAG, "Error getting path from URI", e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        return null;
    }

    private final BroadcastReceiver mCallReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                if(state.equals(TelephonyManager.EXTRA_STATE_RINGING)){
                    if (DEBUG) Log.d(TAG, "EXTRA_STATE_RINGING");
                    enableCallAnimation();
                }
                if ((state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK))){
                    if (DEBUG) Log.d(TAG, "EXTRA_STATE_OFFHOOK");
                    disableCallAnimation();
                }
                if (state.equals(TelephonyManager.EXTRA_STATE_IDLE)){
                    if (DEBUG) Log.d(TAG, "EXTRA_STATE_IDLE");
                    disableCallAnimation();
                }
            }
        }
    };

    private final AudioManager.OnModeChangedListener mAudioManagerOnModeChangedListener = new AudioManager.OnModeChangedListener() {
        @Override
        public void onModeChanged(int mode) {
            if (mode != AudioManager.MODE_RINGTONE) {
                if (DEBUG) Log.d(TAG, "mAudioManagerOnModeChangedListener: " + mode);
                disableCallAnimation();
            }
        }
    };
}