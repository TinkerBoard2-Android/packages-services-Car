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

package com.android.car.developeroptions.deviceinfo;

import android.app.ActivityManager;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserManager;
import android.os.storage.DiskInfo;
import android.os.storage.StorageEventListener;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.os.storage.VolumeRecord;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.format.Formatter.BytesResult;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.car.developeroptions.R;
import com.android.car.developeroptions.SettingsPreferenceFragment;
import com.android.car.developeroptions.deviceinfo.StorageSettings.MountTask;
import com.android.car.developeroptions.deviceinfo.StorageSettings.UnmountTask;

import java.io.File;
import java.util.Objects;

/**
 * Panel showing summary and actions for a {@link VolumeInfo#TYPE_PUBLIC}
 * storage volume.
 */
public class PublicVolumeSettings extends SettingsPreferenceFragment {
    // TODO: disable unmount when providing over MTP/PTP

    private StorageManager mStorageManager;

    private String mVolumeId;
    private VolumeInfo mVolume;
    private DiskInfo mDisk;

    private StorageSummaryPreference mSummary;

    private Preference mMount;
    private Preference mFormatPublic;
    private Preference mFormatPrivate;
    private Button mUnmount;

    private boolean mIsPermittedToAdopt;

    private boolean isVolumeValid() {
        return (mVolume != null) && (mVolume.getType() == VolumeInfo.TYPE_PUBLIC)
                && mVolume.isMountedReadable();
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DEVICEINFO_STORAGE;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final Context context = getActivity();

        mIsPermittedToAdopt = UserManager.get(context).isAdminUser()
                && !ActivityManager.isUserAMonkey();

        mStorageManager = context.getSystemService(StorageManager.class);

        if (DocumentsContract.ACTION_DOCUMENT_ROOT_SETTINGS.equals(
                getActivity().getIntent().getAction())) {
            final Uri rootUri = getActivity().getIntent().getData();
            final String fsUuid = DocumentsContract.getRootId(rootUri);
            mVolume = mStorageManager.findVolumeByUuid(fsUuid);
        } else {
            final String volId = getArguments().getString(VolumeInfo.EXTRA_VOLUME_ID);
            if (volId != null) {
                mVolume = mStorageManager.findVolumeById(volId);
            }
        }

        if (!isVolumeValid()) {
            getActivity().finish();
            return;
        }

        mDisk = mStorageManager.findDiskById(mVolume.getDiskId());
        Objects.requireNonNull(mDisk);

        mVolumeId = mVolume.getId();

        addPreferencesFromResource(R.xml.device_info_storage_volume);
        getPreferenceScreen().setOrderingAsAdded(true);

        mSummary = new StorageSummaryPreference(getPrefContext());

        mMount = buildAction(R.string.storage_menu_mount);
        mUnmount = new Button(getActivity());
        mUnmount.setText(R.string.storage_menu_unmount);
        mUnmount.setOnClickListener(mUnmountListener);
        mFormatPublic = buildAction(R.string.storage_menu_format);
        if (mIsPermittedToAdopt) {
            mFormatPrivate = buildAction(R.string.storage_menu_format_private);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // If the volume isn't valid, we are not scaffolded to set up a view.
        if (!isVolumeValid()) {
            return;
        }

        final Resources resources = getResources();
        final int padding = resources.getDimensionPixelSize(
                R.dimen.unmount_button_padding);
        final ViewGroup buttonBar = getButtonBar();
        buttonBar.removeAllViews();
        buttonBar.setPadding(padding, padding, padding, padding);
        buttonBar.addView(mUnmount, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void update() {
        if (!isVolumeValid()) {
            getActivity().finish();
            return;
        }

        getActivity().setTitle(mStorageManager.getBestVolumeDescription(mVolume));

        final Context context = getActivity();
        final PreferenceScreen screen = getPreferenceScreen();

        screen.removeAll();

        if (mVolume.isMountedReadable()) {
            addPreference(mSummary);

            final File file = mVolume.getPath();
            final long totalBytes = file.getTotalSpace();
            final long freeBytes = file.getFreeSpace();
            final long usedBytes = totalBytes - freeBytes;

            final BytesResult result = Formatter.formatBytes(getResources(), usedBytes, 0);
            mSummary.setTitle(TextUtils.expandTemplate(getText(R.string.storage_size_large),
                    result.value, result.units));
            mSummary.setSummary(getString(R.string.storage_volume_used,
                    Formatter.formatFileSize(context, totalBytes)));
            mSummary.setPercent(usedBytes, totalBytes);
        }

        if (mVolume.getState() == VolumeInfo.STATE_UNMOUNTED) {
            addPreference(mMount);
        }
        if (mVolume.isMountedReadable()) {
            getButtonBar().setVisibility(View.VISIBLE);
        }
        addPreference(mFormatPublic);
        if (mDisk.isAdoptable() && mIsPermittedToAdopt) {
            addPreference(mFormatPrivate);
        }
    }

    private void addPreference(Preference pref) {
        pref.setOrder(Preference.DEFAULT_ORDER);
        getPreferenceScreen().addPreference(pref);
    }

    private Preference buildAction(int titleRes) {
        final Preference pref = new Preference(getPrefContext());
        pref.setTitle(titleRes);
        return pref;
    }

    @Override
    public void onResume() {
        super.onResume();

        // Refresh to verify that we haven't been formatted away
        mVolume = mStorageManager.findVolumeById(mVolumeId);
        if (!isVolumeValid()) {
            getActivity().finish();
            return;
        }

        mStorageManager.registerListener(mStorageListener);
        update();
    }

    @Override
    public void onPause() {
        super.onPause();
        mStorageManager.unregisterListener(mStorageListener);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference pref) {
        if (pref == mMount) {
            new MountTask(getActivity(), mVolume).execute();
        } else if (pref == mFormatPublic) {
            StorageWizardFormatConfirm.showPublic(getActivity(), mDisk.getId());
        } else if (pref == mFormatPrivate) {
            StorageWizardFormatConfirm.showPrivate(getActivity(), mDisk.getId());
        }

        return super.onPreferenceTreeClick(pref);
    }

    private final View.OnClickListener mUnmountListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            new UnmountTask(getActivity(), mVolume).execute();
        }
    };

    private final StorageEventListener mStorageListener = new StorageEventListener() {
        @Override
        public void onVolumeStateChanged(VolumeInfo vol, int oldState, int newState) {
            if (Objects.equals(mVolume.getId(), vol.getId())) {
                mVolume = vol;
                update();
            }
        }

        @Override
        public void onVolumeRecordChanged(VolumeRecord rec) {
            if (Objects.equals(mVolume.getFsUuid(), rec.getFsUuid())) {
                mVolume = mStorageManager.findVolumeById(mVolumeId);
                update();
            }
        }
    };
}
