package com.example.myapplication;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class RepeatSmartlockWorker extends Worker {

    public RepeatSmartlockWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.v("Worker", "doWork: start");
        Kernel kernel = new Kernel(getApplicationContext());
        kernel.smartLock("PeriodicWorker");  // doesnt do anything
        Log.v("Worker", "doWork: end");
        return Result.retry();
    }
}