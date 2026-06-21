/*
 * SPDX-FileCopyrightText: 2026 VariationUI
 * SPDX-License-Identifier: Apache-2.0
 */

package org.varia.dataappinstaller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class InstallResultReceiver extends BroadcastReceiver {
    static final String ACTION_INSTALL_COMMIT =
            "org.varia.dataappinstaller.action.INSTALL_COMMIT";
    static final String EXTRA_SESSION_ID = "session_id";

    private static final String TAG = "DataAppInstaller";
    private static final Map<Integer, ResultWaiter> WAITERS = new ConcurrentHashMap<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_INSTALL_COMMIT.equals(intent.getAction())) {
            return;
        }
        int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
        ResultWaiter waiter = WAITERS.get(sessionId);
        if (waiter == null) {
            Log.w(TAG, "No waiter for session " + sessionId);
            return;
        }
        waiter.setResult(new Result(
                intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_FAILURE),
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)));
    }

    static ResultWaiter createWaiter(int sessionId) {
        ResultWaiter waiter = new ResultWaiter(sessionId);
        WAITERS.put(sessionId, waiter);
        return waiter;
    }

    static final class Result {
        final int status;
        final String message;

        Result(int status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    static final class ResultWaiter {
        private final int mSessionId;
        private final CountDownLatch mLatch = new CountDownLatch(1);
        private volatile Result mResult;

        ResultWaiter(int sessionId) {
            mSessionId = sessionId;
        }

        void setResult(Result result) {
            mResult = result;
            mLatch.countDown();
        }

        Result await(long timeoutMillis) throws InterruptedException {
            if (!mLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                return null;
            }
            return mResult;
        }

        void close() {
            WAITERS.remove(mSessionId);
        }
    }
}
