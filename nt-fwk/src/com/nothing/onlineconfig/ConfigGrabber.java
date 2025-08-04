package com.nothing.onlineconfig;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;

/**
 * A stub for the com.nothing.onlineconfig.ConfigGrabber class.
 * The original application uses reflection to instantiate this class,
 * and the constructor was likely causing a crash. This stub provides a
 * safe, non-functional implementation that will be found by the reflection
 * code and will not crash.
 */
public class ConfigGrabber {
    private static final String TAG = "ConfigGrabberStub";

    // The original constructor likely took a Context and a String.
    // We must match this signature for the reflection call to succeed.
    public ConfigGrabber(Context context, String projectName) {
        Log.d(TAG, "ConfigGrabber stub created for project: " + projectName);
        // The original constructor would have been the source of the crash.
        // We've replaced it with this safe, no-op implementation.
    }

    /**
     * Stubs the grabConfig method to return an empty JSONArray.
     * This prevents any dependent code from processing a null or invalid config.
     *
     * @return An empty JSONArray.
     */
    public JSONArray grabConfig() {
        Log.d(TAG, "grabConfig() called on stub. Returning an empty JSONArray.");
        return new JSONArray();
    }
}
