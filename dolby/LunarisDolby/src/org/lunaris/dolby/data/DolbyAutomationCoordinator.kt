/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.PowerManager
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.domain.models.ProfileChangeSource
import org.lunaris.dolby.domain.models.SleepTimerAction

object DolbyAutomationCoordinator {

    /**
     * Apply a profile change from an automation or user source.
     *
     * When [respectPriority] is true (default), [AutomationPriorityResolver] may
     * block lower-ranked automation sources (APP / DEVICE / SCHEDULE / MEDIA / FOCUS)
     * unless [force] is set. Unranked sources (manual, widget, sleep timer, etc.)
     * always pass through.
     *
     * @return true if the profile was applied, false if blocked by priority.
     */
    fun applyProfileChange(
        context: Context,
        profileId: Int,
        source: ProfileChangeSource,
        detail: String,
        saveUndo: Boolean = true,
        force: Boolean = false,
        respectPriority: Boolean = true
    ): Boolean {
        if (respectPriority) {
            val resolver = AutomationPriorityResolver(context)
            if (!resolver.shouldAllow(source, force)) {
                DolbyConstants.dlog(
                    "Automation",
                    "Skipping profile $profileId from ${source.key}: blocked by priority"
                )
                return false
            }
        }

        val repository = DolbyRepository(context)
        val history = ProfileChangeHistoryManager(context)
        try {
            if (saveUndo) {
                val previous = repository.getCurrentProfile()
                history.saveUndoProfile(previous)
                UndoOfferNotifier.offer(previous, detail)
            }
            repository.setCurrentProfile(profileId)
            history.recordChange(profileId, source, detail)
            return true
        } finally {
            repository.close()
        }
    }

    fun applyBluetoothRules(context: Context, device: AudioDeviceInfo?) {
        if (device == null) return
        val btManager = BluetoothProfileManager(context)
        val rule = btManager.findRuleForDevice(device) ?: return
        applyProfileChange(
            context,
            rule.profileId,
            ProfileChangeSource.BLUETOOTH,
            rule.displayName,
            saveUndo = false
        )
    }

    fun applyMediaContentProfile(context: Context, contentType: String) {
        val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(DolbyConstants.PREF_MEDIA_CONTENT_DETECTION, false)) return

        val rules = MediaContentRulesManager(context)
        val profileId = rules.getProfileForContentType(contentType)
        if (profileId < 0) return

