/*
 * Copyright (C) 2025-2026 AxionOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.audio

import android.os.HwBinder
import android.os.HwParcel
import android.os.IHwBinder
import org.lunaris.dolby.DolbyConstants

/**
 * Client for the vendor Dolby Management Service,
 * vendor.dolby.hardware.dms@2.0::IDms/default.
 *
 * This is the channel for the DAX parameters the AudioEffect path cannot carry. The parameter
 * *names* DolbyHalBridge used to write through AudioManager.setParameters() only ever meant
 * something to DMS: libdlbdsservice.so is where the name table lives, no audio HAL property
 * consumer knows them, so every one of those calls was silently dropped. DMS also has to be
 * the other end, because it is the master service that pushes tuning to the effect.
 *
 * The tree ships no .hal file, so there are no generated Java stubs; the interface is driven
 * through android.os.HwBinder directly. Every constant here was read out of the vendor's own
 * generated proxy (BpHwDms::_hidl_* in
 * proprietary/vendor/lib64/vendor.dolby.hardware.dms@2.0-dolby.so):
 *
 *  - transaction codes come from the `mov w1, #<code>` that feeds each binder transact()
 *  - setIntParam()'s wire sequence is writeInterfaceToken(), writeUint32(paramId),
 *    writeInt64(value), and the reply is taken with readFromParcel(&Status, reply)
 *
 * HwParcel lands on the same native functions the proxy uses (writeInterfaceToken() is
 * android::hardware::Parcel::writeInterfaceToken(), verifySuccess() is readFromParcel()), so
 * the parcels are byte-identical to the proxy's.
 *
 * No policy or manifest work is needed: dolby/sepolicy/vendor/platform_app.te already grants
 * this app (sharedUserId=android.uid.system, hence platform_app) hwservice_manager find and
 * binder_call to hal_dms_default, and dolby/dolby_framework_matrix.xml already declares
 * IDms for framework-side clients.
 */
object DolbyDmsBridge {

    private const val TAG = "DolbyDmsBridge"

    private const val IFACE = "vendor.dolby.hardware.dms@2.0::IDms"
    private const val INSTANCE = "default"

    // BpHwDms transaction codes, in .hal declaration order.
    private const val CODE_GET_INT_PARAM = 20
    private const val CODE_SET_INT_PARAM = 21

    /**
     * DAX parameter ids (dolby::Parameter), asserted from the
     * SimpleValueSetter(name, Parameter) registrations in libdlbdsservice.so, so they line up
     * name for name with configs/dax-default.xml.
     */
    object Param {
        const val BASS_ENHANCER_BOOST = 9
        const val BASS_ENHANCER_CUTOFF_FREQUENCY = 10
        const val BASS_ENHANCER_ENABLE = 11
        const val BASS_ENHANCER_WIDTH = 12
        const val CALIBRATION_BOOST = 17
        const val HEADPHONE_VIRTUALIZER_MODE = 30
        const val HEADPHONE_VIRTUALIZER_STEERER_SOURCE_DISTANCE = 31
        const val SPEAKER_VIRTUALIZER_MODE = 69
        const val VIRTUAL_BASS_BLEND_LINEAR_GAIN = 72
        const val VIRTUAL_BASS_PROCESS_ENABLE = 74
        const val VIRTUAL_BASS_MODE = 80
        const val VIRTUAL_BASS_OVERALL_GAIN = 81
        const val VIRTUAL_BASS_ROLLOFF_GAIN = 82
        const val VIRTUAL_BASS_SLOPE_GAIN = 83
        const val VIRTUALIZER_ENABLE = 88
        const val VIRTUALIZER_START_BAND = 91
    }

    /** Set once a transaction has succeeded; stays false when the HAL is not running. */
    @Volatile
    var isAvailable: Boolean = false
        private set

    private fun service(): IHwBinder? = try {
        HwBinder.getService(IFACE, INSTANCE)
    } catch (t: Throwable) {
        DolbyConstants.dlog(TAG, "DMS service lookup failed: ${t.message}")
        null
    }

    /**
     * IDms::setIntParam(paramId, value).
     *
     * One transaction per parameter on purpose: the scalar call needs no hidl_vec, so it is
     * the one shape whose wire format the proxy pins down exactly.
     */
    fun setIntParam(paramId: Int, value: Int): Boolean {
        val binder = service()
        if (binder == null) {
            isAvailable = false
            return false
        }
        val request = HwParcel()
        val reply = HwParcel()
        return try {
            request.writeInterfaceToken(IFACE)
            request.writeInt32(paramId)
            request.writeInt64(value.toLong())
            binder.transact(CODE_SET_INT_PARAM, request, reply, 0)
            reply.verifySuccess()
            isAvailable = true
            true
        } catch (t: Throwable) {
            DolbyConstants.dlog(TAG, "setIntParam($paramId, $value) failed: $t")
            false
        } finally {
            request.releaseTemporaryStorage()
            reply.release()
        }
    }

    /**
     * Sets a batch of parameters, in iteration order of [values]. Returns how many went
     * through, so callers can tell a missing HAL (0) from a rejected parameter.
     */
    fun setIntParams(values: Map<Int, Int>): Int =
        values.entries.count { (paramId, value) -> setIntParam(paramId, value) }

    /**
     * IDms::getIntParam(paramId). Reads a value back out of the engine; this is how to confirm
     * on device that a parameter id means what the table above claims.
     */
    fun getIntParam(paramId: Int): Int? {
        val binder = service()
        if (binder == null) {
            isAvailable = false
            return null
        }
        val request = HwParcel()
        val reply = HwParcel()
        return try {
            request.writeInterfaceToken(IFACE)
            request.writeInt32(paramId)
            binder.transact(CODE_GET_INT_PARAM, request, reply, 0)
            reply.verifySuccess()
            val value = reply.readInt64()
            isAvailable = true
            value.toInt()
        } catch (t: Throwable) {
            DolbyConstants.dlog(TAG, "getIntParam($paramId) failed: $t")
            null
        } finally {
            request.releaseTemporaryStorage()
            reply.release()
        }
    }
}
