package com.gathin.glasshome;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Repairs only our routing entry after Xiaomi clears accessibility state during standby. */
final class HomeRouting {
    private static boolean registered;
    static SharedPreferences preferences(Context c){return c.getSharedPreferences("home_routing",Context.MODE_PRIVATE);}
    static boolean enabled(Context c){return preferences(c).getBoolean("enabled",true);}
    static String component(Context c){return c.getPackageName()+"/com.gathin.glasshome.HomeGuardService";}

    static void register(Application app){
        if(registered)return;
        registered=true;
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_USER_UNLOCKED);
        BroadcastReceiver receiver=new BroadcastReceiver(){
            @Override public void onReceive(Context context,Intent intent){wake(context,intent.getAction());}
        };
        if(Build.VERSION.SDK_INT>=33)app.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);
        else app.registerReceiver(receiver,filter);
        ensure(app,"process start");
    }

    static void ensure(Context context,String reason){
        if(!enabled(context))return;
        try{
            String value=Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            List<String> entries=new ArrayList<>();
            if(value!=null&&!value.isEmpty()&&!value.equals("null"))entries.addAll(Arrays.asList(value.split(":")));
            String own=component(context);
            if(!entries.contains(own)){
                entries.add(own);
                Settings.Secure.putString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,String.join(":",entries));
                Log.i("GlassHomeRouting","Restored own routing entry: "+reason);
            }
            if(Settings.Secure.getInt(context.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0)!=1)
                Settings.Secure.putInt(context.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,1);
        }catch(SecurityException error){Log.w("GlassHomeRouting","Routing repair needs the installation grant",error);}
    }

    static void wake(Context context,String reason){
        if(!enabled(context))return;
        preferences(context).edit().putBoolean("wake_pending",true).apply();
        ensure(context,reason);
        HomeGuardService.wakeIfConnected();
    }

    static void configure(Context context,boolean enable){
        preferences(context).edit().putBoolean("enabled",enable).putBoolean("wake_pending",false).commit();
        if(enable){ensure(context,"explicit enable");return;}
        String current=Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        List<String> entries=new ArrayList<>();
        if(current!=null&&!current.equals("null"))for(String entry:current.split(":"))if(!entry.isEmpty()&&!entry.equals(component(context)))entries.add(entry);
        Settings.Secure.putString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,String.join(":",entries));
    }
    private HomeRouting(){}
}
