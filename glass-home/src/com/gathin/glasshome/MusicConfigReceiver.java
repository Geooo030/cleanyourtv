package com.gathin.glasshome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Provisioned by the local installer; only callers with WRITE_SECURE_SETTINGS may invoke it. */
public final class MusicConfigReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        String base=intent.getStringExtra("base"),token=intent.getStringExtra("token");
        if(base==null||token==null||!token.matches("[a-f0-9]{64}"))return;
        Uri uri=Uri.parse(base);String host=uri.getHost();
        if(!"http".equals(uri.getScheme())||host==null||!host.matches("(192\\.168\\.|10\\.|127\\.)[0-9.]+")||uri.getPort()<1024)return;
        context.getSharedPreferences("music",0).edit().putString("base",base).putString("token",token).apply();
        setResultCode(-1);
    }
}
