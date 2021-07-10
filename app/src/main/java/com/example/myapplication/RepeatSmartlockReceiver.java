package com.example.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class RepeatSmartlockReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Kernel kernel = new Kernel(context);
        kernel.log("MyReceiver.onReceive: intent " + intent.toString());

        String action = intent.getAction();
        if (action != null) {
            kernel.log("MyReceiver.onReceive: action " + action);

            if (action.equals(Intent.ACTION_USER_PRESENT) && kernel.isNowSafe()) {
                kernel.log("MyReceiver.onReceive: going HOME" + action);
                kernel.runAnotherHomeLauncher();
                return;
            }
        } else {
            kernel.log("MyReceiver.onReceive: action NULL");
        }

        Intent i = new Intent(context, RepeatSmartlockService.class);
        i.putExtra("caller", "BoroadcastReceiver");
        context.startService(i);
    }
}
