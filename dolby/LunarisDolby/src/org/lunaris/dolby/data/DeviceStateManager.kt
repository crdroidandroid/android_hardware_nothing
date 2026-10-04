/*
 * Copyright (C) 2026 tranQuila-Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import android.media.AudioManager

import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.domain.models.BandGain
import org.lunaris.dolby.domain.models.BandMode
import org.lunaris.dolby.domain.models.DeviceSnapshotSummary

class DeviceStateManager(private val context: Context) {

    fun deviceKey(device: AudioDeviceInfo): String {
        return when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> {
                val addr = device.address?.takeIf { it.isNotBlank() } ?: "unknown"
                "bt_${addr.replace(":", "_")}"
            }
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE -> "wired_headphones"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "builtin_speaker"
            else -> "device_type_${device.type}"
        }
    }

    fun getCurrentOutputDevice(audioManager: AudioManager? = null): AudioDeviceInfo? {
        val am = audioManager
            ?: context.getSystemService(AudioManager::class.java)
            ?: return null
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (type in OUTPUT_DEVICE_PRIORITY) {
            devices.firstOrNull { it.type == type }?.let { return it }
        }
        return devices.firstOrNull()
    }

    fun deviceDisplayName(device: AudioDeviceInfo): String {
        val productName = device.productName?.toString()?.takeIf { it.isNotBlank() }
        return when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone Speaker"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> productName ?: "Wired Headphones"
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE -> productName ?: "USB Audio"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> productName ?: "Bluetooth Device"
            else -> productName ?: "Audio Device"
        }
    }

    fun saveSnapshot(deviceKey: String, repository: DolbyRepository) {
        val profile = repository.getCurrentProfile()
        val prefs = getDevicePrefs(deviceKey)
        val editor = prefs.edit()

        editor.putInt(KEY_VERSION, SNAPSHOT_VERSION)

        editor.putBoolean(KEY_DOLBY_ENABLED, repository.getDolbyEnabled())
        editor.putInt(KEY_PROFILE, profile)

        editor.putInt(KEY_IEQ, repository.getIeqPreset(profile))

        editor.putBoolean(KEY_HP_VIRT, repository.getHeadphoneVirtualizerEnabled(profile))
        editor.putBoolean(KEY_SPK_VIRT, repository.getSpeakerVirtualizerEnabled(profile))

        editor.putBoolean(KEY_DIALOGUE, repository.getDialogueEnhancerEnabled(profile))
        editor.putInt(KEY_DIALOGUE_AMT, repository.getDialogueEnhancerAmount(profile))

        editor.putBoolean(KEY_BASS_ENABLED, repository.getBassEnhancerEnabled(profile))
        editor.putInt(KEY_BASS_LEVEL, repository.getBassLevel(profile))
        editor.putInt(KEY_BASS_CURVE, repository.getBassCurve(profile))

        editor.putBoolean(KEY_TREBLE_ENABLED, repository.getTrebleEnhancerEnabled(profile))
        editor.putInt(KEY_TREBLE_LEVEL, repository.getTrebleLevel(profile))

        editor.putBoolean(KEY_MID_ENABLED, repository.getMidEnhancerEnabled(profile))
        editor.putInt(KEY_MID_LEVEL, repository.getMidLevel(profile))

        if (repository.stereoWideningSupported) {
            editor.putInt(KEY_STEREO, repository.getStereoWideningAmount(profile))
        }

        editor.putBoolean(KEY_OUTPUT_BOOST, repository.isOutputBoostEnabled(profile))
        editor.putInt(KEY_OUTPUT_BOOST_TENTHS, repository.getOutputBoostTenths(profile))
        when {
            deviceKey == "builtin_speaker" ->
                editor.putBoolean(
                    KEY_VIRTUAL_BASS_SPEAKER,
                    repository.isVirtualBassSpeakerEnabled(profile)
                )
            deviceKey.startsWith("bt_") ->
                editor.putBoolean(
                    KEY_VIRTUAL_BASS_BLUETOOTH,
                    repository.isVirtualBassBluetoothEnabled(profile)
                )
        }

        editor.putBoolean(KEY_ADV_BASS, repository.isAdvancedBassEnabled(profile))
        editor.putInt(KEY_ADV_BASS_BOOST, repository.getAdvancedBassBoost(profile))
        editor.putInt(KEY_ADV_BASS_CUTOFF, repository.getAdvancedBassCutoff(profile))
        editor.putInt(KEY_ADV_BASS_WIDTH, repository.getAdvancedBassWidth(profile))
        editor.putBoolean(KEY_DIALOGUE_DUCKING, repository.isDialogueDuckingEnabled(profile))
        editor.putInt(KEY_DIALOGUE_DUCKING_AMT, repository.getDialogueDuckingAmount(profile))
        editor.putBoolean(KEY_GRAPHIC_EQ, repository.isGraphicEqEnabled(profile))
        editor.putInt(KEY_VB_MODE, repository.getVirtualBassMode(profile))
        editor.putInt(KEY_VB_OVERALL, repository.getVirtualBassOverallGain(profile))
        editor.putInt(KEY_VB_SLOPE, repository.getVirtualBassSlopeGain(profile))
        editor.putInt(KEY_HP_VIRT_MODE, repository.getHpVirtMode(profile))
        editor.putInt(KEY_HP_VIRT_ANGLE, repository.getHpVirtLrAngle(profile))
        editor.putInt(KEY_HP_VIRT_BAND, repository.getHpVirtStartBand(profile))
        editor.putBoolean(KEY_SPATIAL, repository.isSpatialAudioEnabled())
        editor.putInt(KEY_CAL_SPEAKER, repository.getCalibrationBoostSpeaker())
        editor.putInt(KEY_CAL_HEADPHONE, repository.getCalibrationBoostHeadphone())
        editor.putInt(KEY_CAL_BT, repository.getCalibrationBoostBluetooth())
        val engine = repository.getAudioEnginePreferences()
        editor.putBoolean(KEY_CROSSFEED, engine.isCrossfeedEnabled())
        editor.putInt(KEY_CROSSFEED_STRENGTH, engine.getCrossfeedStrength())
        editor.putBoolean(KEY_STEREO_BALANCE, engine.isStereoBalanceEnabled())
        editor.putInt(KEY_STEREO_BALANCE_VAL, engine.getStereoBalance())

        val gains = repository.getEqualizerGains(profile, BandMode.TWENTY_BAND)
        editor.putInt(KEY_EQ_BAND_COUNT, gains.size)
        editor.putString(KEY_EQ_GAINS, gains.joinToString(",") { it.gain.toString() })

        editor.apply()
        DolbyConstants.dlog(TAG,
            "Snapshot saved for device=$deviceKey profile=$profile bands=${gains.size} v=$SNAPSHOT_VERSION")
    }

    fun restoreSnapshot(deviceKey: String, repository: DolbyRepository): Boolean {
        val prefs = getDevicePrefs(deviceKey)

        if (!prefs.contains(KEY_VERSION)) {
            DolbyConstants.dlog(TAG, "No snapshot for device=$deviceKey")
            return false
        }

        val storedVersion = prefs.getInt(KEY_VERSION, -1)
        if (storedVersion != SNAPSHOT_VERSION) {
            DolbyConstants.dlog(TAG,
                "Snapshot version mismatch for $deviceKey: stored=$storedVersion current=$SNAPSHOT_VERSION — discarding")
            clearSnapshot(deviceKey)
            return false
        }

        return try {
            val enabled = prefs.getBoolean(KEY_DOLBY_ENABLED, true)
            val profile = prefs.getInt(KEY_PROFILE, 0)

            repository.setDolbyEnabled(enabled)
            repository.setCurrentProfile(profile)

            val storedBandCount = prefs.getInt(KEY_EQ_BAND_COUNT, -1)
            val gainsStr = prefs.getString(KEY_EQ_GAINS, null)
            if (gainsStr != null && storedBandCount > 0) {
                val gains = gainsStr.split(",").mapNotNull { it.toIntOrNull() }
                if (gains.size == storedBandCount) {
                    val bandGains = gains.mapIndexed { i, g ->
                        BandGain(
                            frequency = DolbyRepository.BAND_FREQUENCIES_20.getOrElse(i) { i },
                            gain = g
                        )
                    }
                    repository.setEqualizerGains(profile, bandGains, BandMode.TWENTY_BAND)
                } else {
                    DolbyConstants.dlog(TAG,
                        "EQ band count mismatch for $deviceKey: stored=$storedBandCount actual=${gains.size} — skipping EQ restore")
                }
            }

            repository.setIeqPreset(profile, prefs.getInt(KEY_IEQ, 0))

            repository.setHeadphoneVirtualizerEnabled(profile, prefs.getBoolean(KEY_HP_VIRT, false))
            repository.setSpeakerVirtualizerEnabled(profile, prefs.getBoolean(KEY_SPK_VIRT, false))

            repository.setDialogueEnhancerEnabled(profile, prefs.getBoolean(KEY_DIALOGUE, false))
            repository.setDialogueEnhancerAmount(profile, prefs.getInt(KEY_DIALOGUE_AMT, 6))

            repository.setBassEnhancerEnabled(profile, prefs.getBoolean(KEY_BASS_ENABLED, false))
            repository.setBassCurve(profile, prefs.getInt(KEY_BASS_CURVE, 0))
            repository.setBassLevel(profile, prefs.getInt(KEY_BASS_LEVEL, 0))

            repository.setTrebleEnhancerEnabled(profile, prefs.getBoolean(KEY_TREBLE_ENABLED, false))
            repository.setTrebleLevel(profile, prefs.getInt(KEY_TREBLE_LEVEL, 0))

            repository.setMidEnhancerEnabled(profile, prefs.getBoolean(KEY_MID_ENABLED, false))
            repository.setMidLevel(profile, prefs.getInt(KEY_MID_LEVEL, 0))

            if (repository.stereoWideningSupported) {
                repository.setStereoWideningAmount(profile, prefs.getInt(KEY_STEREO, 32))
            }

            if (prefs.contains(KEY_OUTPUT_BOOST)) {
                repository.setOutputBoost(
                    profile,
                    prefs.getBoolean(KEY_OUTPUT_BOOST, false),
                    prefs.getInt(KEY_OUTPUT_BOOST_TENTHS, 0)
                )
            }
            if (prefs.contains(KEY_VIRTUAL_BASS_SPEAKER)) {
                repository.setVirtualBassSpeakerEnabled(
                    profile,
                    prefs.getBoolean(KEY_VIRTUAL_BASS_SPEAKER, false)
                )
            }
            if (prefs.contains(KEY_VIRTUAL_BASS_BLUETOOTH)) {
                repository.setVirtualBassBluetoothEnabled(
                    profile,
                    prefs.getBoolean(KEY_VIRTUAL_BASS_BLUETOOTH, false)
                )
            }
            if (prefs.contains(KEY_ADV_BASS)) {
                repository.setAdvancedBass(
                    profile,
                    prefs.getBoolean(KEY_ADV_BASS, false),
                    prefs.getInt(KEY_ADV_BASS_BOOST, 40),
                    prefs.getInt(KEY_ADV_BASS_CUTOFF, DolbyConstants.ADV_BASS_CUTOFF_STOCK_HZ),
                    prefs.getInt(KEY_ADV_BASS_WIDTH, DolbyConstants.ADV_BASS_WIDTH_STOCK)
                )
            }
            if (prefs.contains(KEY_DIALOGUE_DUCKING)) {
                repository.setDialogueDucking(
                    profile,
                    prefs.getBoolean(KEY_DIALOGUE_DUCKING, false),
                    prefs.getInt(KEY_DIALOGUE_DUCKING_AMT, 8)
                )
            }
            if (prefs.contains(KEY_GRAPHIC_EQ)) {
                repository.setGraphicEqEnabled(profile, prefs.getBoolean(KEY_GRAPHIC_EQ, true))
            }
            if (prefs.contains(KEY_VB_MODE)) {
                repository.setVirtualBassDetails(
                    profile,
                    prefs.getInt(KEY_VB_MODE, DolbyConstants.VB_MODE_STOCK),
                    prefs.getInt(KEY_VB_OVERALL, DolbyConstants.VB_OVERALL_GAIN_STOCK),
                    prefs.getInt(KEY_VB_SLOPE, DolbyConstants.VB_SLOPE_GAIN_STOCK)
                )
            }
            if (prefs.contains(KEY_HP_VIRT_MODE)) {
                repository.setHeadphoneVirtualizerTuning(
                    profile,
                    prefs.getInt(KEY_HP_VIRT_MODE, 0),
                    prefs.getInt(KEY_HP_VIRT_ANGLE, 45),
                    prefs.getInt(KEY_HP_VIRT_BAND, 0)
                )
            }
            if (prefs.contains(KEY_SPATIAL)) {
                repository.setSpatialAudioEnabled(prefs.getBoolean(KEY_SPATIAL, false))
            }
            if (prefs.contains(KEY_CAL_SPEAKER)) {
                repository.setCalibrationBoostSpeaker(prefs.getInt(KEY_CAL_SPEAKER, 0))
            }
            if (prefs.contains(KEY_CAL_HEADPHONE)) {
                repository.setCalibrationBoostHeadphone(prefs.getInt(KEY_CAL_HEADPHONE, 0))
            }
            if (prefs.contains(KEY_CAL_BT)) {
                repository.setCalibrationBoostBluetooth(prefs.getInt(KEY_CAL_BT, 0))
            }
            if (prefs.contains(KEY_CROSSFEED)) {
                val engine = repository.getAudioEnginePreferences()
                engine.setBoolean(DolbyConstants.PREF_CROSSFEED_ENABLED, prefs.getBoolean(KEY_CROSSFEED, false))
                if (prefs.contains(KEY_CROSSFEED_STRENGTH)) {
                    context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE).edit()
                        .putInt(DolbyConstants.PREF_CROSSFEED_STRENGTH, prefs.getInt(KEY_CROSSFEED_STRENGTH, 40))
                        .apply()
                }
            }
            if (prefs.contains(KEY_STEREO_BALANCE)) {
                val engine = repository.getAudioEnginePreferences()
                engine.setBoolean(DolbyConstants.PREF_STEREO_BALANCE_ENABLED, prefs.getBoolean(KEY_STEREO_BALANCE, false))
                if (prefs.contains(KEY_STEREO_BALANCE_VAL)) {
                    context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE).edit()
                        .putInt(DolbyConstants.PREF_STEREO_BALANCE_VALUE, prefs.getInt(KEY_STEREO_BALANCE_VAL, 0))
                        .apply()
                }
            }

            DolbyConstants.dlog(TAG,
                "Snapshot restored for device=$deviceKey profile=$profile v=$storedVersion")
            true
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG,
                "Failed to restore snapshot for $deviceKey: ${e.message} — discarding")
            clearSnapshot(deviceKey)
            false
        }
    }

    fun hasSnapshot(deviceKey: String): Boolean {
        val prefs = getDevicePrefs(deviceKey)
        return prefs.contains(KEY_VERSION) &&
                prefs.getInt(KEY_VERSION, -1) == SNAPSHOT_VERSION
    }

    fun clearSnapshot(deviceKey: String) {
        getDevicePrefs(deviceKey).edit().clear().apply()
        DolbyConstants.dlog(TAG, "Snapshot cleared for device=$deviceKey")
    }

    fun clearAllSnapshots() {
        getAllDeviceKeys().forEach { clearSnapshot(it) }
        DolbyConstants.dlog(TAG, "All device snapshots cleared")
    }

    fun getSnapshotSummary(deviceKey: String, currentDeviceKey: String?): DeviceSnapshotSummary {
        val prefs = getDevicePrefs(deviceKey)
        return DeviceSnapshotSummary(
            deviceKey = deviceKey,
            displayName = deviceKeyToDisplayName(deviceKey),
            profile = prefs.getInt(KEY_PROFILE, 0),
            dolbyEnabled = prefs.getBoolean(KEY_DOLBY_ENABLED, true),
            isCurrentDevice = deviceKey == currentDeviceKey
        )
    }

    fun getSnapshotSummaries(currentDeviceKey: String?): List<DeviceSnapshotSummary> {
        return getAllDeviceKeys()
            .filter { hasSnapshot(it) }
            .map { getSnapshotSummary(it, currentDeviceKey) }
            .sortedWith(
                compareByDescending<DeviceSnapshotSummary> { it.isCurrentDevice }
                    .thenBy { it.displayName.lowercase() }
            )
    }

    fun deviceKeyToDisplayName(deviceKey: String): String {
        return when (deviceKey) {
            "builtin_speaker" -> "Phone Speaker"
            "wired_headphones" -> "Wired Headphones"
            else -> when {
                deviceKey.startsWith("bt_") -> {
                    val address = deviceKey.removePrefix("bt_").replace("_", ":")
                    findDeviceNameByAddress(address) ?: "Bluetooth ($address)"
                }
                deviceKey.startsWith("device_type_") -> "Audio Device"
                else -> deviceKey
            }
        }
    }

    private fun findDeviceNameByAddress(address: String): String? {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return null
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.address == address }
            ?.productName?.toString()?.takeIf { it.isNotBlank() }
    }

    fun getAllDeviceKeys(): List<String> {
        val dir = context.filesDir.parentFile?.let {
            java.io.File(it, "shared_prefs")
        } ?: return emptyList()
        return dir.listFiles()
            ?.filter { it.name.startsWith("device_state_") }
            ?.map { it.name.removePrefix("device_state_").removeSuffix(".xml") }
            ?: emptyList()
    }

    private fun getDevicePrefs(deviceKey: String): SharedPreferences =
        context.getSharedPreferences("device_state_$deviceKey", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "DeviceStateManager"

        const val SNAPSHOT_VERSION = 4

        val OUTPUT_DEVICE_PRIORITY = listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        )

        private const val KEY_VERSION = "snapshot_version"
        private const val KEY_DOLBY_ENABLED = "enabled"
        private const val KEY_PROFILE = "profile"
        private const val KEY_IEQ = "ieq"
        private const val KEY_HP_VIRT = "hp_virt"
        private const val KEY_SPK_VIRT = "spk_virt"
        private const val KEY_DIALOGUE = "dialogue"
        private const val KEY_DIALOGUE_AMT  = "dialogue_amt"
        private const val KEY_BASS_ENABLED = "bass_enabled"
        private const val KEY_BASS_LEVEL = "bass_level"
        private const val KEY_BASS_CURVE = "bass_curve"
        private const val KEY_TREBLE_ENABLED = "treble_enabled"
        private const val KEY_TREBLE_LEVEL = "treble_level"
        private const val KEY_MID_ENABLED = "mid_enabled"
        private const val KEY_MID_LEVEL = "mid_level"
        private const val KEY_STEREO = "stereo"
        private const val KEY_OUTPUT_BOOST = "output_boost"
        private const val KEY_OUTPUT_BOOST_TENTHS = "output_boost_tenths"
        private const val KEY_VIRTUAL_BASS_SPEAKER = "virtual_bass_speaker"
        private const val KEY_VIRTUAL_BASS_BLUETOOTH = "virtual_bass_bluetooth"
        private const val KEY_ADV_BASS = "adv_bass"
        private const val KEY_ADV_BASS_BOOST = "adv_bass_boost"
        private const val KEY_ADV_BASS_CUTOFF = "adv_bass_cutoff"
        private const val KEY_ADV_BASS_WIDTH = "adv_bass_width"
        private const val KEY_DIALOGUE_DUCKING = "dialogue_ducking"
        private const val KEY_DIALOGUE_DUCKING_AMT = "dialogue_ducking_amt"
        private const val KEY_GRAPHIC_EQ = "graphic_eq"
        private const val KEY_VB_MODE = "vb_mode"
        private const val KEY_VB_OVERALL = "vb_overall"
        private const val KEY_VB_SLOPE = "vb_slope"
        private const val KEY_HP_VIRT_MODE = "hp_virt_mode"
        private const val KEY_HP_VIRT_ANGLE = "hp_virt_angle"
        private const val KEY_HP_VIRT_BAND = "hp_virt_band"
        private const val KEY_SPATIAL = "spatial"
        private const val KEY_CAL_SPEAKER = "cal_speaker"
        private const val KEY_CAL_HEADPHONE = "cal_headphone"
        private const val KEY_CAL_BT = "cal_bt"
        private const val KEY_CROSSFEED = "crossfeed"
        private const val KEY_CROSSFEED_STRENGTH = "crossfeed_strength"
        private const val KEY_STEREO_BALANCE = "stereo_balance"
        private const val KEY_STEREO_BALANCE_VAL = "stereo_balance_val"
        private const val KEY_EQ_BAND_COUNT = "eq_band_count"
        private const val KEY_EQ_GAINS = "eq_gains"
    }
}
