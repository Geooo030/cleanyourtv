package com.gathin.glasshome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.app.ActivityOptions;
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
    private final HomeKeyRouter keys=new HomeKeyRouter();
    private int directReturns,fallbackReturns;

    @Override protected void onServiceConnected() {
        current=this;
        filterHome(HomeRouting.enabled(this));
        Log.i("GlassHomeGuard","Home routing ready");
        if(HomeRouting.preferences(this).getBoolean("wake_pending",false))scheduleWake();
    }

    static void wakeIfConnected(){if(current!=null)current.scheduleWake();}
    static boolean sleepScreen(){return current!=null&&android.os.Build.VERSION.SDK_INT>=28&&current.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);}
    static boolean showPowerMenu(){return current!=null&&current.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);}
    static void glassVisible(){
        if(current!=null){current.visitingStock=false;current.filterHome(HomeRouting.enabled(current));}
    }
    static String snapshot(){if(current==null)return "connected=false";AccessibilityServiceInfo info=current.getServiceInfo();return "connected=true filter="+(info!=null&&(info.flags&AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS)!=0)+" visitingStock="+current.visitingStock+" direct="+current.directReturns+" fallback="+current.fallbackReturns;}
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
        // System-injected Home bypasses key filtering and has the stock launcher's
        // signature permission. This action is used only for the explicit top-bar entry.
        boolean opened=current.performGlobalAction(GLOBAL_ACTION_HOME);
        if(!opened)current.visitingStock=false;return opened;
    }

    private void filterHome(boolean enabled) {
        AccessibilityServiceInfo info=getServiceInfo();
        if(info==null)return;
        int flag=AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        int flags=enabled?(info.flags|flag):(info.flags&~flag);if(info.flags==flags)return;
        info.flags=flags;
        setServiceInfo(info);
    }

    private void returnHome() {
        visitingStock=false;
        filterHome(HomeRouting.enabled(this));
        try {
            ActivityOptions options=ActivityOptions.makeCustomAnimation(this,R.anim.home_return_enter,R.anim.home_return_exit);
            options.setLaunchDisplayId(android.view.Display.DEFAULT_DISPLAY);
            startActivity(new Intent(this,GlassHomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),options.toBundle());
            Log.i("GlassHomeGuard","Returned to Glass Home");
        } catch(RuntimeException ex){ Log.e("GlassHomeGuard","Cannot start home",ex); }
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        int decision=keys.key(event.getKeyCode(),event.getAction(),event.getRepeatCount(),event.getDownTime(),event.isCanceled(),HomeRouting.enabled(this));
        if(decision==HomeKeyRouter.RETURN_HOME){directReturns++;Log.i("GlassHomeGuard","Home key intercepted before launcher");returnHome();}
        return decision!=HomeKeyRouter.PASS;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName()==null || event.getClassName()==null)return;
        String pkg=event.getPackageName().toString(),cls=event.getClassName().toString();
        try{getPackageManager().getActivityInfo(new ComponentName(pkg,cls),0);lastPackage=pkg;}catch(Exception ignored){}
        if(STOCK.equals(pkg)) {
            if(HomeRouting.enabled(this)&&!visitingStock && (cls.equals("com.mitv.tvhome.MainActivityUserMode") || cls.equals("com.mitv.tvhome.MainActivity"))){fallbackReturns++;returnHome();}
            return;
        }
        if(visitingStock && !pkg.equals(getPackageName()) && !pkg.equals("android")) {
            // Visiting a real app ends the explicit stock-home visit; ignore system popups.
            try {
                getPackageManager().getActivityInfo(new ComponentName(pkg,cls),0);
                visitingStock=false;
                filterHome(HomeRouting.enabled(this));
            }catch(Exception ignored) { }
        }
    }

    @Override public void onInterrupt() { }
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);if(current==this)current=null;super.onDestroy();}
}
