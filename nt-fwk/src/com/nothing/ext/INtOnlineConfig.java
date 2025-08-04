package com.nothing.ext;

import android.content.ContentResolver;
import android.database.ContentObserver;
import org.json.JSONArray;

public interface INtOnlineConfig {
    public static final INtOnlineConfig DEFAULT = new INtOnlineConfig() {};

    default JSONArray grabConfig(ContentResolver resolver, String projectName) {
        return null;
    }

    default void registerContentObserver(ContentResolver resolver, String projectName, boolean notifyForDescendants, ContentObserver observer) {
    }
}
