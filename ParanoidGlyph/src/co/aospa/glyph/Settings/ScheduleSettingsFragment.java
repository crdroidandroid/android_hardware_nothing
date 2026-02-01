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

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.SharedPreferences;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.CompoundButton;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import co.aospa.glyph.Manager.GlyphScheduleManager;
import co.aospa.glyph.R;
import co.aospa.glyph.Utils.ServiceUtils;

import java.util.HashSet;
import java.util.Set;

public class ScheduleSettingsFragment extends SettingsBasePreferenceFragment 
        implements CompoundButton.OnCheckedChangeListener {

    private MainSwitchPreference mScheduleSwitch;
    private Preference mDaysPreference;
    private Preference mStartTimePreference;
    private Preference mEndTimePreference;
    private Preference mStatusPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.glyph_schedule_settings);

        getActivity().setTitle("Glyph Schedule");

        mScheduleSwitch = findPreference("glyph_schedule_enable");
        mDaysPreference = findPreference("glyph_schedule_days");
        mStartTimePreference = findPreference("glyph_schedule_start_time");
        mEndTimePreference = findPreference("glyph_schedule_end_time");
        mStatusPreference = findPreference("glyph_schedule_status");

        if (mScheduleSwitch != null) {
            mScheduleSwitch.setChecked(GlyphScheduleManager.isScheduleEnabled(requireContext()));
            mScheduleSwitch.addOnSwitchChangeListener(this);
        }

        if (mDaysPreference != null) {
            mDaysPreference.setOnPreferenceClickListener(preference -> {
                showDayPickerDialog(preference);
                return true;
            });
        }

        if (mStartTimePreference != null) {
            mStartTimePreference.setOnPreferenceClickListener(preference -> {
                showTimePickerDialog(true);
                return true;
            });
        }

        if (mEndTimePreference != null) {
            mEndTimePreference.setOnPreferenceClickListener(preference -> {
                showTimePickerDialog(false);
                return true;
            });
        }

        updatePreferences();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        GlyphScheduleManager.setScheduleEnabled(requireContext(), isChecked);
        updatePreferences();
        if (!isChecked) {
            ServiceUtils.checkGlyphService();
        }
    }

    private void showTimePickerDialog(boolean isStartTime) {
        int hour, minute;
        
        if (isStartTime) {
            hour = GlyphScheduleManager.getScheduleStartHour(requireContext());
            minute = GlyphScheduleManager.getScheduleStartMinute(requireContext());
        } else {
            hour = GlyphScheduleManager.getScheduleEndHour(requireContext());
            minute = GlyphScheduleManager.getScheduleEndMinute(requireContext());
        }

        boolean is24HourFormat = DateFormat.is24HourFormat(requireContext());

        TimePickerDialog dialog = new TimePickerDialog(
            requireContext(),
            (view, selectedHour, selectedMinute) -> {
                if (isStartTime) {
                    GlyphScheduleManager.setScheduleStartTime(
                        requireContext(), selectedHour, selectedMinute);
                } else {
                    GlyphScheduleManager.setScheduleEndTime(
                        requireContext(), selectedHour, selectedMinute);
                }
                updatePreferences();
            },
            hour,
            minute,
            is24HourFormat
        );

        dialog.setTitle(isStartTime ? "Select Start Time" : "Select End Time");
        dialog.show();
    }

    private void showDayPickerDialog(Preference preference) {
    String[] days = getResources().getStringArray(R.array.day_of_week_names);
    String[] dayValues = getResources().getStringArray(R.array.day_of_week_values);

    // Load current selection from SharedPreferences
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
    Set<String> selected = prefs.getStringSet(preference.getKey(), new HashSet<>());

    boolean[] checked = new boolean[days.length];
    for (int i = 0; i < dayValues.length; i++) {
        checked[i] = selected.contains(dayValues[i]);
    }

    new AlertDialog.Builder(getContext())
        .setTitle(preference.getTitle())
        .setMultiChoiceItems(days, checked, (dialog, which, isChecked) -> {
            checked[which] = isChecked;
        })
        .setOnDismissListener(dialog -> {
            Set<String> newSelected = new HashSet<>();
            for (int i = 0; i < checked.length; i++) {
                if (checked[i]) newSelected.add(dayValues[i]);
            }
            prefs.edit().putStringSet(preference.getKey(), newSelected).apply();
            GlyphScheduleManager.setScheduleDays(requireContext(), newSelected);
            updatePreferences();
        })
        .show();
}


    private void updatePreferences() {
        if (mDaysPreference != null) {
            Set<String> selectedDays = GlyphScheduleManager.getScheduleDays(requireContext());
            mDaysPreference.setSummary(GlyphScheduleManager.getScheduleDaysFormatted(requireContext()));
        }

        if (mStartTimePreference != null) {
            int hour = GlyphScheduleManager.getScheduleStartHour(requireContext());
            int minute = GlyphScheduleManager.getScheduleStartMinute(requireContext());
            mStartTimePreference.setSummary(
                GlyphScheduleManager.formatTime(requireContext(), hour, minute));
        }

        if (mEndTimePreference != null) {
            int hour = GlyphScheduleManager.getScheduleEndHour(requireContext());
            int minute = GlyphScheduleManager.getScheduleEndMinute(requireContext());
            mEndTimePreference.setSummary(
                GlyphScheduleManager.formatTime(requireContext(), hour, minute));
        }

        if (mStatusPreference != null) {
            boolean scheduleEnabled = GlyphScheduleManager.isScheduleEnabled(requireContext());
            boolean scheduleActiveToday = GlyphScheduleManager.isScheduleActiveToday(requireContext());
            boolean scheduleActive = GlyphScheduleManager.isScheduleCurrentlyActive(requireContext());
            
            String status;
            if (!scheduleEnabled) {
                status = "Schedule disabled";
            } else if (!scheduleActiveToday) {
                status = "⏸ Not active today";
            } else if (scheduleActive) {
                status = "⏸ Schedule active - Glyph disabled (Torch still works)";
            } else {
                status = "✓ Schedule inactive - Glyph enabled";
            }
            
            mStatusPreference.setSummary(status);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferences();
    }
}
