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

package co.aospa.glyph.Composer;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import co.aospa.glyph.Manager.AnimationManager;

import java.io.IOException;
import java.util.List;

public class GlyphSyncPlayer {
    
    private static final String TAG = "GlyphSyncPlayer";
    private static final boolean DEBUG = true;
    
    private Context mContext;
    private MediaPlayer mMediaPlayer;
    private GlyphPattern mPattern;
    private Handler mSyncHandler;
    private int mCurrentFrameIndex;
    private boolean mIsPlaying;
    private long mStartTime;
    
    private OnCompletionListener mCompletionListener;
    
    public interface OnCompletionListener {
        void onCompletion();
    }
    
    public GlyphSyncPlayer(Context context) {
        mContext = context;
        mSyncHandler = new Handler(Looper.getMainLooper());
    }
    
    public boolean play(Uri audioUri, GlyphPattern pattern) {
        if (audioUri == null || pattern == null) {
            if (DEBUG) Log.e(TAG, "Invalid audio URI or pattern");
            return false;
        }
        
        if (!GlyphComposerParser.isValid(pattern)) {
            if (DEBUG) Log.e(TAG, "Invalid pattern data");
            return false;
        }
        
        stop();
        
        mPattern = pattern;
        mCurrentFrameIndex = 0;
        
        try {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            );
            
            mMediaPlayer.setDataSource(mContext, audioUri);
            mMediaPlayer.setOnPreparedListener(mp -> {
                if (DEBUG) Log.d(TAG, "MediaPlayer prepared, starting playback");
                mp.start();
                mIsPlaying = true;
                mStartTime = System.currentTimeMillis();
                startGlyphSync();
            });
            
            mMediaPlayer.setOnCompletionListener(mp -> {
                if (DEBUG) Log.d(TAG, "Playback completed");
                stop();
                if (mCompletionListener != null) {
                    mCompletionListener.onCompletion();
                }
            });
            
            mMediaPlayer.setOnErrorListener((mp, what, extra) -> {
                if (DEBUG) Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                stop();
                return true;
            });
            
            mMediaPlayer.prepareAsync();
            return true;
            
        } catch (IOException e) {
            if (DEBUG) Log.e(TAG, "Error setting up MediaPlayer", e);
            return false;
        }
    }
    
    public boolean playWithoutSync(Uri audioUri) {
        if (audioUri == null) {
            if (DEBUG) Log.e(TAG, "Invalid audio URI");
            return false;
        }
        
        stop();
        
        try {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            );
            
            mMediaPlayer.setDataSource(mContext, audioUri);
            mMediaPlayer.setOnPreparedListener(mp -> {
                if (DEBUG) Log.d(TAG, "MediaPlayer prepared (no sync)");
                mp.start();
                mIsPlaying = true;
            });
            
            mMediaPlayer.setOnCompletionListener(mp -> {
                if (DEBUG) Log.d(TAG, "Playback completed (no sync)");
                stop();
                if (mCompletionListener != null) {
                    mCompletionListener.onCompletion();
                }
            });
            
            mMediaPlayer.prepareAsync();
            return true;
            
        } catch (IOException e) {
            if (DEBUG) Log.e(TAG, "Error setting up MediaPlayer", e);
            return false;
        }
    }
    
    public void stop() {
        mIsPlaying = false;
        
        if (mSyncHandler != null) {
            mSyncHandler.removeCallbacksAndMessages(null);
        }
        
        if (mMediaPlayer != null) {
            try {
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.stop();
                }
                mMediaPlayer.release();
            } catch (IllegalStateException e) {
                if (DEBUG) Log.e(TAG, "Error stopping MediaPlayer", e);
            }
            mMediaPlayer = null;
        }
        
        AnimationManager.stopAll();
        mPattern = null;
        mCurrentFrameIndex = 0;
    }
    
    private void startGlyphSync() {
        if (mPattern == null || mPattern.getFrames() == null) {
            if (DEBUG) Log.e(TAG, "No pattern to sync");
            return;
        }
        
        if (DEBUG) Log.d(TAG, "Starting Glyph sync with " + mPattern.getFrames().size() + " frames");
        scheduleNextFrame();
    }
    
    private void scheduleNextFrame() {
        if (!mIsPlaying || mPattern == null) {
            return;
        }
        
        List<GlyphPattern.GlyphFrame> frames = mPattern.getFrames();
        if (mCurrentFrameIndex >= frames.size()) {
            if (DEBUG) Log.d(TAG, "All frames completed");
            return;
        }
        
        GlyphPattern.GlyphFrame frame = frames.get(mCurrentFrameIndex);
        long currentTime = System.currentTimeMillis() - mStartTime;
        long delay = frame.getTimestamp() - currentTime;
        
        if (delay < 0) delay = 0;
        
        if (DEBUG) Log.d(TAG, "Scheduling frame " + mCurrentFrameIndex + " at " + frame.getTimestamp() + "ms (delay: " + delay + "ms)");
        
        mSyncHandler.postDelayed(() -> {
            if (mIsPlaying) {
                activateGlyphFrame(frame);
                mCurrentFrameIndex++;
                scheduleNextFrame();
            }
        }, delay);
    }
    
    private void activateGlyphFrame(GlyphPattern.GlyphFrame frame) {
        if (frame == null || frame.getZones() == null) {
            return;
        }
        
        if (DEBUG) Log.d(TAG, "Activating frame: zones=" + frame.getZones().length + 
                              ", brightness=" + frame.getBrightness() + 
                              ", duration=" + frame.getDuration() + "ms");
        
        int brightness = scaleBrightness(frame.getBrightness());
        
        for (int zone : frame.getZones()) {
            AnimationManager.singleLedBlink(mContext, zone, brightness, frame.getDuration());
        }
    }
    
    private int scaleBrightness(int patternBrightness) {
        int userBrightness = co.aospa.glyph.Manager.SettingsManager.getGlyphBrightness();
        return (patternBrightness * userBrightness) / 100;
    }
    
    public boolean isPlaying() {
        return mIsPlaying;
    }
    
    public void setOnCompletionListener(OnCompletionListener listener) {
        mCompletionListener = listener;
    }
}