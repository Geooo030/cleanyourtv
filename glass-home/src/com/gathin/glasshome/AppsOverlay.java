package com.gathin.glasshome;

import android.app.Dialog;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

/** Modal icon grid on the TV. The launcher and its local wallpaper remain underneath. */
final class AppsOverlay extends Dialog {
    interface Launch { void open(ResolveInfo app); }
    private final GlassHomeActivity owner;
    private final Launch launch;
    private final ExecutorService loader=Executors.newSingleThreadExecutor();
    private final List<Entry> entries=new ArrayList<>();
    private final Typeface regular,medium;
    private FrameLayout panel;
    private FocusHintView focusHint;
    private SmoothAppsGrid grid;
    private FrameLayout iconContent;
    private TextView close;
    private int selectedPosition;
    private final List<AppTile> tiles=new ArrayList<>();
    private TextView count,page;
    private float unit;
    private boolean closing,disposed;
    private float expansion;
    private android.animation.ValueAnimator motion;
    private Runnable pendingLaunch;

    static final class Entry {
        final ResolveInfo info; final String label; final Drawable icon;
        Entry(ResolveInfo info,String label,Drawable icon){this.info=info;this.label=label;this.icon=icon;}
    }

    AppsOverlay(GlassHomeActivity owner,Launch launch){
        super(owner);this.owner=owner;this.launch=launch;
        regular=GlassFonts.create(owner,400);medium=GlassFonts.create(owner,500);
        setOnDismissListener(d->{
            disposed=true;if(focusHint!=null)focusHint.release();loader.shutdownNow();if(grid!=null)grid.stopMotion();
            if(motion!=null){motion.removeAllListeners();motion.cancel();}
            owner.setAppsExpansion(0);owner.setAppsMotion(false);
            owner.appsOverlayClosed(this);
            Runnable action=pendingLaunch;pendingLaunch=null;if(action!=null)action.run();
        });
    }

    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);requestWindowFeature(Window.FEATURE_NO_TITLE);
        int screenW=owner.getWindow().getDecorView().getWidth(),screenH=owner.getWindow().getDecorView().getHeight();
        if(screenW==0||screenH==0){screenW=owner.getResources().getDisplayMetrics().widthPixels;screenH=owner.getResources().getDisplayMetrics().heightPixels;}
        unit=Math.min(screenW/1920f,screenH/1080f);
        final float s=unit;
        FrameLayout root=new FrameLayout(owner);
        root.setOnClickListener(v->{if(expansion>=.999f)dismiss();});
        panel=new FrameLayout(owner);
        panel.setOnClickListener(v->{});
        panel.setBackground(new GlassPanelDrawable(s,screenW,screenH,owner.scene));
        FrameLayout.LayoutParams panelParams=new FrameLayout.LayoutParams(px(1510),px(868),Gravity.CENTER);
        root.addView(panel,panelParams);
        focusHint=FocusHintView.attach(owner,root,screenW,screenH);
        focusHint.showHint("全部应用 · 方向键选择，确认打开，返回关闭");
        TextView title=label("全部应用",34,Color.WHITE,medium);
        panel.addView(title,box(52,40,600,55));
        count=label("正在整理应用…",17,0xaaffffff,regular);
        panel.addView(count,box(54,105,1000,30));
        close=label("×",38,0xe6ffffff,regular);
        close.setGravity(Gravity.CENTER);close.setId(View.generateViewId());
        close.setContentDescription("关闭全部应用");close.setFocusable(true);close.setClickable(true);
        close.setBackground(closeBackground(false));
        close.setOnFocusChangeListener((v,f)->{close.setBackground(closeBackground(f));close.setTextColor(f?0xff14242e:0xe6ffffff);if(f)focusHint.showHint("全部应用 · 确认或返回关闭");});
        close.setOnClickListener(v->dismiss());
        panel.addView(close,box(1406,42,52,52));
        grid=new SmoothAppsGrid();grid.setId(View.generateViewId());
        grid.setVerticalScrollBarEnabled(false);grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        iconContent=new FrameLayout(owner);iconContent.setClipChildren(false);iconContent.setClipToPadding(false);
        grid.addView(iconContent,new android.widget.ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,px(606)));
        panel.addView(grid,box(35,160,1440,610));
        page=label("",15,0x88ffffff,regular);page.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        panel.addView(page,box(1236,801,220,30));
        setContentView(root);
        Window window=getWindow();
        if(window!=null){
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.08f);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
            window.setWindowAnimations(0);
            window.getDecorView().setSystemUiVisibility(owner.getWindow().getDecorView().getSystemUiVisibility());
        }
        // Expansion grows from the dock entry using the same glass material as the dock.
        panel.setPivotX(px(80));panel.setPivotY(px(868));
        applyExpansion(0);
        loadApplications();
    }

    private void loadApplications(){
        loader.execute(()->{
            List<Entry> loaded;
            try{loaded=owner.appsCatalog.get();}catch(Exception unavailable){return;}
            owner.runOnUiThread(()->{
                if(disposed||closing)return;
                entries.addAll(loaded);populateIcons();
                count.setText(loaded.size()+" 个应用");
                grid.post(()->{
                    if(disposed||closing)return;
                    if(!loaded.isEmpty()){tiles.get(0).requestFocusFromTouch();updateSelection();}
                    // Lay out and upload the icon layer before its first moving frame.
                    panel.setLayerType(View.LAYER_TYPE_HARDWARE,null);panel.buildLayer();animateExpansion(true);
                });
                if(loaded.isEmpty())count.setText("暂未找到可打开的应用");
            });
        });
    }

    static FutureTask<List<Entry>> warmApplications(GlassHomeActivity owner){
        FutureTask<List<Entry>> task=new FutureTask<>(()->{
            Map<String,ResolveInfo> unique=new LinkedHashMap<>();
            for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
                for(ResolveInfo info:owner.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)){
                    if(!info.activityInfo.packageName.equals(owner.getPackageName()))unique.putIfAbsent(info.activityInfo.packageName,info);
                }
            }
            List<Entry> loaded=new ArrayList<>();
            for(ResolveInfo info:unique.values()){
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                try{
                    Drawable source=info.loadIcon(owner.getPackageManager());
                    android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(192,192,android.graphics.Bitmap.Config.ARGB_8888);
                    source.setBounds(0,0,192,192);source.draw(new Canvas(bitmap));
                    Drawable icon=new android.graphics.drawable.BitmapDrawable(owner.getResources(),bitmap);
                    loaded.add(new Entry(info,info.loadLabel(owner.getPackageManager()).toString(),icon));
                }catch(RuntimeException ignored){}
            }
            Collator collator=Collator.getInstance(Locale.CHINA);
            loaded.sort((a,b)->collator.compare(a.label,b.label));
            return loaded;
        });
        new Thread(task,"glass-app-catalog").start();return task;
    }

    private void populateIcons(){
        int rows=(entries.size()+5)/6;iconContent.getLayoutParams().height=px(Math.max(610,38+rows*180+Math.max(0,rows-1)*16));iconContent.requestLayout();
        for(int i=0;i<entries.size();i++){
            final int position=i;AppTile tile=new AppTile();tile.bind(entries.get(i));tile.setId(View.generateViewId());tile.setFocusable(true);tile.setClickable(true);
            iconContent.addView(tile,box(16+(i%6)*(1422f/6),14+(i/6)*196,1422f/6-22,180));tiles.add(tile);
            tile.setOnFocusChangeListener((v,focused)->{if(focused){selectedPosition=position;grid.reveal(position);focusHint.showHint(entries.get(position).label+" · 确认打开，返回关闭");}tile.highlight(focused);updateSelection();});
            tile.setOnClickListener(v->launchAt(position));tile.setOnKeyListener((v,key,event)->{
                if(key<KeyEvent.KEYCODE_DPAD_UP||key>KeyEvent.KEYCODE_DPAD_RIGHT)return false;
                if(event.getAction()==KeyEvent.ACTION_DOWN)moveSelection(key);return true;
            });
        }
        if(!tiles.isEmpty())close.setNextFocusDownId(tiles.get(0).getId());
    }
    private void launchAt(int position){if(closing||position<0||position>=entries.size())return;ResolveInfo app=entries.get(position).info;pendingLaunch=()->launch.open(app);dismiss();}
    private void moveSelection(int key){
        int row=selectedPosition/6,col=selectedPosition%6,target=selectedPosition;
        if(key==KeyEvent.KEYCODE_DPAD_UP){if(row==0){close.requestFocus();return;}target=selectedPosition-6;}
        else if(key==KeyEvent.KEYCODE_DPAD_DOWN){if((row+1)*6>=entries.size())return;target=Math.min(entries.size()-1,selectedPosition+6);}
        else if(key==KeyEvent.KEYCODE_DPAD_LEFT){if(col>0)target--;}
        else if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(col<5&&target+1<entries.size())target++;}
        if(target!=selectedPosition)tiles.get(target).requestFocus();
    }
    private void updateSelection(){if(page!=null)page.setText(entries.isEmpty()?"":(selectedPosition/6+1)+" / "+Math.max(1,(entries.size()+5)/6)+" 行");}

    private final class SmoothAppsGrid extends android.widget.ScrollView {
        private android.animation.ValueAnimator scrollMotion;
        private int destination;
        SmoothAppsGrid(){super(owner);setClipToPadding(false);setSmoothScrollingEnabled(false);}
        // Focus follows the remote immediately; the viewport is moved only by our eased timeline.
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect rect){return 0;}
        void reveal(int position){
            int top=px(14+(position/6)*196),bottom=top+px(180);
            int target=destination;
            if(top<target+px(14))target=top-px(14);
            else if(bottom>target+getHeight()-px(20))target=Math.max(0,position/6-2)*px(196);
            slideTo(target);
        }
        void slideTo(int target){
            target=Math.max(0,Math.min(target,Math.max(0,iconContent.getHeight()-getHeight())));
            if(destination==target&&scrollMotion!=null&&scrollMotion.isRunning())return;
            if(scrollMotion!=null){scrollMotion.removeAllListeners();scrollMotion.cancel();}
            destination=target;
            if(getScrollY()==target){owner.setAppsMotion(false);return;}
            if(!android.animation.ValueAnimator.areAnimatorsEnabled()){scrollTo(0,target);return;}
            owner.setAppsMotion(true);scrollMotion=android.animation.ValueAnimator.ofInt(getScrollY(),target);scrollMotion.setDuration(320);scrollMotion.setInterpolator(new PathInterpolator(.22f,1f,.36f,1f));
            scrollMotion.addUpdateListener(a->scrollTo(0,(int)a.getAnimatedValue()));
            scrollMotion.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){owner.setAppsMotion(false);}});scrollMotion.start();
        }
        void stopMotion(){if(scrollMotion!=null){scrollMotion.removeAllListeners();scrollMotion.cancel();}owner.setAppsMotion(false);}
        @Override public boolean onGenericMotionEvent(android.view.MotionEvent e){if(e.getAction()==android.view.MotionEvent.ACTION_SCROLL){float amount=e.getAxisValue(android.view.MotionEvent.AXIS_VSCROLL);if(amount!=0){slideTo(destination-Math.round(amount*px(196)));return true;}}return super.onGenericMotionEvent(e);}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)stopMotion();boolean handled=super.onTouchEvent(e);destination=getScrollY();return handled;}
    }

    @Override public void onBackPressed(){dismiss();}
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(key==KeyEvent.KEYCODE_MENU)return true;
        return super.onKeyDown(key,event);
    }
    @Override public void dismiss(){
        if(closing||disposed)return;closing=true;
        if(panel==null){super.dismiss();return;}
        animateExpansion(false);
    }
    private void applyExpansion(float value){
        expansion=value;panel.setAlpha(Math.min(1,value*1.7f));
        panel.setScaleX(.9f+.1f*value);panel.setScaleY(.9f+.1f*value);
        panel.setTranslationY(px(20)*(1-value));
        owner.setAppsExpansion(value);
    }
    private void animateExpansion(boolean opening){
        if(motion!=null){motion.removeAllListeners();motion.cancel();}
        owner.setAppsMotion(true);
        if(!android.animation.ValueAnimator.areAnimatorsEnabled()){applyExpansion(opening?1:0);owner.setAppsMotion(false);if(!opening)super.dismiss();return;}
        motion=android.animation.ValueAnimator.ofFloat(expansion,opening?1:0);
        panel.setLayerType(View.LAYER_TYPE_HARDWARE,null);
        motion.setDuration(opening?360:pendingLaunch==null?240:180);
        motion.setInterpolator(new PathInterpolator(.2f,0f,.1f,1f));
        motion.addUpdateListener(a->applyExpansion((float)a.getAnimatedValue()));
        motion.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator animation){panel.setLayerType(View.LAYER_TYPE_NONE,null);owner.setAppsMotion(false);if(!opening)AppsOverlay.super.dismiss();}});
        motion.start();
    }
    void dismissImmediately(){if(disposed)return;closing=true;pendingLaunch=null;if(motion!=null){motion.removeAllListeners();motion.cancel();}super.dismiss();}
    void updateBackdrop(android.graphics.Bitmap frame){if(!disposed&&panel!=null)panel.getBackground().invalidateSelf();}
    private int px(float value){return Math.round(value*unit);}
    private Drawable closeBackground(boolean focused){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(focused?0xffe6edf0:0x0effffff);d.setCornerRadius(px(26));d.setStroke(Math.max(1,px(1)),0x25ffffff);return d;}
    private FrameLayout.LayoutParams box(float x,float y,float w,float h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(px(w),px(h));p.leftMargin=px(x);p.topMargin=px(y);return p;}
    private TextView label(String value,float size,int color,Typeface font){TextView v=new TextView(owner);v.setText(value);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size));v.setTypeface(font);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);v.setIncludeFontPadding(false);v.setShadowLayer(px(4),0,px(1),0x55000000);return v;}

    private final class AppTile extends FrameLayout {
        private final android.widget.ImageView icon;
        private final TextView text;
        private boolean active;
        private Entry bound;
        AppTile(){
            super(owner);setClipChildren(false);setClipToPadding(false);setFocusable(false);
            icon=new android.widget.ImageView(owner);icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            icon.setClipToOutline(true);icon.setOutlineProvider(new android.view.ViewOutlineProvider(){@Override public void getOutline(View v,android.graphics.Outline o){o.setRoundRect(0,0,v.getWidth(),v.getHeight(),px(27));}});
            FrameLayout.LayoutParams imageParams=new FrameLayout.LayoutParams(px(120),px(120),Gravity.TOP|Gravity.CENTER_HORIZONTAL);imageParams.topMargin=px(11);addView(icon,imageParams);
            text=label("",18,0xdfffffff,regular);text.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);text.setMaxLines(2);text.setEllipsize(TextUtils.TruncateAt.END);
            FrameLayout.LayoutParams labelParams=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,px(43),Gravity.TOP);labelParams.topMargin=px(137);labelParams.leftMargin=px(2);labelParams.rightMargin=px(2);addView(text,labelParams);
        }
        void bind(Entry item){
            if(bound==item)return;bound=item;active=false;icon.animate().cancel();icon.setScaleX(1);icon.setScaleY(1);
            icon.setImageDrawable(item.icon);text.setText(item.label);setContentDescription(item.label);
        }
        void highlight(boolean selected){
            if(active==selected)return;active=selected;
            icon.animate().cancel();icon.animate().withLayer().scaleX(selected?1.12f:1).scaleY(selected?1.12f:1).setDuration(210).setInterpolator(new PathInterpolator(.16f,1f,.3f,1f)).start();
            text.setTextColor(selected?Color.WHITE:0xdfffffff);invalidate();
        }
    }

}
