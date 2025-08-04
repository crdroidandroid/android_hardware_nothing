package com.nothing.onlineconfig;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.util.Log;
import com.nothing.ext.INtOnlineConfig;
import org.json.JSONArray;

public class NtOnlineConfigImpl implements INtOnlineConfig {
    private static final String TAG = "NtOnlineConfigImplStub";

    public NtOnlineConfigImpl() {
        Log.d(TAG, "NtOnlineConfigImpl stub created.");
    }

    @Override
    public JSONArray grabConfig(ContentResolver resolver, String projectName) {
        Log.d(TAG, "grabConfig() called on stub. Returning an empty array.");
        return new JSONArray();
    }

    @Override
    public void registerContentObserver(ContentResolver resolver, String projectName, boolean notifyForDescendants, ContentObserver observer) {
        Log.d(TAG, "registerContentObserver() called on stub. No-op.");
    }
}
