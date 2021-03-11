package com.example.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;

public class MyReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        MyUtils myUtils = new MyUtils(context);
        myUtils.log("MyReceiver.onReceive: intent " + intent.toString());

        String action = intent.getAction();
        if (action != null) {
            myUtils.log("MyReceiver.onReceive: action " + action);

            if (action.equals(Intent.ACTION_USER_PRESENT) && myUtils.isNowSafe()) {
                myUtils.log("MyReceiver.onReceive: going HOME" + action);
                myUtils.runAnotherHomeLauncher();
                return;
            }
        } else {
            myUtils.log("MyReceiver.onReceive: action NULL");
        }

        Intent i = new Intent(context, MyService.class);
        i.putExtra("caller", "BoroadcastReceiver");
        context.startService(i);
    }
}
