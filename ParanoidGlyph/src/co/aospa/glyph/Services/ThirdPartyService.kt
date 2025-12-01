package co.aospa.glyph.Services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import co.aospa.glyph.Manager.AnimationManager
import co.aospa.glyph.Manager.StatusManager
import com.nothing.thirdparty.IGlyphService

class ThirdPartyService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val binder = object : IGlyphService.Stub() {
        override fun setFrameColors(iArray: IntArray?) {
            Log.d("ThirdPartyService", "received data: ${iArray.contentToString()}")
            AnimationManager.updateLedFrame(iArray)
        }

        override fun openSession() {
            Log.d("IGlyphServiceImpl", "openSession")
            acquireWakeLock() // Acquire the wake lock when opening the session
            StatusManager.setAnimationActive(true);
        }

        override fun closeSession() {
            Log.d("IGlyphServiceImpl", "closeSession")
            StatusManager.setAnimationActive(false);
            releaseWakeLock() // Release the wake lock when closing the session
        }

        override fun register(str: String) = true
        override fun registerSDK(str1: String, str2: String) = true
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ThirdPartyService::WakeLock")
            wakeLock?.acquire()
            Log.d("ThirdPartyService", "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock != null) {
            wakeLock?.release()
            wakeLock = null
            Log.d("ThirdPartyService", "WakeLock released")
        }
    }
}
