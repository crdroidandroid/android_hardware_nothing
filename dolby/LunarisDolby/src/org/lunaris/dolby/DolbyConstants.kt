/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby

import android.util.Log

object DolbyConstants {

    const val TAG = "Dolby"
    
    const val PREF_ENABLE = "dolby_enable"
    const val PREF_PROFILE = "dolby_profile"
    const val PREF_PRESET = "dolby_preset"
    const val PREF_IEQ = "dolby_ieq"
    const val PREF_HP_VIRTUALIZER = "dolby_virtualizer"
    const val PREF_SPK_VIRTUALIZER = "dolby_spk_virtualizer"
    const val PREF_STEREO_WIDENING = "dolby_stereo_widening"
    const val PREF_DIALOGUE = "dolby_dialogue_enabled"
    const val PREF_DIALOGUE_AMOUNT = "dolby_dialogue_amount"
    const val PREF_BASS = "dolby_bass"
    const val PREF_BASS_LEVEL = "dolby_bass_level"
    const val PREF_BASS_CURVE = "dolby_bass_curve"
    const val PREF_MID = "dolby_mid"
    const val PREF_MID_LEVEL = "dolby_mid_level"
    const val PREF_TREBLE = "dolby_treble"
    const val PREF_TREBLE_LEVEL = "dolby_treble_level"
    const val PREF_OUTPUT_BOOST_ENABLED = "dolby_output_boost_enabled"
    const val PREF_OUTPUT_BOOST_TENTHS = "dolby_output_boost_tenths"
    const val PREF_VIRTUAL_BASS = "dolby_virtual_bass"
    const val PREF_VIRTUAL_BASS_SPEAKER = "dolby_virtual_bass_speaker"
    const val PREF_VIRTUAL_BASS_BLUETOOTH = "dolby_virtual_bass_bluetooth"
    const val PREF_DIALOGUE_DUCKING_ENABLED = "dolby_dialogue_ducking_enabled"
    const val PREF_DIALOGUE_DUCKING_AMOUNT = "dolby_dialogue_ducking_amount"
    const val PREF_GRAPHIC_EQ_ENABLED = "dolby_graphic_eq_enabled"
    const val PREF_ADV_BASS_ENABLED = "dolby_adv_bass_enabled"
    const val PREF_ADV_BASS_BOOST = "dolby_adv_bass_boost"
    const val PREF_ADV_BASS_CUTOFF = "dolby_adv_bass_cutoff"
    const val PREF_ADV_BASS_WIDTH = "dolby_adv_bass_width"
    const val PREF_VB_MODE = "dolby_vb_mode"
    const val PREF_VB_OVERALL_GAIN = "dolby_vb_overall_gain"
    const val PREF_VB_SLOPE_GAIN = "dolby_vb_slope_gain"
    const val PREF_HP_VIRT_MODE = "dolby_hp_virt_mode"
    const val PREF_HP_VIRT_LR_ANGLE = "dolby_hp_virt_lr_angle"
    const val PREF_HP_VIRT_START_BAND = "dolby_hp_virt_start_band"
    const val PREF_CALIBRATION_BOOST_SPEAKER = "dolby_calibration_boost_speaker"
    const val PREF_CALIBRATION_BOOST_HEADPHONE = "dolby_calibration_boost_headphone"
    const val PREF_CALIBRATION_BOOST_BT = "dolby_calibration_boost_bt"
    const val PREF_PRESETS_MIGRATED = "presets_migrated"
    const val PREF_BAND_MODE = "dolby_band_mode"
    const val PREF_DEVICE_STATE_MEMORY = "device_state_memory_enabled"
    const val PREF_PROFILE_PRIORITY = "profile_priority"
    const val PREF_SCHEDULED_PROFILES = "scheduled_profiles"
    const val PREF_SCHEDULED_PROFILES_ENABLED = "scheduled_profiles_enabled"

