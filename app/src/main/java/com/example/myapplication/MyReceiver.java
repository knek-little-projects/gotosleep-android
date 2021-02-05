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
        Log.i("MyReceiver", "MyService Receiver Intent" + intent.toString());

        String action = intent.getAction();
        if (action != null) {
            MyUtils myUtils = new MyUtils(context);
            myUtils.log("ACTION " + action);

            if (action.equals(Intent.ACTION_USER_PRESENT) && myUtils.isNowSafe()) {
                myUtils.runAnotherHomeLauncher();
                return;
            }
        }

        Intent i = new Intent(context, MyService.class);
        i.putExtra("caller", "BoroadcastReceiver");
        context.startService(i);

    }
}
