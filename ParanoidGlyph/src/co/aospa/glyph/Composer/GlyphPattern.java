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

import java.util.List;

public class GlyphPattern {
    
    private int version;
    private String audioFile;
    private long duration;
    private List<GlyphFrame> frames;
    
    public GlyphPattern() {
    }
    
    public GlyphPattern(int version, String audioFile, long duration, List<GlyphFrame> frames) {
        this.version = version;
        this.audioFile = audioFile;
        this.duration = duration;
        this.frames = frames;
    }
    
    public int getVersion() {
        return version;
    }
    
    public void setVersion(int version) {
        this.version = version;
    }
    
    public String getAudioFile() {
        return audioFile;
    }
    
    public void setAudioFile(String audioFile) {
        this.audioFile = audioFile;
    }
    
    public long getDuration() {
        return duration;
    }
    
    public void setDuration(long duration) {
        this.duration = duration;
    }
    
    public List<GlyphFrame> getFrames() {
        return frames;
    }
    
    public void setFrames(List<GlyphFrame> frames) {
        this.frames = frames;
    }
    
    public static class GlyphFrame {
        private long timestamp;
        private int[] zones;
        private int brightness;
        private int duration;
        
        public GlyphFrame() {
        }
        
        public GlyphFrame(long timestamp, int[] zones, int brightness, int duration) {
            this.timestamp = timestamp;
            this.zones = zones;
            this.brightness = brightness;
            this.duration = duration;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
        
        public int[] getZones() {
            return zones;
        }
        
        public void setZones(int[] zones) {
            this.zones = zones;
        }
        
        public int getBrightness() {
            return brightness;
        }
        
        public void setBrightness(int brightness) {
            this.brightness = brightness;
        }
        
        public int getDuration() {
            return duration;
        }
        
        public void setDuration(int duration) {
            this.duration = duration;
        }
    }
}