package com.gathin.glasshome;

import android.app.Activity;
import android.app.AlertDialog;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Four-app, remote-first home. The stock Xiaomi launcher remains installed and enabled. */
public final class GlassHomeActivity extends Activity {
    static final String[] LABELS = {"云视听小电视", "网易云音乐", "无线投屏", "Netflix"};
    static final String[] PACKAGES = {"com.xiaodianshi.tv.yst", "com.netease.cloudmusic.tv",
            "com.xiaomi.mitv.smartshare", "com.netflix.ninja"};
    GlassScene scene;
    private VideoBackdrop video;
    private Bitmap wallpaper;
    private boolean resumed;

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
        scene = new GlassScene(this, wallpaper);
        root.addView(scene, new FrameLayout.LayoutParams(-1, -1));
        video.setFrameListener(frame -> scene.useVideoFrame(frame));
        setContentView(root);
        // Create the app-specific directory so ADB can copy a local video without media permissions.
        getExternalFilesDir(null);
        scene.post(() -> scene.focusApp(0));
    }

    @Override protected void onResume() {
        super.onResume();
        HomeRouting.ensure(this,"home resumed");
        resumed = true;
        scene.startClock();
        video.setPlaybackEnabled(getPreferences(0).getBoolean("video", true));
        video.start();
    }

    @Override protected void onPause() {
        resumed = false;
        video.stop();
        scene.stopClock();
        super.onPause();
    }

    @Override protected void onDestroy() {
        video.close();
        scene.close();
        wallpaper.recycle();
        super.onDestroy();
    }

    @Override public void onBackPressed() { scene.focusApp(0); }

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
        else if (index == 6) openSettings(true);
        else openSettings(false);
    }

    private void open(Intent intent) {
        try { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (RuntimeException ex) {
            new AlertDialog.Builder(this).setTitle("暂时无法打开")
                    .setMessage("电视系统没有提供可用的入口，请从原桌面打开该应用。")
                    .setPositiveButton("知道了", null).show();
        }
    }

    private void openSettings(boolean wifi) {
        Intent target = new Intent(wifi ? Settings.ACTION_WIFI_SETTINGS : Settings.ACTION_SETTINGS);
        if (target.resolveActivity(getPackageManager()) == null) {
            target = getPackageManager().getLaunchIntentForPackage("com.xiaomi.mitv.settings");
        }
        if (target != null) open(target);
        else Toast.makeText(this, "请使用遥控器的设置键打开电视设置", Toast.LENGTH_LONG).show();
    }

    private void allApps() {
        Map<String, ResolveInfo> items = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo info : getPackageManager().queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                if (!info.activityInfo.packageName.equals(getPackageName())) items.putIfAbsent(info.activityInfo.packageName, info);
            }
        }
        List<ResolveInfo> apps = new ArrayList<>(items.values());
        Collections.sort(apps, (a,b) -> a.loadLabel(getPackageManager()).toString()
                .compareTo(b.loadLabel(getPackageManager()).toString()));
        String[] labels = new String[apps.size()];
        for (int i=0; i<apps.size(); i++) labels[i] = apps.get(i).loadLabel(getPackageManager()).toString();
        new AlertDialog.Builder(this).setTitle("全部应用").setItems(labels, (dialog, which) -> {
            ResolveInfo app = apps.get(which);
            open(new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(app.activityInfo.packageName, app.activityInfo.name)));
        }).setNegativeButton("返回", null).show();
    }

    private void wallpaperOptions() {
        boolean dynamic = getPreferences(0).getBoolean("video", true);
        new AlertDialog.Builder(this).setTitle("壁纸")
                .setSingleChoiceItems(new String[]{"夏威夷海岸 · Apple TV 4K 航拍", "暮色山海 · 静态壁纸"}, dynamic ? 0 : 1, (dialog, which) -> {
                    boolean enabled = which == 0;
                    getPreferences(0).edit().putBoolean("video", enabled).apply();
                    video.setPlaybackEnabled(enabled);
                    if (!enabled) scene.useStill();
                    else if (resumed) video.start();
                    dialog.dismiss();
                }).setNegativeButton("返回", null).show();
    }

    boolean online() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        return cm != null && cm.getActiveNetwork() != null;
    }
}
