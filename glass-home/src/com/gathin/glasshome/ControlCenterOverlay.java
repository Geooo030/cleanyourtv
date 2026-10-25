package com.gathin.glasshome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** A remote-first control center anchored to the top-right Settings button. */
final class ControlCenterOverlay extends OptionsOverlay {
    private static final int INK=0xff17212b;
    private static final float DIAL_SIZE=336;
    private static final PathInterpolator EASE=new PathInterpolator(.16f,1f,.3f,1f);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private final Paint iconPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable wallpaperIcon,settingsIcon;
    private final QuickButton[] shortcuts=new QuickButton[6];
    private final String[] titles={"待机","电源选项","刷新桌面","设备信息","动态壁纸","系统设置"};
    private TextView time,date;
    private LevelSlider brightness,volume;
    private LevelSlider expandedSlider;
    private FrameLayout detailLayer,dialPanel;
    private LevelDial dial;
    private ValueAnimator detailMotion;
    private float detailExpansion;
    private boolean dialClosing;
    private InputCard input;
    private View shortcutFocus;
    private View glassPlate;
    private boolean glassRefreshed;
    private String lastMinute="";
    private final Runnable refresh=new Runnable(){@Override public void run(){
        refreshValues();handler.postDelayed(this,1000);
    }};

    ControlCenterOverlay(GlassHomeActivity owner){
        super(owner,7,false,1104,210,704,674);
        audio=(AudioManager)owner.getSystemService(Context.AUDIO_SERVICE);
        wallpaperIcon=owner.getDrawable(R.drawable.ic_wallpaper).mutate();
        settingsIcon=owner.getDrawable(R.drawable.ic_settings).mutate();
        setOnShowListener(dialog->shortcuts[0].requestFocusFromTouch());
    }
    @Override float motionPivotX(){return w-68;}
    @Override float collapsedScale(){return .955f;}
    @Override float collapsedOffset(){return -18;}
    @Override long openingDuration(){return 360;}
    @Override long closingDuration(){return 240;}
    @Override int restingLayer(){return View.LAYER_TYPE_NONE;}
    @Override android.animation.TimeInterpolator panelEasing(){return EASE;}

