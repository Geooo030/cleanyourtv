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
    static final String[] LABELS = {"云视听小电视", "Apple Music", "无线投屏", "云视听极光"};
    static final String[] PACKAGES = {"com.xiaodianshi.tv.yst", "com.gathin.glasshome",
            "com.xiaomi.mitv.smartshare", "com.ktcp.video"};
    GlassScene scene;
    MusicController music;
    private MusicVisibility musicVisibility;
    private final android.os.Handler musicHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private int focusedCard;
    private HomePreviewView homePreview;
    java.util.concurrent.FutureTask<java.util.List<AppsOverlay.Entry>> appsCatalog;
    private VideoBackdrop video;
    private Bitmap wallpaper;
    private ImageView still;
    private String loadedStillId="";
    private boolean resumed,controlCenterOpen,panelMoving;
    private AppsOverlay appsOverlay;
    private OptionsOverlay optionsOverlay;
    private FocusHintView homeHint;
    private HomeStatusView homeStatus;
    private final java.util.Map<String,Bitmap> covers=new java.util.HashMap<>();

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        wallpaper = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        loadStillWallpaper();
        FrameLayout root = new FrameLayout(this);
        still = new ImageView(this);
        still.setScaleType(ImageView.ScaleType.CENTER_CROP);
        still.setImageBitmap(wallpaper);
        root.addView(still, new FrameLayout.LayoutParams(-1, -1));
        video = new VideoBackdrop(this);
        video.setFallback(still);
        root.addView(video, new FrameLayout.LayoutParams(-1, -1));
        ImageView transitionCover=new ImageView(this);transitionCover.setScaleType(ImageView.ScaleType.CENTER_CROP);transitionCover.setAlpha(0);root.addView(transitionCover,new FrameLayout.LayoutParams(-1,-1));video.setCover(transitionCover);
        music=new MusicController(this);
        musicVisibility=new MusicVisibility(new MusicVisibility.Scheduler(){
            public void later(Runnable action,long delay){musicHandler.postDelayed(action,delay);}
            public void cancel(Runnable action){musicHandler.removeCallbacks(action);}
        },music::foreground);
        scene = new GlassScene(this, wallpaper);
        video.setMovieListener(id->{getPreferences(0).edit().putString("movie",id).apply();scene.invalidate();});
        root.addView(scene, new FrameLayout.LayoutParams(-1, -1));
        homeStatus=new HomeStatusView(this);
        root.addView(homeStatus,new FrameLayout.LayoutParams(-1,-1));
        int sw=getResources().getDisplayMetrics().widthPixels,sh=getResources().getDisplayMetrics().heightPixels;float previewUnit=Math.min(sw/1920f,sh/1080f);
        homePreview=new HomePreviewView(this,previewUnit,sw,sh);FrameLayout.LayoutParams previewBounds=new FrameLayout.LayoutParams(Math.round(HomePreviewView.W*previewUnit),Math.round(HomePreviewView.H*previewUnit));previewBounds.leftMargin=Math.round(HomePreviewView.X*previewUnit);previewBounds.topMargin=Math.round(HomePreviewView.Y*previewUnit);root.addView(homePreview,previewBounds);homePreview.focus(focusedCard);
        video.setFrameListener(frame -> {scene.useVideoFrame(frame);if(appsOverlay!=null)appsOverlay.updateBackdrop(frame);if(optionsOverlay!=null)optionsOverlay.updateBackdrop();if(homePreview!=null)homePreview.updateBackdrop();});
        homeHint=FocusHintView.attach(this,root,getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels);
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
        musicVisibility.resumed();
        updateHomeHintVisibility();
        if(appsCatalog!=null)appsCatalog.cancel(true);
        appsCatalog=AppsOverlay.warmApplications(this);
        scene.startClock();
        homeStatus.start();
        if(!dynamicWallpaper()){loadStillWallpaper();scene.useStill();}
        video.setPlaybackEnabled(getPreferences(0).getBoolean("video", true));
        video.setRotation(wallpaperRotation());video.select(getPreferences(0).getString("movie","hawaii"));
        updateSampling();
        video.start();
    }

    @Override protected void onPause() {
        resumed = false;
        homePreview.setHomeVisible(false);
        video.stop();
        scene.stopClock();
        homeStatus.stop();
        super.onPause();
    }

    @Override protected void onStop(){
        super.onStop();
        musicVisibility.stopped();
    }

    @Override protected void onDestroy() {
        if(appsOverlay!=null)appsOverlay.dismissImmediately();
        if(optionsOverlay!=null)optionsOverlay.dismissImmediately();
        if(appsCatalog!=null)appsCatalog.cancel(true);
        homePreview.close();musicVisibility.close();musicHandler.removeCallbacksAndMessages(null);music.close();video.close();
        scene.close();homeHint.release();homeStatus.close();
        for(Bitmap cover:covers.values())cover.recycle();covers.clear();
        wallpaper.recycle();
        super.onDestroy();
    }

    @Override public void onBackPressed() { scene.focusApp(0); }

    @Override public void dump(String prefix,java.io.FileDescriptor fd,java.io.PrintWriter writer,String[] args){
        super.dump(prefix,fd,writer,args);
        if(music!=null)writer.println(prefix+"GlassMusic: playing="+music.playing+" preparing="+music.busy()+" position="+music.position()+" duration="+music.duration()+" queue="+music.tracks.size()+" index="+music.index+" phase="+music.phase+" lyrics="+music.lyricLines.length()+" wordTimed="+music.wordTimed+" rhythm="+music.rhythmSamples()+" level="+music.level(0)+" backgroundPending="+musicVisibility.pending());
        if(homePreview!=null)writer.println(prefix+"GlassPreview: "+homePreview.snapshot());
        if(music!=null)writer.println(prefix+"GlassBookmark: track="+(music.previewTrack()==null?0:music.previewTrack().id)+" saved="+music.savedPosition());
        if(music!=null)writer.println(prefix+"GlassArtwork: "+music.artworkSnapshot());
        if(optionsOverlay instanceof MusicOverlay)writer.println(prefix+"GlassMusicPanel: "+((MusicOverlay)optionsOverlay).snapshot());
    }

    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        // Returning Home closes navigation only. The player, queue and playhead
        // remain owned by this singleTask Activity across the intent handoff.
        if(appsOverlay!=null)appsOverlay.dismissImmediately();
        if(optionsOverlay!=null)optionsOverlay.dismissImmediately();
        scene.focusApp(0);
    }

    void appsOverlayClosed(AppsOverlay overlay){
        if(appsOverlay==overlay)appsOverlay=null;
        scene.focusControl(4);updateHomeHintVisibility();updateSampling();
    }

    void showHomeHint(String value){if(homeHint!=null)homeHint.showHint(value);}
    private void updateHomeHintVisibility(){if(homeHint!=null)homeHint.setVisibility(appsOverlay==null&&optionsOverlay==null?View.VISIBLE:View.INVISIBLE);if(homePreview!=null)homePreview.setHomeVisible(resumed&&appsOverlay==null&&optionsOverlay==null);}
    void requestHomePreviewBackdrop(){video.requestGlassFrame();}

    void setAppsExpansion(float amount){float alpha=1-Math.max(0,Math.min(1,amount));scene.setAlpha(alpha);homeStatus.setAlpha(alpha);if(amount<.001f)scene.musicChanged();}
    private void updateSampling(){video.setSamplingPaused(panelMoving||controlCenterOpen||(appsOverlay==null&&optionsOverlay==null));}
    void setAppsMotion(boolean moving){panelMoving=moving;updateSampling();}
    void setControlCenterOpen(boolean open){
        controlCenterOpen=open;
        // The movie stays live; only its expensive readback and static glass scene are held.
        scene.setLayerType(open?View.LAYER_TYPE_HARDWARE:View.LAYER_TYPE_NONE,null);
        updateSampling();
    }
    void optionsClosed(OptionsOverlay overlay){if(optionsOverlay==overlay)optionsOverlay=null;if(overlay.focusReturn==1)scene.focusApp(1);else scene.focusControl(overlay.focusReturn);updateHomeHintVisibility();updateSampling();}

    void appFocused(int index){focusedCard=index;if(homePreview!=null)homePreview.focus(index);}
    private void musicOptions(){if(optionsOverlay!=null||appsOverlay!=null||!resumed)return;video.requestGlassFrame();optionsOverlay=new MusicOverlay(this);updateHomeHintVisibility();optionsOverlay.show();}

    boolean isInstalled(int index) {
        if (index == 1) return true;
        if (index == 2) return getPackageManager().getLaunchIntentForPackage(PACKAGES[2]) != null;
        return launchIntent(index) != null;
    }

    private Intent launchIntent(int index) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(PACKAGES[index]);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(PACKAGES[index]);
        return i;
    }

    void activate(int index) {
        if(index==1){musicOptions();return;}
        if (index == 8) {
            if(!HomeGuardService.openStockHome()) Toast.makeText(this,"主页键服务尚未就绪",Toast.LENGTH_LONG).show();
        } else if (index < 4) {
            Intent target = launchIntent(index);
            if (target == null) {
                new AlertDialog.Builder(this).setTitle(LABELS[index] + "尚未安装")
                        .setMessage(index == 3 ? "安装云视听极光电视版后，这张卡片会自动启用。"
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
        video.requestGlassFrame();
        appsOverlay=new AppsOverlay(this,app->{
            if(app.activityInfo.packageName.equals("com.mitv.tvhome")){HomeGuardService.openStockHome();return;}
            open(new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(app.activityInfo.packageName,app.activityInfo.name)));
        });
        updateHomeHintVisibility();appsOverlay.show();
    }

    private void wallpaperOptions() {
        if(optionsOverlay!=null)return;video.requestGlassFrame();optionsOverlay=new WallpaperOverlay(this);updateHomeHintVisibility();optionsOverlay.show();
    }

    private void inputsOptions(){if(optionsOverlay!=null)return;video.requestGlassFrame();optionsOverlay=new InputsOverlay(this,InputsOverlay.available(this));updateHomeHintVisibility();optionsOverlay.show();}
    private void systemOptions(boolean wifi){if(optionsOverlay!=null)return;video.requestGlassFrame();optionsOverlay=wifi?new SystemMenuOverlay(this,true):new ControlCenterOverlay(this);updateHomeHintVisibility();optionsOverlay.show();}
    String networkSummary(){ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);android.net.NetworkCapabilities c=cm==null?null:cm.getNetworkCapabilities(cm.getActiveNetwork());return c==null?"未连接网络":c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)?"Wi-Fi 已连接":"网络已连接";}
    String networkAddress(){ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);android.net.LinkProperties p=cm==null?null:cm.getLinkProperties(cm.getActiveNetwork());if(p!=null)for(android.net.LinkAddress a:p.getLinkAddresses())if(a.getAddress() instanceof java.net.Inet4Address&&!a.getAddress().isLoopbackAddress())return a.getAddress().getHostAddress();return "—";}
    void openInput(String inputId){
        // This firmware needs Xiaomi's numeric source, rather than the standard passthrough URI.
        String hardware=inputId.substring(inputId.lastIndexOf("/HW")+3);
        try{int port=Integer.parseInt(hardware);open(new Intent("com.xiaomi.mitv.tvplayer.EXTSRC_PLAY").setComponent(new ComponentName("com.xiaomi.mitv.tvplayer","com.xiaomi.mitv.tvplayer.ExternalSourceActivity")).putExtra("input",port+18));}
        catch(NumberFormatException unknown){open(new Intent(Intent.ACTION_VIEW).setDataAndType(android.media.tv.TvContract.buildChannelUriForPassthroughInput(inputId),"vnd.android.cursor.item/channel"));}
    }
    boolean dynamicWallpaper(){return getPreferences(0).getBoolean("video",true);}
    private String selectedStillWallpaper(){WallpaperCatalog.Item item=WallpaperCatalog.find(getPreferences(0).getString("still","dusk"));return item.dynamic?"dusk":item.id;}
    String selectedWallpaper(){return dynamicWallpaper()?getPreferences(0).getString("movie","hawaii"):selectedStillWallpaper();}
    private void loadStillWallpaper(){
        String id=selectedStillWallpaper();if(id.equals(loadedStillId))return;
        Bitmap original=BitmapFactory.decodeResource(getResources(),WallpaperCatalog.find(id).imageResource(this));
        if(original==null)return;
        float crop=Math.max(1920f/original.getWidth(),1080f/original.getHeight());
        float w=crop*original.getWidth(),h=crop*original.getHeight();
        new Canvas(wallpaper).drawBitmap(original,null,new RectF((1920-w)/2,(1080-h)/2,(1920+w)/2,(1080+h)/2),new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
        original.recycle();loadedStillId=id;
        if(still!=null)still.invalidate();
        if(scene!=null)scene.updateStill(wallpaper);
    }
    Bitmap wallpaperCover(WallpaperCatalog.Item item){
        if(covers.containsKey(item.id))return covers.get(item.id);
        int id=item.imageResource(this);if(id==0)return wallpaper;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        BitmapFactory.decodeResource(getResources(),id,options);options.inJustDecodeBounds=false;options.inSampleSize=1;
        while(options.outWidth/options.inSampleSize>640||options.outHeight/options.inSampleSize>432)options.inSampleSize*=2;
        Bitmap cover=BitmapFactory.decodeResource(getResources(),id,options);if(cover==null)return wallpaper;
        covers.put(item.id,cover);return cover;
    }
    boolean wallpaperRotation(){return getPreferences(0).getBoolean("wallpaper_rotation",false);}
    void setWallpaperRotation(boolean enabled){getPreferences(0).edit().putBoolean("wallpaper_rotation",enabled).apply();video.setRotation(enabled);}
    void chooseWallpaper(WallpaperCatalog.Item item){
        if(!item.available(this)){Toast.makeText(this,"壁纸素材尚未就绪",Toast.LENGTH_SHORT).show();return;}
        getPreferences(0).edit().putBoolean("video",item.dynamic).apply();
        if(item.dynamic){getPreferences(0).edit().putString("movie",item.id).apply();video.select(item.id);video.setPlaybackEnabled(true);if(resumed)video.start();}
        else{getPreferences(0).edit().putString("still",item.id).apply();loadStillWallpaper();video.setPlaybackEnabled(false);scene.useStill();if(optionsOverlay!=null)optionsOverlay.updateBackdrop();}
        scene.invalidate();
    }

    boolean online() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        return cm != null && cm.getActiveNetwork() != null;
    }
}
