/*
 * Copyright (C) 2017 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ceco.nougat.gravitybox.quicksettings;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class QsPanelQuick {
    private static final String TAG = "GB:QsPanelQuick";
    private static final boolean DEBUG = false;

    //private static final String CLASS_QS_PANEL_QUICK = "com.android.systemui.qs.QuickQSPanel";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public QsPanelQuick(XSharedPreferences prefs, ClassLoader classLoader) {
        // Reserved for potential future use
        if (DEBUG) log("QsPanelQuick wrapper created");
    }
}