package com.example.myapplication;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import static android.content.Context.DEVICE_POLICY_SERVICE;

public class MyWorker extends Worker {

    public MyWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.v("Worker", "doWork: start");
        new MyUtils(getApplicationContext()).smartLock("PeriodicWorker");
        Log.v("Worker", "doWork: end");
        return Result.retry();
    }
}