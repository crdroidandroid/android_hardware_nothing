/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.content.SharedPreferences
import android.os.IBinder
import android.os.Looper
import android.util.Log
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.R
import org.lunaris.dolby.data.DeviceStateManager
import org.lunaris.dolby.data.DolbyRepository
import org.lunaris.dolby.data.DolbyAutomationCoordinator

class DolbyEffectService : Service() {

    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val dolbyPrefs: SharedPreferences by lazy {
        getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
    }
    private val isDeviceStateMemoryEnabled: Boolean
        get() = dolbyPrefs.getBoolean(DolbyConstants.PREF_DEVICE_STATE_MEMORY, false)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repository: DolbyRepository
    private lateinit var deviceStateManager: DeviceStateManager
    private var previousActiveDevice: AudioDeviceInfo? = null
    private var wasInCall = false
    private val callCheckRunnable = object : Runnable {
        override fun run() {
            checkCallState()
            DolbyAutomationCoordinator.applyGameLatencyMode(this@DolbyEffectService)
            DolbyAutomationCoordinator.enforceSafeListeningLimit(this@DolbyEffectService)
            handler.postDelayed(this, 2000L)
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            Log.d(TAG, "Devices added: ${addedDevices.map { it.productName }}")
            handleDeviceChange()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            Log.d(TAG, "Devices removed: ${removedDevices.map { it.productName }}")
            if (isDeviceStateMemoryEnabled) {
                removedDevices.forEach { device ->
                    val key = deviceStateManager.deviceKey(device)
                    Log.d(TAG, "Snapshotting state for removed device: $key")
                    deviceStateManager.saveSnapshot(key, repository)
                }
            }
            handleDeviceChange()
        }
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            val isActive = configs?.any { it.isActive } == true
            if (isActive) {
                repository.applySavedState()
            }
        }
    }

    private fun promoteForeground() {
        // Must call startForeground successfully after startForegroundService(), or Android
        // kills the whole process and DolbyActivity appears unable to open.
        fun startFg(notification: android.app.Notification) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    DolbyForegroundNotifications.NOTIFICATION_ID_EFFECT,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(DolbyForegroundNotifications.NOTIFICATION_ID_EFFECT, notification)
            }
        }
        val attempts = listOf(
            {
                startFg(
                    DolbyForegroundNotifications.build(
                        this,
                        R.string.notification_effect_active
                    )
                )
            },
            {
                DolbyForegroundNotifications.ensureChannel(this)
                val fallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    android.app.Notification.Builder(this, DolbyForegroundNotifications.CHANNEL_ID)
                } else {
                    @Suppress("DEPRECATION")
                    android.app.Notification.Builder(this)
                }
                    .setContentTitle(getString(R.string.dolby_title))
                    .setContentText(getString(R.string.notification_effect_active))
                    .setSmallIcon(R.drawable.ic_dolby_qs)
                    .setOngoing(true)
                    .build()
                startFg(fallback)
            },
            {
                // Absolute last resort: system icon in case the Dolby drawable is invalid.
                DolbyForegroundNotifications.ensureChannel(this)
                val emergency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    android.app.Notification.Builder(this, DolbyForegroundNotifications.CHANNEL_ID)
                } else {
                    @Suppress("DEPRECATION")
                    android.app.Notification.Builder(this)
                }
                    .setContentTitle("Dolby")
                    .setContentText("Active")
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setOngoing(true)
                    .build()
                startFg(emergency)
            }
        )
        for ((index, attempt) in attempts.withIndex()) {
            try {
                attempt()
                return
            } catch (e: Exception) {
                Log.e(TAG, "promoteForeground attempt #$index failed", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = DolbyRepository(this)
        deviceStateManager = DeviceStateManager(this)
        // Must reach startForeground before any HAL/prefs work that might throw.
        promoteForeground()
        try {
            DolbyAutomationCoordinator.applyBatterySaverIfNeeded(this)
            DolbyAutomationCoordinator.applyGameLatencyMode(this)
            DolbyAutomationCoordinator.enforceSafeListeningLimit(this)
            val currentDevice = getCurrentOutputDevice()
            if (currentDevice != null) {
                previousActiveDevice = currentDevice
                if (isDeviceStateMemoryEnabled) {
                    val key = deviceStateManager.deviceKey(currentDevice)
                    val restored = deviceStateManager.restoreSnapshot(key, repository)
                    if (!restored) repository.applySavedState()
                } else {
                    repository.applySavedState()
                }
            } else {
                repository.applySavedState()
            }

            audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)
            audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
            handler.post(callCheckRunnable)
            Log.d(TAG, "Dolby effect service created")
        } catch (e: Exception) {
            Log.e(TAG, "Dolby effect service init failed after foreground promote", e)
        }
    }

    private fun isInCall(): Boolean =
        audioManager.mode == AudioManager.MODE_IN_CALL ||
            audioManager.mode == AudioManager.MODE_IN_COMMUNICATION

    private fun handleDeviceChange() {
        if (isInCall()) {
            Log.d(TAG, "Device change during call, deferring to call end")
            return
        }
        val appMonitoring = dolbyPrefs.getBoolean("app_profile_monitoring_enabled", false)
        val priority = dolbyPrefs.getString(
            DolbyConstants.PREF_PROFILE_PRIORITY,
            DolbyConstants.PROFILE_PRIORITY_DEVICE
        ) ?: DolbyConstants.PROFILE_PRIORITY_DEVICE

        if (appMonitoring && priority == DolbyConstants.PROFILE_PRIORITY_APP) {
            Log.d(TAG, "App profile priority active, skipping device snapshot restore")
            repository.applySavedState()
            previousActiveDevice = getCurrentOutputDevice()
            return
        }

        val newDevice = getCurrentOutputDevice()
        val oldDevice = previousActiveDevice

        if (oldDevice != null) {
            if (isDeviceStateMemoryEnabled) {
                val oldKey = deviceStateManager.deviceKey(oldDevice)
                Log.d(TAG, "Saving snapshot for previous device: $oldKey")
                deviceStateManager.saveSnapshot(oldKey, repository)
            }
        }

        if (newDevice != null) {
            val newKey = deviceStateManager.deviceKey(newDevice)
            if (isDeviceStateMemoryEnabled) {
                Log.d(TAG, "Restoring snapshot for new device: $newKey")
                val restored = deviceStateManager.restoreSnapshot(newKey, repository)
                if (!restored) {
                    Log.d(TAG, "First time device, applying saved state as base")
                    repository.applySavedState()
                }
            } else {
                Log.d(TAG, "Device state memory disabled, applying saved state")
                repository.applySavedState()
            }
            // BT rules go through the coordinator so priority ranking can block
            // them when APP mode (or a higher last source) should win.
            DolbyAutomationCoordinator.applyBluetoothRules(this, newDevice)
            previousActiveDevice = newDevice
        } else {
            repository.updateSpeakerState()
            repository.applySavedState()
            previousActiveDevice = null
        }
        repository.updateSpeakerState()
        repository.reapplyVirtualBass()
        repository.applyCalibrationBoost()
    }

    private fun getCurrentOutputDevice(): AudioDeviceInfo? {
        return deviceStateManager.getCurrentOutputDevice(audioManager)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteForeground()
        try {
            repository.applySavedState()
        } catch (e: Exception) {
            Log.e(TAG, "applySavedState in onStartCommand failed", e)
        }
        return START_STICKY
    }

    private fun checkCallState() {
        val inCall = isInCall()
        if (inCall != wasInCall) {
            wasInCall = inCall
            DolbyAutomationCoordinator.handleCallState(this, inCall)
            if (!inCall) {
                handleDeviceChange()
            }
        }
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        handler.removeCallbacks(callCheckRunnable)
        if (isDeviceStateMemoryEnabled) {
            previousActiveDevice?.let { device ->
                val key = deviceStateManager.deviceKey(device)
                deviceStateManager.saveSnapshot(key, repository)
            }
        }
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        handler.removeCallbacksAndMessages(null)
        repository.close()
        Log.d(TAG, "Dolby effect service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "DolbyEffectService"

        fun start(context: Context) {
            val intent = Intent(context, DolbyEffectService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "startForegroundService failed, falling back to startService", e)
                try {
                    context.startService(intent)
                } catch (fatal: Exception) {
                    Log.e(TAG, "startService fallback failed", fatal)
                }
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DolbyEffectService::class.java)
            context.stopService(intent)
        }
    }
}
