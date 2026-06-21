/*
 * SPDX-FileCopyrightText: 2026 VariationUI
 * SPDX-License-Identifier: Apache-2.0
 */

package org.varia.dataappinstaller;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

public final class FirstBootInstallJobService extends JobService {
    private static final String TAG = "DataAppInstaller";
    private static final int JOB_ID = 0x44544149; // "DTAI"

    private volatile Thread mWorker;

    static void schedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) {
            Log.e(TAG, "JobScheduler is unavailable");
            return;
        }

        ComponentName componentName =
                new ComponentName(context, FirstBootInstallJobService.class);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, componentName)
                .setOverrideDeadline(0)
                .setBackoffCriteria(
                        JobInfo.DEFAULT_INITIAL_BACKOFF_MILLIS,
                        JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build();
        int result = scheduler.schedule(jobInfo);
        if (result != JobScheduler.RESULT_SUCCESS) {
            Log.e(TAG, "Failed to schedule data-app install job");
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        mWorker = new Thread(() -> {
            boolean completed = false;
            try {
                completed = new FirstBootInstaller(getApplicationContext()).run();
            } finally {
                jobFinished(params, !completed);
            }
        }, "DataAppInstallerJob");
        mWorker.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Thread worker = mWorker;
        if (worker != null) {
            worker.interrupt();
        }
        return FirstBootInstaller.shouldRun(this);
    }
}
