/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.audio

import android.content.Context
import android.media.AudioManager
import org.lunaris.dolby.DolbyConstants

/**
 * Vendor HAL parameter bridge for DAX settings not exposed via AudioEffect DsParam.
 */
object DolbyHalBridge {

    private fun audioManager(context: Context): AudioManager? =
        context.getSystemService(AudioManager::class.java)

    fun applyVirtualBassHal(
        context: Context,
        enabled: Boolean,
        mode: Int = DolbyConstants.VB_MODE_STOCK,
        overallGain: Int = DolbyConstants.VB_OVERALL_GAIN_STOCK,
        slopeGain: Int = DolbyConstants.VB_SLOPE_GAIN_STOCK
    ) {
        val flag = if (enabled) 1 else 0
        val vbMode = mode.coerceIn(0, DolbyConstants.VB_MODE_MAX)
        val overall = overallGain.coerceIn(DolbyConstants.VB_OVERALL_GAIN_MIN, DolbyConstants.VB_OVERALL_GAIN_MAX)
        val slope = slopeGain.coerceIn(DolbyConstants.VB_SLOPE_GAIN_MIN, DolbyConstants.VB_SLOPE_GAIN_MAX)
        setParameters(context, listOf(
            "dolby_virtual_bass=$flag",
            "virtual_bass_process_enable=$flag",
            "virtual-bass-process-enable=$flag",
            "virtual_bass_process=$flag",
            "virtual_bass_mode=$vbMode",
            "virtual-bass-mode=$vbMode",
            "virtual_bass_overall_gain=$overall",
            "virtual-bass-overall-gain=$overall",
            "virtual_bass_slope_gain=$slope",
            "virtual-bass-slope-gain=$slope"
        ))
        DolbyConstants.dlog(TAG, "virtual_bass=$flag mode=$vbMode overall=$overall slope=$slope")
    }

    fun applyAdvancedBass(
        context: Context,
        enabled: Boolean,
        boostPercent: Int,
        cutoffHz: Int,
        width: Int = DolbyConstants.ADV_BASS_WIDTH_STOCK
    ) {
        val flag = if (enabled) 1 else 0
        val rawBoost = if (enabled) {
            boostPercent.coerceIn(0, 100) * DolbyConstants.ADV_BASS_BOOST_RAW_MAX / 100
        } else {
            DolbyConstants.ADV_BASS_BOOST_RAW_STOCK
        }
        val cutoff = if (enabled) {
            cutoffHz.coerceIn(DolbyConstants.ADV_BASS_CUTOFF_MIN_HZ, DolbyConstants.ADV_BASS_CUTOFF_MAX_HZ)
        } else {
            DolbyConstants.ADV_BASS_CUTOFF_STOCK_HZ
        }
        val widthVal = if (enabled) {
            width.coerceIn(DolbyConstants.ADV_BASS_WIDTH_MIN, DolbyConstants.ADV_BASS_WIDTH_MAX)
        } else {
            DolbyConstants.ADV_BASS_WIDTH_STOCK
        }
        setParameters(context, listOf(
            "bass_enhancer_enable=$flag",
            "bass-enhancer-enable=$flag",
            "bass_enhancer_boost=$rawBoost",
            "bass-enhancer-boost=$rawBoost",
            "bass_enhancer_cutoff_frequency=$cutoff",
            "bass-enhancer-cutoff-frequency=$cutoff",
            "bass_enhancer_width=$widthVal",
            "bass-enhancer-width=$widthVal"
        ))
        DolbyConstants.dlog(TAG, "adv_bass enabled=$enabled boost=$rawBoost cutoff=$cutoff width=$widthVal")
    }

    fun applyHeadphoneVirtualizerTuning(
        context: Context,
        mode: Int,
        lrAngle: Int,
        startBand: Int
    ) {
        val virtMode = mode.coerceIn(0, 1)
        val angle = lrAngle.coerceIn(0, DolbyConstants.HP_VIRT_LR_ANGLE_MAX)
        val band = startBand.coerceIn(0, DolbyConstants.HP_VIRT_START_BAND_MAX)
        setParameters(context, listOf(
            "headphone_virtualizer_mode=$virtMode",
            "headphone-virtualizer-mode=$virtMode",
            "advanced_headphone_virtualizer_lr_angle=$angle",
            "advanced-headphone-virtualizer-lr-angle=$angle",
            "virtualizer_start_band=$band",
            "virtualizer-start-band=$band"
        ))
        DolbyConstants.dlog(TAG, "hp_virt_tuning mode=$virtMode angle=$angle startBand=$band")
    }

    fun applyCalibrationBoost(context: Context, boost: Int) {
        val value = boost.coerceIn(0, DolbyConstants.CALIBRATION_BOOST_MAX)
        setParameters(context, listOf(
            "calibration_boost=$value",
            "calibration-boost=$value",
            "dolby_calibration_boost=$value"
        ))
        DolbyConstants.dlog(TAG, "calibration_boost=$value")
    }

    fun applyGameLatencyHal(context: Context, enabled: Boolean) {
        val flag = if (enabled) 1 else 0
        setParameters(context, listOf(
            "game_latency_mode=$flag",
            "game-latency-mode=$flag",
            "dolby_low_latency=$flag",
            "dolby-low-latency=$flag"
        ))
        DolbyConstants.dlog(TAG, "game_latency=$flag")
    }

    fun applySpatialAudio(
        context: Context,
        balanceEnabled: Boolean,
        balance: Int,
        monoEnabled: Boolean,
        crossfeedEnabled: Boolean,
        crossfeedStrength: Int
    ) {
        val balanceVal = if (balanceEnabled) balance.coerceIn(-100, 100) else 0
        val mono = if (monoEnabled) 1 else 0
        val crossfeed = if (crossfeedEnabled) crossfeedStrength.coerceIn(0, 100) else 0
        setParameters(context, listOf(
            "stereo_balance=$balanceVal",
            "dolby_stereo_balance=$balanceVal",
            "mono_mix=$mono",
            "dolby_mono_mix=$mono",
            "crossfeed=$crossfeed",
            "dolby_crossfeed=$crossfeed"
        ))
        DolbyConstants.dlog(TAG, "spatial balance=$balanceVal mono=$mono crossfeed=$crossfeed")
    }

    private fun setParameters(context: Context, params: List<String>) {
        val am = audioManager(context) ?: return
        params.forEach { param ->
            try {
                am.setParameters(param)
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "setParameters failed for $param: ${e.message}")
            }
        }
    }

    private const val TAG = "DolbyHalBridge"
}
