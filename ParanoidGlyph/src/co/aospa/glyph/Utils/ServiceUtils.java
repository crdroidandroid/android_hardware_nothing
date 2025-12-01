/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2019 The LineageOS Project
 *               2020-2024 Paranoid Android
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

package co.aospa.glyph.Utils;

import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.Manager.SettingsManager;
import co.aospa.glyph.Manager.ShakeManager;
import co.aospa.glyph.Manager.StatusManager;
import co.aospa.glyph.Services.AutoBrightnessService;
import co.aospa.glyph.Services.CallReceiverService;
import co.aospa.glyph.Services.ChargingService;
import co.aospa.glyph.Services.FlipToGlyphService;
import co.aospa.glyph.Services.MusicVisualizerService;
import co.aospa.glyph.Services.PowershareService;
import co.aospa.glyph.Services.ThirdPartyService;
import co.aospa.glyph.Services.VolumeLevelService;

public final class ServiceUtils {

    private static final String TAG = "GlyphServiceUtils";
    private static final boolean DEBUG = true;

    private static Context context = Constants.CONTEXT;

    public static boolean isNotificationServiceEnabled() {
        String pkgName = context.getPackageName();
        final String flat = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_NOTIFICATION_LISTENERS);
        if (flat != null) {
            String[] names = flat.split(":");
            for (String name : names) {
                ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void startCallReceiverService() {
        if (DEBUG) Log.d(TAG, "Starting Glyph call receiver service");
        context.startServiceAsUser(new Intent(context, CallReceiverService.class),
                UserHandle.CURRENT);
    }

    private static void stopCallReceiverService() {
        if (DEBUG) Log.d(TAG, "Stopping Glyph call receiver service");
        context.stopServiceAsUser(new Intent(context, CallReceiverService.class),
                UserHandle.CURRENT);
    }

    private static void startChargingService() {
        if (DEBUG) Log.d(TAG, "Starting Glyph charging service");
        context.startServiceAsUser(new Intent(context, ChargingService.class),
                UserHandle.CURRENT);
    }

    private static void stopChargingService() {
        if (DEBUG) Log.d(TAG, "Stopping Glyph charging service");
        context.stopServiceAsUser(new Intent(context, ChargingService.class),
                UserHandle.CURRENT);
    }

    private static void startFlipToGlyphService() {
        if (DEBUG) Log.d(TAG, "Starting Flip to Glyph service");
        context.startServiceAsUser(new Intent(context, FlipToGlyphService.class),
                UserHandle.CURRENT);
    }

    private static void stopFlipToGlyphService() {
        if (DEBUG) Log.d(TAG, "Stopping Flip to Glyph service");
        context.stopServiceAsUser(new Intent(context, FlipToGlyphService.class),
                UserHandle.CURRENT);
    }

    public static void startMusicVisualizerService() {
        if (DEBUG) Log.d(TAG, "Starting Music Visualizer service");
        context.startServiceAsUser(new Intent(context, MusicVisualizerService.class),
                UserHandle.CURRENT);
    }

    protected static void stopMusicVisualizerService() {
        if (DEBUG) Log.d(TAG, "Stopping Music Visualizer service");
        context.stopServiceAsUser(new Intent(context, MusicVisualizerService.class),
                UserHandle.CURRENT);
    }

    private static void startPowershareService() {
        if (DEBUG) Log.d(TAG, "Starting Glyph powershare service");
        context.startServiceAsUser(new Intent(context, PowershareService.class),
                UserHandle.CURRENT);
    }

    private static void stopPowershareService() {
        if (DEBUG) Log.d(TAG, "Stopping Glyph powershare service");
        context.stopServiceAsUser(new Intent(context, PowershareService.class),
                UserHandle.CURRENT);
    }

    public static void startVolumeLevelService() {
        if (DEBUG) Log.d(TAG, "Starting Volume Level service");
        context.startServiceAsUser(new Intent(context, VolumeLevelService.class),
                UserHandle.CURRENT);
    }

    protected static void stopVolumeLevelService() {
        if (DEBUG) Log.d(TAG, "Stopping Volume Listener service");
        context.stopServiceAsUser(new Intent(context, VolumeLevelService.class),
                UserHandle.CURRENT);
    }

    private static void startAutoBrightnessService() {
        if (DEBUG) Log.d(TAG, "Starting Auto Brightness service");
        context.startServiceAsUser(new Intent(context, AutoBrightnessService.class),
                UserHandle.CURRENT);
    }

    private static void stopAutoBrightnessService() {
        if (DEBUG) Log.d(TAG, "Stopping Auto Brightness service");
        context.stopServiceAsUser(new Intent(context, AutoBrightnessService.class),
                UserHandle.CURRENT);
    }

    public static void startThirdPartyService() {
        if (DEBUG) Log.d(TAG, "Starting ThirdParty service");
        context.startServiceAsUser(new Intent(context, ThirdPartyService.class),
                UserHandle.CURRENT);
    }

    protected static void stopThirdPartyService() {
        if (DEBUG) Log.d(TAG, "Stopping ThirdParty service");
        context.stopServiceAsUser(new Intent(context, ThirdPartyService.class),
                UserHandle.CURRENT);
    }

    public static void checkGlyphService() {
        if (SettingsManager.getGlyphBrightness() != Constants.getBrightness()) {
            Constants.setBrightness(SettingsManager.getGlyphBrightness());
            startThirdPartyService();
            if (StatusManager.isEssentialLedActive())
                AnimationManager.playEssential();
        }
        
        boolean glyphEnabled = SettingsManager.isGlyphEnabled();
        
        boolean glyphBaseEnabled = SettingsManager.isGlyphEnabledIgnoreSchedule();
        
        if (glyphEnabled) {
            if (SettingsManager.isGlyphChargingEnabled()) {
                startChargingService();
            } else {
                stopChargingService();
            }
            if (SettingsManager.isGlyphPowershareEnabled()) {
                startPowershareService();
            } else {
                stopPowershareService();
            }
            if (SettingsManager.isGlyphCallEnabled()) {
                startCallReceiverService();
            } else {
                stopCallReceiverService();
            }
            if (SettingsManager.isGlyphFlipEnabled()) {
                startFlipToGlyphService();
            } else {
                stopFlipToGlyphService();
            }
            if (SettingsManager.isGlyphMusicVisualizerEnabled()) {
                startMusicVisualizerService();
            } else {
                stopMusicVisualizerService();
            }
            if (SettingsManager.isGlyphVolumeLevelEnabled()) {
                startVolumeLevelService();
            } else {
                stopVolumeLevelService();
            }
            if (SettingsManager.isGlyphAutoBrightnessEnabled()) {
                startAutoBrightnessService();
            } else {
                stopAutoBrightnessService();
            }
        } else {
            stopChargingService();
            stopPowershareService();
            stopCallReceiverService();
            stopFlipToGlyphService();
            stopMusicVisualizerService();
            stopVolumeLevelService();
            stopAutoBrightnessService();
        }
        
        if (glyphBaseEnabled && ShakeManager.isShakeEnabled(context)) {
            ShakeManager.startShakeService(context);
        } else {
            ShakeManager.stopShakeService(context);
        }
        
        if (glyphBaseEnabled) {
            startThirdPartyService();
        } else {
            stopThirdPartyService();
        }
    }
}