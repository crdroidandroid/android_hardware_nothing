/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.DolbyConstants.DsParam
import org.lunaris.dolby.R
import org.lunaris.dolby.audio.DolbyAudioEffect
import org.lunaris.dolby.domain.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DolbyRepository(private val context: Context) : AutoCloseable {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val dolbyEffect: DolbyAudioEffect
        get() = getOrCreateDolbyEffect()
    
    private val defaultPrefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
    private val presetsPrefs = context.getSharedPreferences(DolbyConstants.PREF_FILE_PRESETS, Context.MODE_PRIVATE)
    
    private val deviceStateManager = DeviceStateManager(context)
    private val audioEnginePrefs = AudioEnginePreferences(context)
    private val equalizerPresetStore = EqualizerPresetStore(context)
    private val customDolbyPresetStore = CustomDolbyPresetStore(
        context = context,
        host = object : CustomDolbyPresetStore.Host {
            override fun getCurrentProfile(): Int = this@DolbyRepository.getCurrentProfile()
            override fun getBandMode(): BandMode = this@DolbyRepository.getBandMode()
            override fun setBandMode(mode: BandMode) = this@DolbyRepository.setBandMode(mode)
            override fun getDolbyEnabled(): Boolean = this@DolbyRepository.getDolbyEnabled()
            override fun setDolbyEnabled(enabled: Boolean) = this@DolbyRepository.setDolbyEnabled(enabled)
            override fun setCurrentProfile(profile: Int) = this@DolbyRepository.setCurrentProfile(profile)
            override fun getProfilePrefs(profile: Int): SharedPreferences =
                this@DolbyRepository.getProfilePrefs(profile)
            override fun applyRestoredProfile(profile: Int) {
                restoreProfilePreset(profile)
                applyProfileSettings(profile)
            }
            override fun getUserPresets(): List<EqualizerPreset> = this@DolbyRepository.getUserPresets()
            override fun clearAllUserPresets() = this@DolbyRepository.clearAllUserPresets()
            override fun addUserPreset(name: String, bandGains: List<BandGain>, bandMode: BandMode) =
                this@DolbyRepository.addUserPreset(name, bandGains, bandMode)
        }
    )

    private fun clampGeqGain(gain: Int): Int = audioEnginePrefs.clampEqGain(gain)

    private val audioTuning = AudioTuningController(
        context = context,
        dolbyEffectProvider = { dolbyEffect },
        checkEffect = { checkEffect() },
        isReleased = { isReleased },
        profilePrefs = { getProfilePrefs(it) },
        defaultPrefs = defaultPrefs,
        activeDeviceCategory = { resolveActiveAudioDevice().category }
    )

    private val _activeAudioDevice = MutableStateFlow(resolveActiveAudioDevice())
    val activeAudioDevice: StateFlow<ActiveAudioDevice> = _activeAudioDevice.asStateFlow()

    private val _isOnSpeaker = MutableStateFlow(_activeAudioDevice.value.isOnSpeaker)
    val isOnSpeaker: StateFlow<Boolean> = _isOnSpeaker.asStateFlow()
    
    private val _currentProfile = MutableStateFlow(0)
    val currentProfile: StateFlow<Int> = _currentProfile.asStateFlow()

    val stereoWideningSupported = context.resources.getBoolean(R.bool.dolby_stereo_widening_supported)
    
    private var isReleased = false
    
    private var isBypassActive = false

    private fun checkEffect() {
        if (isReleased) {
            DolbyConstants.dlog(TAG, "Repository released, skipping effect check")
            return
        }

        var recovered = false
        try {
            synchronized(EFFECT_LOCK) {
                val effect = getOrCreateDolbyEffect()
                if (!effect.hasControl()) {
                    DolbyConstants.dlog(TAG, "Shared audio effect lost control, recreating once")
                    effect.release()
                    sharedDolbyEffect = null
                    getOrCreateDolbyEffect()
                    recovered = true
                }
            }
            if (recovered) restoreSavedProfileIfNeeded()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error checking effect: ${e.message}")
        }
    }

    fun hasEffectControl(): Boolean {
        if (isReleased) return false
        return try {
            synchronized(EFFECT_LOCK) { getOrCreateDolbyEffect().hasControl() }
        } catch (e: Exception) {
            false
        }
    }

    private fun readSavedProfile(): Int? {
        return defaultPrefs.getString(DolbyConstants.PREF_PROFILE, null)
            ?.toIntOrNull()
    }

    private fun restoreSavedProfileIfNeeded() {
        val savedProfile = readSavedProfile() ?: return
        if (dolbyEffect.profile != savedProfile) {
            dolbyEffect.profile = savedProfile
        }
        restoreProfilePreset(savedProfile)
        applyProfileSettings(savedProfile)
    }

    private fun applyProfileSettings(profile: Int) {
        try {
            val prefs = getProfilePrefs(profile)
            
            val ieqPreset = prefs.getString(DolbyConstants.PREF_IEQ, "0")?.toIntOrNull() ?: 0
            dolbyEffect.setDapParameter(DsParam.IEQ_PRESET, ieqPreset, profile)
            
            val hpVirtualizer = prefs.getBoolean(DolbyConstants.PREF_HP_VIRTUALIZER, false)
            dolbyEffect.setDapParameter(DsParam.HEADPHONE_VIRTUALIZER, hpVirtualizer, profile)
            
            val spkVirtualizer = prefs.getBoolean(DolbyConstants.PREF_SPK_VIRTUALIZER, false)
            dolbyEffect.setDapParameter(DsParam.SPEAKER_VIRTUALIZER, spkVirtualizer, profile)
            
            if (stereoWideningSupported) {
                val stereoWidening = prefs.getInt(DolbyConstants.PREF_STEREO_WIDENING, 32)
                dolbyEffect.setDapParameter(DsParam.STEREO_WIDENING_AMOUNT, stereoWidening, profile)
            }
            
            val dialogueEnabled = prefs.getBoolean(DolbyConstants.PREF_DIALOGUE, false)
            dolbyEffect.setDapParameter(DsParam.DIALOGUE_ENHANCER_ENABLE, dialogueEnabled, profile)
            
            val dialogueAmount = prefs.getInt(DolbyConstants.PREF_DIALOGUE_AMOUNT, 6)
            dolbyEffect.setDapParameter(DsParam.DIALOGUE_ENHANCER_AMOUNT, dialogueAmount, profile)
            
            val bassEnabled = prefs.getBoolean(DolbyConstants.PREF_BASS, false)
            dolbyEffect.setDapParameter(DsParam.BASS_ENHANCER_ENABLE, bassEnabled, profile)

            audioTuning.applyAll(profile)
            
            DolbyConstants.dlog(TAG, "Successfully restored all settings for profile $profile")
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Failed to restore profile settings: ${e.message}")
        }
    }

    fun applySavedState() {
        try {
            checkEffect()
            val enabled = defaultPrefs.getBoolean(DolbyConstants.PREF_ENABLE, false)
            dolbyEffect.dsOn = enabled
            if (enabled) {
                restoreSavedProfileIfNeeded()
            }
        } catch (e: Exception) {
            // Must never escape to DolbyEffectService.onCreate — an uncaught throw after
            // startForegroundService() kills the whole process and DolbyActivity cannot stay open
            // while widget/QS (which catch effect errors) still appear to work.
            DolbyConstants.elog(TAG, "Failed to apply saved state: ${e.message}", e)
        }
    }

    fun getCurrentOutputDevice(): AudioDeviceInfo? {
        return deviceStateManager.getCurrentOutputDevice(audioManager)
    }

    private fun resolveActiveAudioDevice(): ActiveAudioDevice {
        val device = getCurrentOutputDevice() ?: return ActiveAudioDevice.Unknown
        return ActiveAudioDevice(
            name = deviceStateManager.deviceDisplayName(device),
            category = device.toAudioCategory()
        )
    }

    private fun AudioDeviceInfo.toAudioCategory(): AudioDeviceCategory {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioDeviceCategory.SPEAKER
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioDeviceCategory.WIRED
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> AudioDeviceCategory.BLUETOOTH
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE -> AudioDeviceCategory.USB
            else -> AudioDeviceCategory.OTHER
        }
    }

    fun updateSpeakerState() {
        if (!isReleased) {
            val activeDevice = resolveActiveAudioDevice()
            _activeAudioDevice.value = activeDevice
            _isOnSpeaker.value = activeDevice.isOnSpeaker
        }
    }

    fun getDolbyEnabled(): Boolean {
        return try {
            dolbyEffect.dsOn
        } catch (e: Exception) {
            DolbyConstants.elog(TAG, "Error getting Dolby enabled state: ${e.message}", e)
            false
        }
    }

    fun setDolbyEnabled(enabled: Boolean) {
        if (isReleased) return
        
        try {
            checkEffect()
            dolbyEffect.dsOn = enabled
            defaultPrefs.edit().putBoolean(DolbyConstants.PREF_ENABLE, enabled).apply()
            isBypassActive = false
            DolbyStatusNotifier.notifyChanged(context)
        } catch (e: Exception) {
            DolbyConstants.elog(TAG, "Error setting Dolby enabled: ${e.message}", e)
        }
    }

    fun setDolbyBypass(bypass: Boolean) {
        if (isReleased) return

        try {
            checkEffect()
            if (bypass) {
                if (!isBypassActive && defaultPrefs.getBoolean(DolbyConstants.PREF_ENABLE, false)) {
                    dolbyEffect.dsOn = false
                    isBypassActive = true
                    DolbyConstants.dlog(TAG, "Dolby bypass active")
                }
            } else if (isBypassActive) {
                val savedEnabled = defaultPrefs.getBoolean(DolbyConstants.PREF_ENABLE, false)
                dolbyEffect.dsOn = savedEnabled
                isBypassActive = false
                DolbyConstants.dlog(TAG, "Dolby bypass released")
            }
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting Dolby bypass: ${e.message}")
        }
    }

    fun getCurrentProfile(): Int {
        return try {
            checkEffect()
            restoreSavedProfileIfNeeded()
            dolbyEffect.profile
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting current profile: ${e.message}")
            0
        }
    }

    fun setCurrentProfile(profile: Int) {
        if (isReleased) return
        
        try {
            checkEffect()
            dolbyEffect.profile = profile
            defaultPrefs.edit().putString(DolbyConstants.PREF_PROFILE, profile.toString()).apply()
            if (!verifyProfileSaved(profile)) {
                DolbyConstants.dlog(TAG, "WARNING: Profile may not have been saved correctly!")
            }
            restoreProfilePreset(profile)
            applyProfileSettings(profile)
            _currentProfile.value = profile
            DolbyConstants.dlog(TAG, "Profile set to: $profile")
            DolbyStatusNotifier.notifyChanged(context)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting current profile: ${e.message}")
        }
    }

    fun cycleToNextProfile(): Int {
        val profileIds = context.resources.getStringArray(R.array.dolby_profile_values)
            .map { it.toInt() }
        if (profileIds.isEmpty()) return getCurrentProfile()

        val current = getCurrentProfile()
        val currentIndex = profileIds.indexOf(current)
        val nextProfile = profileIds[(currentIndex + 1) % profileIds.size]
        setCurrentProfile(nextProfile)
        return nextProfile
    }

    fun getProfileDisplayName(profile: Int): String {
        val profiles = context.resources.getStringArray(R.array.dolby_profile_entries)
        val profileValues = context.resources.getStringArray(R.array.dolby_profile_values)
        val index = profileValues.indexOf(profile.toString())
        return if (index != -1) profiles[index] else context.getString(R.string.dolby_unknown)
    }

    private fun restoreProfilePreset(profile: Int) {
        audioTuning.pushGeqToHardware(profile)
    }

    fun verifyProfileSaved(profile: Int): Boolean {
        val prefs = defaultPrefs.getString(DolbyConstants.PREF_PROFILE, "0")?.toIntOrNull()
        val saved = prefs == profile
        DolbyConstants.dlog(TAG, "Profile verification: requested=$profile, saved=$prefs, match=$saved")
        return saved
    }

    private fun getProfilePrefs(profile: Int): SharedPreferences {
        return context.getSharedPreferences("profile_$profile", Context.MODE_PRIVATE)
    }

    fun getBandMode(): BandMode {
        val mode = defaultPrefs.getString(DolbyConstants.PREF_BAND_MODE, "10")
        return when (mode) {
            "10" -> BandMode.TEN_BAND
            "15" -> BandMode.FIFTEEN_BAND
            "20" -> BandMode.TWENTY_BAND
            else -> BandMode.TEN_BAND
        }
    }

    fun setBandMode(mode: BandMode) {
        defaultPrefs.edit().putString(DolbyConstants.PREF_BAND_MODE, mode.value).apply()
    }

    fun getBassEnhancerEnabled(profile: Int): Boolean {
        return try {
            dolbyEffect.getDapParameterBool(DsParam.BASS_ENHANCER_ENABLE, profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting bass enhancer: ${e.message}")
            false
        }
    }

    fun setBassEnhancerEnabled(profile: Int, enabled: Boolean) {
        if (isReleased) return

        try {
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.BASS_ENHANCER_ENABLE, enabled, profile)
            getProfilePrefs(profile).edit().putBoolean(DolbyConstants.PREF_BASS, enabled).apply()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting bass enhancer: ${e.message}")
        }
    }

    fun getBassLevel(profile: Int): Int {
        val prefs = getProfilePrefs(profile)
        return prefs.getInt(DolbyConstants.PREF_BASS_LEVEL, 0)
    }

    fun getBassCurve(profile: Int): Int {
        val prefs = getProfilePrefs(profile)
        return prefs.getInt(DolbyConstants.PREF_BASS_CURVE, 0)
    }

    fun setBassCurve(profile: Int, curve: Int) {
        if (isReleased) return
        
        try {
            val prefs = getProfilePrefs(profile)
            val previousCurve = prefs.getInt(DolbyConstants.PREF_BASS_CURVE, 0)
            val level = prefs.getInt(DolbyConstants.PREF_BASS_LEVEL, 0)
            if (previousCurve == curve) return

            prefs.edit().putInt(DolbyConstants.PREF_BASS_CURVE, curve).apply()

            if (level <= 0) return
            checkEffect()
            val currentGains = audioTuning.getRawGeqArray(profile)?.copyOf()
                ?: dolbyEffect.getDapParameter(DsParam.GEQ_BAND_GAINS, profile)
            val modifiedGains = currentGains.copyOf()
            applyBassCurve(modifiedGains, level, previousCurve, -1)
            applyBassCurve(modifiedGains, level, curve, 1)
            audioTuning.saveRawGeqArray(profile, modifiedGains)
            audioTuning.pushGeqToHardware(profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting bass curve: ${e.message}")
            throw e
        }
    }

    private fun applyBassCurve(gains: IntArray, level: Int, curve: Int, direction: Int) {
        val weights = BASS_CURVES.getOrElse(curve) { BASS_CURVES[0] }
        val baseGain = level * BASS_GAIN_MULTIPLIER
        for (i in weights.indices) {
            if (i >= gains.size) break
            val weightedGain = (baseGain * weights[i] * direction).toInt()
            gains[i] = clampGeqGain(gains[i] + weightedGain)
        }
    }

    fun setBassLevel(profile: Int, level: Int) {
        if (isReleased) return
        
        DolbyConstants.dlog(TAG, "setBassLevel: profile=$profile level=$level")

        if (level !in 0..100) {
            DolbyConstants.dlog(TAG, "setBassLevel: invalid level $level")
            throw IllegalArgumentException("Bass level must be between 0 and 100")
        }
        
        try {
            val prefs = getProfilePrefs(profile)
            val previousLevel = prefs.getInt(DolbyConstants.PREF_BASS_LEVEL, 0)
            
            prefs.edit().putInt(DolbyConstants.PREF_BASS_LEVEL, level).apply()
            
            setBassEnhancerEnabled(profile, level > 0)
            
            checkEffect()
            val currentGains = audioTuning.getRawGeqArray(profile)?.copyOf()
                ?: dolbyEffect.getDapParameter(DsParam.GEQ_BAND_GAINS, profile)
            val modifiedGains = currentGains.copyOf()
            
            val curve = prefs.getInt(DolbyConstants.PREF_BASS_CURVE, 0)
            if (previousLevel > 0) {
                applyBassCurve(modifiedGains, previousLevel, curve, -1)
            }

            if (level > 0) {
                applyBassCurve(modifiedGains, level, curve, 1)
            }
            audioTuning.saveRawGeqArray(profile, modifiedGains)
            audioTuning.pushGeqToHardware(profile)
            
            DolbyConstants.dlog(TAG, "setBassLevel: success")
        } catch (e: IllegalArgumentException) {
            DolbyConstants.dlog(TAG, "setBassLevel: validation error - ${e.message}")
            val prefs = getProfilePrefs(profile)
            prefs.edit().putInt(DolbyConstants.PREF_BASS_LEVEL, 0).apply()
            throw e
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "setBassLevel: unexpected error - ${e.message}")
            throw e
        }
    }

    fun getTrebleEnhancerEnabled(profile: Int): Boolean {
        val prefs = getProfilePrefs(profile)
        return prefs.getBoolean(DolbyConstants.PREF_TREBLE, false)
    }

    fun setTrebleEnhancerEnabled(profile: Int, enabled: Boolean) {
        getProfilePrefs(profile).edit().putBoolean(DolbyConstants.PREF_TREBLE, enabled).apply()
    }

    fun getTrebleLevel(profile: Int): Int {
        val prefs = getProfilePrefs(profile)
        return prefs.getInt(DolbyConstants.PREF_TREBLE_LEVEL, 0)
    }

    fun setTrebleLevel(profile: Int, level: Int) {
        if (isReleased) return
        
        DolbyConstants.dlog(TAG, "setTrebleLevel: profile=$profile level=$level")

        if (level !in 0..100) {
            DolbyConstants.dlog(TAG, "setTrebleLevel: invalid level $level")
            throw IllegalArgumentException("Treble level must be between 0 and 100")
        }

        try {
            val prefs = getProfilePrefs(profile)
            val previousLevel = prefs.getInt(DolbyConstants.PREF_TREBLE_LEVEL, 0)

            prefs.edit().putInt(DolbyConstants.PREF_TREBLE_LEVEL, level).apply()
            setTrebleEnhancerEnabled(profile, level > 0)

            checkEffect()
            val currentGains = audioTuning.getRawGeqArray(profile)?.copyOf()
                ?: dolbyEffect.getDapParameter(DsParam.GEQ_BAND_GAINS, profile)
            val modifiedGains = currentGains.copyOf()

            if (previousLevel > 0) {
                val previousGain = (previousLevel * TREBLE_GAIN_MULTIPLIER).toInt()
                for (i in 14..19) {
                    if (i < modifiedGains.size) {
                        modifiedGains[i] = clampGeqGain(modifiedGains[i] - previousGain)
                    }
                }
            }

            if (level > 0) {
                val trebleGain = (level * TREBLE_GAIN_MULTIPLIER).toInt()
                for (i in 14..19) {
                    if (i < modifiedGains.size) {
                        modifiedGains[i] = clampGeqGain(modifiedGains[i] + trebleGain)
                    }
                }
            }

            audioTuning.saveRawGeqArray(profile, modifiedGains)
            audioTuning.pushGeqToHardware(profile)
            
            DolbyConstants.dlog(TAG, "setTrebleLevel: success")
        } catch (e: IllegalArgumentException) {
            DolbyConstants.dlog(TAG, "setTrebleLevel: validation error - ${e.message}")
            val prefs = getProfilePrefs(profile)
            prefs.edit().putInt(DolbyConstants.PREF_TREBLE_LEVEL, 0).apply()
            throw e
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "setTrebleLevel: unexpected error - ${e.message}")
            throw e
        }
    }

    fun getIeqPreset(profile: Int): Int {
        return try {
            dolbyEffect.getDapParameterInt(DsParam.IEQ_PRESET, profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting IEQ preset: ${e.message}")
            0
        }
    }

    fun setIeqPreset(profile: Int, preset: Int) {
        if (isReleased) return
        
        try {
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.IEQ_PRESET, preset, profile)
            getProfilePrefs(profile).edit().putString(DolbyConstants.PREF_IEQ, preset.toString()).apply()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting IEQ preset: ${e.message}")
        }
    }

    fun getHeadphoneVirtualizerEnabled(profile: Int): Boolean {
        return try {
            dolbyEffect.getDapParameterBool(DsParam.HEADPHONE_VIRTUALIZER, profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting headphone virtualizer: ${e.message}")
            false
        }
    }

    fun setHeadphoneVirtualizerEnabled(profile: Int, enabled: Boolean) {
        if (isReleased) return
        
        try {
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.HEADPHONE_VIRTUALIZER, enabled, profile)
            getProfilePrefs(profile).edit().putBoolean(DolbyConstants.PREF_HP_VIRTUALIZER, enabled).apply()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting headphone virtualizer: ${e.message}")
        }
    }

    fun getSpeakerVirtualizerEnabled(profile: Int): Boolean {
        return try {
            dolbyEffect.getDapParameterBool(DsParam.SPEAKER_VIRTUALIZER, profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting speaker virtualizer: ${e.message}")
            false
        }
    }

    fun setSpeakerVirtualizerEnabled(profile: Int, enabled: Boolean) {
        if (isReleased) return
        
        try {
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.SPEAKER_VIRTUALIZER, enabled, profile)
            getProfilePrefs(profile).edit().putBoolean(DolbyConstants.PREF_SPK_VIRTUALIZER, enabled).apply()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting speaker virtualizer: ${e.message}")
        }
    }

    fun getStereoWideningAmount(profile: Int): Int {
        if (!stereoWideningSupported) return 0
        val prefs = getProfilePrefs(profile)
        if (prefs.contains(DolbyConstants.PREF_STEREO_WIDENING)) {
            return prefs.getInt(DolbyConstants.PREF_STEREO_WIDENING, 32)
        }
        return try {
            val amount = dolbyEffect.getDapParameterInt(DsParam.STEREO_WIDENING_AMOUNT, profile)
            prefs.edit().putInt(DolbyConstants.PREF_STEREO_WIDENING, amount).apply()
            amount
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting stereo widening: ${e.message}")
            32
        }
    }

    fun setStereoWideningAmount(profile: Int, amount: Int) {
        if (!stereoWideningSupported || isReleased) return
        
        try {
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.STEREO_WIDENING_AMOUNT, amount, profile)
            getProfilePrefs(profile).edit().putInt(DolbyConstants.PREF_STEREO_WIDENING, amount).apply()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting stereo widening: ${e.message}")
        }
    }

    fun getDialogueEnhancerEnabled(profile: Int): Boolean {
        return try {
            dolbyEffect.getDapParameterBool(DsParam.DIALOGUE_ENHANCER_ENABLE, profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting dialogue enhancer: ${e.message}")
            false
        }
    }

    fun setDialogueEnhancerEnabled(profile: Int, enabled: Boolean) {
        if (isReleased) return
        
        try {
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.DIALOGUE_ENHANCER_ENABLE, enabled, profile)
            getProfilePrefs(profile).edit().putBoolean(DolbyConstants.PREF_DIALOGUE, enabled).apply()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting dialogue enhancer: ${e.message}")
        }
    }

    fun getDialogueEnhancerAmount(profile: Int): Int {
        val prefs = getProfilePrefs(profile)
        if (prefs.contains(DolbyConstants.PREF_DIALOGUE_AMOUNT)) {
            return prefs.getInt(DolbyConstants.PREF_DIALOGUE_AMOUNT, 6)
        }
        return try {
            val amount = dolbyEffect.getDapParameterInt(DsParam.DIALOGUE_ENHANCER_AMOUNT, profile)
            prefs.edit().putInt(DolbyConstants.PREF_DIALOGUE_AMOUNT, amount).apply()
            amount
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting dialogue enhancer amount: ${e.message}")
            6
        }
    }

    fun setDialogueEnhancerAmount(profile: Int, amount: Int) {
        if (isReleased) return
        
        try {
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.DIALOGUE_ENHANCER_AMOUNT, amount, profile)
            getProfilePrefs(profile).edit().putInt(DolbyConstants.PREF_DIALOGUE_AMOUNT, amount).apply()
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting dialogue enhancer amount: ${e.message}")
        }
    }

    fun getEqualizerGains(profile: Int, bandMode: BandMode): List<BandGain> {
        return try {
            val gains = audioTuning.getRawGeqArray(profile)
                ?: dolbyEffect.getDapParameter(DsParam.GEQ_BAND_GAINS, profile)
            deserializeGains(gains, bandMode)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting equalizer gains: ${e.message}")
            val frequencies = when (bandMode) {
                BandMode.TEN_BAND -> BAND_FREQUENCIES_10
                BandMode.FIFTEEN_BAND -> BAND_FREQUENCIES_15
                BandMode.TWENTY_BAND -> BAND_FREQUENCIES_20
            }
            frequencies.map { BandGain(frequency = it, gain = 0) }
        }
    }

    fun setEqualizerGains(profile: Int, bandGains: List<BandGain>, bandMode: BandMode) {
        if (isReleased) return
        
        try {
            checkEffect()
            val gains = serializeGains(bandGains, bandMode)
            audioTuning.saveRawGeqArray(profile, gains)
            audioTuning.pushGeqToHardware(profile)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error setting equalizer gains: ${e.message}")
        }
    }

    fun getPresetName(profile: Int): String {
        return try {
            val gains = audioTuning.getRawGeqArray(profile)
                ?: dolbyEffect.getDapParameter(DsParam.GEQ_BAND_GAINS, profile)
            
            val tenBandGains = gains.filterIndexed { index, _ -> index % 2 == 0 }
            val currentGainsString = tenBandGains.joinToString(",")
            
            val presetValues = context.resources.getStringArray(R.array.dolby_preset_values)
            val presetNames = context.resources.getStringArray(R.array.dolby_preset_entries)
            
            presetValues.forEachIndexed { index, preset ->
                val presetTenBand = convertTo10Band(preset)
                if (gainsMatch(presetTenBand, currentGainsString)) {
                    return presetNames[index]
                }
            }
            
            presetsPrefs.all.forEach { (name, value) ->
                val presetTenBand = convertTo10Band(value.toString())
                if (gainsMatch(presetTenBand, currentGainsString)) {
                    return name
                }
            }
            
            context.getString(R.string.dolby_preset_custom)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting preset name: ${e.message}")
            context.getString(R.string.dolby_preset_custom)
        }
    }

    private fun convertTo10Band(gainsString: String): String {
        val gains = gainsString.split(",").map { it.trim().toIntOrNull() ?: 0 }
        
        if (gains.size == 10) {
            return gains.joinToString(",")
        }
        
        if (gains.size == 20) {
            val tenBand = gains.filterIndexed { index, _ -> index % 2 == 0 }
            return tenBand.joinToString(",")
        }
        
        return gainsString
    }

    private fun gainsMatch(gains1: String, gains2: String): Boolean {
        val g1 = gains1.split(",").map { it.trim().toIntOrNull() ?: 0 }
        val g2 = gains2.split(",").map { it.trim().toIntOrNull() ?: 0 }
        
        if (g1.size != g2.size) return false
        
        return g1.zip(g2).all { (a, b) -> kotlin.math.abs(a - b) <= 1 }
    }

    fun getUserPresets(): List<EqualizerPreset> {
        return equalizerPresetStore.getUserPresets(getBandMode())
    }

    fun addUserPreset(name: String, bandGains: List<BandGain>, bandMode: BandMode) {
        equalizerPresetStore.addUserPreset(name, bandGains, bandMode)
    }

    fun deleteUserPreset(name: String) {
        equalizerPresetStore.deleteUserPreset(name)
    }

    fun clearAllUserPresets() {
        equalizerPresetStore.clearAllUserPresets()
    }

    fun getCustomDolbyPresets(): List<CustomDolbyPresetSummary> =
        customDolbyPresetStore.getCustomDolbyPresets()

    fun saveCustomDolbyPreset(name: String) =
        customDolbyPresetStore.saveCustomDolbyPreset(name)

    fun applyCustomDolbyPreset(name: String) =
        customDolbyPresetStore.applyCustomDolbyPreset(name)

    fun deleteCustomDolbyPreset(name: String) =
        customDolbyPresetStore.deleteCustomDolbyPreset(name)

    fun exportFullBackupJson(): String =
        customDolbyPresetStore.exportFullBackupJson()

    fun importFullBackup(jsonString: String) =
        customDolbyPresetStore.importFullBackup(jsonString)

    fun resetProfile(profile: Int) {
        if (isReleased) return
        
        try {
            checkEffect()
            dolbyEffect.resetProfileSpecificSettings(profile)
            context.deleteSharedPreferences("profile_$profile")
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error resetting profile: ${e.message}")
        }
    }

    fun resetAllProfiles() {
        if (isReleased) return
        
        try {
            checkEffect()
            context.resources.getStringArray(R.array.dolby_profile_values)
                .map { it.toInt() }
                .forEach { resetProfile(it) }
            setCurrentProfile(0)
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error resetting all profiles: ${e.message}")
        }
    }

    private fun deserializeGains(gains: IntArray, bandMode: BandMode): List<BandGain> =
        EqualizerBandCodec.deserializeGains(gains, bandMode)

    private fun serializeGains(bandGains: List<BandGain>, bandMode: BandMode): IntArray =
        EqualizerBandCodec.serializeGains(bandGains, bandMode)

    fun getMidEnhancerEnabled(profile: Int): Boolean {
        val prefs = getProfilePrefs(profile)
        return prefs.getBoolean(DolbyConstants.PREF_MID, false)
    }

    fun setMidEnhancerEnabled(profile: Int, enabled: Boolean) {
        getProfilePrefs(profile).edit().putBoolean(DolbyConstants.PREF_MID, enabled).apply()
    }

    fun getMidLevel(profile: Int): Int {
        val prefs = getProfilePrefs(profile)
        return prefs.getInt(DolbyConstants.PREF_MID_LEVEL, 0)
    }

    fun setMidLevel(profile: Int, level: Int) {
        if (isReleased) return
        
        DolbyConstants.dlog(TAG, "setMidLevel: profile=$profile level=$level")

        if (level !in 0..100) {
            DolbyConstants.dlog(TAG, "setMidLevel: invalid level $level")
            throw IllegalArgumentException("Mid level must be between 0 and 100")
        }

        try {
            val prefs = getProfilePrefs(profile)
            val previousLevel = prefs.getInt(DolbyConstants.PREF_MID_LEVEL, 0)

            prefs.edit().putInt(DolbyConstants.PREF_MID_LEVEL, level).apply()
            setMidEnhancerEnabled(profile, level > 0)

            checkEffect()
            val currentGains = audioTuning.getRawGeqArray(profile)?.copyOf()
                ?: dolbyEffect.getDapParameter(DsParam.GEQ_BAND_GAINS, profile)
            val modifiedGains = currentGains.copyOf()

            if (previousLevel > 0) {
                val previousGain = (previousLevel * MID_GAIN_MULTIPLIER).toInt()
                for (i in 5..13) {
                    if (i < modifiedGains.size) {
                        modifiedGains[i] = clampGeqGain(modifiedGains[i] - previousGain)
                    }
                }
            }

            if (level > 0) {
                val midGain = (level * MID_GAIN_MULTIPLIER).toInt()
                for (i in 5..13) {
                    if (i < modifiedGains.size) {
                        modifiedGains[i] = clampGeqGain(modifiedGains[i] + midGain)
                    }
                }
            }

            audioTuning.saveRawGeqArray(profile, modifiedGains)
            audioTuning.pushGeqToHardware(profile)
            
            DolbyConstants.dlog(TAG, "setMidLevel: success")
        } catch (e: IllegalArgumentException) {
            DolbyConstants.dlog(TAG, "setMidLevel: validation error - ${e.message}")
            val prefs = getProfilePrefs(profile)
            prefs.edit().putInt(DolbyConstants.PREF_MID_LEVEL, 0).apply()
            throw e
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "setMidLevel: unexpected error - ${e.message}")
            throw e
        }
    }
    
    private fun release() {
        if (!isReleased) {
            DolbyConstants.dlog(TAG, "Releasing repository resources")
            isReleased = true
        }
    }
    
    override fun close() {
        release()
    }

    fun getAudioEnginePreferences(): AudioEnginePreferences = audioEnginePrefs

    fun getOutputBoostMaxTenths(): Int = audioTuning.getOutputBoostMaxTenths()
    fun getOutputBoostMinTenths(): Int = audioTuning.getOutputBoostMinTenths()

    fun getHeadroomInfo(profile: Int): HeadroomInfo {
        val raw = audioTuning.getRawGeqArray(profile) ?: IntArray(20)
        return HeadroomCalculator.calculate(
            raw,
            getOutputBoostTenths(profile),
            isOutputBoostEnabled(profile),
            audioEnginePrefs
        )
    }

    fun applyLoudnessPreset(preset: LoudnessPreset) {
        LoudnessPresetManager.applyToProfile(this, getCurrentProfile(), preset, audioEnginePrefs)
    }

    fun refreshSpatialAndGeq(profile: Int = getCurrentProfile()) {
        audioTuning.pushGeqToHardware(profile)
        audioTuning.applyAll(profile)
    }
    fun isOutputBoostEnabled(profile: Int) = audioTuning.isOutputBoostEnabled(profile)
    fun getOutputBoostTenths(profile: Int) = audioTuning.getOutputBoostTenths(profile)
    fun setOutputBoost(profile: Int, enabled: Boolean, tenthsDb: Int) =
        audioTuning.setOutputBoost(profile, enabled, tenthsDb)

    fun isVirtualBassBluetoothEnabled(profile: Int) =
        audioTuning.isVirtualBassBluetoothEnabled(profile)
    fun setVirtualBassBluetoothEnabled(profile: Int, enabled: Boolean) =
        audioTuning.setVirtualBassBluetoothEnabled(profile, enabled)
    fun reapplyVirtualBass(profile: Int = getCurrentProfile()) =
        audioTuning.reapplyVirtualBass(profile)

    fun isGraphicEqEnabled(profile: Int) = audioTuning.isGraphicEqEnabled(profile)
    fun setGraphicEqEnabled(profile: Int, enabled: Boolean) =
        audioTuning.setGraphicEqEnabled(profile, enabled)

    fun isDialogueDuckingEnabled(profile: Int) = audioTuning.isDialogueDuckingEnabled(profile)
    fun getDialogueDuckingAmount(profile: Int) = audioTuning.getDialogueDuckingAmount(profile)
    fun setDialogueDucking(profile: Int, enabled: Boolean, amount: Int) =
        audioTuning.setDialogueDucking(profile, enabled, amount)
    fun getHpVirtMode(profile: Int) = audioTuning.getHpVirtMode(profile)
    fun getHpVirtLrAngle(profile: Int) = audioTuning.getHpVirtLrAngle(profile)
    fun getHpVirtStartBand(profile: Int) = audioTuning.getHpVirtStartBand(profile)
    fun setHeadphoneVirtualizerTuning(profile: Int, mode: Int, lrAngle: Int, startBand: Int) =
        audioTuning.setHeadphoneVirtualizerTuning(profile, mode, lrAngle, startBand)

    fun getVirtualBassMode(profile: Int) = audioTuning.getVirtualBassMode(profile)
    fun getVirtualBassOverallGain(profile: Int) = audioTuning.getVirtualBassOverallGain(profile)
    fun getVirtualBassSlopeGain(profile: Int) = audioTuning.getVirtualBassSlopeGain(profile)
    fun setVirtualBassDetails(profile: Int, mode: Int, overallGain: Int, slopeGain: Int) =
        audioTuning.setVirtualBassDetails(profile, mode, overallGain, slopeGain)
    fun isSpatialAudioEnabled() = audioTuning.isSpatialAudioEnabled()
    fun setSpatialAudioEnabled(enabled: Boolean) = audioTuning.setSpatialAudioEnabled(enabled)

    fun getCalibrationBoostSpeaker() = audioTuning.getCalibrationBoostSpeaker()
    fun getCalibrationBoostHeadphone() = audioTuning.getCalibrationBoostHeadphone()
    fun getCalibrationBoostBluetooth() = audioTuning.getCalibrationBoostBluetooth()
    fun setCalibrationBoostSpeaker(boost: Int) = audioTuning.setCalibrationBoostSpeaker(boost)
    fun setCalibrationBoostHeadphone(boost: Int) = audioTuning.setCalibrationBoostHeadphone(boost)
    fun setCalibrationBoostBluetooth(boost: Int) = audioTuning.setCalibrationBoostBluetooth(boost)
    fun applyCalibrationBoost() = audioTuning.applyCalibrationBoost()

    companion object {
        private const val TAG = "DolbyRepository"
        private const val EFFECT_PRIORITY = 100
        private val EFFECT_LOCK = Any()

        @Volatile
        private var sharedDolbyEffect: DolbyAudioEffect? = null

        private fun getOrCreateDolbyEffect(): DolbyAudioEffect {
            sharedDolbyEffect?.let { return it }
            return synchronized(EFFECT_LOCK) {
                sharedDolbyEffect ?: try {
                    DolbyAudioEffect(EFFECT_PRIORITY, audioSession = 0).also {
                        sharedDolbyEffect = it
                    }
                } catch (e: Exception) {
                    DolbyConstants.elog(TAG, "Failed to create shared Dolby effect: ${e.message}", e)
                    throw e
                }
            }
        }

        private const val BASS_GAIN_MULTIPLIER = 1.4f
        private const val MID_GAIN_MULTIPLIER = 1.3f
        private const val TREBLE_GAIN_MULTIPLIER = 1.5f
        
        private val ATTRIBUTES_MEDIA = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val BAND_FREQUENCIES_10 = EqualizerBandCodec.BAND_FREQUENCIES_10
        val BAND_FREQUENCIES_15 = EqualizerBandCodec.BAND_FREQUENCIES_15
        val BAND_FREQUENCIES_20 = EqualizerBandCodec.BAND_FREQUENCIES_20

        private val BASS_CURVES = listOf(
            floatArrayOf(
                1.00f, 1.00f, 0.95f, 0.90f, 0.80f, 0.70f, 0.55f, 0.40f, 0.25f, 0.15f,
                0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f
            ),
            floatArrayOf(
                1.20f, 1.15f, 1.05f, 0.90f, 0.70f, 0.55f, 0.40f, 0.25f, 0.10f, 0.05f,
                0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f
            ),
            floatArrayOf(
                0.90f, 0.95f, 1.00f, 1.00f, 0.90f, 0.75f, 0.60f, 0.45f, 0.30f, 0.20f,
                0.10f, 0.05f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f
            )
        )
    }
}
