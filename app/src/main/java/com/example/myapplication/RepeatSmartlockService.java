package com.example.myapplication;

import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.JobIntentService;

/**
 * Example implementation of a JobIntentService.
 */
public class RepeatSmartlockService extends JobIntentService {
    @Override
    protected void onHandleWork(Intent intent) {
        Kernel kernel = new Kernel(this);

        // We have received work to do.  The system or framework is already
        // holding a wake lock for us at this point, so we can just go.

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