    @Override void buildPanel(){
        owner.setControlCenterOpen(true);
        FrameLayout.LayoutParams position=(FrameLayout.LayoutParams)panel.getLayoutParams();
        View decor=owner.getWindow().getDecorView();
        panel.setBackground(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        View plate=new View(owner);
        glassPlate=plate;
        plate.setBackground(new GlassPanelDrawable(unit,decor.getWidth(),decor.getHeight(),owner.scene)
                .at(position.leftMargin,position.topMargin).controlCenter());
        plate.setLayerType(View.LAYER_TYPE_HARDWARE,null);
        panel.addView(plate,new FrameLayout.LayoutParams(-1,-1));
        panel.setClipChildren(false);panel.setClipToPadding(false);
        panel.setFocusable(false);((View)panel.getParent()).setFocusable(false);
        panel.setContentDescription("控制中心");
        time=label("",51,Color.WHITE,medium);panel.addView(time,box(38,27,178,72));
        date=label("",22,0xebffffff,medium);panel.addView(date,box(218,31,444,68));
        shortcutFocus=new View(owner);shortcutFocus.setBackground(surface(0xefffffff,0xaaffffff,px(44)));shortcutFocus.setAlpha(0);shortcutFocus.setLayerType(View.LAYER_TYPE_HARDWARE,null);
        panel.addView(shortcutFocus,box(38,126,88,88));
        for(int i=0;i<shortcuts.length;i++){
            shortcuts[i]=new QuickButton(i);
            panel.addView(shortcuts[i],box(32+i*108,120,100,100));
        }
        brightness=new LevelSlider(true);volume=new LevelSlider(false);input=new InputCard();
        panel.addView(brightness,box(36,278,632,96));
        panel.addView(volume,box(36,394,632,96));
        panel.addView(input,box(36,512,632,122));
        for(int i=0;i<shortcuts.length;i++){
            shortcuts[i].setNextFocusLeftId(shortcuts[Math.max(0,i-1)].getId());
            shortcuts[i].setNextFocusRightId(shortcuts[Math.min(5,i+1)].getId());
            shortcuts[i].setNextFocusUpId(shortcuts[i].getId());
            shortcuts[i].setNextFocusDownId(brightness.getId());
        }
        brightness.setNextFocusUpId(shortcuts[0].getId());brightness.setNextFocusDownId(volume.getId());
        volume.setNextFocusUpId(brightness.getId());volume.setNextFocusDownId(input.getId());
        input.setNextFocusUpId(volume.getId());input.setNextFocusDownId(input.getId());
        refreshValues();handler.postDelayed(refresh,1000);
        panel.post(()->shortcuts[0].requestFocus());
    }

    private void refreshValues(){
        String now=new SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date());
        if(!now.equals(lastMinute)){
            lastMinute=now;time.setText(now);
            date.setText(new SimpleDateFormat("M月d日  EEEE",Locale.CHINA).format(new Date()));
        }
        if(!brightness.dragging)brightness.setLevel(readBrightness());
        if(!volume.dragging)volume.setLevel(readVolume());
    }
    private int levelStep(boolean light){
        return light?2:Math.max(1,(int)Math.ceil(100f/Math.max(1,audio==null?100:audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))));
    }
    private void buildDial(){
        FrameLayout root=(FrameLayout)panel.getParent();
        FrameLayout.LayoutParams position=(FrameLayout.LayoutParams)panel.getLayoutParams();
        detailLayer=new FrameLayout(owner);detailLayer.setFocusable(false);
        detailLayer.setOnClickListener(v->hideDial());
        dialPanel=new FrameLayout(owner);dialPanel.setFocusable(false);
        int left=position.leftMargin+px((w-DIAL_SIZE)/2),top=position.topMargin+px(120+(404-DIAL_SIZE)/2);
        FrameLayout.LayoutParams circle=new FrameLayout.LayoutParams(px(DIAL_SIZE),px(DIAL_SIZE));circle.leftMargin=left;circle.topMargin=top;
        View decor=owner.getWindow().getDecorView();
        View plate=new View(owner);plate.setBackground(new GlassPanelDrawable(unit,decor.getWidth(),decor.getHeight(),owner.scene).at(left,top).circular());
        plate.setLayerType(View.LAYER_TYPE_HARDWARE,null);dialPanel.addView(plate,new FrameLayout.LayoutParams(-1,-1));
        dial=new LevelDial();dialPanel.addView(dial,new FrameLayout.LayoutParams(-1,-1));
        detailLayer.addView(dialPanel,circle);
        root.addView(detailLayer,new FrameLayout.LayoutParams(-1,-1));
    }
    private void showDial(LevelSlider source){
        if(expandedSlider!=null||!settleOpening())return;
        if(detailLayer==null)buildDial();
        expandedSlider=source;dialClosing=false;source.dragging=false;
        panel.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        detailLayer.setVisibility(View.VISIBLE);dial.updateDescription();dial.requestFocusFromTouch();
        hint((source.lightControl?"亮度":"音量")+" · 方向键调节，确认或返回收起");
        animateDial(true);
    }
    private void hideDial(){
        if(expandedSlider==null||dialClosing)return;
        dialClosing=true;panel.setVisibility(View.VISIBLE);animateDial(false);
    }
    private void applyDial(float value){
        detailExpansion=value;
        panel.setAlpha(1-value);panel.setScaleX(1-.035f*value);panel.setScaleY(1-.035f*value);panel.setTranslationY(-px(12)*value);
        dialPanel.setAlpha(value);dialPanel.setScaleX(.88f+.12f*value);dialPanel.setScaleY(.88f+.12f*value);dialPanel.setTranslationY(px(18)*(1-value));
    }
    private void cancelDetailMotion(){
        if(detailMotion!=null){detailMotion.removeAllListeners();detailMotion.removeAllUpdateListeners();detailMotion.cancel();detailMotion=null;}
    }
    private void finishDial(boolean opening){
        panel.setLayerType(View.LAYER_TYPE_NONE,null);dialPanel.setLayerType(View.LAYER_TYPE_NONE,null);owner.setAppsMotion(false);
        if(opening)panel.setVisibility(View.INVISIBLE);
        else{
            LevelSlider source=expandedSlider;expandedSlider=null;dialClosing=false;
            detailLayer.setVisibility(View.GONE);panel.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            if(source!=null)source.requestFocusFromTouch();
        }
    }
    private void animateDial(boolean opening){
        cancelDetailMotion();owner.setAppsMotion(true);applyDial(detailExpansion);
        if(!ValueAnimator.areAnimatorsEnabled()){applyDial(opening?1:0);finishDial(opening);return;}
        panel.setLayerType(View.LAYER_TYPE_HARDWARE,null);dialPanel.setLayerType(View.LAYER_TYPE_HARDWARE,null);
        detailMotion=ValueAnimator.ofFloat(detailExpansion,opening?1:0);detailMotion.setDuration(opening?280:200);detailMotion.setInterpolator(EASE);
        detailMotion.addUpdateListener(a->applyDial((float)a.getAnimatedValue()));
        detailMotion.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){finishDial(opening);}});
        detailMotion.start();
    }
    // This window keeps its cached glass plate; the wallpaper underneath remains live.
    @Override void updateBackdrop(){if(!glassRefreshed&&glassPlate!=null){glassRefreshed=true;glassPlate.invalidate();}}
    @Override public void onBackPressed(){if(expandedSlider!=null)hideDial();else super.onBackPressed();}
    @Override public void dismiss(){if(expandedSlider!=null&&isShowing())hideDial();else super.dismiss();}
    private int readBrightness(){
        int raw=Settings.System.getInt(owner.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,102);
        return Math.max(0,Math.min(100,Math.round(raw*100f/255)));
    }
    private int readVolume(){
        if(audio==null)return 0;
        int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return audio.isStreamMute(AudioManager.STREAM_MUSIC)?0:Math.round(audio.getStreamVolume(AudioManager.STREAM_MUSIC)*100f/Math.max(1,max));
    }
    private boolean changeLevel(boolean light,int percent){
        try{
            if(light){
                if(!Settings.System.canWrite(owner)){
                    Toast.makeText(owner,"请在系统设置中允许玻璃桌面修改亮度",Toast.LENGTH_SHORT).show();return false;
                }
                Settings.System.putInt(owner.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                return Settings.System.putInt(owner.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,Math.round(Math.max(5,percent)*255f/100));
            }
            if(audio==null||audio.isVolumeFixed()){
                Toast.makeText(owner,"当前音频输出使用固定音量，请通过音响调节",Toast.LENGTH_SHORT).show();return false;
            }
            int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.round(percent*max/100f),0);
            if(percent>0&&audio.isStreamMute(AudioManager.STREAM_MUSIC))audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_UNMUTE,0);
            return true;
        }catch(RuntimeException error){
            Toast.makeText(owner,light?"暂时无法调节亮度，请使用系统设置":"暂时无法调节音量",Toast.LENGTH_SHORT).show();return false;
        }
    }
    private void runAfterClose(Runnable action){afterClose=action;dismiss();}
    private void activateShortcut(int index){
        if(index==0)runAfterClose(()->{
            if(!HomeGuardService.sleepScreen())Toast.makeText(owner,"请使用遥控器电源键待机",Toast.LENGTH_SHORT).show();
        });
        else if(index==1)runAfterClose(()->{
            if(!HomeGuardService.showPowerMenu())Toast.makeText(owner,"请长按遥控器电源键打开电源选项",Toast.LENGTH_SHORT).show();
        });
        else if(index==2)runAfterClose(owner::recreate);
        else if(index==3)runAfterClose(()->{
            Intent about=new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS);
            if(about.resolveActivity(owner.getPackageManager())!=null)owner.startActivity(about);
            else owner.openSettings(false);
        });
        else if(index==4)runAfterClose(()->owner.activate(5));
        else runAfterClose(()->owner.openSettings(false));
    }
    @Override void onClosed(){
        cancelDetailMotion();expandedSlider=null;
        if(shortcutFocus!=null)shortcutFocus.animate().cancel();
        owner.setControlCenterOpen(false);
        handler.removeCallbacksAndMessages(null);
        for(QuickButton button:shortcuts)if(button!=null)button.release();
        if(brightness!=null)brightness.release();if(volume!=null)volume.release();if(input!=null)input.release();
        if(dial!=null)dial.release();
    }

    /** Focus and value motion only invalidate small controls, never the full backdrop. */
    private abstract class Control extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        float focus;
        private ValueAnimator focusMotion;
        Control(String description){
            super(owner);setId(View.generateViewId());setFocusable(true);setFocusableInTouchMode(true);setClickable(true);
            setContentDescription(description);
        }
        @Override protected void onFocusChanged(boolean gain,int direction,Rect previous){
            super.onFocusChanged(gain,direction,previous);
            if(gain&&!(this instanceof QuickButton))moveShortcutFocus(-1);
            if(focusMotion!=null)focusMotion.cancel();
            if(!ValueAnimator.areAnimatorsEnabled()){focus=gain?1:0;invalidate();return;}
            focusMotion=ValueAnimator.ofFloat(focus,gain?1:0);focusMotion.setDuration(220);focusMotion.setInterpolator(EASE);
            focusMotion.addUpdateListener(a->{focus=(float)a.getAnimatedValue();invalidate();});focusMotion.start();
        }
        @Override public boolean onHoverEvent(android.view.MotionEvent e){
            if(e.getActionMasked()==android.view.MotionEvent.ACTION_HOVER_ENTER)requestFocusFromTouch();
            return super.onHoverEvent(e);
        }
        void text(Canvas c,String value,float x,float baseline,float size,int color,Typeface font,Paint.Align align){
            paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTypeface(font);paint.setTextSize(size);paint.setTextAlign(align);
            c.drawText(value,x,baseline,paint);
        }
        void release(){if(focusMotion!=null){focusMotion.removeAllListeners();focusMotion.removeAllUpdateListeners();focusMotion.cancel();}}
    }

    private void moveShortcutFocus(int index){
        if(shortcutFocus==null)return;
        shortcutFocus.animate().cancel();
        boolean visible=index>=0;
        float target=visible?px(index*108):shortcutFocus.getTranslationX();
        if(visible&&shortcutFocus.getAlpha()<.01f)shortcutFocus.setTranslationX(target);
        if(!ValueAnimator.areAnimatorsEnabled()){
            shortcutFocus.setTranslationX(target);shortcutFocus.setTranslationY(visible?-px(3):0);
            shortcutFocus.setScaleX(visible?1.06f:1);shortcutFocus.setScaleY(visible?1.06f:1);shortcutFocus.setAlpha(visible?1:0);return;
        }
        shortcutFocus.animate().translationX(target).translationY(visible?-px(3):0)
                .scaleX(visible?1.06f:1).scaleY(visible?1.06f:1).alpha(visible?1:0)
                .setDuration(visible?300:180).setInterpolator(EASE).start();
    }

    private final class QuickButton extends Control {
        final int index;
        QuickButton(int index){
            super(titles[index]);this.index=index;
            setOnClickListener(v->activateShortcut(index));
        }
        @Override protected void onFocusChanged(boolean gain,int direction,Rect previous){
            super.onFocusChanged(gain,direction,previous);
            if(gain){moveShortcutFocus(index);hint(titles[index]+(index==0?" · 确认进入待机":index==2?" · 确认刷新桌面":" · 确认打开"));brightness.setNextFocusUpId(getId());}
        }
        @Override protected void onDraw(Canvas c){
            c.save();c.scale(unit,unit);
            float press=isPressed()?.96f:1f;c.translate(0,-3*focus);c.scale((1+.06f*focus)*press,(1+.06f*focus)*press,50,50);
            paint.setAlpha(255);
            paint.setShader(new LinearGradient(0,0,85,90,new int[]{blend(0x53ffffff,0x00ffffff,focus),blend(0x2bffffff,0x00ffffff,focus)},null,Shader.TileMode.CLAMP));
            c.drawCircle(50,50,44,paint);paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(blend(0x24ffffff,0xb3ffffff,focus));c.drawCircle(50,50,43.5f,paint);paint.setStyle(Paint.Style.FILL);
            drawIcon(c,index,50,50,35,blend(Color.WHITE,INK,focus));c.restore();
        }
    }

    private final class LevelSlider extends Control {
        final boolean lightControl;
        private int level=-1;
        private float displayed;
        private ValueAnimator levelMotion;
        boolean dragging;
        private float downX;
        private boolean moved;
        LevelSlider(boolean lightControl){
            super(lightControl?"亮度，左右键调节":"音量，左右键调节");this.lightControl=lightControl;
            setOnClickListener(v->showDial(this));
        }
        void setLevel(int value){
            if(value==level)return;
            boolean initial=level<0;level=value;
            setContentDescription((lightControl?"亮度 ":"音量 ")+level+"%，左右键调节，确认展开圆环");
            if(expandedSlider==this&&dial!=null)dial.updateDescription();
            if(levelMotion!=null)levelMotion.cancel();
            if(initial||!ValueAnimator.areAnimatorsEnabled()){displayed=level;invalidateLevel();return;}
            levelMotion=ValueAnimator.ofFloat(displayed,level);levelMotion.setDuration(160);levelMotion.setInterpolator(EASE);
            levelMotion.addUpdateListener(a->{displayed=(float)a.getAnimatedValue();invalidateLevel();});levelMotion.start();
        }
        private void invalidateLevel(){invalidate();if(expandedSlider==this&&dial!=null)dial.invalidate();}
        private void change(int value){
            value=Math.max(lightControl?5:0,Math.min(100,value));
            if(value!=level&&changeLevel(lightControl,value))setLevel(lightControl?readBrightness():readVolume());
        }
        @Override protected void onFocusChanged(boolean gain,int direction,Rect previous){
            super.onFocusChanged(gain,direction,previous);
            if(gain)hint((lightControl?"亮度":"音量")+" · 左右调节，确认展开圆环");
        }
        @Override public boolean onKeyDown(int key,KeyEvent event){
            if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){
                int step=levelStep(lightControl);
                change(level+(key==KeyEvent.KEYCODE_DPAD_RIGHT?step:-step));return true;
            }
            return super.onKeyDown(key,event);
        }
        @Override public boolean onKeyUp(int key,KeyEvent event){if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)return true;return super.onKeyUp(key,event);}
        @Override public boolean onTouchEvent(MotionEvent event){
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:dragging=true;moved=false;downX=event.getX();requestFocusFromTouch();setPressed(true);return true;
                case MotionEvent.ACTION_UP:
                    dragging=false;setPressed(false);
                    if(moved)change(Math.round(event.getX()/getWidth()*100));else performClick();return true;
                case MotionEvent.ACTION_CANCEL:dragging=false;setPressed(false);return true;
                case MotionEvent.ACTION_MOVE:
                    if(Math.abs(event.getX()-downX)>ViewConfiguration.get(owner).getScaledTouchSlop())moved=true;
                    if(moved)change(Math.round(event.getX()/getWidth()*100));return true;
                default:return super.onTouchEvent(event);
            }
        }
        @Override protected void onDraw(Canvas c){
            c.save();c.scale(unit,unit);RectF r=new RectF(2,2,630,94);
            paint.setColor(blend(0x20ffffff,0x55ffffff,focus));c.drawRoundRect(r,46,46,paint);
            Path clip=new Path();clip.addRoundRect(r,46,46,Path.Direction.CW);
            c.save();c.clipPath(clip);paint.setColor(blend(0x34ffffff,0xdfedf3fa,focus));c.drawRect(0,0,632*displayed/100f,96,paint);c.restore();
            drawLabels(c,Color.WHITE);
            c.save();c.clipPath(clip);c.clipRect(0,0,632*displayed/100f,96);drawLabels(c,blend(Color.WHITE,INK,focus));c.restore();
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1+1.4f*focus);paint.setColor(blend(0x25ffffff,0xddffffff,focus));c.drawRoundRect(r,46,46,paint);paint.setStyle(Paint.Style.FILL);c.restore();
        }
        private void drawLabels(Canvas c,int color){
            drawLevelIcon(c,lightControl,displayed,43,48,28,color);
            text(c,lightControl?"亮度":"音量",78,57,25,color,medium,Paint.Align.LEFT);
        }
        @Override void release(){super.release();if(levelMotion!=null){levelMotion.removeAllUpdateListeners();levelMotion.cancel();}}
    }

    /** A standalone circular HUD. Shares the slider's live value and interruptible value animation. */
    private final class LevelDial extends Control {
        private final RectF ring=new RectF(27,27,DIAL_SIZE-27,DIAL_SIZE-27);
        LevelDial(){
            super("圆环调节");setOnClickListener(v->hideDial());
            setNextFocusLeftId(getId());setNextFocusRightId(getId());setNextFocusUpId(getId());setNextFocusDownId(getId());
        }
        void updateDescription(){
            if(expandedSlider!=null)setContentDescription((expandedSlider.lightControl?"亮度":"音量")+"圆环，"+expandedSlider.level+"%，方向键调节，确认或返回收起");
            invalidate();
        }
        private boolean adjustmentKey(int key){return key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN;}
        @Override public boolean onKeyDown(int key,KeyEvent event){
            if(adjustmentKey(key)){
                if(expandedSlider!=null&&!dialClosing){
                    int direction=key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP?1:-1;
                    expandedSlider.change(expandedSlider.level+direction*levelStep(expandedSlider.lightControl));
                }
                return true;
            }
            return super.onKeyDown(key,event);
        }
        @Override public boolean onKeyUp(int key,KeyEvent event){if(adjustmentKey(key))return true;return super.onKeyUp(key,event);}
        @Override protected void onDraw(Canvas c){
            LevelSlider source=expandedSlider;if(source==null)return;
            c.save();c.scale(unit,unit);
            paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(7);paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0x22ffffff);c.drawOval(ring,paint);
            if(source.displayed>.01f){paint.setColor(0xf2ffffff);c.drawArc(ring,-90,360*source.displayed/100f,false,paint);}
            paint.setStyle(Paint.Style.FILL);
            drawLevelIcon(c,source.lightControl,source.displayed,DIAL_SIZE/2,DIAL_SIZE/2,82,0xf5ffffff);
            c.restore();
        }
    }

    private final class InputCard extends Control {
        InputCard(){
            super("输入源，当前主屏幕");setOnClickListener(v->runAfterClose(()->owner.activate(9)));
        }
        @Override protected void onFocusChanged(boolean gain,int direction,Rect previous){super.onFocusChanged(gain,direction,previous);if(gain)hint("输入源 · 确认选择 HDMI 设备");}
        @Override protected void onDraw(Canvas c){
            c.save();c.scale(unit,unit);RectF r=new RectF(2,2,630,120);
            paint.setAlpha(255);
            paint.setShader(new LinearGradient(0,0,632,122,new int[]{blend(0x62ffffff,0xd9ffffff,focus),blend(0x45ffffff,0xc2ffffff,focus)},null,Shader.TileMode.CLAMP));c.drawRoundRect(r,31,31,paint);paint.setShader(null);
            paint.setColor(0xff1684ff);c.drawRoundRect(24,31,84,91,21,21,paint);drawIcon(c,8,54,61,32,Color.WHITE);
            int ink=blend(Color.WHITE,INK,focus);
            text(c,"输入源",106,56,27,ink,medium,Paint.Align.LEFT);
            text(c,"当前：主屏幕",107,88,19,blend(0xd4ffffff,0xc117212b,focus),regular,Paint.Align.LEFT);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.5f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(ink);
            c.drawLine(590,53,598,61,paint);c.drawLine(598,61,590,69,paint);paint.setStyle(Paint.Style.FILL);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1+focus);paint.setColor(blend(0x24ffffff,0xccffffff,focus));c.drawRoundRect(r,31,31,paint);paint.setStyle(Paint.Style.FILL);c.restore();
        }
    }

    private static int blend(int from,int to,float t){
        return Color.argb(Math.round(Color.alpha(from)+(Color.alpha(to)-Color.alpha(from))*t),Math.round(Color.red(from)+(Color.red(to)-Color.red(from))*t),Math.round(Color.green(from)+(Color.green(to)-Color.green(from))*t),Math.round(Color.blue(from)+(Color.blue(to)-Color.blue(from))*t));
    }
    /** The symbol and continuous ring carry the level; visible numbers are intentionally absent. */
    private void drawLevelIcon(Canvas c,boolean light,float percent,float x,float y,float size,int color){
        float amount=Math.max(0,Math.min(1,percent/100f));
        Paint p=iconPaint;p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);p.setStrokeWidth(1.9f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        c.save();c.translate(x-size/2,y-size/2);c.scale(size/32,size/32);
        if(light){
            c.drawCircle(16,16,5.5f,p);p.setStyle(Paint.Style.STROKE);
            p.setAlpha(Math.round(Color.alpha(color)*(.58f+.42f*amount)));
            for(int n=0;n<8;n++){
                double angle=n*Math.PI/4;
                c.drawLine(16+(float)Math.cos(angle)*10.3f,16+(float)Math.sin(angle)*10.3f,
                        16+(float)Math.cos(angle)*13.5f,16+(float)Math.sin(angle)*13.5f,p);
            }
        }else{
            Path speaker=new Path();speaker.moveTo(3,12);speaker.lineTo(9,12);speaker.lineTo(16,6);speaker.lineTo(16,26);speaker.lineTo(9,20);speaker.lineTo(3,20);speaker.close();c.drawPath(speaker,p);
            p.setStyle(Paint.Style.STROKE);
            float inner=Math.min(1,amount/.18f),outer=Math.max(0,Math.min(1,(amount-.18f)/.32f));
            p.setAlpha(Math.round(Color.alpha(color)*inner));c.drawArc(10,8,27,24,-55,110,false,p);
            p.setAlpha(Math.round(Color.alpha(color)*outer));c.drawArc(7,2,33,30,-55,110,false,p);
            float muted=1-Math.min(1,amount/.025f);
            if(muted>0){p.setAlpha(Math.round(Color.alpha(color)*muted));c.drawLine(22,13,28,19,p);c.drawLine(28,13,22,19,p);}
        }
        p.setAlpha(255);p.setStyle(Paint.Style.FILL);c.restore();
    }

    /** Small vector marks, drawn in a consistent 32-unit coordinate space. */
    private void drawIcon(Canvas canvas,int kind,float x,float y,float size,int color){
        Canvas c=canvas;Paint p=iconPaint;p.setStyle(Paint.Style.FILL);p.setColor(color);p.setStrokeWidth(2.4f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        c.save();c.translate(x-size/2,y-size/2);c.scale(size/32,size/32);
        if(kind==0){
            Path moon=new Path();moon.moveTo(18,2);moon.cubicTo(3,-1,-3,20,10,28);moon.cubicTo(19,34,29,27,30,20);moon.cubicTo(17,25,9,12,18,2);c.drawPath(moon,p);
        }else if(kind==1){
            p.setStyle(Paint.Style.STROKE);c.drawArc(5,5,27,29,-55,290,false,p);c.drawLine(16,2,16,16,p);
        }else if(kind==2){
            p.setStyle(Paint.Style.STROKE);c.drawArc(5,5,27,27,36,300,false,p);c.drawLine(26,3,26,11,p);c.drawLine(18,11,26,11,p);
        }else if(kind==3){
            p.setStyle(Paint.Style.STROKE);c.drawRoundRect(7,7,25,25,3,3,p);c.drawRect(12,12,20,20,p);
            for(int n=10;n<=22;n+=6){c.drawLine(n,3,n,7,p);c.drawLine(n,25,n,29,p);c.drawLine(3,n,7,n,p);c.drawLine(25,n,29,n,p);}
        }else if(kind==4||kind==5){
            Drawable d=kind==4?wallpaperIcon:settingsIcon;d.setTint(color);d.setBounds(1,1,31,31);d.draw(c);
        }else if(kind==6){
            c.drawCircle(16,16,6,p);p.setStyle(Paint.Style.STROKE);
            for(int n=0;n<8;n++){double a=n*Math.PI/4;c.drawLine(16+(float)Math.cos(a)*11,16+(float)Math.sin(a)*11,16+(float)Math.cos(a)*14,16+(float)Math.sin(a)*14,p);}
        }else if(kind==7){
            Path speaker=new Path();speaker.moveTo(3,12);speaker.lineTo(9,12);speaker.lineTo(16,6);speaker.lineTo(16,26);speaker.lineTo(9,20);speaker.lineTo(3,20);speaker.close();c.drawPath(speaker,p);
            p.setStyle(Paint.Style.STROKE);c.drawArc(10,8,27,24,-55,110,false,p);c.drawArc(7,2,33,30,-55,110,false,p);
        }else{
            p.setStyle(Paint.Style.STROKE);Path frame=new Path();frame.moveTo(5,12);frame.lineTo(5,5);frame.lineTo(28,5);frame.lineTo(28,26);frame.lineTo(21,26);c.drawPath(frame,p);
            c.drawArc(-10,11,20,41,-90,90,false,p);c.drawArc(-5,16,15,36,-90,90,false,p);p.setStyle(Paint.Style.FILL);c.drawCircle(4,27,2,p);
        }
        c.restore();
    }
}
