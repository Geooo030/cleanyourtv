package com.gathin.glasshome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

/** Four-app, remote-first home. The stock Xiaomi launcher remains installed and enabled. */
public final class GlassHomeActivity extends Activity {
    static final String[] LABELS = {"云视听小电视", "网易云音乐", "无线投屏", "Netflix"};
    static final String[] PACKAGES = {"com.xiaodianshi.tv.yst", "com.netease.cloudmusic.tv",
            "com.xiaomi.mitv.smartshare", "com.netflix.ninja"};
    GlassScene scene;
    java.util.concurrent.FutureTask<java.util.List<AppsOverlay.Entry>> appsCatalog;
    private VideoBackdrop video;
    private Bitmap wallpaper;
    private boolean resumed;
    private AppsOverlay appsOverlay;
    private OptionsOverlay optionsOverlay;
    private final java.util.Map<String,Bitmap> covers=new java.util.HashMap<>();

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        Bitmap original = BitmapFactory.decodeResource(getResources(), R.drawable.wallpaper);
        wallpaper = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(wallpaper);
        float crop = Math.max(1920f / original.getWidth(), 1080f / original.getHeight());
        float w = crop * original.getWidth(), h = crop * original.getHeight();
        canvas.drawBitmap(original, null, new RectF((1920-w)/2, (1080-h)/2, (1920+w)/2, (1080+h)/2), null);
        original.recycle();
        FrameLayout root = new FrameLayout(this);
        ImageView still = new ImageView(this);
        still.setScaleType(ImageView.ScaleType.CENTER_CROP);
        still.setImageBitmap(wallpaper);
        root.addView(still, new FrameLayout.LayoutParams(-1, -1));
        video = new VideoBackdrop(this);
        root.addView(video, new FrameLayout.LayoutParams(-1, -1));
        ImageView transitionCover=new ImageView(this);transitionCover.setScaleType(ImageView.ScaleType.CENTER_CROP);transitionCover.setAlpha(0);root.addView(transitionCover,new FrameLayout.LayoutParams(-1,-1));video.setCover(transitionCover);
        scene = new GlassScene(this, wallpaper);
        video.setMovieListener(id->{getPreferences(0).edit().putString("movie",id).apply();scene.invalidate();});
        root.addView(scene, new FrameLayout.LayoutParams(-1, -1));
        video.setFrameListener(frame -> {scene.useVideoFrame(frame);if(appsOverlay!=null)appsOverlay.updateBackdrop(frame);if(optionsOverlay!=null)optionsOverlay.updateBackdrop();});
        setContentView(root);
        // Create the app-specific directory so ADB can copy a local video without media permissions.
        getExternalFilesDir(null);
        scene.post(() -> scene.focusApp(0));
    }

    @Override protected void onResume() {
        super.onResume();
        HomeRouting.ensure(this,"home resumed");
        HomeGuardService.glassVisible();
        resumed = true;
        if(appsCatalog!=null)appsCatalog.cancel(true);
        appsCatalog=AppsOverlay.warmApplications(this);
        scene.startClock();
        video.setPlaybackEnabled(getPreferences(0).getBoolean("video", true));
        video.setRotation(wallpaperRotation());video.select(getPreferences(0).getString("movie","hawaii"));
        video.start();
    }

    @Override protected void onPause() {
        resumed = false;
        video.stop();
        scene.stopClock();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if(appsOverlay!=null)appsOverlay.dismissImmediately();
        if(optionsOverlay!=null)optionsOverlay.dismissImmediately();
        if(appsCatalog!=null)appsCatalog.cancel(true);
        video.close();
        scene.close();
        for(Bitmap cover:covers.values())cover.recycle();covers.clear();
        wallpaper.recycle();
        super.onDestroy();
    }

    @Override public void onBackPressed() { scene.focusApp(0); }

    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        if(appsOverlay!=null)appsOverlay.dismissImmediately();
        if(optionsOverlay!=null)optionsOverlay.dismissImmediately();
        scene.focusApp(0);
    }

    void appsOverlayClosed(AppsOverlay overlay){
        if(appsOverlay==overlay)appsOverlay=null;
        scene.focusControl(4);
    }

    void setAppsExpansion(float amount){scene.setAlpha(1-Math.max(0,Math.min(1,amount)));}
    void setAppsMotion(boolean moving){video.setSamplingPaused(moving);}
    void optionsClosed(OptionsOverlay overlay){if(optionsOverlay==overlay)optionsOverlay=null;scene.focusControl(overlay.focusReturn);}

    boolean isInstalled(int index) {
        if (index == 2) return getPackageManager().getLaunchIntentForPackage(PACKAGES[2]) != null;
        return launchIntent(index) != null;
    }

    private Intent launchIntent(int index) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(PACKAGES[index]);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(PACKAGES[index]);
        if (i == null && index == 3) i = getPackageManager().getLaunchIntentForPackage("com.netflix.mediaclient");
        return i;
    }

    void activate(int index) {
        if (index == 8) {
            if(!HomeGuardService.openStockHome()) Toast.makeText(this,"主页键服务尚未就绪",Toast.LENGTH_LONG).show();
        } else if (index < 4) {
            Intent target = launchIntent(index);
            if (target == null) {
                new AlertDialog.Builder(this).setTitle(LABELS[index] + "尚未安装")
                        .setMessage(index == 3 ? "主页已为 Netflix 预留入口。安装兼容的 Netflix 电视版后，这张卡片会自动启用。"
                                : "这台电视尚未提供可启动的应用入口。")
                        .setPositiveButton("知道了", null).show();
                return;
            }
            open(target);
        } else if (index == 4) allApps();
        else if (index == 5) wallpaperOptions();
        else if (index == 6) systemOptions(true);
        else if (index == 9) inputsOptions();
        else systemOptions(false);
    }

    private void open(Intent intent) {
        try {
            ActivityOptions options=ActivityOptions.makeBasic();
            options.setLaunchDisplayId(android.view.Display.DEFAULT_DISPLAY);
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),options.toBundle());
        }
        catch (RuntimeException ex) {
            new AlertDialog.Builder(this).setTitle("暂时无法打开")
                    .setMessage("电视系统没有提供可用的入口，请从原桌面打开该应用。")
                    .setPositiveButton("知道了", null).show();
        }
    }

    void openSettings(boolean wifi) {
        Intent target = new Intent(wifi ? Settings.ACTION_WIFI_SETTINGS : Settings.ACTION_SETTINGS);
        if (target.resolveActivity(getPackageManager()) == null) {
            target = getPackageManager().getLaunchIntentForPackage("com.xiaomi.mitv.settings");
        }
        if (target != null) open(target);
        else Toast.makeText(this, "请使用遥控器的设置键打开电视设置", Toast.LENGTH_LONG).show();
    }

    private void allApps() {
        if(appsOverlay!=null)return;
        appsOverlay=new AppsOverlay(this,app->{
            if(app.activityInfo.packageName.equals("com.mitv.tvhome")){HomeGuardService.openStockHome();return;}
            open(new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(app.activityInfo.packageName,app.activityInfo.name)));
        });
        appsOverlay.show();
    }

    private void wallpaperOptions() {
        if(optionsOverlay!=null)return;optionsOverlay=new WallpaperOverlay(this);optionsOverlay.show();
    }

    private void inputsOptions(){if(optionsOverlay!=null)return;optionsOverlay=new InputsOverlay(this,InputsOverlay.available(this));optionsOverlay.show();}
    private void systemOptions(boolean wifi){if(optionsOverlay!=null)return;optionsOverlay=new SystemMenuOverlay(this,wifi);optionsOverlay.show();}
    String networkSummary(){ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);android.net.NetworkCapabilities c=cm==null?null:cm.getNetworkCapabilities(cm.getActiveNetwork());return c==null?"未连接网络":c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)?"Wi-Fi 已连接":"网络已连接";}
    String networkAddress(){ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);android.net.LinkProperties p=cm==null?null:cm.getLinkProperties(cm.getActiveNetwork());if(p!=null)for(android.net.LinkAddress a:p.getLinkAddresses())if(a.getAddress() instanceof java.net.Inet4Address&&!a.getAddress().isLoopbackAddress())return a.getAddress().getHostAddress();return "—";}
    void openInput(String inputId){
        // This firmware needs Xiaomi's numeric source, rather than the standard passthrough URI.
        String hardware=inputId.substring(inputId.lastIndexOf("/HW")+3);
        try{int port=Integer.parseInt(hardware);open(new Intent("com.xiaomi.mitv.tvplayer.EXTSRC_PLAY").setComponent(new ComponentName("com.xiaomi.mitv.tvplayer","com.xiaomi.mitv.tvplayer.ExternalSourceActivity")).putExtra("input",port+18));}
        catch(NumberFormatException unknown){open(new Intent(Intent.ACTION_VIEW).setDataAndType(android.media.tv.TvContract.buildChannelUriForPassthroughInput(inputId),"vnd.android.cursor.item/channel"));}
    }
    boolean dynamicWallpaper(){return getPreferences(0).getBoolean("video",true);}
    String selectedWallpaper(){return dynamicWallpaper()?getPreferences(0).getString("movie","hawaii"):"dusk";}
    Bitmap wallpaperCover(WallpaperCatalog.Item item){if(!item.dynamic)return wallpaper;if(covers.containsKey(item.id))return covers.get(item.id);int id=getResources().getIdentifier("wp_"+item.id.replace('-','_'),"drawable",getPackageName());Bitmap cover=id==0?null:BitmapFactory.decodeResource(getResources(),id);if(cover==null)return wallpaper;covers.put(item.id,cover);return cover;}
    boolean wallpaperRotation(){return getPreferences(0).getBoolean("wallpaper_rotation",false);}
    void setWallpaperRotation(boolean enabled){getPreferences(0).edit().putBoolean("wallpaper_rotation",enabled).apply();video.setRotation(enabled);}
    void chooseWallpaper(WallpaperCatalog.Item item){
        if(!item.available(this)){Toast.makeText(this,"壁纸素材尚未就绪",Toast.LENGTH_SHORT).show();return;}
        getPreferences(0).edit().putBoolean("video",item.dynamic).apply();
        if(item.dynamic){getPreferences(0).edit().putString("movie",item.id).apply();video.select(item.id);video.setPlaybackEnabled(true);if(resumed)video.start();}
        else{video.setPlaybackEnabled(false);scene.useStill();}
        scene.invalidate();
    }

    boolean online() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        return cm != null && cm.getActiveNetwork() != null;
    }
}
