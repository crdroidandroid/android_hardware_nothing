/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

enum class LoudnessPreset(val key: String) {
    FLAT("flat"),
    PUNCH("punch"),
    WARM("warm"),
    MAX("max");

    companion object {
        fun fromKey(key: String): LoudnessPreset? = entries.find { it.key == key }
    }
}

object LoudnessPresetManager {

    data class LoudnessApplyResult(
        val outputBoostEnabled: Boolean,
        val outputBoostTenths: Int,
        val bassLevel: Int,
        val bassCurve: Int
    )

    fun buildForPreset(preset: LoudnessPreset, engine: AudioEnginePreferences): LoudnessApplyResult {
        val maxBoost = if (engine.isExtendedOutputBoostEnabled()) {
            engine.getOutputBoostMaxTenths()
        } else {
            30 // +3 dB default when extended off
        }
        return when (preset) {
            LoudnessPreset.FLAT -> LoudnessApplyResult(
                outputBoostEnabled = false,
                outputBoostTenths = 0,
                bassLevel = 0,
                bassCurve = 0
            )
            LoudnessPreset.PUNCH -> LoudnessApplyResult(
                outputBoostEnabled = true,
                outputBoostTenths = (maxBoost * 0.5f).toInt().coerceAtMost(80),
                bassLevel = 55,
                bassCurve = 2
            )
            LoudnessPreset.WARM -> LoudnessApplyResult(
                outputBoostEnabled = true,
                outputBoostTenths = (maxBoost * 0.35f).toInt().coerceAtMost(50),
                bassLevel = 45,
                bassCurve = 0
            )
            LoudnessPreset.MAX -> LoudnessApplyResult(
                outputBoostEnabled = true,
                outputBoostTenths = maxBoost,
                bassLevel = 70,
                bassCurve = 2
            )
        }
    }

    fun applyToProfile(
        repository: DolbyRepository,
        profile: Int,
        preset: LoudnessPreset,
        engine: AudioEnginePreferences
    ) {
        if (!engine.isLoudnessPresetsEnabled()) return
        val config = buildForPreset(preset, engine)
        repository.setOutputBoost(profile, config.outputBoostEnabled, config.outputBoostTenths)
        repository.setBassLevel(profile, config.bassLevel)
        repository.setBassCurve(profile, config.bassCurve)
    }
}
