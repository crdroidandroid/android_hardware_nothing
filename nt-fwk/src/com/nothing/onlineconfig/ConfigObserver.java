/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nothing.onlineconfig;

import android.content.Context;
import android.os.Handler;

import org.json.JSONArray;

public class ConfigObserver {

    public interface ConfigUpdater {
        void updateConfig(JSONArray config);
    }

    public ConfigObserver(Context context, Handler handler, ConfigUpdater updater,
            String configName) {
    }

    public void register() {
    }

    public void unregister() {
    }

    public void onChange(boolean selfChange) {
    }
}
