/*******************************************************************************
 * Copyright (c) 2019 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - Initial creation
 ******************************************************************************/
package org.eclipse.californium.examples;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.eclipse.californium.examples.PreferenceIDs.PREF_TIMED_REQUEST;

public final class JobManager {
    private static final String LOG_TAG = "job";
    private static final int JOB_ID = 1;

    private static long getInterval(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String time = preferences.getString(PREF_TIMED_REQUEST, "0");
        long interval = 0;
        try {
            interval = Long.parseLong(time);
            interval = TimeUnit.MINUTES.toMillis(interval);
        } catch (NumberFormatException e) {
        }
        return interval;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private static void createJob(Context context, JobScheduler jobScheduler, int id, long interval) {
        ComponentName component = new ComponentName(context, TimedRequestJob.class);
        JobInfo.Builder builder = new JobInfo.Builder(id, component);
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        builder.setMinimumLatency(interval);
        builder.setOverrideDeadline(interval * 2);
        JobInfo jobInfo = builder.build();
        jobScheduler.schedule(jobInfo);
        Log.i(LOG_TAG, "create job " + id + " for timed requests with interval " + TimeUnit.MILLISECONDS.toMinutes(interval) + " min.");
    }

    @TargetApi(Build.VERSION_CODES.M)
    private static List<JobInfo> getAllPendingJobs(JobScheduler jobScheduler) {
        List<JobInfo> infos = jobScheduler.getAllPendingJobs();
        for (JobInfo info : infos) {
            Log.i(LOG_TAG, info.toString());
        }
        return infos;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private static void createNewJob(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        List<JobInfo> infos = getAllPendingJobs(jobScheduler);
        for (JobInfo info : infos) {
            jobScheduler.cancel(info.getId());
        }
        long interval = getInterval(context);
        if (interval > 0) {
            createJob(context, jobScheduler, JOB_ID, interval);
        } else {
            Log.i(LOG_TAG, "no jobs for timed requests.");
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private static void createNextJob(Context context, int keep, long interval) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        List<JobInfo> infos = getAllPendingJobs(jobScheduler);
        for (JobInfo info : infos) {
            if (info.getId() != keep) {
                jobScheduler.cancel(info.getId());
            }
        }
        // alternate job id between 1 and 2
        createJob(context, jobScheduler, 3 - keep, interval);
    }


    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    public static void initialize(Context context) {
        if (isSupported()) {
            createNewJob(context);
        }
    }

    public static void createNext(Context context, int id) {
        if (isSupported()) {
            long interval = getInterval(context);
            if (interval > 0) {
                createNextJob(context, id, interval);
            }
        }
    }
}
