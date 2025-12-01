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

package co.aospa.glyph.Manager;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.format.DateFormat;
import android.util.Log;

import androidx.preference.PreferenceManager;

import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Utils.ServiceUtils;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public final class GlyphScheduleManager {

    private static final String TAG = "GlyphScheduleManager";
    private static final boolean DEBUG = true;

    private static final String PREF_SCHEDULE_ENABLED = "glyph_schedule_enabled";
    private static final String PREF_SCHEDULE_START_HOUR = "glyph_schedule_start_hour";
    private static final String PREF_SCHEDULE_START_MINUTE = "glyph_schedule_start_minute";
    private static final String PREF_SCHEDULE_END_HOUR = "glyph_schedule_end_hour";
    private static final String PREF_SCHEDULE_END_MINUTE = "glyph_schedule_end_minute";
    private static final String PREF_SCHEDULE_ACTIVE = "glyph_schedule_currently_active";
    private static final String PREF_SCHEDULE_DAYS = "glyph_schedule_days";

    private static final String ACTION_SCHEDULE_START = "co.aospa.glyph.ACTION_SCHEDULE_START";
    private static final String ACTION_SCHEDULE_END = "co.aospa.glyph.ACTION_SCHEDULE_END";

    private static final int REQUEST_CODE_START = 1001;
    private static final int REQUEST_CODE_END = 1002;

    public static final int SUNDAY = Calendar.SUNDAY;
    public static final int MONDAY = Calendar.MONDAY;
    public static final int TUESDAY = Calendar.TUESDAY;
    public static final int WEDNESDAY = Calendar.WEDNESDAY;
    public static final int THURSDAY = Calendar.THURSDAY;
    public static final int FRIDAY = Calendar.FRIDAY;
    public static final int SATURDAY = Calendar.SATURDAY;

    public static boolean isScheduleEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREF_SCHEDULE_ENABLED, false);
    }

    public static void setScheduleEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean(PREF_SCHEDULE_ENABLED, enabled).apply();

        if (enabled) {
            setupScheduleAlarms(context);
            if (isWithinSchedulePeriod(context)) {
                applyScheduleStart(context);
            }
        } else {
            cancelScheduleAlarms(context);
            if (isScheduleCurrentlyActive(context)) {
                setScheduleActive(context, false);
                ServiceUtils.checkGlyphService();
                updateTorchTile(context);
            }
        }
    }

    public static int getScheduleStartHour(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getInt(PREF_SCHEDULE_START_HOUR, 22);
    }

    public static int getScheduleStartMinute(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getInt(PREF_SCHEDULE_START_MINUTE, 0);
    }

    public static int getScheduleEndHour(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getInt(PREF_SCHEDULE_END_HOUR, 7);
    }

    public static int getScheduleEndMinute(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getInt(PREF_SCHEDULE_END_MINUTE, 0);
    }

    public static void setScheduleStartTime(Context context, int hour, int minute) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
            .putInt(PREF_SCHEDULE_START_HOUR, hour)
            .putInt(PREF_SCHEDULE_START_MINUTE, minute)
            .apply();

        if (isScheduleEnabled(context)) {
            setupScheduleAlarms(context);
        }
    }

    public static void setScheduleEndTime(Context context, int hour, int minute) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
            .putInt(PREF_SCHEDULE_END_HOUR, hour)
            .putInt(PREF_SCHEDULE_END_MINUTE, minute)
            .apply();

        if (isScheduleEnabled(context)) {
            setupScheduleAlarms(context);
        }
    }

    public static Set<String> getScheduleDays(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> defaultDays = new HashSet<>();
        defaultDays.add(String.valueOf(MONDAY));
        defaultDays.add(String.valueOf(TUESDAY));
        defaultDays.add(String.valueOf(WEDNESDAY));
        defaultDays.add(String.valueOf(THURSDAY));
        defaultDays.add(String.valueOf(FRIDAY));
        defaultDays.add(String.valueOf(SATURDAY));
        defaultDays.add(String.valueOf(SUNDAY));
        return prefs.getStringSet(PREF_SCHEDULE_DAYS, defaultDays);
    }

    public static void setScheduleDays(Context context, Set<String> days) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putStringSet(PREF_SCHEDULE_DAYS, days).apply();

        if (isScheduleEnabled(context)) {
            setupScheduleAlarms(context);
        }
    }

    public static boolean isScheduleActiveToday(Context context) {
        if (!isScheduleEnabled(context)) {
            return false;
        }

        Calendar now = Calendar.getInstance();
        int currentDay = now.get(Calendar.DAY_OF_WEEK);
        
        Set<String> enabledDays = getScheduleDays(context);
        return enabledDays.contains(String.valueOf(currentDay));
    }

    public static boolean isScheduleCurrentlyActive(Context context) {
        if (!isScheduleEnabled(context)) {
            return false;
        }

        if (!isScheduleActiveToday(context)) {
            return false;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREF_SCHEDULE_ACTIVE, false);
    }

    public static boolean isWithinSchedulePeriod(Context context) {
        if (!isScheduleActiveToday(context)) {
            return false;
        }

        Calendar now = Calendar.getInstance();
        int currentHour = now.get(Calendar.HOUR_OF_DAY);
        int currentMinute = now.get(Calendar.MINUTE);

        int startHour = getScheduleStartHour(context);
        int startMinute = getScheduleStartMinute(context);
        int endHour = getScheduleEndHour(context);
        int endMinute = getScheduleEndMinute(context);

        int currentTotalMinutes = currentHour * 60 + currentMinute;
        int startTotalMinutes = startHour * 60 + startMinute;
        int endTotalMinutes = endHour * 60 + endMinute;

        if (startTotalMinutes < endTotalMinutes) {
            return currentTotalMinutes >= startTotalMinutes && currentTotalMinutes < endTotalMinutes;
        } else {
            return currentTotalMinutes >= startTotalMinutes || currentTotalMinutes < endTotalMinutes;
        }
    }

    private static void setScheduleActive(Context context, boolean active) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean(PREF_SCHEDULE_ACTIVE, active).apply();
    }

    /**
     * Setup schedule alarms
     */
    public static void setupScheduleAlarms(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager is null");
            return;
        }

        cancelScheduleAlarms(context);

        Intent startIntent = new Intent(context, ScheduleReceiver.class);
        startIntent.setAction(ACTION_SCHEDULE_START);

        Intent endIntent = new Intent(context, ScheduleReceiver.class);
        endIntent.setAction(ACTION_SCHEDULE_END);

        PendingIntent startPendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_START, startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent endPendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_END, endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long startTriggerTime = calculateNextTriggerTime(
            getScheduleStartHour(context),
            getScheduleStartMinute(context)
        );

        long endTriggerTime = calculateNextTriggerTime(
            getScheduleEndHour(context),
            getScheduleEndMinute(context)
        );

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            startTriggerTime,
            AlarmManager.INTERVAL_DAY,
            startPendingIntent
        );

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            endTriggerTime,
            AlarmManager.INTERVAL_DAY,
            endPendingIntent
        );

        if (isWithinSchedulePeriod(context)) {
            applyScheduleStart(context);
        } else {
            applyScheduleEnd(context);
        }

        if (DEBUG) {
            Log.d(TAG, "Start alarm set for: " + new java.util.Date(startTriggerTime));
            Log.d(TAG, "End alarm set for: " + new java.util.Date(endTriggerTime));
        }
    }

    public static void cancelScheduleAlarms(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent startIntent = new Intent(context, ScheduleReceiver.class);
        startIntent.setAction(ACTION_SCHEDULE_START);

        Intent endIntent = new Intent(context, ScheduleReceiver.class);
        endIntent.setAction(ACTION_SCHEDULE_END);

        PendingIntent startPendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_START, startIntent,
            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent endPendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_END, endIntent,
            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (startPendingIntent != null) {
            alarmManager.cancel(startPendingIntent);
            startPendingIntent.cancel();
        }

        if (endPendingIntent != null) {
            alarmManager.cancel(endPendingIntent);
            endPendingIntent.cancel();
        }
    }

    private static long calculateNextTriggerTime(int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        return calendar.getTimeInMillis();
    }

    public static void applyScheduleStart(Context context) {
        setScheduleActive(context, true);
        ServiceUtils.checkGlyphService();
        updateTorchTile(context);
    }

    public static void applyScheduleEnd(Context context) {
        setScheduleActive(context, false);
        ServiceUtils.checkGlyphService();
        updateTorchTile(context);
    }
    
    private static void updateTorchTile(Context context) {
        try {
            Intent intent = new Intent("co.aospa.glyph.UPDATE_TORCH_TILE");
            context.sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to update torch tile", e);
        }
    }

    /**
     * Format time based on system 12/24 hour preference
     */
    public static String formatTime(Context context, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        
        // Use system preference for 12/24 hour format
        java.text.DateFormat timeFormat = DateFormat.getTimeFormat(context);
        return timeFormat.format(calendar.getTime());
    }

    /**
     * Format time with explicit 24-hour format (for backwards compatibility)
     */
    public static String formatTime24Hour(int hour, int minute) {
        return String.format("%02d:%02d", hour, minute);
    }

    public static String getScheduleDaysFormatted(Context context) {
        Set<String> days = getScheduleDays(context);
        
        if (days.size() == 7) {
            return "Every day";
        }
        
        if (days.size() == 0) {
            return "No days selected";
        }
        
        Set<String> weekdays = new HashSet<>();
        weekdays.add(String.valueOf(MONDAY));
        weekdays.add(String.valueOf(TUESDAY));
        weekdays.add(String.valueOf(WEDNESDAY));
        weekdays.add(String.valueOf(THURSDAY));
        weekdays.add(String.valueOf(FRIDAY));
        
        if (days.equals(weekdays)) {
            return "Weekdays (Mon-Fri)";
        }
        
        Set<String> weekends = new HashSet<>();
        weekends.add(String.valueOf(SATURDAY));
        weekends.add(String.valueOf(SUNDAY));
        
        if (days.equals(weekends)) {
            return "Weekends (Sat-Sun)";
        }
        
        StringBuilder result = new StringBuilder();
        String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        int[] dayOrder = {SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY};
        
        for (int i = 0; i < dayOrder.length; i++) {
            if (days.contains(String.valueOf(dayOrder[i]))) {
                if (result.length() > 0) {
                    result.append(", ");
                }
                result.append(dayNames[i]);
            }
        }
        
        return result.toString();
    }

    public static String getScheduleTimeRange(Context context) {
        int startHour = getScheduleStartHour(context);
        int startMinute = getScheduleStartMinute(context);
        int endHour = getScheduleEndHour(context);
        int endMinute = getScheduleEndMinute(context);
        return formatTime(context, startHour, startMinute) + " - " + formatTime(context, endHour, endMinute);
    }

    public static String getScheduleSummary(Context context) {
        if (!isScheduleEnabled(context)) {
            return "Schedule disabled";
        }
        
        String days = getScheduleDaysFormatted(context);
        String time = getScheduleTimeRange(context);
        
        return days + " • " + time;
    }

    public static class ScheduleReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;

            Constants.CONTEXT = context.getApplicationContext();
            if (!isScheduleEnabled(context)) {
                return;
            }
            if (!isScheduleActiveToday(context)) {
                return;
            }
            String action = intent.getAction();
            if (ACTION_SCHEDULE_START.equals(action)) {
                applyScheduleStart(context);
            } else if (ACTION_SCHEDULE_END.equals(action)) {
                applyScheduleEnd(context);
            }
        }
    }
}