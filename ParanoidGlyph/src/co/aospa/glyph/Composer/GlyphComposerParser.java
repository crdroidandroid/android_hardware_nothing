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
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class GlyphComposerParser {
    
    private static final String TAG = "GlyphComposerParser";
    private static final boolean DEBUG = true;
    
    public static GlyphPattern parseFromFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            if (DEBUG) Log.e(TAG, "Invalid file path");
            return null;
        }
        
        File file = new File(filePath);
        if (!file.exists() || !file.canRead()) {
            if (DEBUG) Log.e(TAG, "File does not exist or cannot be read: " + filePath);
            return null;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            
            GlyphPattern pattern = parseJson(json.toString());
            if (DEBUG) Log.d(TAG, "Successfully parsed pattern from: " + filePath);
            return pattern;
            
        } catch (IOException e) {
            if (DEBUG) Log.e(TAG, "Error reading file: " + filePath, e);
            return null;
        } catch (JSONException e) {
            if (DEBUG) Log.e(TAG, "Invalid JSON format in: " + filePath, e);
            return null;
        }
    }
    
    public static GlyphPattern parseFromUri(Context context, Uri uri) {
        if (uri == null) {
            if (DEBUG) Log.e(TAG, "Invalid URI");
            return null;
        }
        
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            
            GlyphPattern pattern = parseJson(json.toString());
            if (DEBUG) Log.d(TAG, "Successfully parsed pattern from URI: " + uri);
            return pattern;
            
        } catch (IOException e) {
            if (DEBUG) Log.e(TAG, "Error reading URI: " + uri, e);
            return null;
        } catch (JSONException e) {
            if (DEBUG) Log.e(TAG, "Invalid JSON format from URI: " + uri, e);
            return null;
        }
    }

    private static GlyphPattern parseJson(String jsonString) throws JSONException {
        JSONObject json = new JSONObject(jsonString);
        
        GlyphPattern pattern = new GlyphPattern();
        pattern.setVersion(json.optInt("version", 1));
        pattern.setAudioFile(json.optString("audio_file", ""));
        pattern.setDuration(json.optLong("duration", 0));
        
        JSONArray framesArray = json.getJSONArray("frames");
        List<GlyphPattern.GlyphFrame> frames = new ArrayList<>();
        
        for (int i = 0; i < framesArray.length(); i++) {
            JSONObject frameJson = framesArray.getJSONObject(i);
            GlyphPattern.GlyphFrame frame = new GlyphPattern.GlyphFrame();
            
            frame.setTimestamp(frameJson.getLong("timestamp"));
            frame.setBrightness(frameJson.getInt("brightness"));
            frame.setDuration(frameJson.getInt("duration"));
            
            // Parse zones array
            JSONArray zonesArray = frameJson.getJSONArray("zones");
            int[] zones = new int[zonesArray.length()];
            for (int j = 0; j < zonesArray.length(); j++) {
                zones[j] = zonesArray.getInt(j);
            }
            frame.setZones(zones);
            
            frames.add(frame);
        }
        
        pattern.setFrames(frames);
        return pattern;
    }

    public static String getGlyphPatternPath(String audioPath) {
        if (audioPath == null) return null;
        
        String basePath = audioPath;
        int lastDot = audioPath.lastIndexOf('.');
        if (lastDot > 0) {
            basePath = audioPath.substring(0, lastDot);
        }
        
        String glyphPath = basePath + ".glyphring";
        File glyphFile = new File(glyphPath);
        
        if (glyphFile.exists() && glyphFile.canRead()) {
            if (DEBUG) Log.d(TAG, "Found Glyph pattern file: " + glyphPath);
            return glyphPath;
        }
        
        if (DEBUG) Log.d(TAG, "No Glyph pattern found for: " + audioPath);
        return null;
    }

    public static boolean isValid(GlyphPattern pattern) {
        if (pattern == null) return false;
        if (pattern.getFrames() == null || pattern.getFrames().isEmpty()) return false;
        if (pattern.getDuration() <= 0) return false;
        
        for (GlyphPattern.GlyphFrame frame : pattern.getFrames()) {
            if (frame.getZones() == null || frame.getZones().length == 0) return false;
            if (frame.getBrightness() < 0 || frame.getBrightness() > 4095) return false;
            if (frame.getTimestamp() < 0) return false;
        }
        
        return true;
    }
}