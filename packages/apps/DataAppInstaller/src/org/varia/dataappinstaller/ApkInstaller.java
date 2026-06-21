/*
 * SPDX-FileCopyrightText: 2026 VariationUI
 * SPDX-License-Identifier: Apache-2.0
 */

package org.varia.dataappinstaller;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class ApkInstaller {
    private static final String TAG = "DataAppInstaller";
    private static final long INSTALL_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5);

    private final Context mContext;
    private final PackageInstaller mInstaller;
    private final AtomicInteger mRequestCode = new AtomicInteger();

    ApkInstaller(Context context) {
        mContext = context;
        mInstaller = context.getPackageManager().getPackageInstaller();
    }

    boolean install(PackageSet packageSet) {
        int sessionId = -1;
        InstallResultReceiver.ResultWaiter waiter = null;
        try {
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(
                            PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(packageSet.packageName);
            params.setInstallReason(PackageManager.INSTALL_REASON_DEVICE_SETUP);
            params.setInstallerPackageName(mContext.getPackageName());
            params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE);
            params.setRequireUserAction(
                    PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);

            sessionId = mInstaller.createSession(params);
            try (PackageInstaller.Session session = mInstaller.openSession(sessionId)) {
                for (File apk : packageSet.apks) {
                    writeApk(session, apk);
                }
                waiter = InstallResultReceiver.createWaiter(sessionId);
                session.commit(createStatusIntent(sessionId));
            }

            InstallResultReceiver.Result result = waiter.await(INSTALL_TIMEOUT_MILLIS);
            if (result == null) {
                Log.e(TAG, "Timed out installing " + packageSet.label);
                abandonQuietly(sessionId);
                return false;
            }
            if (result.status == PackageInstaller.STATUS_SUCCESS) {
                Log.i(TAG, "Installed " + packageSet.label);
                return true;
            }
            Log.e(TAG, "Failed installing " + packageSet.label + ": status="
                    + result.status + ", message=" + result.message);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed installing " + packageSet.label, e);
            if (sessionId != -1) {
                abandonQuietly(sessionId);
            }
            return false;
        } finally {
            if (waiter != null) {
                waiter.close();
            }
        }
    }

    private void writeApk(PackageInstaller.Session session, File apk) throws IOException {
        Log.i(TAG, "Writing " + apk.getAbsolutePath());
        try (FileInputStream in = new FileInputStream(apk);
                OutputStream out = session.openWrite(apk.getName(), 0, apk.length())) {
            byte[] buffer = new byte[1024 * 1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            session.fsync(out);
        }
    }

    private android.content.IntentSender createStatusIntent(int sessionId) {
        Intent intent = new Intent(mContext, InstallResultReceiver.class)
                .setAction(InstallResultReceiver.ACTION_INSTALL_COMMIT)
                .putExtra(InstallResultReceiver.EXTRA_SESSION_ID, sessionId);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                mContext,
                mRequestCode.incrementAndGet(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        return pendingIntent.getIntentSender();
    }

    private void abandonQuietly(int sessionId) {
        try {
            mInstaller.abandonSession(sessionId);
        } catch (Exception e) {
            Log.w(TAG, "Unable to abandon session " + sessionId, e);
        }
    }

    static boolean waitForPackageManager(Context context) {
        long deadline = SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(2);
        PackageManager pm = context.getPackageManager();
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                pm.getInstalledPackages(0);
                return true;
            } catch (RuntimeException e) {
                Log.w(TAG, "PackageManager is not ready yet", e);
                SystemClock.sleep(TimeUnit.SECONDS.toMillis(5));
            }
        }
        return false;
    }

    static final class PackageSet {
        final String label;
        final String packageName;
        final List<File> apks;

        PackageSet(String label, String packageName, List<File> apks) {
            this.label = label;
            this.packageName = packageName;
            this.apks = apks;
        }
    }
}
