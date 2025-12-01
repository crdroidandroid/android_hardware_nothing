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

package co.aospa.glyph.Settings;

import android.app.Activity;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import co.aospa.glyph.Composer.GlyphComposerParser;
import co.aospa.glyph.Composer.GlyphPattern;
import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.Manager.SettingsManager;
import co.aospa.glyph.R;

public class GlyphPatternPreviewActivity extends Activity {
    
    private static final String TAG = "GlyphPatternPreview";
    
    private TextView statusText;
    private Button previewComposerButton;
    private Button previewFallbackButton;
    private Button stopButton;
    
    private Uri currentRingtoneUri;
    private GlyphPattern composerPattern;
    private boolean hasComposerPattern = false;
    
    private Handler previewHandler;
    private boolean isPreviewRunning = false;
    private Thread fallbackThread;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        previewHandler = new Handler(Looper.getMainLooper());
        
        android.widget.ScrollView scrollView = createLayout();
        setContentView(scrollView);
        
        scrollView.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bottom);
            return insets.consumeSystemWindowInsets();
        });
        
        getWindow().getDecorView().setSystemUiVisibility(
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
        
        analyzeCurrentRingtone();
    }
    
    private android.widget.ScrollView createLayout() {
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setFillViewport(true);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        
        int paddingHorizontal = 50;
        int paddingVertical = 50;
        layout.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
        
        TextView title = new TextView(this);
        title.setText("Glyph Pattern Preview");
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 30);
        
        statusText = new TextView(this);
        statusText.setText("Analyzing current ringtone...");
        statusText.setTextSize(14);
        statusText.setPadding(0, 0, 0, 30);
        statusText.setLineSpacing(8, 1.0f);
        
        previewComposerButton = new Button(this);
        previewComposerButton.setText("Preview Composer Pattern");
        previewComposerButton.setEnabled(false);
        previewComposerButton.setOnClickListener(v -> previewComposerPattern());
        
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        buttonParams.setMargins(0, 10, 0, 10);
        
        previewFallbackButton = new Button(this);
        previewFallbackButton.setText("Preview Fallback Animation");
        previewFallbackButton.setEnabled(true);
        previewFallbackButton.setOnClickListener(v -> previewFallbackAnimation());
        
        stopButton = new Button(this);
        stopButton.setText("Stop Preview");
        stopButton.setEnabled(false);
        stopButton.setOnClickListener(v -> stopPreview());
        
        layout.addView(title);
        layout.addView(statusText);
        layout.addView(previewComposerButton, buttonParams);
        layout.addView(previewFallbackButton, buttonParams);
        layout.addView(stopButton, buttonParams);
        
        scrollView.addView(layout);
        return scrollView;
    }
    
    private void analyzeCurrentRingtone() {
        currentRingtoneUri = RingtoneManager.getActualDefaultRingtoneUri(
            this, RingtoneManager.TYPE_RINGTONE);
        
        if (currentRingtoneUri == null) {
            statusText.setText("No ringtone set\n\nOnly fallback preview available");
            return;
        }
        
        String audioPath = getRealPathFromUri(currentRingtoneUri);
        
        if (audioPath == null) {
            statusText.setText("Current Ringtone: " + currentRingtoneUri.getLastPathSegment() + 
                             "\n\n⚠ Cannot access file path\n\nOnly fallback preview available");
            return;
        }
        
        StringBuilder status = new StringBuilder();
        status.append("Current Ringtone:\n");
        status.append(audioPath.substring(audioPath.lastIndexOf('/') + 1));
        status.append("\n\n");
        
        String patternPath = GlyphComposerParser.getGlyphPatternPath(audioPath);
        
        if (patternPath != null) {
            composerPattern = GlyphComposerParser.parseFromFile(patternPath);
            
            if (composerPattern != null && GlyphComposerParser.isValid(composerPattern)) {
                hasComposerPattern = true;
                status.append("✓ Composer Pattern Found\n");
                status.append("  Frames: ").append(composerPattern.getFrames().size()).append("\n");
                status.append("  Duration: ").append(composerPattern.getDuration() / 1000).append("s\n\n");
                status.append("Both preview options available:");
                
                previewComposerButton.setEnabled(true);
            } else {
                status.append("✗ Pattern file invalid\n\n");
                status.append("Only fallback preview available");
            }
        } else {
            status.append("✗ No Composer Pattern\n\n");
            status.append("Only fallback preview available");
        }
        
        statusText.setText(status.toString());
    }
    
    private void previewComposerPattern() {
        if (!hasComposerPattern || composerPattern == null) {
            Toast.makeText(this, "No composer pattern available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!SettingsManager.isGlyphEnabled()) {
            Toast.makeText(this, "Glyph is disabled in settings", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "Playing Composer Pattern", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Previewing composer pattern with " + composerPattern.getFrames().size() + " frames");
        
        previewComposerButton.setEnabled(false);
        previewFallbackButton.setEnabled(false);
        stopButton.setEnabled(true);
        isPreviewRunning = true;
        
        playComposerPatternPreview(composerPattern, 0, System.currentTimeMillis());
    }
    
    private void playComposerPatternPreview(GlyphPattern pattern, int frameIndex, long startTime) {
        if (!isPreviewRunning) {
            Log.d(TAG, "Preview stopped by user");
            return;
        }
        
        if (frameIndex >= pattern.getFrames().size()) {
            Log.d(TAG, "Composer preview completed");
            runOnUiThread(() -> {
                isPreviewRunning = false;
                previewComposerButton.setEnabled(true);
                previewFallbackButton.setEnabled(true);
                stopButton.setEnabled(false);
                Toast.makeText(this, "Preview completed", Toast.LENGTH_SHORT).show();
            });
            return;
        }
        
        GlyphPattern.GlyphFrame frame = pattern.getFrames().get(frameIndex);
        long currentTime = System.currentTimeMillis() - startTime;
        long delay = frame.getTimestamp() - currentTime;
        
        if (delay < 0) delay = 0;
        
        previewHandler.postDelayed(() -> {
            if (!isPreviewRunning) return;
            int brightness = scaleBrightness(frame.getBrightness());
            AnimationManager.playGlyphFrame(this, frame.getZones(), brightness, frame.getDuration());
            playComposerPatternPreview(pattern, frameIndex + 1, startTime);
        }, delay);
    }
    
    private void previewFallbackAnimation() {
        if (!SettingsManager.isGlyphEnabled()) {
            Toast.makeText(this, "Glyph is disabled in settings", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String fallbackAnimation = SettingsManager.getGlyphCallAnimation();
        Toast.makeText(this, "Playing Fallback: " + fallbackAnimation, Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Previewing fallback animation: " + fallbackAnimation);
        
        previewComposerButton.setEnabled(false);
        previewFallbackButton.setEnabled(false);
        stopButton.setEnabled(true);
        isPreviewRunning = true;
        
        fallbackThread = new Thread(() -> {
            AnimationManager.playCsv(this, fallbackAnimation);
            
            if (isPreviewRunning) {
                runOnUiThread(() -> {
                    isPreviewRunning = false;
                    previewComposerButton.setEnabled(hasComposerPattern);
                    previewFallbackButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    Toast.makeText(this, "Preview completed", Toast.LENGTH_SHORT).show();
                });
            }
        });
        fallbackThread.start();
    }
    
    private void stopPreview() {
        Log.d(TAG, "Stopping preview");
        
        isPreviewRunning = false;
        
        if (previewHandler != null) {
            previewHandler.removeCallbacksAndMessages(null);
        }
        
        if (fallbackThread != null && fallbackThread.isAlive()) {
            fallbackThread.interrupt();
        }
        
        AnimationManager.stopAll();
        
        previewComposerButton.setEnabled(hasComposerPattern);
        previewFallbackButton.setEnabled(true);
        stopButton.setEnabled(false);
        
        Toast.makeText(this, "Preview stopped", Toast.LENGTH_SHORT).show();
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
                    int columnIndex = cursor.getColumnIndexOrThrow(
                        android.provider.MediaStore.Audio.Media.DATA);
                    return cursor.getString(columnIndex);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting path from URI", e);
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        
        return null;
    }
    
    @Override
    protected void onDestroy() {
        stopPreview();
        super.onDestroy();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (isPreviewRunning) {
            stopPreview();
        }
    }
}