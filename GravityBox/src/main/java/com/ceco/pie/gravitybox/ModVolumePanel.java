/*
 * Copyright (C) 2019 Peter Gregus for GravityBox Project (C3C076@xda)
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
package com.ceco.pie.gravitybox;

import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModVolumePanel {
    private static final String TAG = "GB:ModVolumePanel";
    public static final String PACKAGE_NAME = "com.android.systemui";
    private static final String CLASS_VOLUME_PANEL = Utils.isSamsungRom() ?
            "com.android.systemui.volume.SecVolumeDialogImpl" :
            "com.android.systemui.volume.VolumeDialogImpl";
    private static final String CLASS_VOLUME_ROW = CLASS_VOLUME_PANEL + ".VolumeRow";
    private static final boolean DEBUG = false;

    private static Object mVolumePanel;
    private static boolean mVolForceRingControl;
    private static boolean mVolumesLinked;
    private static boolean mVolumePanelExpanded;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static BroadcastReceiver mBrodcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_MEDIA_CONTROL_CHANGED)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_VOL_FORCE_RING_CONTROL)) {
                    mVolForceRingControl = intent.getBooleanExtra(
                            GravityBoxSettings.EXTRA_VOL_FORCE_RING_CONTROL, false);
                    updateDefaultStream();
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_VOL_LINKED)) {
                    mVolumesLinked = intent.getBooleanExtra(GravityBoxSettings.EXTRA_VOL_LINKED, true);
                    if (DEBUG) log("mVolumesLinked set to: " + mVolumesLinked);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_VOL_EXPANDED)) {
                    mVolumePanelExpanded = intent.getBooleanExtra(GravityBoxSettings.EXTRA_VOL_EXPANDED, false);
                    if (DEBUG) log("mVolumePanelExpanded set to: " + mVolumesLinked);
                }
            }
        }
    };

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            final Class<?> classVolumePanel = XposedHelpers.findClass(CLASS_VOLUME_PANEL, classLoader);

            mVolForceRingControl = prefs.getBoolean(
                    GravityBoxSettings.PREF_KEY_VOL_FORCE_RING_CONTROL, false);
            mVolumesLinked = prefs.getBoolean(GravityBoxSettings.PREF_KEY_LINK_VOLUMES, true);
            mVolumePanelExpanded = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VOL_EXPANDED, false);

            XposedBridge.hookAllConstructors(classVolumePanel, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) {
                    mVolumePanel = param.thisObject;
                    Context context = (Context) XposedHelpers.getObjectField(mVolumePanel, "mContext");
                    if (DEBUG) log("VolumePanel constructed; mVolumePanel set");

                    IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_MEDIA_CONTROL_CHANGED);
                    context.registerReceiver(mBrodcastReceiver, intentFilter);
                }
            });

            XposedHelpers.findAndHookMethod(classVolumePanel, "initDialog", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) {
                    if (!Utils.isSamsungRom()) {
                        prepareNotificationRow();
                    }
                    updateDefaultStream();
                }
            });

            XC_MethodHook shouldBeVisibleHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) {
                    int streamType = XposedHelpers.getIntField(param.args[0], "stream");
                    if (mVolumePanelExpanded && (streamType == AudioManager.STREAM_MUSIC ||
                            streamType == AudioManager.STREAM_RING ||
                            (streamType == AudioManager.STREAM_NOTIFICATION && !mVolumesLinked) ||
                            streamType == AudioManager.STREAM_ALARM ||
                            streamType == AudioManager.STREAM_VOICE_CALL)) {
                        param.setResult(true);
                    } else if (XposedHelpers.getAdditionalInstanceField(
                            param.args[0], "gbNotifSlider") != null) {
                        boolean visible = (boolean) param.getResult();
                        visible &= !mVolumesLinked;
                        param.setResult(visible);
                    }
                }
            };
            XposedHelpers.findAndHookMethod(classVolumePanel, "shouldBeVisibleH",
                    CLASS_VOLUME_ROW, CLASS_VOLUME_ROW, shouldBeVisibleHook);

            XposedHelpers.findAndHookMethod(classVolumePanel, "updateVolumeRowSliderH",
                    CLASS_VOLUME_ROW, boolean.class, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) {
                    if (!mVolumesLinked && XposedHelpers.getAdditionalInstanceField(
                            param.args[0], "gbNotifSlider") != null) {
                        View slider = (View) XposedHelpers.getObjectField(param.args[0], "slider");
                        slider.setEnabled(isRingerSliderEnabled());
                        View icon = (View) XposedHelpers.getObjectField(param.args[0], "icon");
                        icon.setEnabled(slider.isEnabled());
                    }
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private static void prepareNotificationRow() {
        try {
            List<?> rows = (List<?>) XposedHelpers.getObjectField(mVolumePanel, "mRows");
            for (Object row : rows) {
                if (XposedHelpers.getIntField(row, "stream") == AudioManager.STREAM_NOTIFICATION) {
                    return;
                }
            }
            XposedHelpers.callMethod(mVolumePanel, "addRow",
                    AudioManager.STREAM_NOTIFICATION,
                    ResourceProxy.getFakeResId("ic_audio_notification"),
                    ResourceProxy.getFakeResId("ic_audio_notification_mute"),
                    true, true);
            Object row = rows.get(rows.size()-1);
            XposedHelpers.setAdditionalInstanceField(row, "gbNotifSlider", true);
            if (DEBUG) log("notification row added");
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private static boolean isRingerSliderEnabled() {
        try {
            List<?> rows = (List<?>) XposedHelpers.getObjectField(mVolumePanel, "mRows");
            for (Object row : rows) {
                if (XposedHelpers.getIntField(row, "stream") == AudioManager.STREAM_RING) {
                    return ((View)XposedHelpers.getObjectField(row, "slider")).isEnabled();
                }
            }
            return true;
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
            return true;
        }
    }

    private static void updateDefaultStream() {
        try {
            List<?> rows = (List<?>) XposedHelpers.getObjectField(mVolumePanel, "mRows");
            for (Object row : rows) {
                int streamType = XposedHelpers.getIntField(row, "stream");
                if (streamType == AudioManager.STREAM_MUSIC) {
                    XposedHelpers.setBooleanField(row, "defaultStream", !mVolForceRingControl);
                } else if (streamType == AudioManager.STREAM_RING) {
                    XposedHelpers.setBooleanField(row, "defaultStream", mVolForceRingControl);
                }
            }
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }
}
