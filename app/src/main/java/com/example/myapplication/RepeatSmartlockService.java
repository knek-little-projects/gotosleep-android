package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

/**
 * JobIntentService that performs the smart lock check. On Android 8+ you MUST NOT start it with
 * {@link Context#startService(Intent)} from background - the system will throw
 * {@code BackgroundServiceStartNotAllowedException}. Use {@link #enqueue(Context, Intent)} instead,
 * which routes through JobScheduler on O+.
 */
public class RepeatSmartlockService extends JobIntentService {
    /** Unique job id for JobScheduler on Android 8+. Must be stable per-service in the app. */
    private static final int JOB_ID = 1001;

    /** Safe way to kick off work on this service (handles pre-O and O+ transparently). */
    public static void enqueue(@NonNull Context context, @NonNull Intent work) {
        enqueueWork(context, RepeatSmartlockService.class, JOB_ID, work);
    }

    @Override
    protected void onHandleWork(Intent intent) {
        Kernel kernel = new Kernel(this);

        String label = intent.getStringExtra("label");
        if (label == null) {
            label = intent.toString();
        }
        kernel.log("MyService.onHandleWork: " + label);

        if (intent.getBooleanExtra("LockNow", false)) {
            DeviceAdmin.lockNow(this);
            return;
        }

        kernel.smartLock(intent.getStringExtra("caller"));

        Log.i("MyService", "Completed service @ " + SystemClock.elapsedRealtime());
    }

    @Override
    public void onDestroy() {
        Log.i("MyService", "Destroyed");
        super.onDestroy();
    }
}
