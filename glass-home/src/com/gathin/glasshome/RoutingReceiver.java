package com.gathin.glasshome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Only the ADB shell/system can send the configuration action (manifest permission). */
public final class RoutingReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if("com.gathin.glasshome.CONFIGURE_ROUTING".equals(intent.getAction()))
            HomeRouting.configure(context,intent.getBooleanExtra("enabled",false));
    }
}
