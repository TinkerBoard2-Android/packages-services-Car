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

package com.android.car.developeroptions.notification;

import android.app.Activity;
import android.app.Application;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.SearchIndexableResource;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;

import com.android.car.developeroptions.R;
import com.android.car.developeroptions.RingtonePreference;
import com.android.car.developeroptions.core.OnActivityResultListener;
import com.android.car.developeroptions.dashboard.DashboardFragment;
import com.android.car.developeroptions.dashboard.SummaryLoader;
import com.android.car.developeroptions.search.BaseSearchIndexProvider;
import com.android.car.developeroptions.search.Indexable;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SearchIndexable
public class ConfigureNotificationSettings extends DashboardFragment implements
        OnActivityResultListener {
    private static final String TAG = "ConfigNotiSettings";

    @VisibleForTesting
    static final String KEY_LOCKSCREEN = "lock_screen_notifications";
    @VisibleForTesting
    static final String KEY_LOCKSCREEN_WORK_PROFILE_HEADER =
            "lock_screen_notifications_profile_header";
    @VisibleForTesting
    static final String KEY_LOCKSCREEN_WORK_PROFILE = "lock_screen_notifications_profile";
    @VisibleForTesting
    static final String KEY_SWIPE_DOWN = "gesture_swipe_down_fingerprint_notifications";
    @VisibleForTesting
    static final String KEY_NOTIFICATION_ASSISTANT = "notification_assistant";

    private static final String KEY_NOTI_DEFAULT_RINGTONE = "notification_default_ringtone";

    private RingtonePreference mRequestPreference;
    private static final int REQUEST_CODE = 200;
    private static final String SELECTED_PREFERENCE_KEY = "selected_preference";

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.CONFIGURE_NOTIFICATION;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.configure_notification_settings;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        final Activity activity = getActivity();
        final Application app;
        if (activity != null) {
            app = activity.getApplication();
        } else {
            app = null;
        }
        return buildPreferenceControllers(context, getSettingsLifecycle(), app, this);
    }

    private static List<AbstractPreferenceController> buildPreferenceControllers(Context context,
            Lifecycle lifecycle, Application app, Fragment host) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        final LockScreenNotificationPreferenceController lockScreenNotificationController =
                new LockScreenNotificationPreferenceController(context,
                        KEY_LOCKSCREEN,
                        KEY_LOCKSCREEN_WORK_PROFILE_HEADER,
                        KEY_LOCKSCREEN_WORK_PROFILE);
        if (lifecycle != null) {
            lifecycle.addObserver(lockScreenNotificationController);
        }
        controllers.add(new RecentNotifyingAppsPreferenceController(
                context, new NotificationBackend(), app, host));
        controllers.add(lockScreenNotificationController);
        controllers.add(new NotificationRingtonePreferenceController(context) {
            @Override
            public String getPreferenceKey() {
                return KEY_NOTI_DEFAULT_RINGTONE;
            }

        });
        return controllers;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference instanceof RingtonePreference) {
            mRequestPreference = (RingtonePreference) preference;
            mRequestPreference.onPrepareRingtonePickerIntent(mRequestPreference.getIntent());
            getActivity().startActivityForResultAsUser(
                    mRequestPreference.getIntent(),
                    REQUEST_CODE,
                    null,
                    UserHandle.of(mRequestPreference.getUserId()));
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mRequestPreference != null) {
            mRequestPreference.onActivityResult(requestCode, resultCode, data);
            mRequestPreference = null;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mRequestPreference != null) {
            outState.putString(SELECTED_PREFERENCE_KEY, mRequestPreference.getKey());
        }
    }

    /**
     * For summary
     */
    static class SummaryProvider implements SummaryLoader.SummaryProvider {

        private final Context mContext;
        private final SummaryLoader mSummaryLoader;
        private NotificationBackend mBackend;

        public SummaryProvider(Context context, SummaryLoader summaryLoader) {
            mContext = context;
            mSummaryLoader = summaryLoader;
            mBackend = new NotificationBackend();
        }

        @VisibleForTesting
        protected void setBackend(NotificationBackend backend) {
            mBackend = backend;
        }

        @Override
        public void setListening(boolean listening) {
            if (!listening) {
                return;
            }
            int blockedAppCount = mBackend.getBlockedAppCount();
            if (blockedAppCount == 0) {
                mSummaryLoader.setSummary(this,
                        mContext.getText(R.string.app_notification_listing_summary_zero));
            } else {
                mSummaryLoader.setSummary(this,
                        mContext.getResources().getQuantityString(
                                R.plurals.app_notification_listing_summary_others,
                                blockedAppCount, blockedAppCount));
            }
        }
    }

    public static final SummaryLoader.SummaryProviderFactory SUMMARY_PROVIDER_FACTORY =
            new SummaryLoader.SummaryProviderFactory() {
                @Override
                public SummaryLoader.SummaryProvider createSummaryProvider(Activity activity,
                        SummaryLoader summaryLoader) {
                    return new ConfigureNotificationSettings.SummaryProvider(
                            activity, summaryLoader);
                }
            };

    /**
     * For Search.
     */
    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(
                        Context context, boolean enabled) {
                    final SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.configure_notification_settings;
                    return Arrays.asList(sir);
                }

                @Override
                public List<AbstractPreferenceController> createPreferenceControllers(
                        Context context) {
                    return buildPreferenceControllers(context, null, null, null);
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    final List<String> keys = super.getNonIndexableKeys(context);
                    keys.add(KEY_SWIPE_DOWN);
                    return keys;
                }
            };
}
