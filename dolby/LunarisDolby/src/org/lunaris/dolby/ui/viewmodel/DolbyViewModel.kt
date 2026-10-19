/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.data.DolbyRepository
import org.lunaris.dolby.data.ProfileChangeHistoryManager
import org.lunaris.dolby.data.HeadroomInfo
import org.lunaris.dolby.data.LoudnessPreset
import org.lunaris.dolby.domain.models.*
import org.lunaris.dolby.service.DolbyEffectService
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren

class DolbyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DolbyRepository(application)

    private val _uiState = MutableStateFlow<DolbyUiState>(DolbyUiState.Loading)
    val uiState: StateFlow<DolbyUiState> = _uiState.asStateFlow()
    val currentProfile: StateFlow<Int> = repository.currentProfile

    private val _userMessages = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    private var audioOutputStateJob: Job? = null
    private var profileChangeJob: Job? = null
    private var loadSettingsJob: Job? = null
    private var loadGeneration = 0
    private var isCleared = false

    init {
        DolbyConstants.dlog(TAG, "ViewModel initialized")
        loadSettings()
        observeAudioOutputState()
        observeProfileChanges()
    }

    private fun observeAudioOutputState() {
        audioOutputStateJob?.cancel()
        audioOutputStateJob = viewModelScope.launch {
            repository.activeAudioDevice.collect {
                if (!isCleared) {
                    DolbyConstants.dlog(TAG, "Audio output changed: ${it.name} (${it.category})")
                    loadSettings()
                }
            }
        }
    }

    private fun observeProfileChanges() {
        profileChangeJob?.cancel()
        profileChangeJob = viewModelScope.launch {
            repository.currentProfile.collect {
                if (!isCleared) {
                    DolbyConstants.dlog(TAG, "Profile changed to: $it")
                    loadSettings()
                }
            }
        }
    }

    private suspend fun reportSettingFailure(action: String, e: Exception) {
        DolbyConstants.dlog(TAG, "Error $action: ${e.message}")
        _userMessages.emit(e.message ?: "Failed to $action")
        loadSettings()
    }

    fun loadSettings() {
        if (isCleared) {
            DolbyConstants.dlog(TAG, "ViewModel cleared, skipping loadSettings")
            return
        }

        val generation = ++loadGeneration
        loadSettingsJob?.cancel()
        loadSettingsJob = viewModelScope.launch {
            try {
                val enabled = repository.getDolbyEnabled()
                val profile = repository.getCurrentProfile()
                val bandMode = repository.getBandMode()

                val settings = DolbySettings(
                    enabled = enabled,
                    currentProfile = profile,
                    bassEnhancerEnabled = repository.getBassEnhancerEnabled(profile),
                    bandMode = bandMode
                )

                val profileSettings = ProfileSettings(
                    profile = profile,
                    ieqPreset = repository.getIeqPreset(profile),
                    headphoneVirtualizerEnabled = repository.getHeadphoneVirtualizerEnabled(profile),
                    speakerVirtualizerEnabled = repository.getSpeakerVirtualizerEnabled(profile),
                    stereoWideningAmount = repository.getStereoWideningAmount(profile),
                    dialogueEnhancerEnabled = repository.getDialogueEnhancerEnabled(profile),
                    dialogueEnhancerAmount = repository.getDialogueEnhancerAmount(profile),
                    graphicEqEnabled = repository.isGraphicEqEnabled(profile),
                    dialogueDuckingEnabled = repository.isDialogueDuckingEnabled(profile),
                    dialogueDuckingAmount = repository.getDialogueDuckingAmount(profile),
                    bassLevel = repository.getBassLevel(profile),
                    midLevel = repository.getMidLevel(profile),
                    trebleLevel = repository.getTrebleLevel(profile),
                    bassCurve = repository.getBassCurve(profile),
                    outputBoostEnabled = repository.isOutputBoostEnabled(profile),
                    outputBoostTenthsDb = repository.getOutputBoostTenths(profile),
                    virtualBassBluetoothEnabled = repository.isVirtualBassBluetoothEnabled(profile),
                    virtualBassMode = repository.getVirtualBassMode(profile),
                    virtualBassOverallGain = repository.getVirtualBassOverallGain(profile),
                    virtualBassSlopeGain = repository.getVirtualBassSlopeGain(profile),
                    hpVirtMode = repository.getHpVirtMode(profile),
                    hpVirtLrAngle = repository.getHpVirtLrAngle(profile),
                    hpVirtStartBand = repository.getHpVirtStartBand(profile)
                )

                if (!isCleared && generation == loadGeneration) {
                    _uiState.value = DolbyUiState.Success(
                        settings = settings,
                        profileSettings = profileSettings,
                        currentPresetName = repository.getPresetName(profile),
                        isOnSpeaker = repository.isOnSpeaker.value,
                        activeAudioDevice = repository.activeAudioDevice.value
                    )
                }
            } catch (e: Exception) {
                if (!isCleared && generation == loadGeneration) {
                    DolbyConstants.dlog(TAG, "Error loading settings: ${e.message}")
                    _uiState.value = DolbyUiState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun setDolbyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.setDolbyEnabled(enabled)
                if (enabled) {
                    DolbyEffectService.start(getApplication())
                } else {
                    DolbyEffectService.stop(getApplication())
                }
                loadSettings()
            } catch (e: Exception) {
                reportSettingFailure("setting Dolby enabled", e)
            }
        }
    }

    fun setDolbyBypass(bypass: Boolean) {
        try {
            repository.setDolbyBypass(bypass)
        } catch (e: Exception) {
            viewModelScope.launch { reportSettingFailure("setting Dolby bypass", e) }
        }
    }

    fun setProfile(profile: Int) {
        viewModelScope.launch {
            try {
                ProfileChangeHistoryManager(getApplication()).saveUndoProfile(repository.getCurrentProfile())
                repository.setCurrentProfile(profile)
                ProfileChangeHistoryManager(getApplication()).recordChange(
                    profile,
                    ProfileChangeSource.MANUAL,
                    "Manual selection"
                )
            } catch (e: Exception) {
                reportSettingFailure("setting profile", e)
            }
        }
    }

    fun setBassEnhancer(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val profile = repository.getCurrentProfile()
                repository.setBassEnhancerEnabled(profile, enabled)
                loadSettings()
            } catch (e: Exception) {
                reportSettingFailure("setting bass enhancer", e)
            }
        }
    }

    fun setBassLevel(level: Int) {
        viewModelScope.launch {
            try {
                val profile = repository.getCurrentProfile()
                repository.setBassLevel(profile, level)
                loadSettings()
            } catch (e: Exception) {
                reportSettingFailure("setting bass level", e)
            }
        }
    }

    fun setBassCurve(curve: Int) {
        viewModelScope.launch {
            try {
                val profile = repository.getCurrentProfile()
                repository.setBassCurve(profile, curve)
                loadSettings()
            } catch (e: Exception) {
                reportSettingFailure("setting bass curve", e)
            }
        }
    }

    fun setMidLevel(level: Int) {
        viewModelScope.launch {
            try {
                val profile = repository.getCurrentProfile()
                repository.setMidLevel(profile, level)
                loadSettings()
            } catch (e: Exception) {
                reportSettingFailure("setting mid level", e)
            }
        }
    }

    fun setTrebleLevel(level: Int) {
        viewModelScope.launch {
            try {
                val profile = repository.getCurrentProfile()
                repository.setTrebleLevel(profile, level)
                loadSettings()
            } catch (e: Exception) {
                reportSettingFailure("setting treble level", e)
            }
        }
    }

    fun setIeqPreset(preset: Int) {
        viewModelScope.launch {
            try {
                val profile = repository.getCurrentProfile()
                repository.setIeqPreset(profile, preset)
                loadSettings()
            } catch (e: Exception) {
                reportSettingFailure("setting IEQ preset", e)
            }
        }
    }

    fun setHeadphoneVirtualizer(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val profile = repository.getCurrentProfile()
                repository.setHeadphoneVirtualizerEnabled(profile, enabled)
                loadSettings()
            } catch (e: Exception) {
                reportSettingFailure("setting headphone virtualizer", e)
            }
        }
    }

    fun setSpeakerVirtualizer(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val profile = repository.getCurrentProfile()
                repository.setSpeakerVirtualizerEnabled(profile, enabled)
                loadSettings()
            } catch (e: Exception) {
                reportSettingFailure("setting speaker virtualizer", e)
            }
        }
    }

    fun setStereoWidening(amount: Int) {
        viewModelScope.launch {
            try {
                val profile = repository.getCurrentProfile()
                repository.setStereoWideningAmount(profile, amount)
                loadSettings()
            } catch (e: Exception) {
                reportSettingFailure("setting stereo widening", e)
            }
        }
    }

    fun setDialogueEnhancer(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val profile = repository.getCurrentProfile()
                repository.setDialogueEnhancerEnabled(profile, enabled)
                loadSettings()
            } catch (e: Exception) {
                reportSettingFailure("setting dialogue enhancer", e)
            }
        }
    }

    fun setDialogueEnhancerAmount(amount: Int) {
        viewModelScope.launch {
            try {
                val profile = repository.getCurrentProfile()
                repository.setDialogueEnhancerAmount(profile, amount)
                loadSettings()
            } catch (e: Exception) {
                reportSettingFailure("setting dialogue enhancer amount", e)
            }
        }
    }

    fun resetAllProfiles() {
        viewModelScope.launch {
            try {
                repository.resetAllProfiles()
                loadSettings()
            } catch (e: Exception) {
                reportSettingFailure("resetting profiles", e)
            }
        }
    }

    fun setOutputBoost(enabled: Boolean, tenthsDb: Int) {
        viewModelScope.launch {
            try {
                val profile = repository.getCurrentProfile()
                repository.setOutputBoost(profile, enabled, tenthsDb)
                loadSettings()
            } catch (e: Exception) {
                reportSettingFailure("setting output boost", e)
            }
        }
    }

    fun setVirtualBassBluetooth(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val profile = repository.getCurrentProfile()
                repository.setVirtualBassBluetoothEnabled(profile, enabled)
                loadSettings()
            } catch (e: Exception) {
                reportSettingFailure("setting Bluetooth virtual bass", e)
            }
        }
    }

    fun setGraphicEqEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val profile = repository.getCurrentProfile()
                repository.setGraphicEqEnabled(profile, enabled)
                loadSettings()
            } catch (e: Exception) {
                reportSettingFailure("setting graphic EQ enable", e)
            }
        }
    }

    fun setDialogueDucking(enabled: Boolean, amount: Int) {
        viewModelScope.launch {
            try {
                val profile = repository.getCurrentProfile()
                repository.setDialogueDucking(profile, enabled, amount)
                loadSettings()
            } catch (e: Exception) {
                reportSettingFailure("setting dialogue ducking", e)
            }
        }
    }

    fun setHeadphoneVirtualizerTuning(mode: Int, lrAngle: Int, startBand: Int) {
        viewModelScope.launch {
            try {
                val profile = repository.getCurrentProfile()
                repository.setHeadphoneVirtualizerTuning(profile, mode, lrAngle, startBand)
                loadSettings()
            } catch (e: Exception) {
                reportSettingFailure("setting headphone virtualizer tuning", e)
            }
        }
    }

    fun setVirtualBassDetails(mode: Int, overallGain: Int, slopeGain: Int) {
        viewModelScope.launch {
            try {
                val profile = repository.getCurrentProfile()
                repository.setVirtualBassDetails(profile, mode, overallGain, slopeGain)
                loadSettings()
            } catch (e: Exception) {
                reportSettingFailure("setting virtual bass details", e)
            }
        }
    }

    fun getOutputBoostMinTenths(): Int {
        return try {
            repository.getOutputBoostMinTenths()
        } catch (_: Exception) {
            DolbyConstants.OUTPUT_BOOST_MIN_TENTHS
        }
    }

    fun getOutputBoostMaxTenths(): Int {
        return try {
            repository.getOutputBoostMaxTenths()
        } catch (_: Exception) {
            DolbyConstants.OUTPUT_BOOST_STANDARD_MAX
        }
    }

    fun getHeadroomInfo(): HeadroomInfo? {
        return try {
            repository.getHeadroomInfo(repository.getCurrentProfile())
        } catch (_: Exception) {
            null
        }
    }

    fun applyLoudnessPreset(preset: LoudnessPreset) {
        viewModelScope.launch {
            try {
                repository.applyLoudnessPreset(preset)
                loadSettings()
            } catch (e: Exception) {
                reportSettingFailure("applying loudness preset", e)
            }
        }
    }

    fun updateSpeakerState() {
        if (!isCleared) {
            repository.updateSpeakerState()
        }
    }

    override fun onCleared() {
        DolbyConstants.dlog(TAG, "ViewModel onCleared")
        isCleared = true
        viewModelScope.coroutineContext.cancelChildren()
        audioOutputStateJob?.cancel()
        profileChangeJob?.cancel()
        loadSettingsJob?.cancel()
        audioOutputStateJob = null
        profileChangeJob = null
        loadSettingsJob = null
        repository.close()
        super.onCleared()
        DolbyConstants.dlog(TAG, "ViewModel cleaned up")
    }

    companion object {
        private const val TAG = "DolbyViewModel"
    }
}
