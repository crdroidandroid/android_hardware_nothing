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
import android.app.AlertDialog;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import co.aospa.glyph.Composer.GlyphPattern;
import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.Manager.SettingsManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class GlyphPatternCreatorActivity extends Activity {
    
    private static final String TAG = "GlyphPatternCreator";
    private static final int LED_COUNT = 33; // Nothing Phone 2
    
    private TextView statusText;
    private GridLayout ledGrid;
    private SeekBar brightnessSeeker;
    private SeekBar durationSeeker;
    private TextView brightnessValue;
    private TextView durationValue;
    private LinearLayout framesList;
    private Button addFrameButton;
    private Button previewButton;
    private Button stopPreviewButton;
    private Button saveButton;
    
    private boolean[] selectedZones = new boolean[LED_COUNT];
    private List<FrameData> frames = new ArrayList<>();
    private int currentBrightness = 4095;
    private int currentDuration = 200;
    
    private boolean isPreviewRunning = false;
    private android.os.Handler previewHandler;
    
    private class FrameData {
        long timestamp;
        int[] zones;
        int brightness;
        int duration;
        
        FrameData(long timestamp, int[] zones, int brightness, int duration) {
            this.timestamp = timestamp;
            this.zones = zones;
            this.brightness = brightness;
            this.duration = duration;
        }
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        previewHandler = new android.os.Handler(getMainLooper());
        
        ScrollView scrollView = createLayout();
        setContentView(scrollView);
    }
    
    private ScrollView createLayout() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(40, 100, 40, 40);
        
        TextView title = new TextView(this);
        title.setText("Glyph Pattern Creator");
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        
        statusText = new TextView(this);
        statusText.setText("Select zones and add frames to create your pattern");
        statusText.setTextSize(12);
        statusText.setPadding(0, 0, 0, 20);
        
        TextView templatesLabel = new TextView(this);
        templatesLabel.setText("Quick Templates (15s):");
        templatesLabel.setTextSize(16);
        templatesLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        templatesLabel.setPadding(0, 10, 0, 5);
        
        TextView templatesDesc = new TextView(this);
        templatesDesc.setText("Load a pre-made pattern, then customize if needed");
        templatesDesc.setTextSize(11);
        templatesDesc.setTextColor(0xFF999999);
        templatesDesc.setPadding(0, 0, 0, 10);
        
        LinearLayout templatesLayout = new LinearLayout(this);
        templatesLayout.setOrientation(LinearLayout.HORIZONTAL);
        
        Button waveButton = new Button(this);
        waveButton.setText("Wave");
        waveButton.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        waveButton.setOnClickListener(v -> loadTemplate("wave"));
        
        Button pulseButton = new Button(this);
        pulseButton.setText("Pulse");
        pulseButton.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        pulseButton.setOnClickListener(v -> loadTemplate("pulse"));
        
        Button blinkButton = new Button(this);
        blinkButton.setText("Blink");
        blinkButton.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        blinkButton.setOnClickListener(v -> loadTemplate("blink"));
        
        templatesLayout.addView(waveButton);
        templatesLayout.addView(pulseButton);
        templatesLayout.addView(blinkButton);
        
        LinearLayout templatesLayout2 = new LinearLayout(this);
        templatesLayout2.setOrientation(LinearLayout.HORIZONTAL);
        
        Button breatheButton = new Button(this);
        breatheButton.setText("Breathe");
        breatheButton.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        breatheButton.setOnClickListener(v -> loadTemplate("breathe"));
        
        Button randomButton = new Button(this);
        randomButton.setText("Random");
        randomButton.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        randomButton.setOnClickListener(v -> loadTemplate("random"));
        
        Button allButton = new Button(this);
        allButton.setText("All On");
        allButton.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        allButton.setOnClickListener(v -> loadTemplate("allon"));
        
        templatesLayout2.addView(breatheButton);
        templatesLayout2.addView(randomButton);
        templatesLayout2.addView(allButton);
        
        TextView ledLabel = new TextView(this);
        ledLabel.setText("Select LED Zones:");
        ledLabel.setTextSize(16);
        ledLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        ledLabel.setPadding(0, 10, 0, 10);
        
        ledGrid = createLedGrid();
        
        Button clearButton = new Button(this);
        clearButton.setText("Clear Selection");
        clearButton.setOnClickListener(v -> clearLedSelection());
        
        TextView brightnessLabel = new TextView(this);
        brightnessLabel.setText("Brightness:");
        brightnessLabel.setTextSize(14);
        brightnessLabel.setPadding(0, 20, 0, 5);
        
        LinearLayout brightnessLayout = new LinearLayout(this);
        brightnessLayout.setOrientation(LinearLayout.HORIZONTAL);
        
        brightnessSeeker = new SeekBar(this);
        brightnessSeeker.setMax(4095);
        brightnessSeeker.setProgress(4095);
        brightnessSeeker.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        brightnessSeeker.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentBrightness = progress;
                brightnessValue.setText(String.valueOf(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        brightnessValue = new TextView(this);
        brightnessValue.setText("4095");
        brightnessValue.setTextSize(14);
        brightnessValue.setPadding(10, 0, 0, 0);
        brightnessValue.setMinWidth(100);
        
        brightnessLayout.addView(brightnessSeeker);
        brightnessLayout.addView(brightnessValue);
        
        TextView durationLabel = new TextView(this);
        durationLabel.setText("Duration (ms):");
        durationLabel.setTextSize(14);
        durationLabel.setPadding(0, 10, 0, 5);
        
        LinearLayout durationLayout = new LinearLayout(this);
        durationLayout.setOrientation(LinearLayout.HORIZONTAL);
        
        durationSeeker = new SeekBar(this);
        durationSeeker.setMax(1000);
        durationSeeker.setProgress(200);
        durationSeeker.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        durationSeeker.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentDuration = Math.max(50, progress);
                durationValue.setText(String.valueOf(currentDuration));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        durationValue = new TextView(this);
        durationValue.setText("200");
        durationValue.setTextSize(14);
        durationValue.setPadding(10, 0, 0, 0);
        durationValue.setMinWidth(100);
        
        durationLayout.addView(durationSeeker);
        durationLayout.addView(durationValue);
        
        addFrameButton = new Button(this);
        addFrameButton.setText("Add Frame");
        addFrameButton.setOnClickListener(v -> addFrame());
        LinearLayout.LayoutParams addFrameParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addFrameParams.setMargins(0, 20, 0, 10);
        
        TextView framesLabel = new TextView(this);
        framesLabel.setText("Timeline:");
        framesLabel.setTextSize(16);
        framesLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        framesLabel.setPadding(0, 20, 0, 10);
        
        framesList = new LinearLayout(this);
        framesList.setOrientation(LinearLayout.VERTICAL);
        
        LinearLayout actionLayout = new LinearLayout(this);
        actionLayout.setOrientation(LinearLayout.HORIZONTAL);
        actionLayout.setPadding(0, 20, 0, 0);
        
        previewButton = new Button(this);
        previewButton.setText("Preview");
        previewButton.setEnabled(false);
        previewButton.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        previewButton.setOnClickListener(v -> previewPattern());
        
        stopPreviewButton = new Button(this);
        stopPreviewButton.setText("Stop");
        stopPreviewButton.setVisibility(View.GONE);
        stopPreviewButton.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        stopPreviewButton.setOnClickListener(v -> stopPreview());
        
        saveButton = new Button(this);
        saveButton.setText("Save Pattern");
        saveButton.setEnabled(false);
        saveButton.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        saveButton.setOnClickListener(v -> savePattern());
        
        actionLayout.addView(previewButton);
        actionLayout.addView(stopPreviewButton);
        actionLayout.addView(saveButton);
        
        mainLayout.addView(title);
        mainLayout.addView(statusText);
        mainLayout.addView(templatesLabel);
        mainLayout.addView(templatesDesc);
        mainLayout.addView(templatesLayout);
        mainLayout.addView(templatesLayout2);
        mainLayout.addView(ledLabel);
        mainLayout.addView(ledGrid);
        mainLayout.addView(clearButton);
        mainLayout.addView(brightnessLabel);
        mainLayout.addView(brightnessLayout);
        mainLayout.addView(durationLabel);
        mainLayout.addView(durationLayout);
        mainLayout.addView(addFrameButton, addFrameParams);
        mainLayout.addView(framesLabel);
        mainLayout.addView(framesList);
        mainLayout.addView(actionLayout);
        
        scrollView.addView(mainLayout);
        return scrollView;
    }
    
    private GridLayout createLedGrid() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(8);
        grid.setPadding(10, 10, 10, 10);
        
        for (int i = 0; i < LED_COUNT; i++) {
            final int zone = i;
            
            CheckBox ledBox = new CheckBox(this);
            ledBox.setText(String.valueOf(i));
            ledBox.setTextSize(10);
            ledBox.setPadding(5, 5, 5, 5);
            
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(5, 5, 5, 5);
            
            ledBox.setLayoutParams(params);
            ledBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                selectedZones[zone] = isChecked;
                updateStatus();
            });
            
            grid.addView(ledBox);
        }
        
        return grid;
    }
    
    private void clearLedSelection() {
        for (int i = 0; i < ledGrid.getChildCount(); i++) {
            View child = ledGrid.getChildAt(i);
            if (child instanceof CheckBox) {
                ((CheckBox) child).setChecked(false);
            }
        }
        for (int i = 0; i < LED_COUNT; i++) {
            selectedZones[i] = false;
        }
        updateStatus();
    }
    
    private void loadTemplate(String type) {
        frames.clear();
        framesList.removeAllViews();
        clearLedSelection();
        
        switch (type) {
            case "wave":
                generateWaveTemplate();
                break;
            case "pulse":
                generatePulseTemplate();
                break;
            case "blink":
                generateBlinkTemplate();
                break;
            case "breathe":
                generateBreatheTemplate();
                break;
            case "random":
                generateRandomTemplate();
                break;
            case "allon":
                generateAllOnTemplate();
                break;
        }
        
        refreshFramesList();
        updateStatus();
        previewButton.setEnabled(true);
        saveButton.setEnabled(true);
        
        Toast.makeText(this, "Template loaded: " + type.toUpperCase() + " (15s, " + frames.size() + " frames)", Toast.LENGTH_LONG).show();
    }
    
    private void generateWaveTemplate() {
        int frameCount = 30;
        int ledsPerFrame = 4;
        
        for (int i = 0; i < frameCount; i++) {
            long timestamp = i * 500;
            
            int startZone = (i * ledsPerFrame) % LED_COUNT;
            int[] zones = new int[ledsPerFrame];
            for (int j = 0; j < ledsPerFrame; j++) {
                zones[j] = (startZone + j) % LED_COUNT;
            }
            
            frames.add(new FrameData(timestamp, zones, 4095, 400));
        }
    }
    
    private void generatePulseTemplate() {
        int[] allZones = new int[LED_COUNT];
        for (int i = 0; i < LED_COUNT; i++) {
            allZones[i] = i;
        }
        
        for (int pulse = 0; pulse < 10; pulse++) {
            long baseTime = pulse * 1500;
            
            for (int step = 0; step < 5; step++) {
                long timestamp = baseTime + (step * 60);
                int brightness = 800 + (step * 650);
                frames.add(new FrameData(timestamp, allZones.clone(), brightness, 60));
            }

            frames.add(new FrameData(baseTime + 300, allZones.clone(), 4095, 400));
            
            for (int step = 0; step < 5; step++) {
                long timestamp = baseTime + 700 + (step * 60);
                int brightness = 4095 - (step * 650);
                frames.add(new FrameData(timestamp, allZones.clone(), brightness, 60));
            }
        }
    }
    
    private void generateBlinkTemplate() {
        int[] zones = {0, 5, 10, 15, 20, 25, 30};

        for (int i = 0; i < 30; i++) {
            long timestamp = i * 500;
            
            int[] currentZones;
            if (i % 2 == 0) {
                currentZones = new int[]{0, 2, 4, 6, 8, 10};
            } else {
                currentZones = new int[]{1, 3, 5, 7, 9, 11};
            }
            
            frames.add(new FrameData(timestamp, currentZones, 4095, 250));
        }
    }
    
    private void generateBreatheTemplate() {
        int[] allZones = new int[LED_COUNT];
        for (int i = 0; i < LED_COUNT; i++) {
            allZones[i] = i;
        }

        for (int cycle = 0; cycle < 5; cycle++) {
            long baseTime = cycle * 3000;
            
            // Inhale (10 steps, 1.5s)
            for (int step = 0; step < 10; step++) {
                long timestamp = baseTime + (step * 150);
                int brightness = 500 + (step * 360);
                frames.add(new FrameData(timestamp, allZones.clone(), brightness, 150));
            }
            
            for (int step = 0; step < 10; step++) {
                long timestamp = baseTime + 1500 + (step * 150);
                int brightness = 4095 - (step * 360);
                frames.add(new FrameData(timestamp, allZones.clone(), brightness, 150));
            }
        }
    }
    
    private void generateRandomTemplate() {
        java.util.Random random = new java.util.Random();
        
        for (int i = 0; i < 50; i++) {
            long timestamp = i * 300;
            
            int zoneCount = 3 + random.nextInt(6);
            int[] zones = new int[zoneCount];
            for (int j = 0; j < zoneCount; j++) {
                zones[j] = random.nextInt(LED_COUNT);
            }
            
            int brightness = 2000 + random.nextInt(2096);
            
            frames.add(new FrameData(timestamp, zones, brightness, 250));
        }
    }
    
    private void generateAllOnTemplate() {
        int[] allZones = new int[LED_COUNT];
        for (int i = 0; i < LED_COUNT; i++) {
            allZones[i] = i;
        }
        
        frames.add(new FrameData(0, allZones.clone(), 4095, 5000));
        
        frames.add(new FrameData(5000, allZones.clone(), 3000, 5000));
        
        frames.add(new FrameData(10000, allZones.clone(), 4095, 5000));
    }
    
    private void addFrame() {
        List<Integer> zonesList = new ArrayList<>();
        for (int i = 0; i < selectedZones.length; i++) {
            if (selectedZones[i]) {
                zonesList.add(i);
            }
        }
        
        if (zonesList.isEmpty()) {
            Toast.makeText(this, "Please select at least one zone", Toast.LENGTH_SHORT).show();
            return;
        }
        
        int[] zonesArray = new int[zonesList.size()];
        for (int i = 0; i < zonesList.size(); i++) {
            zonesArray[i] = zonesList.get(i);
        }
        
        long timestamp = 0;
        for (FrameData frame : frames) {
            timestamp += frame.duration;
        }

        FrameData frame = new FrameData(timestamp, zonesArray, currentBrightness, currentDuration);
        frames.add(frame);
        
        addFrameToList(frame, frames.size() - 1);
        
        updateStatus();
        previewButton.setEnabled(true);
        saveButton.setEnabled(true);
        
        Toast.makeText(this, "Frame added at " + timestamp + "ms", Toast.LENGTH_SHORT).show();
    }
    
    private void addFrameToList(FrameData frame, int index) {
        LinearLayout frameLayout = new LinearLayout(this);
        frameLayout.setOrientation(LinearLayout.HORIZONTAL);
        frameLayout.setPadding(10, 10, 10, 10);
        frameLayout.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
        
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        frameParams.setMargins(0, 5, 0, 5);
        
        TextView frameInfo = new TextView(this);
        frameInfo.setText(String.format("Frame %d: %dms\n%d zones, B:%d, D:%dms", 
            index + 1, frame.timestamp, frame.zones.length, frame.brightness, frame.duration));
        frameInfo.setTextSize(12);
        frameInfo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        
        Button deleteButton = new Button(this);
        deleteButton.setText("Delete");
        deleteButton.setOnClickListener(v -> {
            frames.remove(index);
            refreshFramesList();
            if (frames.isEmpty()) {
                previewButton.setEnabled(false);
                saveButton.setEnabled(false);
            }
            updateStatus();
        });
        
        frameLayout.addView(frameInfo);
        frameLayout.addView(deleteButton);
        
        framesList.addView(frameLayout, frameParams);
    }
    
    private void refreshFramesList() {
        framesList.removeAllViews();
        for (int i = 0; i < frames.size(); i++) {
            addFrameToList(frames.get(i), i);
        }
    }
    
    private void updateStatus() {
        int selectedCount = 0;
        for (boolean selected : selectedZones) {
            if (selected) selectedCount++;
        }
        
        long totalDuration = 0;
        for (FrameData frame : frames) {
            totalDuration += frame.duration;
        }
        
        statusText.setText(String.format("Selected: %d zones | Frames: %d | Duration: %.1fs",
            selectedCount, frames.size(), totalDuration / 1000.0));
    }
    
    private void previewPattern() {
        if (frames.isEmpty()) return;
        
        Toast.makeText(this, "Playing preview...", Toast.LENGTH_SHORT).show();
        
        isPreviewRunning = true;
        previewButton.setVisibility(View.GONE);
        stopPreviewButton.setVisibility(View.VISIBLE);
        saveButton.setEnabled(false);
        
        playPreview(0, System.currentTimeMillis());
    }
    
    private void playPreview(int frameIndex, long startTime) {
        if (!isPreviewRunning) {
            onPreviewComplete();
            return;
        }
        
        if (frameIndex >= frames.size()) {
            isPreviewRunning = false;
            onPreviewComplete();
            Toast.makeText(this, "Preview completed", Toast.LENGTH_SHORT).show();
            return;
        }
        
        FrameData frame = frames.get(frameIndex);
        long currentTime = System.currentTimeMillis() - startTime;
        long delay = frame.timestamp - currentTime;
        
        if (delay < 0) delay = 0;
        
        previewHandler.postDelayed(() -> {
            if (!isPreviewRunning) return;
            AnimationManager.playGlyphFrame(this, frame.zones, frame.brightness, frame.duration);
            playPreview(frameIndex + 1, startTime);
        }, delay);
    }
    
    private void stopPreview() {
        if (!isPreviewRunning) return;
        
        Log.d(TAG, "Stopping preview");
        isPreviewRunning = false;
        
        if (previewHandler != null) {
            previewHandler.removeCallbacksAndMessages(null);
        }

        AnimationManager.stopAll();
        
        onPreviewComplete();
        Toast.makeText(this, "Preview stopped", Toast.LENGTH_SHORT).show();
    }
    
    private void onPreviewComplete() {
        runOnUiThread(() -> {
            previewButton.setVisibility(View.VISIBLE);
            stopPreviewButton.setVisibility(View.GONE);
            saveButton.setEnabled(true);
        });
    }
    
    private void savePattern() {
        if (frames.isEmpty()) {
            Toast.makeText(this, "No frames to save", Toast.LENGTH_SHORT).show();
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Save Pattern");
        
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("pattern_name");
        builder.setView(input);
        
        builder.setPositiveButton("Save", (dialog, which) -> {
            String filename = input.getText().toString().trim();
            if (filename.isEmpty()) {
                Toast.makeText(this, "Please enter a filename", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (!filename.endsWith(".glyphring")) {
                filename += ".glyphring";
            }
            
            saveToFile(filename);
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
    
    private void saveToFile(String filename) {
        try {
            JSONObject json = new JSONObject();
            json.put("version", 1);
            json.put("audio_file", filename.replace(".glyphring", ".ogg"));
            
            long totalDuration = 0;
            for (FrameData frame : frames) {
                totalDuration = Math.max(totalDuration, frame.timestamp + frame.duration);
            }
            json.put("duration", totalDuration);
            
            JSONArray framesArray = new JSONArray();
            for (FrameData frame : frames) {
                JSONObject frameJson = new JSONObject();
                frameJson.put("timestamp", frame.timestamp);
                
                JSONArray zonesArray = new JSONArray();
                for (int zone : frame.zones) {
                    zonesArray.put(zone);
                }
                frameJson.put("zones", zonesArray);
                frameJson.put("brightness", frame.brightness);
                frameJson.put("duration", frame.duration);
                
                framesArray.put(frameJson);
            }
            json.put("frames", framesArray);
            
            File baseDir = new File(Environment.getExternalStorageDirectory(), "Ringtones");
            File dir = new File(baseDir, "SavedPattern");
            
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created) {
                    Toast.makeText(this, "Failed to create directory", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to create directory: " + dir.getAbsolutePath());
                    return;
                }
            }

            File file = new File(dir, filename);
            FileWriter writer = new FileWriter(file);
            writer.write(json.toString(2));
            writer.close();
            
            Toast.makeText(this, "Saved to: SavedPattern/" + filename, Toast.LENGTH_LONG).show();
            Log.d(TAG, "Pattern saved: " + file.getAbsolutePath());
            
            showSaveSuccessDialog(file);
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving pattern", e);
            Toast.makeText(this, "Error saving pattern: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void showSaveSuccessDialog(File savedFile) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pattern Saved!");
        builder.setMessage("Pattern saved to:\n" + savedFile.getAbsolutePath() + 
                          "\n\nWhat would you like to do?");
        
        builder.setPositiveButton("Create Another", (dialog, which) -> {
            frames.clear();
            refreshFramesList();
            clearLedSelection();
            previewButton.setEnabled(false);
            saveButton.setEnabled(false);
            updateStatus();
            Toast.makeText(this, "Ready to create new pattern", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNeutralButton("Done", (dialog, which) -> {
            finish();
        });
        
        builder.setNegativeButton("View File", (dialog, which) -> {
            Toast.makeText(this, "File: " + savedFile.getName(), Toast.LENGTH_LONG).show();
        });
        
        builder.show();
    }
}