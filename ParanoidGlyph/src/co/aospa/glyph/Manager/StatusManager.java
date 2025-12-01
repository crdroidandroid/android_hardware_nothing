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

package co.aospa.glyph.Manager;

import co.aospa.glyph.Utils.ResourceUtils;

public final class StatusManager {

    private static final String TAG = "GlyphStatusManager";
    private static final boolean DEBUG = true;

    private static boolean allLedActive = false;
    private static boolean animationActive = false;
    private static boolean chargingAnimationActive = false;
    private static boolean volumeAnimationActive = false;
    private static boolean callLedActive = false;
    private static boolean essentialLedActive = false;
    private static int chargingLedLast = 0;
    private static int[] batteryArray = new int[ResourceUtils.getInteger("glyph_settings_battery_levels_num")];
    private static int volumeLedLast = 0;
    private static int[] volumeArray = new int[ResourceUtils.getInteger("glyph_settings_volume_levels_num")];

    private static boolean callLedEnabled = false;

    public static boolean isAnimationActive() {
        return animationActive;
    }

    public static void setAnimationActive(boolean status) {
        animationActive = status;
    }

    public static boolean isChargingAnimationActive() {
        return chargingAnimationActive;
    }

    public static void setChargingAnimationActive(boolean status) {
        chargingAnimationActive = status;
    }

    public static boolean isVolumeAnimationActive() {
        return volumeAnimationActive;
    }

    public static void setVolumeAnimationActive(boolean status) {
        volumeAnimationActive = status;
    }

    public static boolean isAllLedActive() {
        return allLedActive;
    }

    public static void setAllLedsActive(boolean status) {
        allLedActive = status;
    }

    public static boolean isCallLedActive() {
        return callLedActive;
    }

    public static void setCallLedActive(boolean status) {
        callLedActive = status;
    }

    public static boolean isEssentialLedActive() {
        return essentialLedActive;
    }

    public static void setEssentialLedActive(boolean status) {
        essentialLedActive = status;
    }

    public static int getChargingLedLast() {
        return chargingLedLast;
    }

    public static void setChargingLedLast(int last) {
        chargingLedLast = last;
    }

    public static int[] getBatteryArray() {
        return batteryArray;
    }

    public static void setBatteryArray(int[] batteryArrayNext) {
        batteryArray = batteryArrayNext;
    }

    public static int getVolumeLedLast() {
        return volumeLedLast;
    }

    public static void setVolumeLedLast(int last) {
        volumeLedLast = last;
    }

    public static int[] getVolumeArray() {
        return volumeArray;
    }

    public static void setVolumeArray(int[] volumeArrayNext) {
        volumeArray = volumeArrayNext;
    }

    public static boolean isCallLedEnabled() {
        return callLedEnabled;
    }

    public static void setCallLedEnabled(boolean status) {
        callLedEnabled = status;
    }
    
    public static boolean isGlyphIdle() {
        if (isAllLedActive() || isCallLedActive() || isAnimationActive() 
            || isChargingAnimationActive() || isVolumeAnimationActive() || isCallLedEnabled()) {
            return false;
        } else {
            return true;
        }
    }
}
