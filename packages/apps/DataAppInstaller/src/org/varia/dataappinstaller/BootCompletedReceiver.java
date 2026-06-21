/*
 * SPDX-FileCopyrightText: 2026 VariationUI
 * SPDX-License-Identifier: Apache-2.0
 */

package org.varia.dataappinstaller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "DataAppInstaller";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        if (!FirstBootInstaller.shouldRun(context)) {
            Log.i(TAG, "Data-app installation marker is already current");
            return;
        }

        FirstBootInstallJobService.schedule(context.getApplicationContext());
    }
}
