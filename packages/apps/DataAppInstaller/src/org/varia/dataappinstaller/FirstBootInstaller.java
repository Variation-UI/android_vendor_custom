/*
 * SPDX-FileCopyrightText: 2026 VariationUI
 * SPDX-License-Identifier: Apache-2.0
 */

package org.varia.dataappinstaller;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.SystemProperties;
import android.util.Log;

import org.varia.dataappinstaller.ApkInstaller.PackageSet;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class FirstBootInstaller {
    private static final String TAG = "DataAppInstaller";
    private static final String PREFS_NAME = "data_app_installer";
    private static final String PROP_INSTALLER_VERSION = "ro.varia.datainstaller";
    private static final String KEY_INSTALLED_VERSION = "installed_version";
    private static final int DEFAULT_INSTALLER_VERSION = 1;
    private static final File DATA_APP_DIR = new File("/product/data-app");

    private final Context mContext;

    FirstBootInstaller(Context context) {
        mContext = context;
    }

    static boolean shouldRun(Context context) {
        return getTargetVersion() > getInstalledVersion(context);
    }

    boolean run() {
        int targetVersion = getTargetVersion();
        int installedVersion = getInstalledVersion(mContext);
        if (targetVersion <= installedVersion) {
            Log.i(TAG, "Data-app installer version is already current: " + installedVersion);
            return true;
        }

        Log.i(TAG, "Running data-app installer version " + targetVersion
                + ", previous version " + installedVersion);
        if (!ApkInstaller.waitForPackageManager(mContext)) {
            Log.e(TAG, "PackageManager did not become ready; will retry on next boot");
            return false;
        }

        List<PackageSet> packageSets = scanPackageSets();
        if (packageSets.isEmpty()) {
            Log.i(TAG, "No APKs found under " + DATA_APP_DIR);
            markInstalledVersion(targetVersion);
            return true;
        }

        ApkInstaller installer = new ApkInstaller(mContext);
        int installed = 0;
        for (PackageSet packageSet : packageSets) {
            if (Thread.currentThread().isInterrupted()) {
                Log.w(TAG, "Data-app installation interrupted");
                return false;
            }
            if (installer.install(packageSet)) {
                installed++;
            }
        }

        if (installed == packageSets.size()) {
            Log.i(TAG, "Installed all data-app packages for version " + targetVersion);
            markInstalledVersion(targetVersion);
            return true;
        } else {
            Log.e(TAG, "Installed " + installed + " of " + packageSets.size()
                    + " packages; will retry while version marker is stale");
            return false;
        }
    }

    private List<PackageSet> scanPackageSets() {
        File[] entries = DATA_APP_DIR.listFiles();
        if (entries == null || entries.length == 0) {
            return Collections.emptyList();
        }

        Arrays.sort(entries, Comparator.comparing(File::getName));
        List<PackageSet> packageSets = new ArrayList<>();
        for (File entry : entries) {
            PackageSet packageSet = null;
            if (entry.isFile() && isApk(entry)) {
                packageSet = createPackageSet(entry.getName(), Collections.singletonList(entry));
            } else if (entry.isDirectory()) {
                packageSet = createPackageSet(entry.getName(), listApks(entry));
            }

            if (packageSet != null) {
                packageSets.add(packageSet);
            }
        }
        return packageSets;
    }

    private PackageSet createPackageSet(String label, List<File> apks) {
        if (apks.isEmpty()) {
            return null;
        }

        File baseApk = findBaseApk(apks);
        PackageInfo info = mContext.getPackageManager().getPackageArchiveInfo(
                baseApk.getAbsolutePath(), 0);
        if (info == null || info.packageName == null) {
            Log.e(TAG, "Skipping invalid APK set " + label);
            return null;
        }
        return new PackageSet(label, info.packageName, apks);
    }

    private File findBaseApk(List<File> apks) {
        for (File apk : apks) {
            if ("base.apk".equals(apk.getName())) {
                return apk;
            }
        }
        return apks.get(0);
    }

    private List<File> listApks(File dir) {
        File[] files = dir.listFiles(file -> file.isFile() && isApk(file));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }
        Arrays.sort(files, (left, right) -> {
            boolean leftBase = "base.apk".equals(left.getName());
            boolean rightBase = "base.apk".equals(right.getName());
            if (leftBase != rightBase) {
                return leftBase ? -1 : 1;
            }
            return left.getName().compareTo(right.getName());
        });
        return Arrays.asList(files);
    }

    private static boolean isApk(File file) {
        return file.getName().toLowerCase().endsWith(".apk");
    }

    private void markInstalledVersion(int version) {
        if (!prefs(mContext).edit().putInt(KEY_INSTALLED_VERSION, version).commit()) {
            Log.e(TAG, "Failed to write data-app installer version marker");
        }
    }

    private static int getTargetVersion() {
        return Math.max(DEFAULT_INSTALLER_VERSION,
                SystemProperties.getInt(PROP_INSTALLER_VERSION, DEFAULT_INSTALLER_VERSION));
    }

    private static int getInstalledVersion(Context context) {
        return prefs(context).getInt(KEY_INSTALLED_VERSION, 0);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
