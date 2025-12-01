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
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import co.aospa.glyph.Composer.GlyphComposerParser;
import co.aospa.glyph.Composer.GlyphPattern;
import co.aospa.glyph.Manager.SettingsManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class GlyphPatternSelectorActivity extends Activity {
    
    private static final String TAG = "GlyphPatternSelector";
    
    private TextView statusText;
    private TextView currentRingtoneText;
    private TextView currentPatternText;
    private ListView patternListView;
    private Button selectPatternButton;
    
    private List<File> patternFiles = new ArrayList<>();
    private File currentRingtoneFile = null;
    private File currentPatternFile = null;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        ScrollView scrollView = createLayout();
        setContentView(scrollView);
        
        loadCurrentRingtone();
        scanPatterns();
    }
    
    private ScrollView createLayout() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(40, 100, 40, 40);

        TextView title = new TextView(this);
        title.setText("Apply Glyph Pattern");
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 20);

        statusText = new TextView(this);
        statusText.setText("Select a pattern to apply to your current ringtone");
        statusText.setTextSize(12);
        statusText.setPadding(0, 0, 0, 20);

        TextView ringtoneLabel = new TextView(this);
        ringtoneLabel.setText("Current Ringtone:");
        ringtoneLabel.setTextSize(16);
        ringtoneLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        ringtoneLabel.setPadding(0, 10, 0, 5);
        
        currentRingtoneText = new TextView(this);
        currentRingtoneText.setText("Loading...");
        currentRingtoneText.setTextSize(14);
        currentRingtoneText.setPadding(10, 5, 10, 15);

        TextView patternStatusLabel = new TextView(this);
        patternStatusLabel.setText("Current Pattern:");
        patternStatusLabel.setTextSize(16);
        patternStatusLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        patternStatusLabel.setPadding(0, 10, 0, 5);
        
        currentPatternText = new TextView(this);
        currentPatternText.setText("None");
        currentPatternText.setTextSize(14);
        currentPatternText.setPadding(10, 5, 10, 15);

        TextView availableLabel = new TextView(this);
        availableLabel.setText("Available Patterns:");
        availableLabel.setTextSize(16);
        availableLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        availableLabel.setPadding(0, 20, 0, 10);
        
        patternListView = new ListView(this);
        patternListView.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            400
        ));
        
        selectPatternButton = new Button(this);
        selectPatternButton.setText("Apply Selected Pattern");
        selectPatternButton.setEnabled(false);
        selectPatternButton.setOnClickListener(v -> applySelectedPattern());
        
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.setMargins(0, 20, 0, 10);
        
        Button removeButton = new Button(this);
        removeButton.setText("Remove Current Pattern");
        removeButton.setOnClickListener(v -> removeCurrentPattern());

        mainLayout.addView(title);
        mainLayout.addView(statusText);
        mainLayout.addView(ringtoneLabel);
        mainLayout.addView(currentRingtoneText);
        mainLayout.addView(patternStatusLabel);
        mainLayout.addView(currentPatternText);
        mainLayout.addView(availableLabel);
        mainLayout.addView(patternListView);
        mainLayout.addView(selectPatternButton, buttonParams);
        mainLayout.addView(removeButton);
        
        scrollView.addView(mainLayout);
        return scrollView;
    }
    
    private void loadCurrentRingtone() {
        Uri ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE);
        
        if (ringtoneUri == null) {
            currentRingtoneText.setText("No ringtone set");
            statusText.setText("Please set a ringtone first");
            return;
        }
        
        String audioPath = getRealPathFromUri(ringtoneUri);
        
        if (audioPath == null) {
            currentRingtoneText.setText(ringtoneUri.getLastPathSegment());
            statusText.setText("⚠ Cannot access ringtone file");
            return;
        }
        
        currentRingtoneFile = new File(audioPath);
        currentRingtoneText.setText(currentRingtoneFile.getName());
        
        String patternPath = GlyphComposerParser.getGlyphPatternPath(audioPath);
        if (patternPath != null) {
            currentPatternFile = new File(patternPath);
            String appliedPatternInfo = findOriginalPatternName(currentPatternFile);
            currentPatternText.setText("✓ Applied: " + appliedPatternInfo);
            currentPatternText.setTextColor(0xFF00FF00);
        } else {
            currentPatternText.setText("None (using fallback animation)");
            currentPatternText.setTextColor(0xFFFF9800);
        }
    }

    private String findOriginalPatternName(File appliedPattern) {
        File savedPatternDir = new File(Environment.getExternalStorageDirectory(), "Ringtones/SavedPattern");
        
        if (!savedPatternDir.exists()) {
            return appliedPattern.getName();
        }
        
        File[] savedPatterns = savedPatternDir.listFiles((dir, name) -> name.endsWith(".glyphring"));
        if (savedPatterns == null) {
            return appliedPattern.getName();
        }
        
        try {
            GlyphPattern appliedParsed = GlyphComposerParser.parseFromFile(appliedPattern.getAbsolutePath());
            if (appliedParsed == null) return appliedPattern.getName();
            
            for (File savedPattern : savedPatterns) {
                GlyphPattern savedParsed = GlyphComposerParser.parseFromFile(savedPattern.getAbsolutePath());
                if (savedParsed != null && patternsMatch(appliedParsed, savedParsed)) {
                    return savedPattern.getName();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error comparing patterns", e);
        }
        
        return appliedPattern.getName();
    }
    
    private boolean patternsMatch(GlyphPattern p1, GlyphPattern p2) {
        if (p1.getFrames().size() != p2.getFrames().size()) return false;
        if (p1.getDuration() != p2.getDuration()) return false;
        return true;
    }
    
    private void scanPatterns() {
        patternFiles.clear();
        
        File savedPatternDir = new File(Environment.getExternalStorageDirectory(), "Ringtones/SavedPattern");
        
        if (!savedPatternDir.exists()) {
            statusText.setText("No patterns found. Create some patterns first!");
            return;
        }
        
        File[] files = savedPatternDir.listFiles((dir, name) -> name.endsWith(".glyphring"));
        
        if (files == null || files.length == 0) {
            statusText.setText("No patterns found in SavedPattern folder");
            return;
        }
        
        List<String> patternNames = new ArrayList<>();
        for (File file : files) {
            patternFiles.add(file);

            GlyphPattern pattern = GlyphComposerParser.parseFromFile(file.getAbsolutePath());
            String info = file.getName();
            if (pattern != null) {
                info += "\n  " + pattern.getFrames().size() + " frames, " + 
                       (pattern.getDuration() / 1000) + "s";
            }
            patternNames.add(info);
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, 
            android.R.layout.simple_list_item_single_choice,
            patternNames
        );
        
        patternListView.setAdapter(adapter);
        patternListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        patternListView.setOnItemClickListener((parent, view, position, id) -> {
            selectPatternButton.setEnabled(true);
        });
        
        statusText.setText("Found " + patternFiles.size() + " pattern(s). Select one to apply.");
    }
    
    private void applySelectedPattern() {
        int selectedPosition = patternListView.getCheckedItemPosition();
        
        if (selectedPosition == -1) {
            Toast.makeText(this, "Please select a pattern", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (currentRingtoneFile == null) {
            Toast.makeText(this, "No ringtone file found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        File selectedPattern = patternFiles.get(selectedPosition);
        
        new AlertDialog.Builder(this)
            .setTitle("Apply Pattern")
            .setMessage("Apply pattern '" + selectedPattern.getName() + "' to ringtone '" + 
                       currentRingtoneFile.getName() + "'?")
            .setPositiveButton("Apply", (dialog, which) -> {
                applyPattern(selectedPattern);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void applyPattern(File patternFile) {
        try {
            File ringtoneDir = currentRingtoneFile.getParentFile();
            
            String ringtoneName = currentRingtoneFile.getName();
            String baseName = ringtoneName.substring(0, ringtoneName.lastIndexOf('.'));
            String targetPatternName = baseName + ".glyphring";
            
            File targetPattern = new File(ringtoneDir, targetPatternName);
            
            copyFile(patternFile, targetPattern);
            
            currentPatternFile = targetPattern;
            currentPatternText.setText("✓ " + targetPattern.getName());
            currentPatternText.setTextColor(0xFF00FF00);
            
            Toast.makeText(this, "Pattern applied successfully!", Toast.LENGTH_LONG).show();
            Log.d(TAG, "Pattern applied: " + targetPattern.getAbsolutePath());
            
            new AlertDialog.Builder(this)
                .setTitle("Success!")
                .setMessage("Pattern applied to your ringtone!\n\n" +
                           "Test it by making a call or use 'Preview patterns' to see it.")
                .setPositiveButton("Preview", (dialog, which) -> {
                    Intent intent = new Intent(this, GlyphPatternPreviewActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton("Done", null)
                .show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error applying pattern", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void removeCurrentPattern() {
        if (currentPatternFile == null || !currentPatternFile.exists()) {
            Toast.makeText(this, "No pattern to remove", Toast.LENGTH_SHORT).show();
            return;
        }
        
        new AlertDialog.Builder(this)
            .setTitle("Remove Pattern")
            .setMessage("Remove pattern from current ringtone?\n\n" +
                       "The pattern file in SavedPattern folder will NOT be deleted.")
            .setPositiveButton("Remove", (dialog, which) -> {
                if (currentPatternFile.delete()) {
                    currentPatternFile = null;
                    currentPatternText.setText("None (using fallback animation)");
                    currentPatternText.setTextColor(0xFFFF9800);
                    Toast.makeText(this, "Pattern removed", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to remove pattern", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void copyFile(File source, File dest) throws Exception {
        try (FileChannel sourceChannel = new FileInputStream(source).getChannel();
             FileChannel destChannel = new FileOutputStream(dest).getChannel()) {
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        }
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
}