/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.domain.models

enum class BandMode(val value: String, val displayName: String, val bandCount: Int) {
    TEN_BAND("10", "10 Bands", 10),
    FIFTEEN_BAND("15", "15 Bands", 15),
    TWENTY_BAND("20", "20 Bands", 20);
    
    companion object {
        fun fromValue(value: String): BandMode {
            return values().find { it.value == value } ?: TEN_BAND
        }
    }
}

data class DolbyProfile(
    val id: Int,
    val nameResId: Int
)

data class DolbySettings(
    val enabled: Boolean = true,
    val currentProfile: Int = 0,
    val bassEnhancerEnabled: Boolean = false,
    val bandMode: BandMode = BandMode.TEN_BAND
)

data class ProfileSettings(
    val profile: Int,
    val ieqPreset: Int = 0,
    val headphoneVirtualizerEnabled: Boolean = false,
    val speakerVirtualizerEnabled: Boolean = false,
    val stereoWideningAmount: Int = 32,
    val dialogueEnhancerEnabled: Boolean = false,
    val dialogueEnhancerAmount: Int = 6,
    val graphicEqEnabled: Boolean = true,
    val dialogueDuckingEnabled: Boolean = false,
    val dialogueDuckingAmount: Int = 8,
    val bassLevel: Int = 0,
    val midLevel: Int = 0,
    val trebleLevel: Int = 0,
    val bassCurve: Int = 0,
    val outputBoostEnabled: Boolean = false,
    val outputBoostTenthsDb: Int = 0,
    val virtualBassBluetoothEnabled: Boolean = false,
    val virtualBassMode: Int = 0,
    val virtualBassOverallGain: Int = -187,
    val virtualBassSlopeGain: Int = -2,
    val hpVirtMode: Int = 0,
    val hpVirtLrAngle: Int = 45,
    val hpVirtStartBand: Int = 0
)

data class EqualizerPreset(
    val name: String,
    val bandGains: List<BandGain>,
    val isUserDefined: Boolean = false,
    val isCustom: Boolean = false,
    val bandMode: BandMode = BandMode.TEN_BAND
)

data class BandGain(
    val frequency: Int,
    val gain: Int = 0
)

enum class AudioDeviceCategory {
    SPEAKER,
    WIRED,
    BLUETOOTH,
    USB,
    OTHER
}

data class ActiveAudioDevice(
    val name: String,
    val category: AudioDeviceCategory
) {
    val isOnSpeaker: Boolean
        get() = category == AudioDeviceCategory.SPEAKER

    companion object {
        val Unknown = ActiveAudioDevice("Unknown", AudioDeviceCategory.OTHER)
    }
}

data class DeviceSnapshotSummary(
    val deviceKey: String,
    val displayName: String,
    val profile: Int,
    val dolbyEnabled: Boolean,
    val isCurrentDevice: Boolean
)

sealed class DeviceMemoryUiState {
    object Loading : DeviceMemoryUiState()
    data class Success(
        val snapshots: List<DeviceSnapshotSummary>,
        val isMemoryEnabled: Boolean
    ) : DeviceMemoryUiState()
    data class Error(val message: String) : DeviceMemoryUiState()
}

data class CustomDolbyPresetSummary(
    val name: String,
    val savedFromProfile: Int,
    val bandMode: BandMode,
    val savedAt: Long
)

sealed class CustomPresetUiState {
    object Loading : CustomPresetUiState()
    data class Success(val presets: List<CustomDolbyPresetSummary>) : CustomPresetUiState()
    data class Error(val message: String) : CustomPresetUiState()
}

data class ScheduledProfileRule(
    val id: String,
    val name: String,
    val profileId: Int,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
    /** Calendar.DAY_OF_WEEK values (1=Sun..7=Sat). Empty = every day. */
    val daysOfWeek: Set<Int> = emptySet(),
    val priority: Int = 0
) {
    fun containsMinute(minuteOfDay: Int): Boolean {
        val start = startHour * 60 + startMinute
        val end = endHour * 60 + endMinute
        return if (start <= end) {
            minuteOfDay in start until end
        } else {
            minuteOfDay >= start || minuteOfDay < end
        }
    }

    fun matchesDay(calendarDayOfWeek: Int): Boolean {
        return daysOfWeek.isEmpty() || calendarDayOfWeek in daysOfWeek
    }
}

enum class ProfileChangeSource(val key: String) {
    MANUAL("manual"),
    SCHEDULE("schedule"),
    APP("app"),
    DEVICE("device"),
    BLUETOOTH("bluetooth"),
    MEDIA("media"),
    AUTOMATION("automation"),
    CALL("call"),
    SLEEP_TIMER("sleep_timer"),
    FOCUS("focus"),
    WIDGET("widget"),
    QS_TILE("qs_tile")
}

data class ProfileHistoryEntry(
    val timestamp: Long,
    val profileId: Int,
    val source: ProfileChangeSource,
    val detail: String
)

data class BluetoothProfileRule(
    val deviceKey: String,
    val displayName: String,
    val profileId: Int,
    val enabled: Boolean = true
)

enum class SleepTimerAction {
    DISABLE,
    RESTORE_PROFILE,
    SWITCH_PROFILE
}

data class SleepTimerConfig(
    val endTimeMs: Long,
    val action: SleepTimerAction,
    val targetProfileId: Int = 0,
    val previousProfileId: Int = 0
)

data class AutomationStatus(
    val activeSource: ProfileChangeSource?,
    val activeDetail: String,
    val scheduledRuleName: String?,
    val appPackage: String?,
    val deviceName: String?,
    val bluetoothRuleName: String?,
    val mediaContentType: String?,
    val atmosContentActive: Boolean,
    val dolbyEffectHealthy: Boolean,
    val spatialAudioEnabled: Boolean
)

sealed class ScheduledProfileUiState {
    object Loading : ScheduledProfileUiState()
    data class Success(
        val rules: List<ScheduledProfileRule>,
        val enabled: Boolean
    ) : ScheduledProfileUiState()
    data class Error(val message: String) : ScheduledProfileUiState()
}

sealed class DolbyUiState {
    object Loading : DolbyUiState()
    data class Success(
        val settings: DolbySettings,
        val profileSettings: ProfileSettings,
        val currentPresetName: String,
        val isOnSpeaker: Boolean,
        val activeAudioDevice: ActiveAudioDevice
    ) : DolbyUiState()
    data class Error(val message: String) : DolbyUiState()
}

sealed class EqualizerUiState {
    object Loading : EqualizerUiState()
    data class Success(
        val presets: List<EqualizerPreset>,
        val currentPreset: EqualizerPreset,
        val bandGains: List<BandGain>,
        val bandMode: BandMode,
        val graphicEqEnabled: Boolean = true
    ) : EqualizerUiState()
    data class Error(val message: String) : EqualizerUiState()
}
