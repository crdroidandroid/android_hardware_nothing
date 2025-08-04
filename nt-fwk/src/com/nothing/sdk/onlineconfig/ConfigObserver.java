package com.nothing.sdk.onlineconfig;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.util.Log;
import org.json.JSONArray;
import com.nothing.onlineconfig.NtOnlineConfigImpl;

/**
 * A stub for the ConfigObserver class, located in the com.nothing.sdk.onlineconfig package.
 * This class is the direct cause of the crash, as indicated by the logcat. This stub
 * provides a non-functional, safe implementation to prevent the app from crashing.
 */
public class ConfigObserver extends ContentObserver {
    private static final String TAG = "ConfigObserverStub";

    public interface ConfigUpdater {
        void updateConfig(JSONArray jSONArray);
    }

    // A simple stub for the ConfigGrabber dependency
    private static class ConfigGrabberStub {
        public ConfigGrabberStub(Context context, String projectName) {}
        public JSONArray grabConfig() { return new JSONArray(); }
    }
    
    private final ConfigGrabberStub mConfigGrabber;
    private final ConfigUpdater mConfigUpdater;

    public ConfigObserver(Context context, Handler handler, ConfigUpdater configUpdater, String projectName) {
        super(handler);
        Log.d(TAG, "ConfigObserver stub created. This should prevent the crash.");
        // We create a local stub for ConfigGrabber to avoid dependencies on other stubs.
        this.mConfigGrabber = new ConfigGrabberStub(context, projectName);
        this.mConfigUpdater = configUpdater;
        // The original code here is likely the source of the crash. We are bypassing it.
    }

    public void register() {
        Log.d(TAG, "register() called on stub. No-op.");
    }

    public void unregister() {
        Log.d(TAG, "unregister() called on stub. No-op.");
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        Log.d(TAG, "onChange() called on stub. Notifying updater with empty config.");
        if (mConfigUpdater != null) {
            mConfigUpdater.updateConfig(mConfigGrabber.grabConfig());
        }
    }
}
