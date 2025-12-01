/*
 * Copyright (C) 2015 The CyanogenMod Project
 * Copyright (C) 2017 The LineageOS Project
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

package co.aospa.glyph.Tiles;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import co.aospa.glyph.R;
import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Manager.SettingsManager;
import co.aospa.glyph.Manager.StatusManager;
import co.aospa.glyph.Utils.FileUtils;
import co.aospa.glyph.Utils.ResourceUtils;

public class TorchTileService extends TileService {

    private static final String TAG = "GlyphTorchTile";
    
    private static final String ACTION_UPDATE_TILE = "co.aospa.glyph.UPDATE_TORCH_TILE";

    private BroadcastReceiver mUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateState();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter(ACTION_UPDATE_TILE);
        registerReceiver(mUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(mUpdateReceiver);
        } catch (Exception e) {
        }
        super.onDestroy();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateState();
    }

    private void updateState() {
        if (Constants.CONTEXT == null) {
            Constants.CONTEXT = getApplicationContext();
        }
        
        boolean glyphEnabled = SettingsManager.isGlyphEnabledIgnoreSchedule();
        
        if (!glyphEnabled) {
            getQsTile().setState(Tile.STATE_UNAVAILABLE);
            getQsTile().setSubtitle(getString(R.string.glyph_accessibility_quick_settings_disabled));
            getQsTile().updateTile();
            return;
        }
        
        boolean enabled = getEnabled();
        getQsTile().setSubtitle(enabled ?
                getString(R.string.glyph_accessibility_quick_settings_on) :
                getString(R.string.glyph_accessibility_quick_settings_off));
        getQsTile().setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        getQsTile().updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!SettingsManager.isGlyphEnabledIgnoreSchedule()) {
            return;
        }
        setEnabled(!getEnabled());
        updateState();
    }

    private boolean getEnabled() {
        return StatusManager.isAllLedActive();
    }

    private void setEnabled(boolean enabled) {
        StatusManager.setAllLedsActive(enabled);
        FileUtils.writeAllLed(enabled ? Constants.getMaxBrightness() : 0);
        if (StatusManager.isEssentialLedActive() && !enabled)
            FileUtils.writeSingleLed(
                ResourceUtils.getInteger("glyph_settings_notifs_essential_led"),
                Constants.getMaxBrightness() / 100 * 7);
    }

    public static void requestTileUpdate(Context context) {
        Intent intent = new Intent(ACTION_UPDATE_TILE);
        context.sendBroadcast(intent);
    }
}