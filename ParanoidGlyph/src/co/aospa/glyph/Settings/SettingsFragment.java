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

package co.aospa.glyph.Settings;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.ListPreference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.PrimarySwitchPreference;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import co.aospa.glyph.R;
import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Manager.GlyphScheduleManager;
import co.aospa.glyph.Manager.SettingsManager;
import co.aospa.glyph.Manager.ShakeManager;
import co.aospa.glyph.Utils.ResourceUtils;
import co.aospa.glyph.Utils.ServiceUtils;

public class SettingsFragment extends SettingsBasePreferenceFragment implements OnPreferenceChangeListener,
        OnCheckedChangeListener {

    private MainSwitchPreference mSwitchBar;

    private SwitchPreferenceCompat mFlipPreference;
    private SwitchPreferenceCompat mAutoBrightnessPreference;
    private SeekBarPreference mBrightnessPreference;
    private PrimarySwitchPreference mNotifsPreference;
    private PrimarySwitchPreference mCallPreference;
    private SwitchPreferenceCompat mChargingLevelPreference;
    private SwitchPreferenceCompat mChargingPowersharePreference;
    private SwitchPreferenceCompat mVolumeLevelPreference;
    private SwitchPreferenceCompat mShakeTorchPreference;
    private SeekBarPreference mShakeSensitivityPreference;
    private SwitchPreferenceCompat mMusicVisualizerPreference;
    private ListPreference mFlipRingerModePreference;
    private SwitchPreferenceCompat mComposerEnablePreference;
    private SwitchPreferenceCompat mComposerFallbackPreference;

    private ContentResolver mContentResolver;
    private SettingObserver mSettingObserver;
    private Preference mSchedulePreference;

    private Handler mHandler = new Handler();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.glyph_settings);

        mContentResolver = getActivity().getContentResolver();
        mSettingObserver = new SettingObserver();
        mSettingObserver.register(mContentResolver);

        boolean glyphEnabled = SettingsManager.isGlyphEnabled();

        mSwitchBar = (MainSwitchPreference) findPreference(Constants.GLYPH_ENABLE);
        mSwitchBar.addOnSwitchChangeListener(this);
        mSwitchBar.setChecked(glyphEnabled);

        mFlipPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_FLIP_ENABLE);
        mFlipPreference.setEnabled(glyphEnabled);
        mFlipPreference.setOnPreferenceChangeListener(this);

        mAutoBrightnessPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_AUTO_BRIGHTNESS_ENABLE);
        mAutoBrightnessPreference.setEnabled(glyphEnabled);
        mAutoBrightnessPreference.setOnPreferenceChangeListener(this);
        mAutoBrightnessPreference.setChecked(SettingsManager.isGlyphAutoBrightnessEnabled());
        if (ResourceUtils.getString("glyph_light_sensor").isBlank()) {
            getPreferenceScreen().removePreference(mAutoBrightnessPreference);
        }

        mBrightnessPreference = (SeekBarPreference) findPreference(Constants.GLYPH_BRIGHTNESS);
        if (mAutoBrightnessPreference.isChecked()) {
            mBrightnessPreference.setEnabled(false);
        } else {
            mBrightnessPreference.setEnabled(glyphEnabled);
        }
        mBrightnessPreference.setMin(1);
        mBrightnessPreference.setMax(Constants.getBrightnessLevels().length);
        mBrightnessPreference.setValue(SettingsManager.getGlyphBrightnessSetting());
        mBrightnessPreference.setUpdatesContinuously(true);
        mBrightnessPreference.setOnPreferenceChangeListener(this);

        mNotifsPreference = (PrimarySwitchPreference) findPreference(Constants.GLYPH_NOTIFS_ENABLE);
        mNotifsPreference.setChecked(SettingsManager.isGlyphNotifsEnabled());
        mNotifsPreference.setEnabled(glyphEnabled);
        mNotifsPreference.setSwitchEnabled(glyphEnabled);
        mNotifsPreference.setOnPreferenceChangeListener(this);

        mCallPreference = (PrimarySwitchPreference) findPreference(Constants.GLYPH_CALL_ENABLE);
        mCallPreference.setChecked(SettingsManager.isGlyphCallEnabled());
        mCallPreference.setEnabled(glyphEnabled);
        mCallPreference.setSwitchEnabled(glyphEnabled);
        mCallPreference.setOnPreferenceChangeListener(this);

        mChargingLevelPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_CHARGING_LEVEL_ENABLE);
        mChargingLevelPreference.setEnabled(glyphEnabled);
        mChargingLevelPreference.setOnPreferenceChangeListener(this);

        mChargingPowersharePreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_CHARGING_POWERSHARE_ENABLE);
        mChargingPowersharePreference.setEnabled(glyphEnabled);
        mChargingPowersharePreference.setOnPreferenceChangeListener(this);

        mVolumeLevelPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_VOLUME_LEVEL_ENABLE);
        mVolumeLevelPreference.setEnabled(glyphEnabled);
        mVolumeLevelPreference.setOnPreferenceChangeListener(this);

        mShakeTorchPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_SHAKE_TORCH_ENABLE);
        mShakeTorchPreference.setEnabled(glyphEnabled);
        mShakeTorchPreference.setOnPreferenceChangeListener(this);

        mShakeSensitivityPreference = (SeekBarPreference) findPreference(Constants.GLYPH_SHAKE_SENSITIVITY);
        mShakeSensitivityPreference.setEnabled(glyphEnabled && mShakeTorchPreference.isChecked());
        mShakeSensitivityPreference.setUpdatesContinuously(false);
        mShakeSensitivityPreference.setOnPreferenceChangeListener(this);

        mMusicVisualizerPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_MUSIC_VISUALIZER_ENABLE);
        mMusicVisualizerPreference.setEnabled(glyphEnabled);
        mMusicVisualizerPreference.setOnPreferenceChangeListener(this);

        mFlipRingerModePreference = (ListPreference) findPreference(Constants.GLYPH_FLIP_RINGER_MODE);
        mFlipRingerModePreference.setEnabled(glyphEnabled && mFlipPreference.isChecked());
        mFlipRingerModePreference.setOnPreferenceChangeListener(this);

        mComposerEnablePreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_COMPOSER_ENABLE);
        mComposerEnablePreference.setEnabled(glyphEnabled);
        mComposerEnablePreference.setOnPreferenceChangeListener(this);

        mComposerFallbackPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_COMPOSER_FALLBACK);
        mComposerFallbackPreference.setEnabled(glyphEnabled && mComposerEnablePreference.isChecked());
        mComposerFallbackPreference.setOnPreferenceChangeListener(this);

        mSchedulePreference = (Preference) findPreference(Constants.GLYPH_SCHEDULE);
        updateScheduleSummary();

        mHandler.post(() -> ServiceUtils.checkGlyphService());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String preferenceKey = preference.getKey();

        if (preferenceKey.equals(Constants.GLYPH_CALL_ENABLE)) {
            SettingsManager.setGlyphCallEnabled(!mCallPreference.isChecked());
        }

        if (preferenceKey.equals(Constants.GLYPH_NOTIFS_ENABLE)) {
            SettingsManager.setGlyphNotifsEnabled(!mNotifsPreference.isChecked());
        }

        if (preferenceKey.equals(Constants.GLYPH_AUTO_BRIGHTNESS_ENABLE)) {
            mBrightnessPreference.setEnabled(mAutoBrightnessPreference.isChecked());
        }

        if (preferenceKey.equals(Constants.GLYPH_SHAKE_TORCH_ENABLE)) {
            boolean enabled = (Boolean) newValue;
            mShakeSensitivityPreference.setEnabled(enabled);
            mHandler.postDelayed(() -> {
                if (enabled) {
                    ShakeManager.startShakeService(getContext());
                } else {
                    ShakeManager.stopShakeService(getContext());
                }
            }, 100);
        }

        if (preferenceKey.equals(Constants.GLYPH_SHAKE_SENSITIVITY)) {
            if (mShakeTorchPreference.isChecked()) {
                mHandler.postDelayed(() -> {
                    ShakeManager.restartShakeService(getContext());
                }, 100);
            }
        }

        if (preferenceKey.equals(Constants.GLYPH_FLIP_RINGER_MODE)) {
            int mode = Integer.parseInt((String) newValue);
            Settings.Secure.putInt(mContentResolver, 
                Constants.GLYPH_FLIP_RINGER_MODE, mode);
        }

        if (preferenceKey.equals(Constants.GLYPH_FLIP_ENABLE)) {
            boolean flipEnabled = (Boolean) newValue;
            mFlipRingerModePreference.setEnabled(flipEnabled && SettingsManager.isGlyphEnabled());
        }

        if (preferenceKey.equals(Constants.GLYPH_COMPOSER_ENABLE)) {
            boolean enabled = (Boolean) newValue;
            SettingsManager.setGlyphComposerEnabled(enabled);
            if (mComposerFallbackPreference != null) {
                mComposerFallbackPreference.setEnabled(enabled);
            }
        }

        mHandler.post(() -> ServiceUtils.checkGlyphService());

        return true;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        SettingsManager.enableGlyph(isChecked);

        mSwitchBar.setChecked(isChecked);

        mFlipPreference.setEnabled(isChecked);
        mAutoBrightnessPreference.setEnabled(isChecked);
        mBrightnessPreference.setEnabled(isChecked && !mAutoBrightnessPreference.isChecked());
        mNotifsPreference.setEnabled(isChecked);
        mNotifsPreference.setSwitchEnabled(isChecked);
        mCallPreference.setEnabled(isChecked);
        mCallPreference.setSwitchEnabled(isChecked);
        mChargingLevelPreference.setEnabled(isChecked);
        mChargingPowersharePreference.setEnabled(isChecked);
        mVolumeLevelPreference.setEnabled(isChecked);
        mShakeTorchPreference.setEnabled(isChecked);
        mShakeSensitivityPreference.setEnabled(isChecked && mShakeTorchPreference.isChecked());
        mMusicVisualizerPreference.setEnabled(isChecked);
        mFlipRingerModePreference.setEnabled(isChecked && mFlipPreference.isChecked());
        mComposerEnablePreference.setEnabled(isChecked);
        mComposerFallbackPreference.setEnabled(isChecked && mComposerEnablePreference.isChecked());

        mHandler.post(() -> {
            ServiceUtils.checkGlyphService();
            updateTorchTile();
        });
    }
    
    private void updateTorchTile() {
        try {
            Intent intent = new Intent("co.aospa.glyph.UPDATE_TORCH_TILE");
            requireContext().sendBroadcast(intent);
        } catch (Exception e) {
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (Constants.GLYPH_NOTIFS_ENABLE.equals(preference.getKey())) {
            if (!ServiceUtils.isNotificationServiceEnabled()) {
                new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.glyph_settings_notifs_permission_dialog_title)
                    .setMessage(R.string.glyph_settings_notifs_permission_dialog_message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                        requireContext().startActivity(intent);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
                return true;
            }
        }

        if ("glyph_settings_composer_apply".equals(preference.getKey())) {
            Intent intent = new Intent(getActivity(), GlyphPatternSelectorActivity.class);
            startActivity(intent);
            return true;
        }

        if ("glyph_settings_composer_preview".equals(preference.getKey())) {
            Intent intent = new Intent(getActivity(), GlyphPatternPreviewActivity.class);
            startActivity(intent);
            return true;
        }

        if ("glyph_settings_composer_create".equals(preference.getKey())) {
            Intent intent = new Intent(getActivity(), GlyphPatternCreatorActivity.class);
            startActivity(intent);
            return true;
        }
        
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onDestroy() {
        mSettingObserver.unregister(mContentResolver);
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateScheduleSummary();
    }

    private void updateScheduleSummary() {
        if (mSchedulePreference != null) {
            String summary = GlyphScheduleManager.getScheduleSummary(requireContext());
            mSchedulePreference.setSummary(summary);
        }
    }

    private class SettingObserver extends ContentObserver {
        public SettingObserver() {
            super(new Handler());
        }

        public void register(ContentResolver cr) {
            cr.registerContentObserver(Settings.Secure.getUriFor(
                Constants.GLYPH_ENABLE), false, this);
            cr.registerContentObserver(Settings.Secure.getUriFor(
                Constants.GLYPH_CALL_ENABLE), false, this);
            cr.registerContentObserver(Settings.Secure.getUriFor(
                Constants.GLYPH_NOTIFS_ENABLE), false, this);
        }

        public void unregister(ContentResolver cr) {
            cr.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (uri.equals(Settings.Secure.getUriFor(Constants.GLYPH_ENABLE))) {
                mSwitchBar.setChecked(SettingsManager.isGlyphEnabled());
            }
            if (uri.equals(Settings.Secure.getUriFor(Constants.GLYPH_CALL_ENABLE))) {
                mCallPreference.setChecked(SettingsManager.isGlyphCallEnabled());
            }
            if (uri.equals(Settings.Secure.getUriFor(Constants.GLYPH_NOTIFS_ENABLE))) {
                mNotifsPreference.setChecked(SettingsManager.isGlyphNotifsEnabled());
            }
        }
    }
}
