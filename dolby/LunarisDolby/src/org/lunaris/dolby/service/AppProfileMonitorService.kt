/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.service

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.R
import org.lunaris.dolby.data.AppProfileManager
import org.lunaris.dolby.data.AutomationPriorityResolver
import org.lunaris.dolby.data.DolbyRepository
import org.lunaris.dolby.data.DolbyAutomationCoordinator
import org.lunaris.dolby.data.MediaContentRulesManager
import org.lunaris.dolby.data.ProfileChangeHistoryManager
import org.lunaris.dolby.domain.models.ProfileChangeSource
import org.lunaris.dolby.utils.ToastHelper
import java.util.concurrent.atomic.AtomicReference

class AppProfileMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val switchHandler = Handler(Looper.getMainLooper())
    private lateinit var appProfileManager: AppProfileManager
    private lateinit var dolbyRepository: DolbyRepository
    private lateinit var historyManager: ProfileChangeHistoryManager
    private lateinit var priorityResolver: AutomationPriorityResolver
    private lateinit var audioManager: AudioManager
    private val lastPackageName = AtomicReference<String?>(null)
    private var originalProfile: Int = -1
    private var isMonitoring = false
    private var screenOn = true
    private var playbackCallbackRegistered = false
    private var screenReceiverRegistered = false
    private var pendingSwitchRunnable: Runnable? = null
    private var hasOriginalProfile = false
    private var lastProfileChangeTime: Long = 0

    private val checkForegroundAppRunnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            scheduleNextPoll()
        }
    }

    private val audioPlaybackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            if (isMonitoring && screenOn) {
                handler.removeCallbacks(checkForegroundAppRunnable)
                handler.post { checkForegroundApp() }
                scheduleNextPoll()
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    handler.removeCallbacks(checkForegroundAppRunnable)
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    if (isMonitoring) {
                        handler.post { checkForegroundApp() }
                        scheduleNextPoll()
                    }
                }
            }
        }
    }

    private fun scheduleNextPoll() {
        if (!isMonitoring || !screenOn) return
        handler.removeCallbacks(checkForegroundAppRunnable)
        handler.postDelayed(checkForegroundAppRunnable, BACKUP_POLL_INTERVAL_MS)
    }

    override fun onCreate() {
        super.onCreate()
        appProfileManager = AppProfileManager(this)
        dolbyRepository = DolbyRepository(this)
        historyManager = ProfileChangeHistoryManager(this)
        priorityResolver = AutomationPriorityResolver(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        val prefs = getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
        val savedProfile = prefs.getString(DolbyConstants.PREF_PROFILE, "0")?.toIntOrNull() ?: 0
        
        if (!hasOriginalProfile) {
            originalProfile = savedProfile
            hasOriginalProfile = true
            DolbyConstants.dlog(TAG, "Service created - saved original profile: $originalProfile")
        } else {
            DolbyConstants.dlog(TAG, "Service created - keeping existing original profile: $originalProfile")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
            ACTION_CHECK_NOW -> {
                if (!isMonitoring) startMonitoring()
                else promoteForeground()
                handler.post { checkForegroundApp() }
            }
            else -> {
                // Sticky restart / unknown action: keep foreground if we were monitoring.
                if (isMonitoring) promoteForeground()
            }
        }
        return START_STICKY
    }

    private fun promoteForeground() {
        fun startFg(notification: android.app.Notification) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    DolbyForegroundNotifications.NOTIFICATION_ID_MONITOR,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(DolbyForegroundNotifications.NOTIFICATION_ID_MONITOR, notification)
            }
        }
        val attempts = listOf(
            {
                startFg(
                    DolbyForegroundNotifications.build(
                        this,
                        R.string.notification_monitor_active
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
                    .setContentText(getString(R.string.notification_monitor_active))
                    .setSmallIcon(R.drawable.ic_dolby_qs)
                    .setOngoing(true)
                    .build()
                startFg(fallback)
            },
            {
                DolbyForegroundNotifications.ensureChannel(this)
                val emergency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    android.app.Notification.Builder(this, DolbyForegroundNotifications.CHANNEL_ID)
                } else {
                    @Suppress("DEPRECATION")
                    android.app.Notification.Builder(this)
                }
                    .setContentTitle("Dolby")
                    .setContentText("Monitor")
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

    private fun startMonitoring() {
        if (!isMonitoring) {
            isMonitoring = true
            promoteForeground()
            
            if (!hasOriginalProfile) {
                val prefs = getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
                originalProfile = prefs.getString(DolbyConstants.PREF_PROFILE, "0")?.toIntOrNull() ?: 0
                hasOriginalProfile = true
                DolbyConstants.dlog(TAG, "Re-initialized original profile on start: $originalProfile")
            }

            registerEventListeners()
            
            DolbyConstants.dlog(TAG, "Started event-driven foreground monitoring (original profile: $originalProfile)")
            handler.post { checkForegroundApp() }
            scheduleNextPoll()
        } else {
            promoteForeground()
        }
    }

    private fun stopMonitoring() {
        if (isMonitoring) {
            isMonitoring = false
            handler.removeCallbacks(checkForegroundAppRunnable)
            unregisterEventListeners()
            stopForeground(STOP_FOREGROUND_REMOVE)
            
            synchronized(this) {
                pendingSwitchRunnable?.let { switchHandler.removeCallbacks(it) }
                pendingSwitchRunnable = null
            }
            
            if (hasOriginalProfile && originalProfile >= 0) {
                DolbyConstants.dlog(TAG, "Restoring original profile: $originalProfile")
                dolbyRepository.setCurrentProfile(originalProfile)
                
                val prefs = getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
                val currentProfile = prefs.getString(DolbyConstants.PREF_PROFILE, "0")?.toIntOrNull() ?: 0
                if (currentProfile != originalProfile) {
                    DolbyConstants.dlog(TAG, "WARNING: Profile restoration mismatch! Expected: $originalProfile, Got: $currentProfile")
                } else {
                    DolbyConstants.dlog(TAG, "Profile restored successfully")
                }
            } else {
                DolbyConstants.dlog(TAG, "No valid original profile to restore (hasOriginal=$hasOriginalProfile, profile=$originalProfile)")
            }
            
            DolbyConstants.dlog(TAG, "Stopped monitoring foreground app")
        }
    }

    private fun registerEventListeners() {
        if (!playbackCallbackRegistered) {
            try {
                audioManager.registerAudioPlaybackCallback(audioPlaybackCallback, handler)
                playbackCallbackRegistered = true
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Failed to register playback callback: ${e.message}")
            }
        }
        if (!screenReceiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                }
                registerReceiver(screenReceiver, filter)
                screenReceiverRegistered = true
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Failed to register screen receiver: ${e.message}")
            }
        }
    }

    private fun unregisterEventListeners() {
        if (playbackCallbackRegistered) {
            try {
                audioManager.unregisterAudioPlaybackCallback(audioPlaybackCallback)
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Failed to unregister playback callback: ${e.message}")
            }
            playbackCallbackRegistered = false
        }
        if (screenReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Failed to unregister screen receiver: ${e.message}")
            }
            screenReceiverRegistered = false
        }
    }

    private fun isHeadphoneConnected(): Boolean {
        return try {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.any { device ->
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking headphone connection", e)
            false
        }
    }

    private fun checkForegroundApp() {
        try {
            val prefs = getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
            val deviceMemory = prefs.getBoolean(DolbyConstants.PREF_DEVICE_STATE_MEMORY, false)
            val priority = prefs.getString(
                DolbyConstants.PREF_PROFILE_PRIORITY,
                DolbyConstants.PROFILE_PRIORITY_DEVICE
            ) ?: DolbyConstants.PROFILE_PRIORITY_DEVICE
            if (deviceMemory && priority == DolbyConstants.PROFILE_PRIORITY_DEVICE) {
                DolbyConstants.dlog(TAG, "Device memory has priority, skipping app profile switch")
                return
            }

            val headphoneOnlyMode = prefs.getBoolean("app_profile_headphone_only", false)
            
            if (headphoneOnlyMode && !isHeadphoneConnected()) {
                DolbyConstants.dlog(TAG, "Headphone-only mode enabled but no headphones connected, skipping profile switch")
                return
            }
            
            val packageName = getForegroundPackage() ?: return
            
            val appName = getAppName(packageName)
            prefs.edit()
                .putString("last_foreground_app_package", packageName)
                .putString("last_foreground_app_name", appName)
                .apply()

            if (prefs.getBoolean(DolbyConstants.PREF_MEDIA_CONTENT_DETECTION, false)) {
                val assignedProfile = appProfileManager.getAppProfile(packageName)
                if (assignedProfile < 0) {
                    detectMediaContent(packageName)?.let { contentType ->
                        DolbyAutomationCoordinator.applyMediaContentProfile(this, contentType)
                    }
                }
            }
            
            val previousPackage = lastPackageName.getAndSet(packageName)
            if (packageName == previousPackage) {
                return
            }
            
            DolbyConstants.dlog(TAG, "Foreground app changed: $previousPackage -> $packageName")
            
            synchronized(this) {
                pendingSwitchRunnable?.let { switchHandler.removeCallbacks(it) }
                
                pendingSwitchRunnable = Runnable {
                    synchronized(this) {
                        try {
                            if (headphoneOnlyMode && !isHeadphoneConnected()) {
                                DolbyConstants.dlog(TAG, "Headphones disconnected, aborting profile switch")
                                return@Runnable
                            }
                            
                            val assignedProfile = appProfileManager.getAppProfile(packageName)
                            val showToasts = prefs.getBoolean("app_profile_show_toasts", true)
                            
                            if (assignedProfile >= 0) {
                                // Central priority check: DEVICE/BT (or other higher source)
                                // can block app-driven switches when mode says so.
                                if (!priorityResolver.shouldAllow(ProfileChangeSource.APP)) {
                                    DolbyConstants.dlog(
                                        TAG,
                                        "App profile blocked by priority for $packageName"
                                    )
                                    return@Runnable
                                }
                                DolbyConstants.dlog(TAG, "Switching to profile $assignedProfile for $packageName")
                                lastProfileChangeTime = System.currentTimeMillis()
                                historyManager.saveUndoProfile(dolbyRepository.getCurrentProfile())
                                dolbyRepository.setCurrentProfile(assignedProfile)
                                historyManager.recordChange(
                                    assignedProfile,
                                    ProfileChangeSource.APP,
                                    appName
                                )
                                DolbyConstants.dlog(TAG, "App profile active - original profile remains: $originalProfile")
                                
                                if (showToasts) {
                                    val profileName = getProfileName(assignedProfile)
                                    val appName = getAppName(packageName)
                                    ToastHelper.showToast(
                                        this@AppProfileMonitorService,
                                        "Dolby: $profileName ($appName)"
                                    )
                                }
                            } else {
                                if (hasOriginalProfile && originalProfile >= 0) {
                                    val currentProfile = prefs.getString(DolbyConstants.PREF_PROFILE, "0")?.toIntOrNull() ?: 0
                                    
                                    if (currentProfile != originalProfile) {
                                        // Restoring the baseline when leaving a mapped app:
                                        // force so a higher-ranked last source cannot trap us.
                                        DolbyConstants.dlog(TAG, "Restoring original profile $originalProfile for $packageName (current: $currentProfile)")
                                        lastProfileChangeTime = System.currentTimeMillis()
                                        dolbyRepository.setCurrentProfile(originalProfile)
                                        historyManager.recordChange(
                                            originalProfile,
                                            ProfileChangeSource.APP,
                                            "Restore: $appName"
                                        )
                                    } else {
                                        DolbyConstants.dlog(TAG, "Already on original profile $originalProfile, no change needed")
                                    }
                                } else {
                                    DolbyConstants.dlog(TAG, "No original profile to restore (hasOriginal=$hasOriginalProfile, profile=$originalProfile)")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error switching profile", e)
                        } finally {
                            pendingSwitchRunnable = null
                        }
                    }
                }
                
                switchHandler.postDelayed(pendingSwitchRunnable!!, SWITCH_DELAY)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking foreground app", e)
        }
    }

    private fun getForegroundPackage(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = System.currentTimeMillis()
        
        val usageEvents = usageStatsManager.queryEvents(currentTime - FOREGROUND_LOOKBACK_MS, currentTime)
        val event = UsageEvents.Event()
        
        var lastPackage: String? = null
        
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackage = event.packageName
            }
        }
        
        return lastPackage
    }
    
    private fun getProfileName(profile: Int): String {
        val profiles = resources.getStringArray(R.array.dolby_profile_entries)
        val profileValues = resources.getStringArray(R.array.dolby_profile_values)
        
        return try {
            val index = profileValues.indexOfFirst { it.toInt() == profile }
            if (index >= 0) profiles[index] else "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun detectMediaContent(packageName: String): String? {
        return MediaContentRulesManager(this).detectContentType(packageName)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        DolbyConstants.dlog(TAG, "Service destroyed")
        stopMonitoring()
        unregisterEventListeners()
        stopForeground(STOP_FOREGROUND_REMOVE)
        dolbyRepository.close()
        hasOriginalProfile = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AppProfileMonitor"
        private const val BACKUP_POLL_INTERVAL_MS = 5000L
        private const val FOREGROUND_LOOKBACK_MS = 8000L
        private const val SWITCH_DELAY = 300L
        
        const val ACTION_START_MONITORING = "org.lunaris.dolby.START_MONITORING"
        const val ACTION_STOP_MONITORING = "org.lunaris.dolby.STOP_MONITORING"
        const val ACTION_CHECK_NOW = "org.lunaris.dolby.CHECK_FOREGROUND_NOW"

        fun startMonitoring(context: Context) {
            val intent = Intent(context, AppProfileMonitorService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "startForegroundService failed: ${e.message}")
                try {
                    context.startService(intent)
                } catch (fatal: Exception) {
                    DolbyConstants.dlog(TAG, "startService fallback failed: ${fatal.message}")
                }
            }
        }

        fun stopMonitoring(context: Context) {
            val intent = Intent(context, AppProfileMonitorService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }

        fun requestImmediateCheck(context: Context) {
            val intent = Intent(context, AppProfileMonitorService::class.java).apply {
                action = ACTION_CHECK_NOW
            }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "requestImmediateCheck FGS failed: ${e.message}")
                try {
                    context.startService(intent)
                } catch (fatal: Exception) {
                    DolbyConstants.dlog(TAG, "requestImmediateCheck fallback failed: ${fatal.message}")
                }
            }
        }
    }
}
