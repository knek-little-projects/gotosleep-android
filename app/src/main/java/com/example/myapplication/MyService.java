package com.example.myapplication;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.JobIntentService;

/**
 * Example implementation of a JobIntentService.
 */
public class MyService extends JobIntentService {
    @Override
    protected void onHandleWork(Intent intent) {
        MyUtils myUtils = new MyUtils(this);

        // We have received work to do.  The system or framework is already
        // holding a wake lock for us at this point, so we can just go.

        String label = intent.getStringExtra("label");
        if (label == null) {
            label = intent.toString();
        }
        myUtils.log("MyService.onHandleWork: " + label);

        if (intent.getBooleanExtra("LockNow", false)) {
            MyAdmin.lockNow(this);
            return;
        }

        myUtils.smartLock(intent.getStringExtra("caller"));

        Log.i("MyService", "Completed service @ " + SystemClock.elapsedRealtime());
    }

    @Override
    public void onDestroy() {
        Log.i("MyService", "Destroyed");
        super.onDestroy();
    }
}