    const val PREF_SIMPLE_UI_MODE = "simple_ui_mode"
    const val PREF_AMOLED_THEME = "amoled_theme"
    const val PREF_DYNAMIC_COLOR = "dynamic_color"
    const val PREF_BATTERY_SAVER_MODE = "battery_saver_mode"
    const val PREF_AUTO_DISABLE_ON_CALL = "auto_disable_on_call"
    const val PREF_MEDIA_CONTENT_DETECTION = "media_content_detection"
    const val PREF_MEDIA_PROFILE_MUSIC = "media_profile_music"
    const val PREF_MEDIA_PROFILE_VIDEO = "media_profile_video"
    const val PREF_MEDIA_PROFILE_GAME = "media_profile_game"
    const val PREF_MEDIA_PROFILE_SPEECH = "media_profile_speech"
    const val PREF_MEDIA_PACKAGE_OVERRIDES = "media_package_overrides"
    const val PREF_GAME_LATENCY_MODE = "game_latency_mode"
    const val PREF_FOCUS_MODE_INTEGRATION = "focus_mode_integration"
    const val PREF_FOCUS_PROFILE_ID = "focus_profile_id"
    const val PREF_FOCUS_PREVIOUS_PROFILE = "focus_previous_profile"
    const val PREF_FOCUS_APPLIED = "focus_profile_applied"
    const val PREF_SLEEP_TIMER_END_MS = "sleep_timer_end_ms"
    const val PREF_SLEEP_TIMER_ACTION = "sleep_timer_action"
    const val PREF_SLEEP_TIMER_PROFILE = "sleep_timer_profile"
    const val PREF_WIDGET_SLEEP_MINUTES = "widget_sleep_minutes"
    const val PREF_BT_PROFILE_RULES = "bt_profile_rules"
    const val PREF_LAST_AUTOMATION_SOURCE = "last_automation_source"
    const val PREF_LAST_AUTOMATION_DETAIL = "last_automation_detail"
    const val PREF_ATMOS_CONTENT_ACTIVE = "atmos_content_active"
    const val PREF_PROFILE_HISTORY = "profile_change_history"
    const val PREF_SAFE_LISTENING_LIMIT = "safe_listening_limit"
    const val PREF_UNDO_PROFILE = "undo_profile"
    const val PREF_UNDO_TIMESTAMP = "undo_timestamp"
    const val PREF_SPATIAL_AUDIO_ENABLED = "spatial_audio_enabled"
    const val PREF_VISUALIZER_FFT_MODE = "visualizer_fft_mode"
    const val PREF_APP_PROFILES_EXPORT = "app_profiles_export"

    // Audio engine (all opt-in, user can disable each)
    const val PREF_EXTENDED_EQ_ENABLED = "extended_eq_enabled"
    const val PREF_EQ_RANGE_PRESET = "eq_range_preset"
    const val PREF_EXTENDED_OUTPUT_BOOST_ENABLED = "extended_output_boost_enabled"
    const val PREF_OUTPUT_BOOST_MAX_TENTHS_USER = "output_boost_max_tenths_user"
    const val PREF_HEADROOM_WARNING_ENABLED = "headroom_warning_enabled"
    const val PREF_LOUDNESS_PRESETS_ENABLED = "loudness_presets_enabled"
    const val PREF_STEREO_BALANCE_ENABLED = "stereo_balance_enabled"
    const val PREF_STEREO_BALANCE_VALUE = "stereo_balance_value"
    const val PREF_MONO_MIX_ENABLED = "mono_mix_enabled"
    const val PREF_CROSSFEED_ENABLED = "crossfeed_enabled"
    const val PREF_CROSSFEED_STRENGTH = "crossfeed_strength"
    const val PREF_HIGH_PASS_ENABLED = "high_pass_enabled"
    const val PREF_HIGH_PASS_HZ = "high_pass_hz"
    const val PREF_EQ_COLORED_DB_LABELS = "eq_colored_db_labels"
    const val PREF_LOUDNESS_STACK_VISUAL = "loudness_stack_visual"

    const val ACTION_SET_PROFILE = "org.lunaris.dolby.action.SET_PROFILE"
    const val ACTION_TOGGLE = "org.lunaris.dolby.action.TOGGLE"
    const val ACTION_APPLY_PRESET = "org.lunaris.dolby.action.APPLY_PRESET"
    const val ACTION_SET_ENABLED = "org.lunaris.dolby.action.SET_ENABLED"
    const val ACTION_SLEEP_TIMER = "org.lunaris.dolby.action.SLEEP_TIMER"
    const val EXTRA_PROFILE = "profile"
    const val EXTRA_PRESET = "preset"
    const val EXTRA_ENABLED = "enabled"
    const val EXTRA_ROUTE = "route"

