/*
 * Copyright (C) 2019 The Android Open Source Project
 *
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

package com.android.car.developeroptions.location;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.provider.Settings;

import androidx.annotation.VisibleForTesting;

import com.android.car.developeroptions.R;
import com.android.car.developeroptions.core.BasePreferenceController;


public class LocationScanningPreferenceController extends BasePreferenceController {
    @VisibleForTesting static final String KEY_LOCATION_SCANNING = "location_scanning";
    private final Context mContext;
    private final WifiManager mWifiManager;

    public LocationScanningPreferenceController(Context context) {
        super(context, KEY_LOCATION_SCANNING);
        mContext = context;
        mWifiManager = context.getSystemService(WifiManager.class);
    }

    @Override
    public CharSequence getSummary() {
        final boolean wifiScanOn = mWifiManager.isScanAlwaysAvailable();
        final boolean bleScanOn = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.BLE_SCAN_ALWAYS_AVAILABLE, 0) == 1;
        int resId;
        if (wifiScanOn && bleScanOn) {
            resId = R.string.scanning_status_text_wifi_on_ble_on;
        } else if (wifiScanOn && !bleScanOn) {
            resId = R.string.scanning_status_text_wifi_on_ble_off;
        } else if (!wifiScanOn && bleScanOn) {
            resId = R.string.scanning_status_text_wifi_off_ble_on;
        } else {
            resId = R.string.scanning_status_text_wifi_off_ble_off;
        }
        return mContext.getString(resId);
    }

    @AvailabilityStatus
    public int getAvailabilityStatus() {
        return mContext.getResources().getBoolean(R.bool.config_show_location_scanning)
                ? AVAILABLE
                : UNSUPPORTED_ON_DEVICE;
    }
}
