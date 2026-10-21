package com.gathin.glasshome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Compatibility for Xiaomi's HOME allowlist. Watches window metadata, never screen contents. */
public final class HomeGuardService extends AccessibilityService {
    private static HomeGuardService current;
    private boolean visitingStock;
    private String lastPackage;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private static final String STOCK="com.mitv.tvhome";

    @Override protected void onServiceConnected() {
        current=this;
        filterHome(false);
        Log.i("GlassHomeGuard","Home routing ready");
        if(HomeRouting.preferences(this).getBoolean("wake_pending",false))scheduleWake();
    }

    static void wakeIfConnected(){if(current!=null)current.scheduleWake();}
    static void glassVisible(){
        if(current!=null){current.visitingStock=false;current.filterHome(false);}
    }
    private void scheduleWake(){
        visitingStock=false;
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(()->{
            HomeRouting.preferences(this).edit().putBoolean("wake_pending",false).apply();
            PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
            if(HomeRouting.enabled(this)&&power!=null&&power.isInteractive()
                    &&(lastPackage==null||lastPackage.equals(STOCK)||lastPackage.equals(getPackageName())))returnHome();
        },1000);
    }

    static boolean openStockHome() {
        if(current==null)return false;
        current.visitingStock=true;
        current.filterHome(true);
        return current.performGlobalAction(GLOBAL_ACTION_HOME);
    }

    private void filterHome(boolean enabled) {
        AccessibilityServiceInfo info=getServiceInfo();
        if(info==null)return;
        int flag=AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.flags=enabled?(info.flags|flag):(info.flags&~flag);
        setServiceInfo(info);
    }

    private void returnHome() {
        visitingStock=false;
        filterHome(false);
        try {
            startActivity(new Intent(this,GlassHomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
            Log.i("GlassHomeGuard","Returned to Glass Home");
        } catch(RuntimeException ex){ Log.e("GlassHomeGuard","Cannot start home",ex); }
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if(!visitingStock || (event.getKeyCode()!=KeyEvent.KEYCODE_HOME && event.getKeyCode()!=KeyEvent.KEYCODE_ALL_APPS))return false;
        if(event.getAction()==KeyEvent.ACTION_UP && !event.isCanceled())returnHome();
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName()==null || event.getClassName()==null)return;
        String pkg=event.getPackageName().toString(),cls=event.getClassName().toString();
        try{getPackageManager().getActivityInfo(new ComponentName(pkg,cls),0);lastPackage=pkg;}catch(Exception ignored){}
        if(STOCK.equals(pkg)) {
            if(!visitingStock && (cls.equals("com.mitv.tvhome.MainActivityUserMode") || cls.equals("com.mitv.tvhome.MainActivity")))returnHome();
            return;
        }
        if(visitingStock && !pkg.equals(getPackageName()) && !pkg.equals("android")) {
            // Visiting a real app ends the explicit stock-home visit; ignore system popups.
            try {
                getPackageManager().getActivityInfo(new ComponentName(pkg,cls),0);
                visitingStock=false;
                filterHome(false);
            }catch(Exception ignored) { }
        }
    }

    @Override public void onInterrupt() { }
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);if(current==this)current=null;super.onDestroy();}
}
