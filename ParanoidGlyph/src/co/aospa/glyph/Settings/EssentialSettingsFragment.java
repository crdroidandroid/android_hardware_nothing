/*
 * Copyright (C) 2024-2025 Lunaris AOSP
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
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.util.ArrayUtils;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import co.aospa.glyph.R;
import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.Manager.EssentialLedManager;
import co.aospa.glyph.Utils.ResourceUtils;

public class EssentialSettingsFragment extends SettingsBasePreferenceFragment 
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private static final String TAG = "EssentialSettings";
    private static final boolean DEBUG = true;

    private PreferenceCategory mAppsCategory;
    private PackageManager mPackageManager;
    private Handler mHandler = new Handler();
    
    private int mDefaultLedZone;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.glyph_essential_settings);

        getActivity().setTitle(R.string.glyph_settings_essential_title);

        mAppsCategory = findPreference("glyph_essential_apps");
        mPackageManager = requireActivity().getPackageManager();
        
        mDefaultLedZone = ResourceUtils.getInteger("glyph_settings_notifs_essential_led");
        
        loadApps();
    }

    private void loadApps() {
        if (mAppsCategory == null) return;
        
        mAppsCategory.removeAll();
        
        Set<String> essentialApps = EssentialLedManager.getEssentialApps(requireContext());
        
        List<ApplicationInfo> apps = mPackageManager.getInstalledApplications(PackageManager.GET_GIDS);
        Collections.sort(apps, new ApplicationInfo.DisplayNameComparator(mPackageManager));
        
        for (ApplicationInfo app : apps) {
            if (mPackageManager.getLaunchIntentForPackage(app.packageName) != null 
                    && !ArrayUtils.contains(Constants.APPS_TO_IGNORE, app.packageName)) {
                
                SwitchPreferenceCompat pref = new SwitchPreferenceCompat(requireContext());
                pref.setKey("app_" + app.packageName);
                pref.setTitle(" " + app.loadLabel(mPackageManager).toString());
                pref.setIcon(app.loadIcon(mPackageManager));
                pref.setChecked(essentialApps.contains(app.packageName));
                pref.setOnPreferenceChangeListener(this);
                pref.setOnPreferenceClickListener(this);
                
                updatePreferenceSummary(pref, app.packageName);
                
                mAppsCategory.addPreference(pref);
            }
        }
    }

    private void updatePreferenceSummary(Preference pref, String packageName) {
        int customZone = EssentialLedManager.getAppLedZone(requireContext(), packageName);
        if (customZone == -1) {
            pref.setSummary(getString(R.string.glyph_settings_essential_using_default));
        } else {
            pref.setSummary(getString(R.string.glyph_settings_essential_using_custom, customZone));
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (key.startsWith("app_")) {
            String packageName = key.substring(4);
            boolean enabled = (Boolean) newValue;
            
            if (enabled) {
                EssentialLedManager.addEssentialApp(requireContext(), packageName);
                
                mHandler.postDelayed(() -> {
                    showLedZoneDialog(packageName, preference);
                }, 100);
            } else {
                EssentialLedManager.removeEssentialApp(requireContext(), packageName);
                EssentialLedManager.removeAppLedZone(requireContext(), packageName);
                updatePreferenceSummary(preference, packageName);
            }
            
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        if (key.startsWith("app_") && ((SwitchPreferenceCompat) preference).isChecked()) {
            String packageName = key.substring(4);
            showLedZoneDialog(packageName, preference);
            return true;
        }
        return false;
    }

    private void showLedZoneDialog(String packageName, Preference preference) {
        String appName;
        try {
            ApplicationInfo appInfo = mPackageManager.getApplicationInfo(packageName, 0);
            appName = mPackageManager.getApplicationLabel(appInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            appName = packageName;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(getString(R.string.glyph_settings_essential_zone_dialog_title) + " - " + appName);

        int currentCustomZone = EssentialLedManager.getAppLedZone(requireContext(), packageName);
        
        String[] options = new String[2];
        options[0] = getString(R.string.glyph_settings_essential_zone_default, mDefaultLedZone);
        options[1] = getString(R.string.glyph_settings_essential_zone_custom);
        
        int selectedIndex = (currentCustomZone == -1) ? 0 : 1;
        
        builder.setSingleChoiceItems(options, selectedIndex, (dialog, which) -> {
            if (which == 0) {
                EssentialLedManager.removeAppLedZone(requireContext(), packageName);
                updatePreferenceSummary(preference, packageName);
                
                previewLed(mDefaultLedZone);
                
                dialog.dismiss();
            } else {
                dialog.dismiss();
                
                mHandler.postDelayed(() -> {
                    showLedZoneSelector(packageName, preference);
                }, 100);
            }
        });

        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void showLedZoneSelector(String packageName, Preference preference) {
        int ledCount = ResourceUtils.getInteger("glyph_settings_led_count");
        
        String[] ledZones = new String[ledCount];
        for (int i = 0; i < ledCount; i++) {
            ledZones[i] = "LED " + i;
        }
        
        int currentZone = EssentialLedManager.getAppLedZone(requireContext(), packageName);
        if (currentZone == -1) {
            currentZone = mDefaultLedZone;
        }
        
        final int initialSelection = currentZone;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(R.string.glyph_settings_essential_zone_select_title);
        
        builder.setSingleChoiceItems(ledZones, initialSelection, (dialog, which) -> {
            previewLed(which);
        });
        
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            int selectedZone = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
            
            EssentialLedManager.setAppLedZone(requireContext(), packageName, selectedZone);
            updatePreferenceSummary(preference, packageName);
            
            Toast.makeText(requireContext(), 
                getString(R.string.glyph_settings_essential_current_zone, selectedZone),
                Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton(android.R.string.cancel, null);
        
        builder.show();
    }

    private void previewLed(int zone) {
        if (DEBUG) Log.d(TAG, "Previewing LED zone: " + zone);
        
        Toast.makeText(requireContext(), 
            getString(R.string.glyph_settings_essential_zone_preview, zone),
            Toast.LENGTH_SHORT).show();
        
        int brightness = Constants.MAX_PATTERN_BRIGHTNESS / 100 * 60;
        AnimationManager.singleLedBlink(requireContext(), zone, brightness, 1000);
    }
}