        prefs.edit().putString("last_media_content_type", contentType).apply()
        applyProfileChange(
            context,
            profileId,
            ProfileChangeSource.MEDIA,
            contentType,
            saveUndo = false
        )
    }

    fun handleCallState(context: Context, inCall: Boolean) {
        val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(DolbyConstants.PREF_AUTO_DISABLE_ON_CALL, true)) return

        val repository = DolbyRepository(context)
        val history = ProfileChangeHistoryManager(context)
        try {
            if (inCall) {
                prefs.edit()
                    .putBoolean("call_dolby_was_enabled", repository.getDolbyEnabled())
                    .putInt("call_previous_profile", repository.getCurrentProfile())
                    .apply()
                if (repository.getDolbyEnabled()) {
                    repository.setDolbyBypass(true)
                    history.recordChange(
                        repository.getCurrentProfile(),
                        ProfileChangeSource.CALL,
                        "Bypass during call"
                    )
                }
            } else {
                val wasEnabled = prefs.getBoolean("call_dolby_was_enabled", true)
                repository.setDolbyBypass(false)
                if (wasEnabled && !repository.getDolbyEnabled()) {
                    repository.setDolbyEnabled(true)
                }
                history.recordChange(
                    repository.getCurrentProfile(),
                    ProfileChangeSource.CALL,
                    "Call ended"
                )
            }
        } finally {
            repository.close()
        }
    }

    fun applyBatterySaverIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(DolbyConstants.PREF_BATTERY_SAVER_MODE, false)) return

        val powerManager = context.getSystemService(PowerManager::class.java) ?: return
        if (!powerManager.isPowerSaveMode) return

        val repository = DolbyRepository(context)
        try {
            val profile = repository.getCurrentProfile()
            repository.setHeadphoneVirtualizerEnabled(profile, false)
            repository.setSpeakerVirtualizerEnabled(profile, false)
        } finally {
            repository.close()
        }
    }

    fun applyGameLatencyMode(context: Context) {
        val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(DolbyConstants.PREF_GAME_LATENCY_MODE, false)
        val wasActive = prefs.getBoolean(PREF_GAME_LATENCY_ACTIVE, false)

        if (!enabled) {
            if (wasActive) {
                org.lunaris.dolby.audio.DolbyHalBridge.applyGameLatencyHal(context, false)
                val repository = DolbyRepository(context)
                try {
                    val profile = repository.getCurrentProfile()
                    // Restore values snapshotted before latency mode mutated prefs
                    if (prefs.contains(PREF_GL_PREV_HP_VIRT)) {
                        repository.setHeadphoneVirtualizerEnabled(
                            profile, prefs.getBoolean(PREF_GL_PREV_HP_VIRT, false)
                        )
                        repository.setSpeakerVirtualizerEnabled(
                            profile, prefs.getBoolean(PREF_GL_PREV_SPK_VIRT, false)
                        )
                    }
                    prefs.edit()
                        .putBoolean(PREF_GAME_LATENCY_ACTIVE, false)
                        .remove(PREF_GL_PREV_HP_VIRT)
                        .remove(PREF_GL_PREV_SPK_VIRT)
                        .apply()
                    repository.applySavedState()
                } catch (e: Exception) {
                    DolbyConstants.dlog("Automation", "Game latency restore failed: ${e.message}")
                } finally {
                    repository.close()
                }
            }
            return
        }

        // Poller runs every 2s — only mutate once when becoming active.
        if (wasActive) return

        val repository = DolbyRepository(context)
        try {
            val profile = repository.getCurrentProfile()
            // Game profile only (matches previous behavior; avoids hammering all profiles).
            if (profile != 3) return

            prefs.edit()
                .putBoolean(PREF_GL_PREV_HP_VIRT, repository.getHeadphoneVirtualizerEnabled(profile))
                .putBoolean(PREF_GL_PREV_SPK_VIRT, repository.getSpeakerVirtualizerEnabled(profile))
                .apply()
            repository.setHeadphoneVirtualizerEnabled(profile, false)
            repository.setSpeakerVirtualizerEnabled(profile, false)
            org.lunaris.dolby.audio.DolbyHalBridge.applySpatialAudio(
                context,
                balanceEnabled = false,
                balance = 0,
                monoEnabled = false,
                crossfeedEnabled = false,
                crossfeedStrength = 0
            )
            org.lunaris.dolby.audio.DolbyHalBridge.applyGameLatencyHal(context, true)
            prefs.edit().putBoolean(PREF_GAME_LATENCY_ACTIVE, true).apply()
        } catch (e: Exception) {
            DolbyConstants.dlog("Automation", "Game latency apply failed: ${e.message}")
        } finally {
            repository.close()
        }
    }

    fun applyFocusModeProfile(context: Context, focusActive: Boolean) {
        val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(DolbyConstants.PREF_FOCUS_MODE_INTEGRATION, false)) return

        val alreadyApplied = prefs.getBoolean(DolbyConstants.PREF_FOCUS_APPLIED, false)
        if (focusActive) {
            if (alreadyApplied) return
            val repository = DolbyRepository(context)
            try {
                val current = repository.getCurrentProfile()
                val focusProfile = prefs.getInt(DolbyConstants.PREF_FOCUS_PROFILE_ID, 4)
                if (current == focusProfile) {
                    prefs.edit()
                        .putInt(DolbyConstants.PREF_FOCUS_PREVIOUS_PROFILE, current)
                        .putBoolean(DolbyConstants.PREF_FOCUS_APPLIED, true)
                        .apply()
                    return
                }
                val applied = applyProfileChange(
                    context,
                    focusProfile,
                    ProfileChangeSource.FOCUS,
                    "Focus mode",
                    saveUndo = false
                )
                if (applied) {
                    prefs.edit()
                        .putInt(DolbyConstants.PREF_FOCUS_PREVIOUS_PROFILE, current)
                        .putBoolean(DolbyConstants.PREF_FOCUS_APPLIED, true)
                        .apply()
                }
            } finally {
                repository.close()
            }
        } else if (alreadyApplied) {
            val previous = prefs.getInt(DolbyConstants.PREF_FOCUS_PREVIOUS_PROFILE, -1)
            prefs.edit()
                .putBoolean(DolbyConstants.PREF_FOCUS_APPLIED, false)
                .remove(DolbyConstants.PREF_FOCUS_PREVIOUS_PROFILE)
                .apply()
            if (previous >= 0) {
                // Force restore when focus ends so a higher-ranked last source
                // cannot leave the user stuck on the focus profile.
                applyProfileChange(
                    context,
                    previous,
                    ProfileChangeSource.FOCUS,
                    "Focus mode ended",
                    saveUndo = false,
                    force = true
                )
            }
        }
    }

    fun isFocusInterruptionFilter(filter: Int): Boolean {
        return filter == android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
            filter == android.app.NotificationManager.INTERRUPTION_FILTER_NONE ||
            filter == android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS
    }

    fun enforceSafeListeningLimit(context: Context) {
        val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
        val limit = prefs.getInt(DolbyConstants.PREF_SAFE_LISTENING_LIMIT, 0)
        if (limit <= 0) return

        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cap = (max * limit / 100f).toInt().coerceAtLeast(1)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current > cap) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, cap, 0)
        }

    }

    fun handleSleepTimerFire(context: Context) {
        val timerManager = SleepTimerManager(context)
        val config = timerManager.getActiveTimer() ?: return
        timerManager.clearTimer()

        when (config.action) {
            SleepTimerAction.DISABLE -> {
                val repository = DolbyRepository(context)
                try {
                    repository.setDolbyEnabled(false)
                } finally {
                    repository.close()
                }
            }
            SleepTimerAction.SWITCH_PROFILE -> {
                applyProfileChange(
                    context,
                    config.targetProfileId,
                    ProfileChangeSource.SLEEP_TIMER,
                    "Sleep timer",
                    force = true
                )
            }
            SleepTimerAction.RESTORE_PROFILE -> {
                applyProfileChange(
                    context,
                    config.previousProfileId,
                    ProfileChangeSource.SLEEP_TIMER,
                    "Sleep timer restore",
                    force = true
                )
            }
        }
    }

    private const val PREF_GAME_LATENCY_ACTIVE = "game_latency_mode_active"
    private const val PREF_GL_PREV_HP_VIRT = "game_latency_prev_hp_virt"
    private const val PREF_GL_PREV_SPK_VIRT = "game_latency_prev_spk_virt"
}