    val WIDGET_SLEEP_MINUTE_OPTIONS = intArrayOf(15, 30, 45, 60, 90)
    const val DEFAULT_WIDGET_SLEEP_MINUTES = 30

    const val SLEEP_ACTION_DISABLE = "disable"
    const val SLEEP_ACTION_PROFILE = "profile"

    const val PROFILE_PRIORITY_DEVICE = "device"
    const val PROFILE_PRIORITY_APP = "app"

    const val UNDO_WINDOW_MS = 30_000L
    const val MAX_HISTORY_ENTRIES = 20
    
    const val PREF_FILE_PRESETS = "presets"
    const val PREF_FILE_CUSTOM_PRESETS = "custom_dolby_presets"

    enum class DsParam(val id: Int, val length: Int = 1) {
        HEADPHONE_VIRTUALIZER(101),
        SPEAKER_VIRTUALIZER(102),
        IEQ_PRESET(104),
        DIALOGUE_ENHANCER_ENABLE(105),
        DIALOGUE_ENHANCER_AMOUNT(108),
        GEQ_BAND_GAINS(110, 20),
        BASS_ENHANCER_ENABLE(111),
        STEREO_WIDENING_AMOUNT(113),
        GRAPHIC_EQ_ENABLE(106),
        DIALOGUE_DUCKING(109),
        VIRTUAL_BASS_ENABLE(112);
        override fun toString(): String = "${name}(${id})"
    }

    const val OUTPUT_BOOST_MIN_TENTHS = -60
    const val OUTPUT_BOOST_MAX_TENTHS = 60
    const val OUTPUT_BOOST_STANDARD_MAX = 60
    const val OUTPUT_BOOST_EXTENDED_MAX = 150
    const val EQ_GAIN_STANDARD = 150
    const val EQ_GAIN_EXTENDED = 240
    const val EQ_GAIN_EXTREME = 300
    const val DIALOGUE_DUCKING_MAX = 16
    // Raw DAX units for the vendor bass-enhancer stage (see configs/dax-default.xml)
    const val ADV_BASS_BOOST_RAW_MAX = 480
    const val ADV_BASS_BOOST_RAW_STOCK = 36
    const val ADV_BASS_CUTOFF_MIN_HZ = 50
    const val ADV_BASS_CUTOFF_MAX_HZ = 1000
    const val ADV_BASS_CUTOFF_STOCK_HZ = 303
    const val ADV_BASS_WIDTH_MIN = 1
    const val ADV_BASS_WIDTH_MAX = 16
    const val ADV_BASS_WIDTH_STOCK = 8
    // Virtual bass (dax-default: mode 3 / overall -187 / slope -2; UI mode clamped 0-2)
    const val VB_MODE_MAX = 2
    const val VB_MODE_STOCK = 0
    const val VB_OVERALL_GAIN_STOCK = -187
    const val VB_OVERALL_GAIN_MIN = -320
    const val VB_OVERALL_GAIN_MAX = 0
    const val VB_SLOPE_GAIN_STOCK = -2
    const val VB_SLOPE_GAIN_MIN = -32
    const val VB_SLOPE_GAIN_MAX = 32
    const val HP_VIRT_LR_ANGLE_MAX = 90
    const val HP_VIRT_START_BAND_MAX = 19
    const val CALIBRATION_BOOST_MAX = 192

    fun dlog(tag: String, msg: String) {
        if (Log.isLoggable(TAG, Log.DEBUG) || Log.isLoggable(tag, Log.DEBUG)) {
            Log.d("$TAG-$tag", msg)
        }
    }

    /** Always-on error log for failures that matter when users cannot share full logcat. */
    fun elog(tag: String, msg: String, error: Throwable? = null) {
        if (error != null) {
            Log.e("$TAG-$tag", msg, error)
        } else {
            Log.e("$TAG-$tag", msg)
        }
    }
}
