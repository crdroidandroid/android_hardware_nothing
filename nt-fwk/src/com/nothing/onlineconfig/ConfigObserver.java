package com.nothing.onlineconfig;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.util.Log;
import org.json.JSONArray;

public class ConfigObserver extends ContentObserver {
    private static final String TAG = "ConfigObserverStub";

    public interface ConfigUpdater {
        void updateConfig(JSONArray jSONArray);
    }
    
    // A dependency on ConfigGrabber, which we now have a stub for.
    private final ConfigGrabber mConfigGrabber;
    private final ConfigUpdater mConfigUpdater;

    public ConfigObserver(Context context, Handler handler, ConfigUpdater configUpdater, String projectName) {
        super(handler);
        Log.d(TAG, "ConfigObserver stub created.");
        this.mConfigGrabber = new ConfigGrabber(context, projectName);
        this.mConfigUpdater = configUpdater;
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
