package com.gathin.glasshome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        String action=intent.getAction();
        if(Intent.ACTION_BOOT_COMPLETED.equals(action))HomeRouting.wake(context,"boot completed");
        else if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))HomeRouting.ensure(context,"app update");
    }
}
