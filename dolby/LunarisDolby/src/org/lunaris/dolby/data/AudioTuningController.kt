/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import android.content.SharedPreferences
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.DolbyConstants.DsParam
import org.lunaris.dolby.audio.DolbyAudioEffect
import org.lunaris.dolby.audio.DolbyHalBridge
import org.lunaris.dolby.domain.models.AudioDeviceCategory

internal class AudioTuningController(
    private val context: Context,
    private val dolbyEffectProvider: () -> DolbyAudioEffect,
    private val checkEffect: () -> Unit,
    private val isReleased: () -> Boolean,
    private val profilePrefs: (Int) -> SharedPreferences,
    private val defaultPrefs: SharedPreferences,
    private val activeDeviceCategory: () -> AudioDeviceCategory
) {

    private val audioEngine = AudioEngineProcessor(context)

    fun applyAll(profile: Int) {
        if (isReleased()) return
        pushGeqToHardware(profile)
        applyGraphicEqEnable(profile)
        applyDialogueDucking(profile)
        applyVirtualBass(profile)
        applyAdvancedBass(profile)
        applyHeadphoneVirtualizerTuning(profile)
        applyCalibrationBoost()
        applySpatialMaster(profile)
    }

    fun pushGeqToHardware(profile: Int) {
        if (isReleased()) return
        try {
            checkEffect()
            val raw = getRawGeqArray(profile) ?: return
            val offset = getOutputBoostOffset(profile)
            val applied = audioEngine.processGeqForHardware(raw, profile, offset)
            dolbyEffectProvider().setDapParameter(DsParam.GEQ_BAND_GAINS, applied, profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "pushGeqToHardware failed: ${e.message}")
        }
    }

    fun getRawGeqArray(profile: Int): IntArray? {
        val saved = profilePrefs(profile).getString(DolbyConstants.PREF_PRESET, null)
        if (saved != null) {
            val arr = saved.split(",").mapNotNull { it.trim().toIntOrNull() }.toIntArray()
            if (arr.size == 20) return arr
        }
        return try {
            checkEffect()
            dolbyEffectProvider().getDapParameter(DsParam.GEQ_BAND_GAINS, profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "getRawGeqArray fallback failed: ${e.message}")
            null
        }
    }

    fun saveRawGeqArray(profile: Int, gains: IntArray) {
        profilePrefs(profile).edit()
            .putString(DolbyConstants.PREF_PRESET, gains.joinToString(","))
            .apply()
    }

    // --- Output boost (user-configurable range) ---

    fun isOutputBoostEnabled(profile: Int): Boolean =
        profilePrefs(profile).getBoolean(DolbyConstants.PREF_OUTPUT_BOOST_ENABLED, false)

    fun getOutputBoostTenths(profile: Int): Int {
        val engine = audioEngine.preferences()
        return profilePrefs(profile).getInt(DolbyConstants.PREF_OUTPUT_BOOST_TENTHS, 0)
            .let { engine.clampOutputBoostTenths(it) }
    }

    fun getOutputBoostMaxTenths(): Int = audioEngine.preferences().getOutputBoostMaxTenths()

    fun getOutputBoostMinTenths(): Int = audioEngine.preferences().getOutputBoostMinTenths()

    fun getOutputBoostOffset(profile: Int): Int =
        if (isOutputBoostEnabled(profile)) getOutputBoostTenths(profile) else 0

    fun setOutputBoost(profile: Int, enabled: Boolean, tenthsDb: Int) {
        if (isReleased()) return
        val engine = audioEngine.preferences()
        val clamped = engine.clampOutputBoostTenths(tenthsDb)
        profilePrefs(profile).edit()
            .putBoolean(DolbyConstants.PREF_OUTPUT_BOOST_ENABLED, enabled)
            .putInt(DolbyConstants.PREF_OUTPUT_BOOST_TENTHS, clamped)
            .apply()
        pushGeqToHardware(profile)
    }

    fun getAudioEnginePreferences(): AudioEnginePreferences = audioEngine.preferences()
    // --- Virtual bass ---

    private fun migrateVirtualBassPreference(profile: Int) {
        val prefs = profilePrefs(profile)
        if (!prefs.contains(DolbyConstants.PREF_VIRTUAL_BASS_SPEAKER)) {
            prefs.edit()
                .putBoolean(
                    DolbyConstants.PREF_VIRTUAL_BASS_SPEAKER,
                    prefs.getBoolean(DolbyConstants.PREF_VIRTUAL_BASS, false)
                )
                .apply()
        }
    }

    fun isVirtualBassSpeakerEnabled(profile: Int): Boolean {
        migrateVirtualBassPreference(profile)
        return profilePrefs(profile).getBoolean(DolbyConstants.PREF_VIRTUAL_BASS_SPEAKER, false)
    }

    fun isVirtualBassBluetoothEnabled(profile: Int): Boolean =
        profilePrefs(profile).getBoolean(DolbyConstants.PREF_VIRTUAL_BASS_BLUETOOTH, false)

    fun isVirtualBassEnabled(profile: Int): Boolean = when (activeDeviceCategory()) {
        AudioDeviceCategory.BLUETOOTH -> isVirtualBassBluetoothEnabled(profile)
        else -> isVirtualBassSpeakerEnabled(profile)
    }

    fun setVirtualBassEnabled(profile: Int, enabled: Boolean) {
        when (activeDeviceCategory()) {
            AudioDeviceCategory.BLUETOOTH -> setVirtualBassBluetoothEnabled(profile, enabled)
            else -> setVirtualBassSpeakerEnabled(profile, enabled)
        }
    }

    fun setVirtualBassSpeakerEnabled(profile: Int, enabled: Boolean) {
        if (isReleased()) return
        profilePrefs(profile).edit()
            .putBoolean(DolbyConstants.PREF_VIRTUAL_BASS_SPEAKER, enabled)
            .apply()
        applyVirtualBass(profile)
    }

    fun setVirtualBassBluetoothEnabled(profile: Int, enabled: Boolean) {
        if (isReleased()) return
        profilePrefs(profile).edit()
            .putBoolean(DolbyConstants.PREF_VIRTUAL_BASS_BLUETOOTH, enabled)
            .apply()
        applyVirtualBass(profile)
    }

    fun getVirtualBassMode(profile: Int): Int =
        profilePrefs(profile).getInt(DolbyConstants.PREF_VB_MODE, DolbyConstants.VB_MODE_STOCK)
            .coerceIn(0, DolbyConstants.VB_MODE_MAX)

    fun getVirtualBassOverallGain(profile: Int): Int =
        profilePrefs(profile).getInt(
            DolbyConstants.PREF_VB_OVERALL_GAIN, DolbyConstants.VB_OVERALL_GAIN_STOCK
        ).coerceIn(DolbyConstants.VB_OVERALL_GAIN_MIN, DolbyConstants.VB_OVERALL_GAIN_MAX)

    fun getVirtualBassSlopeGain(profile: Int): Int =
        profilePrefs(profile).getInt(
            DolbyConstants.PREF_VB_SLOPE_GAIN, DolbyConstants.VB_SLOPE_GAIN_STOCK
        ).coerceIn(DolbyConstants.VB_SLOPE_GAIN_MIN, DolbyConstants.VB_SLOPE_GAIN_MAX)

    fun setVirtualBassDetails(profile: Int, mode: Int, overallGain: Int, slopeGain: Int) {
        if (isReleased()) return
        profilePrefs(profile).edit()
            .putInt(DolbyConstants.PREF_VB_MODE, mode.coerceIn(0, DolbyConstants.VB_MODE_MAX))
            .putInt(
                DolbyConstants.PREF_VB_OVERALL_GAIN,
                overallGain.coerceIn(DolbyConstants.VB_OVERALL_GAIN_MIN, DolbyConstants.VB_OVERALL_GAIN_MAX)
            )
            .putInt(
                DolbyConstants.PREF_VB_SLOPE_GAIN,
                slopeGain.coerceIn(DolbyConstants.VB_SLOPE_GAIN_MIN, DolbyConstants.VB_SLOPE_GAIN_MAX)
            )
            .apply()
        applyVirtualBass(profile)
    }

    fun reapplyVirtualBass(profile: Int) = applyVirtualBass(profile)

    private fun applyVirtualBass(profile: Int) {
        val mode = getVirtualBassMode(profile)
        val overall = getVirtualBassOverallGain(profile)
        val slope = getVirtualBassSlopeGain(profile)
        when (activeDeviceCategory()) {
            AudioDeviceCategory.SPEAKER -> {
                val enabled = isVirtualBassSpeakerEnabled(profile)
                setDapInt(DsParam.VIRTUAL_BASS_ENABLE, profile, if (enabled) 1 else 0)
                DolbyHalBridge.applyVirtualBassHal(context, enabled, mode, overall, slope)
            }
            AudioDeviceCategory.BLUETOOTH -> {
                // DAX rejects VIRTUAL_BASS_ENABLE on non-speaker endpoints. Keep an
                // experimental HAL-only path for vendors that expose a BT-capable handler.
                DolbyHalBridge.applyVirtualBassHal(
                    context,
                    isVirtualBassBluetoothEnabled(profile),
                    mode,
                    overall,
                    slope
                )
            }
            else -> DolbyHalBridge.applyVirtualBassHal(context, false, mode, overall, slope)
        }
    }

    // --- Graphic EQ processing enable ---

    fun isGraphicEqEnabled(profile: Int): Boolean =
        profilePrefs(profile).getBoolean(DolbyConstants.PREF_GRAPHIC_EQ_ENABLED, true)

    fun setGraphicEqEnabled(profile: Int, enabled: Boolean) {
        if (isReleased()) return
        profilePrefs(profile).edit()
            .putBoolean(DolbyConstants.PREF_GRAPHIC_EQ_ENABLED, enabled)
            .apply()
        applyGraphicEqEnable(profile)
    }

    private fun applyGraphicEqEnable(profile: Int) {
        setDapInt(DsParam.GRAPHIC_EQ_ENABLE, profile, if (isGraphicEqEnabled(profile)) 1 else 0)
    }

    // --- Dialogue ducking ---

    fun isDialogueDuckingEnabled(profile: Int): Boolean =
        profilePrefs(profile).getBoolean(DolbyConstants.PREF_DIALOGUE_DUCKING_ENABLED, false)

    fun getDialogueDuckingAmount(profile: Int): Int =
        profilePrefs(profile).getInt(DolbyConstants.PREF_DIALOGUE_DUCKING_AMOUNT, 8)
            .coerceIn(0, DolbyConstants.DIALOGUE_DUCKING_MAX)

    fun setDialogueDucking(profile: Int, enabled: Boolean, amount: Int) {
        if (isReleased()) return
        val clamped = amount.coerceIn(0, DolbyConstants.DIALOGUE_DUCKING_MAX)
        profilePrefs(profile).edit()
            .putBoolean(DolbyConstants.PREF_DIALOGUE_DUCKING_ENABLED, enabled)
            .putInt(DolbyConstants.PREF_DIALOGUE_DUCKING_AMOUNT, clamped)
            .apply()
        applyDialogueDucking(profile)
    }

    private fun applyDialogueDucking(profile: Int) {
        val enabled = isDialogueDuckingEnabled(profile)
        val amount = if (enabled) getDialogueDuckingAmount(profile) else 0
        setDapInt(DsParam.DIALOGUE_DUCKING, profile, amount)
    }

    // --- Advanced bass engine (vendor HAL tuning) ---

    fun isAdvancedBassEnabled(profile: Int): Boolean =
        profilePrefs(profile).getBoolean(DolbyConstants.PREF_ADV_BASS_ENABLED, false)

    fun getAdvancedBassBoost(profile: Int): Int =
        profilePrefs(profile).getInt(DolbyConstants.PREF_ADV_BASS_BOOST, 40)
            .coerceIn(0, 100)

    fun getAdvancedBassCutoff(profile: Int): Int =
        profilePrefs(profile).getInt(
            DolbyConstants.PREF_ADV_BASS_CUTOFF, DolbyConstants.ADV_BASS_CUTOFF_STOCK_HZ
        ).coerceIn(DolbyConstants.ADV_BASS_CUTOFF_MIN_HZ, DolbyConstants.ADV_BASS_CUTOFF_MAX_HZ)

    fun getAdvancedBassWidth(profile: Int): Int =
        profilePrefs(profile).getInt(
            DolbyConstants.PREF_ADV_BASS_WIDTH, DolbyConstants.ADV_BASS_WIDTH_STOCK
        ).coerceIn(DolbyConstants.ADV_BASS_WIDTH_MIN, DolbyConstants.ADV_BASS_WIDTH_MAX)

    fun setAdvancedBass(
        profile: Int,
        enabled: Boolean,
        boostPercent: Int,
        cutoffHz: Int,
        width: Int = getAdvancedBassWidth(profile)
    ) {
        if (isReleased()) return
        profilePrefs(profile).edit()
            .putBoolean(DolbyConstants.PREF_ADV_BASS_ENABLED, enabled)
            .putInt(DolbyConstants.PREF_ADV_BASS_BOOST, boostPercent.coerceIn(0, 100))
            .putInt(
                DolbyConstants.PREF_ADV_BASS_CUTOFF,
                cutoffHz.coerceIn(
                    DolbyConstants.ADV_BASS_CUTOFF_MIN_HZ, DolbyConstants.ADV_BASS_CUTOFF_MAX_HZ
                )
            )
            .putInt(
                DolbyConstants.PREF_ADV_BASS_WIDTH,
                width.coerceIn(DolbyConstants.ADV_BASS_WIDTH_MIN, DolbyConstants.ADV_BASS_WIDTH_MAX)
            )
            .apply()
        applyAdvancedBass(profile)
    }

    private fun applyAdvancedBass(profile: Int) {
        val enabled = isAdvancedBassEnabled(profile)
        if (enabled) {
            setDapInt(DsParam.BASS_ENHANCER_ENABLE, profile, 1)
        }
        DolbyHalBridge.applyAdvancedBass(
            context,
            enabled,
            getAdvancedBassBoost(profile),
            getAdvancedBassCutoff(profile),
            getAdvancedBassWidth(profile)
        )
    }

    // --- Headphone virtualizer tuning ---

    fun getHpVirtMode(profile: Int): Int =
        profilePrefs(profile).getInt(DolbyConstants.PREF_HP_VIRT_MODE, 0).coerceIn(0, 1)

    fun getHpVirtLrAngle(profile: Int): Int =
        profilePrefs(profile).getInt(DolbyConstants.PREF_HP_VIRT_LR_ANGLE, 45)
            .coerceIn(0, DolbyConstants.HP_VIRT_LR_ANGLE_MAX)

    fun getHpVirtStartBand(profile: Int): Int =
        profilePrefs(profile).getInt(DolbyConstants.PREF_HP_VIRT_START_BAND, 0)
            .coerceIn(0, DolbyConstants.HP_VIRT_START_BAND_MAX)

    fun setHeadphoneVirtualizerTuning(profile: Int, mode: Int, lrAngle: Int, startBand: Int) {
        if (isReleased()) return
        profilePrefs(profile).edit()
            .putInt(DolbyConstants.PREF_HP_VIRT_MODE, mode.coerceIn(0, 1))
            .putInt(
                DolbyConstants.PREF_HP_VIRT_LR_ANGLE,
                lrAngle.coerceIn(0, DolbyConstants.HP_VIRT_LR_ANGLE_MAX)
            )
            .putInt(
                DolbyConstants.PREF_HP_VIRT_START_BAND,
                startBand.coerceIn(0, DolbyConstants.HP_VIRT_START_BAND_MAX)
            )
            .apply()
        applyHeadphoneVirtualizerTuning(profile)
    }

    private fun applyHeadphoneVirtualizerTuning(profile: Int) {
        DolbyHalBridge.applyHeadphoneVirtualizerTuning(
            context,
            getHpVirtMode(profile),
            getHpVirtLrAngle(profile),
            getHpVirtStartBand(profile)
        )
    }

    // --- Endpoint calibration boost (global per endpoint category) ---

    fun getCalibrationBoostSpeaker(): Int =
        defaultPrefs.getInt(DolbyConstants.PREF_CALIBRATION_BOOST_SPEAKER, 0)
            .coerceIn(0, DolbyConstants.CALIBRATION_BOOST_MAX)

    fun getCalibrationBoostHeadphone(): Int =
        defaultPrefs.getInt(DolbyConstants.PREF_CALIBRATION_BOOST_HEADPHONE, 0)
            .coerceIn(0, DolbyConstants.CALIBRATION_BOOST_MAX)

    fun getCalibrationBoostBluetooth(): Int =
        defaultPrefs.getInt(DolbyConstants.PREF_CALIBRATION_BOOST_BT, 0)
            .coerceIn(0, DolbyConstants.CALIBRATION_BOOST_MAX)

    fun setCalibrationBoostSpeaker(boost: Int) {
        defaultPrefs.edit()
            .putInt(
                DolbyConstants.PREF_CALIBRATION_BOOST_SPEAKER,
                boost.coerceIn(0, DolbyConstants.CALIBRATION_BOOST_MAX)
            )
            .apply()
        applyCalibrationBoost()
    }

    fun setCalibrationBoostHeadphone(boost: Int) {
        defaultPrefs.edit()
            .putInt(
                DolbyConstants.PREF_CALIBRATION_BOOST_HEADPHONE,
                boost.coerceIn(0, DolbyConstants.CALIBRATION_BOOST_MAX)
            )
            .apply()
        applyCalibrationBoost()
    }

    fun setCalibrationBoostBluetooth(boost: Int) {
        defaultPrefs.edit()
            .putInt(
                DolbyConstants.PREF_CALIBRATION_BOOST_BT,
                boost.coerceIn(0, DolbyConstants.CALIBRATION_BOOST_MAX)
            )
            .apply()
        applyCalibrationBoost()
    }

    fun applyCalibrationBoost() {
        val boost = when (activeDeviceCategory()) {
            AudioDeviceCategory.SPEAKER -> getCalibrationBoostSpeaker()
            AudioDeviceCategory.WIRED, AudioDeviceCategory.USB -> getCalibrationBoostHeadphone()
            AudioDeviceCategory.BLUETOOTH -> getCalibrationBoostBluetooth()
            else -> 0
        }
        DolbyHalBridge.applyCalibrationBoost(context, boost)
    }

    // --- Spatial master (PREF_SPATIAL_AUDIO_ENABLED) ---

    fun isSpatialAudioEnabled(): Boolean =
        defaultPrefs.getBoolean(DolbyConstants.PREF_SPATIAL_AUDIO_ENABLED, false)

    fun setSpatialAudioEnabled(enabled: Boolean) {
        defaultPrefs.edit()
            .putBoolean(DolbyConstants.PREF_SPATIAL_AUDIO_ENABLED, enabled)
            .apply()
        applySpatialMaster()
    }

    private fun applySpatialMaster(@Suppress("UNUSED_PARAMETER") profile: Int = -1) {
        audioEngine.applySpatialProcessing(forceCrossfeed = isSpatialAudioEnabled())
    }

    private fun setDapInt(param: DsParam, profile: Int, value: Int) {
        try {
            checkEffect()
            dolbyEffectProvider().setDapParameter(param, value, profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "setDapInt $param=$value failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AudioTuning"
    }
}
