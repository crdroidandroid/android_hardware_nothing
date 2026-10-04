/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.audio

import android.content.Context
import org.lunaris.dolby.DolbyConstants

/**
 * Tuning for the DAX parameters the AudioEffect DsParam channel cannot carry.
 *
 * Everything here goes to the Dolby Management Service through [DolbyDmsBridge].
 * AudioManager.setParameters() was never an option for these: the audio HAL has no consumer
 * for DAX parameter names, so those keys were dropped without an error, and no amount of
 * underscore/hyphen name variants would have helped.
 */
object DolbyHalBridge {

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
        // Enable first. DMS rejects virtual-bass-process-enable for non-speaker endpoints and
        // says so in its own log; the tuning values are still valid for the endpoint in use.
        val applied = DolbyDmsBridge.setIntParams(
            linkedMapOf(
                DolbyDmsBridge.Param.VIRTUAL_BASS_PROCESS_ENABLE to flag,
                DolbyDmsBridge.Param.VIRTUAL_BASS_MODE to vbMode,
                DolbyDmsBridge.Param.VIRTUAL_BASS_OVERALL_GAIN to overall,
                DolbyDmsBridge.Param.VIRTUAL_BASS_SLOPE_GAIN to slope
            )
        )
        DolbyConstants.dlog(TAG, "virtual_bass=$flag mode=$vbMode overall=$overall slope=$slope applied=$applied")
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
        // bass-enhancer-enable is the same stage AudioTuningController toggles as
        // DsParam.BASS_ENHANCER_ENABLE. Setting it here as well keeps the stage on when only
        // the tuning values changed, so a boost edit cannot land on a disabled stage.
        val applied = DolbyDmsBridge.setIntParams(
            linkedMapOf(
                DolbyDmsBridge.Param.BASS_ENHANCER_ENABLE to flag,
                DolbyDmsBridge.Param.BASS_ENHANCER_BOOST to rawBoost,
                DolbyDmsBridge.Param.BASS_ENHANCER_CUTOFF_FREQUENCY to cutoff,
                DolbyDmsBridge.Param.BASS_ENHANCER_WIDTH to widthVal
            )
        )
        DolbyConstants.dlog(TAG, "adv_bass enabled=$enabled boost=$rawBoost cutoff=$cutoff width=$widthVal applied=$applied")
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
        val applied = DolbyDmsBridge.setIntParams(
            linkedMapOf(
                DolbyDmsBridge.Param.HEADPHONE_VIRTUALIZER_MODE to virtMode,
                DolbyDmsBridge.Param.HEADPHONE_VIRTUALIZER_STEERER_SOURCE_DISTANCE to angle,
                DolbyDmsBridge.Param.VIRTUALIZER_START_BAND to band
            )
        )
        DolbyConstants.dlog(TAG, "hp_virt_tuning mode=$virtMode angle=$angle startBand=$band applied=$applied")
    }

    fun applyCalibrationBoost(context: Context, boost: Int) {
        val value = boost.coerceIn(0, DolbyConstants.CALIBRATION_BOOST_MAX)
        val applied = DolbyDmsBridge.setIntParam(DolbyDmsBridge.Param.CALIBRATION_BOOST, value)
        DolbyConstants.dlog(TAG, "calibration_boost=$value applied=$applied")
    }

    /**
     * No-op on purpose: DAX has no game-latency parameter. Neither "game-latency-mode" nor
     * "dolby-low-latency" exists in the libdlbdsservice.so name table, so there is no engine
     * side to set. Kept so the call sites stay intact until the setting is either wired to a
     * real mechanism or removed from the UI.
     */
    fun applyGameLatencyHal(context: Context, enabled: Boolean) {
        DolbyConstants.dlog(TAG, "game_latency=$enabled not applied: DAX has no such parameter")
    }

    /**
     * No-op on purpose: DAX has no stereo-balance, mono-mix or crossfeed parameter, so these
     * names are not in the libdlbdsservice.so table either. See [applyGameLatencyHal].
     */
    fun applySpatialAudio(
        context: Context,
        balanceEnabled: Boolean,
        balance: Int,
        monoEnabled: Boolean,
        crossfeedEnabled: Boolean,
        crossfeedStrength: Int
    ) {
        DolbyConstants.dlog(TAG, "spatial audio not applied: DAX has no balance/mono/crossfeed parameter")
    }

    private const val TAG = "DolbyHalBridge"
}
