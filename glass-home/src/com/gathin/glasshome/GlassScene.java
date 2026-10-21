package com.gathin.glasshome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** All geometry uses a 1920x1080 design canvas; transparent focusable nodes keep TV/accessibility semantics. */
final class GlassScene extends ViewGroup {
    private static final float W=1920, H=1080;
    private static final String[] FOOTER={"全部应用", "动态壁纸", "网络", "设置"};
    private static final String[] SUB={"bilibili", "NETEASE CLOUD MUSIC", "SCREEN MIRRORING", "NETFLIX"};
    private static final String[] HERO={"让喜欢的，成为日常。", "让音乐，填满此刻。", "小屏精彩，大屏分享。", "今晚，看一部好电影。"};
    private static final String[] DETAILS={"看见有趣的世界，发现你的热爱。", "熟悉的旋律，在客厅慢慢响起。", "把手机里的精彩，带到大屏幕。", "好故事，值得留一整晚。"};
    private static final int[] ACCENTS={0xffff8eb4,0xffff8c96,0xff8edcff,0xffeb6678};
    private final GlassHomeActivity owner;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF full=new RectF(0,0,W,H);
    private final RectF[] boxes=new RectF[9];
    private final View[] nodes=new View[9];
    private final Drawable[] icons=new Drawable[2];
    private final Typeface regular;
    private final Typeface light;
    private final Typeface medium;
    private final float[] weights={1,0,0,0,0,0,0,0,0};
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Bitmap stillBlur;
    private final Bitmap videoBlur=Bitmap.createBitmap(240,135,Bitmap.Config.ARGB_8888);
    private final int[] rgb=new int[240*135], scratch=new int[240*135];
    private boolean dynamic;
    private int selected=0,hero=0;
    private float scale=1,offsetX,offsetY,lightX=354;
    private ValueAnimator focusAnimation,lightAnimation;
    private final Runnable clock=new Runnable() {
        @Override public void run() { invalidate(); handler.postDelayed(this,60000-System.currentTimeMillis()%60000); }
    };

    GlassScene(GlassHomeActivity owner,Bitmap background) {
        super(owner);
        this.owner=owner;
        regular=GlassFonts.create(owner,400);
        light=GlassFonts.create(owner,300);
        medium=GlassFonts.create(owner,500);
        setWillNotDraw(false);
        setClipChildren(false);
        setFocusable(false);
        Bitmap small=Bitmap.createScaledBitmap(background,240,135,true);
        stillBlur=Bitmap.createBitmap(240,135,Bitmap.Config.ARGB_8888);
        blur(small,stillBlur);
        small.recycle();
        for(int i=0;i<9;i++) {
            final int index=i;
            if(i<4) boxes[i]=new RectF(154+i*414,610,154+i*414+370,852);
            else if(i==8)boxes[i]=new RectF(414,75,635,129);
            else boxes[i]=new RectF(562+(i-4)*204,958,562+(i-4)*204+184,1016);
            nodes[i]=new View(owner) {
                @Override protected void drawableStateChanged(){ super.drawableStateChanged(); GlassScene.this.invalidate(); }
            };
            nodes[i].setFocusable(true);
            nodes[i].setClickable(true);
            nodes[i].setContentDescription(i<4?GlassHomeActivity.LABELS[i]:i==8?"切换到小米原主页":FOOTER[i-4]);
            nodes[i].setOnFocusChangeListener((v,focused)-> { if(focused) select(index); });
            nodes[i].setOnClickListener(v->owner.activate(index));
            addView(nodes[i]);
        }
        for(int i=0;i<2;i++) {
            try { icons[i]=owner.getPackageManager().getApplicationIcon(GlassHomeActivity.PACKAGES[i]); }
            catch(Exception ignored) { }
        }
    }

    void startClock(){ handler.removeCallbacks(clock); handler.post(clock); invalidate(); }
    void stopClock(){ handler.removeCallbacks(clock); }
    void close(){ stopClock(); if(focusAnimation!=null)focusAnimation.cancel(); if(lightAnimation!=null)lightAnimation.cancel(); stillBlur.recycle(); videoBlur.recycle(); }
    void useVideoFrame(Bitmap frame){ blur(frame,videoBlur); dynamic=true; invalidate(); }
    void useStill(){ dynamic=false; invalidate(); }
    void focusApp(int index){ nodes[Math.max(0,Math.min(3,index))].requestFocus(); }

    private void select(int index) {
        selected=index;
        if(index<4)hero=index;
        if(focusAnimation!=null)focusAnimation.cancel();
        float[] from=weights.clone();
        focusAnimation=ValueAnimator.ofFloat(0,1);
        focusAnimation.setDuration(240);
        focusAnimation.setInterpolator(new PathInterpolator(.16f,1f,.3f,1f));
        focusAnimation.addUpdateListener(a->{
            float t=(float)a.getAnimatedValue();
            for(int i=0;i<9;i++)weights[i]=from[i]+((i==index?1f:0f)-from[i])*t;
            invalidate();
        });
        focusAnimation.start();
        if(index<4) {
            if(lightAnimation!=null)lightAnimation.cancel();
            lightAnimation=ValueAnimator.ofFloat(lightX,boxes[index].centerX());
            lightAnimation.setDuration(330);
            lightAnimation.setInterpolator(new PathInterpolator(.22f,1f,.36f,1f));
            lightAnimation.addUpdateListener(a->{lightX=(float)a.getAnimatedValue();invalidate();});
            lightAnimation.start();
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key=event.getKeyCode();
        if(event.getAction()==KeyEvent.ACTION_DOWN) {
            int target=selected;
            if(key==KeyEvent.KEYCODE_DPAD_LEFT)target=selected==8?8:Math.max(selected<4?0:4,selected-1);
            else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)target=selected==8?8:Math.min(selected<4?3:7,selected+1);
            else if(key==KeyEvent.KEYCODE_DPAD_DOWN && selected<4)target=selected+4;
            else if(key==KeyEvent.KEYCODE_DPAD_DOWN && selected==8)target=0;
            else if(key==KeyEvent.KEYCODE_DPAD_UP && selected>=4 && selected<8)target=selected-4;
            else if(key==KeyEvent.KEYCODE_DPAD_UP && selected<4)target=8;
            else if(key==KeyEvent.KEYCODE_MENU){owner.activate(4);return true;}
            else return super.dispatchKeyEvent(event);
            if(target!=selected){nodes[target].requestFocus();playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT);}
            return true;
        }
        if(key>=KeyEvent.KEYCODE_DPAD_UP && key<=KeyEvent.KEYCODE_DPAD_RIGHT)return true;
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onMeasure(int width,int height) {
        setMeasuredDimension(MeasureSpec.getSize(width),MeasureSpec.getSize(height));
        scale=Math.min(getMeasuredWidth()/W,getMeasuredHeight()/H);
        offsetX=(getMeasuredWidth()-W*scale)/2;
        offsetY=(getMeasuredHeight()-H*scale)/2;
        for(int i=0;i<9;i++)nodes[i].measure(MeasureSpec.makeMeasureSpec(Math.round(boxes[i].width()*scale),MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.round(boxes[i].height()*scale),MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
        for(int i=0;i<9;i++) {
            RectF box=boxes[i];
            nodes[i].layout(Math.round(offsetX+box.left*scale),Math.round(offsetY+box.top*scale),
                    Math.round(offsetX+box.right*scale),Math.round(offsetY+box.bottom*scale));
        }
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.save(); c.translate(offsetX,offsetY); c.scale(scale,scale);
        p.setShader(new LinearGradient(0,0,W,0,new int[]{0x69051020,0x15051020,0x05051020},null,Shader.TileMode.CLAMP));
        p.setStyle(Paint.Style.FILL); c.drawRect(full,p);
        p.setShader(new LinearGradient(0,470,0,H,new int[]{0x00071322,0x45071322,0xa6071322},null,Shader.TileMode.CLAMP));
        c.drawRect(full,p); p.setShader(null);
        header(c);
        text(c,"把时间，留给喜欢的事。",132,251,21,0xbfffffff,regular,Paint.Align.LEFT);
        text(c,HERO[hero],128,348,65,Color.WHITE,light,Paint.Align.LEFT);
        text(c,DETAILS[hero],133,402,25,0xc2ffffff,regular,Paint.Align.LEFT);
        for(int i=0;i<4;i++) {
            p.setColor(i==hero?0xf0ffffff:0x50ffffff);
            if(i==hero)c.drawRoundRect(134+i*22,442,148+i*22,447,3,3,p);
            else c.drawCircle(139+i*22,444.5f,2.5f,p);
        }
        text(c,"常用应用",136,558,22,0xe6ffffff,medium,Paint.Align.LEFT);
        text(c,dynamic?"HAWAIʻI  /  AERIAL":"DUSK  /  STILL",1782,557,14,0xa6ffffff,regular,Paint.Align.RIGHT);
        p.setShader(new RadialGradient(lightX,760,400,new int[]{withAlpha(ACCENTS[hero],48),0x00000000},null,Shader.TileMode.CLAMP));
        c.drawRect(0,480,W,970,p);p.setShader(null);
        frosted(c,new RectF(120,582,1800,890),46,.48f,0);
        // Nonfocused cards first. The raised card owns the top visual plane.
        for(int i=0;i<4;i++)if(i!=selected)card(c,i);
        if(selected<4)card(c,selected);
        for(int i=4;i<8;i++)control(c,i);
        text(c,"方向键选择 · 确认键打开",138,994,17,0x99ffffff,regular,Paint.Align.LEFT);
        text(c,"GLASS HOME",1782,994,13,0x85ffffff,medium,Paint.Align.RIGHT);
        c.restore();
    }

    private void header(Canvas c) {
        frosted(c,new RectF(130,72,187,129),22,.7f,0);
        p.setColor(0xf2ffffff);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.3f);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);
        Path home=new Path();home.moveTo(147,99);home.lineTo(158.5f,89);home.lineTo(170,99);home.moveTo(150,97);home.lineTo(150,111);home.lineTo(156,111);home.lineTo(156,103);home.lineTo(162,103);home.lineTo(162,111);home.lineTo(167,111);home.lineTo(167,97);c.drawPath(home,p);p.setStyle(Paint.Style.FILL);
        text(c,"客厅",207,110,29,Color.WHITE,medium,Paint.Align.LEFT);
        p.setColor(0x35ffffff);c.drawRect(290,89,291,114,p);
        text(c,"主页",316,109,23,0xd9ffffff,regular,Paint.Align.LEFT);
        RectF stock=boxes[8];
        frosted(c,stock,27,.3f+.6f*weights[8],0);
        text(c,"小米原主页",stock.left+27,stock.centerY()+8,20,0xe6ffffff,regular,Paint.Align.LEFT);
        text(c,"↗",stock.right-26,stock.centerY()+8,23,0xcfffffff,regular,Paint.Align.RIGHT);
        if(weights[8]>.01f){p.setColor(withAlpha(Color.WHITE,(int)(220*weights[8])));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);c.drawRoundRect(stock,27,27,p);p.setStyle(Paint.Style.FILL);}
        Date now=new Date();
        text(c,new SimpleDateFormat("HH:mm",Locale.CHINA).format(now),1786,111,49,Color.WHITE,light,Paint.Align.RIGHT);
        text(c,new SimpleDateFormat("M月d日  EEEE",Locale.CHINA).format(now),1784,148,20,0xc2ffffff,regular,Paint.Align.RIGHT);
        p.setColor(owner.online()?0xffa3e0c3:0xffd8b5a5);c.drawCircle(1649,178,3.5f,p);
        text(c,owner.online()?"网络已连接":"网络未连接",1784,184,16,0xa6ffffff,regular,Paint.Align.RIGHT);
    }

    private void card(Canvas c,int index) {
        float f=weights[index],press=nodes[index].isPressed()?0.972f:1f;
        RectF box=boxes[index];
        c.save();c.translate(0,-10*f);c.scale((1+.045f*f)*press,(1+.045f*f)*press,box.centerX(),box.centerY());
        if(f>.01f){p.setColor(Color.BLACK);p.setAlpha((int)(45*f));p.setShadowLayer(22,0,15,0x70000000);c.drawRoundRect(box,31,31,p);p.clearShadowLayer();p.setAlpha(255);}
        frosted(c,box,31,.58f+.30f*f,withAlpha(ACCENTS[index],(int)(14+12*f)));
        if(f>.01f) {
            p.setShader(new LinearGradient(box.left,box.top,box.right,box.bottom,new int[]{withAlpha(Color.WHITE,(int)(200*f)),withAlpha(Color.WHITE,(int)(45*f)),withAlpha(Color.WHITE,(int)(105*f))},null,Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);c.drawRoundRect(box,31,31,p);p.setStyle(Paint.Style.FILL);p.setShader(null);
        }
        float cx=box.centerX(),cy=box.top+89;
        appIcon(c,index,cx,cy);
        text(c,GlassHomeActivity.LABELS[index],cx,box.top+184,27,Color.WHITE,medium,Paint.Align.CENTER);
        text(c,SUB[index],cx,box.top+214,13.5f,0xa6ffffff,regular,Paint.Align.CENTER);
        if(index==3&&!owner.isInstalled(3)) {
            p.setColor(0x18ffffff);c.drawRoundRect(box.right-82,box.top+17,box.right-17,box.top+43,13,13,p);
            text(c,"待安装",box.right-49.5f,box.top+35,12,0xc2ffffff,regular,Paint.Align.CENTER);
        }
        c.restore();
    }

    private void appIcon(Canvas c,int index,float x,float y) {
        RectF badge=new RectF(x-49,y-49,x+49,y+49);
        p.setColor(index==2?0xff247ea8:0xff12141d);p.setShadowLayer(14,0,6,0x55000000);c.drawRoundRect(badge,25,25,p);p.clearShadowLayer();
        if(index<2&&icons[index]!=null) {
            c.save();Path clip=new Path();clip.addRoundRect(badge,25,25,Path.Direction.CW);c.clipPath(clip);
            icons[index].setBounds((int)badge.left,(int)badge.top,(int)badge.right,(int)badge.bottom);icons[index].draw(c);c.restore();
        } else if(index==2) {
            p.setShader(new LinearGradient(x-49,y-49,x+49,y+49,0xff6dcee0,0xff2679b1,Shader.TileMode.CLAMP));c.drawRoundRect(badge,25,25,p);p.setShader(null);
            p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3.8f);p.setStrokeCap(Paint.Cap.ROUND);
            Path screen=new Path();screen.moveTo(x-29,y+13);screen.lineTo(x-29,y-23);screen.quadTo(x-29,y-28,x-24,y-28);screen.lineTo(x+25,y-28);screen.quadTo(x+30,y-28,x+30,y-23);screen.lineTo(x+30,y+8);screen.quadTo(x+30,y+13,x+25,y+13);screen.lineTo(x+17,y+13);c.drawPath(screen,p);
            p.setStyle(Paint.Style.FILL);Path triangle=new Path();triangle.moveTo(x,y+3);triangle.lineTo(x-21,y+28);triangle.lineTo(x+21,y+28);triangle.close();c.drawPath(triangle,p);
        } else if(index==3) {
            // Three ribbons retain the recognizable Netflix N, with a darker left face.
            p.setColor(0xffb20710);c.drawRect(x-24,y-33,x-10,y+33,p);c.drawRect(x+11,y-33,x+25,y+33,p);
            p.setShader(new LinearGradient(x-23,y-32,x+24,y+32,0xfff51d29,0xffdb0712,Shader.TileMode.CLAMP));
            Path ribbon=new Path();ribbon.moveTo(x-24,y-33);ribbon.lineTo(x-8,y-33);ribbon.lineTo(x+25,y+33);ribbon.lineTo(x+9,y+33);ribbon.close();c.drawPath(ribbon,p);p.setShader(null);
        } else text(c,index==0?"哔":"♪",x,y+16,53,Color.WHITE,medium,Paint.Align.CENTER);
        p.setShader(new LinearGradient(badge.left,badge.top,badge.right,badge.bottom,new int[]{0x32ffffff,0x00ffffff,0x00ffffff},null,Shader.TileMode.CLAMP));c.drawRoundRect(badge,25,25,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0x26ffffff);c.drawRoundRect(badge,25,25,p);p.setStyle(Paint.Style.FILL);
    }

    private void control(Canvas c,int index) {
        RectF r=boxes[index];float f=weights[index];
        c.save();c.scale(1+.045f*f,1+.045f*f,r.centerX(),r.centerY());
        frosted(c,r,29,.30f+.6f*f,0);
        float x=r.left+30,y=r.centerY();
        p.setColor(0xe0ffffff);p.setStrokeWidth(2);p.setStyle(Paint.Style.STROKE);
        if(index==4){for(int i=0;i<4;i++){float a=x+(i%2)*10,b=y-8+(i/2)*10;c.drawRoundRect(a,b,a+6,b+6,1,1,p);}}
        else if(index==5){c.drawRoundRect(x-3,y-10,x+18,y+10,4,4,p);Path tri=new Path();tri.moveTo(x+4,y-5);tri.lineTo(x+4,y+5);tri.lineTo(x+11,y);tri.close();p.setStyle(Paint.Style.FILL);c.drawPath(tri,p);}
        else if(index==6){c.drawArc(x-4,y-10,x+22,y+16,220,100,false,p);c.drawArc(x+1,y-5,x+17,y+11,220,100,false,p);p.setStyle(Paint.Style.FILL);c.drawCircle(x+9,y+5,2,p);}
        else {c.drawCircle(x+8,y,9,p);c.drawCircle(x+8,y,3,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(x+8+(float)Math.cos(a)*11,y+(float)Math.sin(a)*11,x+8+(float)Math.cos(a)*14,y+(float)Math.sin(a)*14,p);}}
        p.setStyle(Paint.Style.FILL);
        text(c,FOOTER[index-4],r.left+69,r.centerY()+7,20,0xe6ffffff,regular,Paint.Align.LEFT);
        if(f>.01f){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setColor(withAlpha(Color.WHITE,(int)(150*f)));c.drawRoundRect(r,29,29,p);p.setStyle(Paint.Style.FILL);}
        c.restore();
    }

    private void frosted(Canvas c,RectF r,float radius,float strength,int accent) {
        c.save();Path clip=new Path();clip.addRoundRect(r,radius,radius,Path.Direction.CW);c.clipPath(clip);
        p.setColor(Color.WHITE);p.setAlpha((int)(190+55*strength));c.drawBitmap(dynamic?videoBlur:stillBlur,null,full,p);p.setAlpha(255);
        p.setColor(0x200b1730);c.drawRect(r,p);
        p.setShader(new LinearGradient(r.left,r.top,r.right,r.bottom,
                new int[]{withAlpha(Color.WHITE,(int)(24+24*strength)),withAlpha(Color.WHITE,(int)(9+9*strength)),0x101a2846},
                new float[]{0,.45f,1},Shader.TileMode.CLAMP));c.drawRect(r,p);p.setShader(null);
        if(accent!=0){p.setColor(accent);c.drawRect(r,p);}
        c.restore();
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.1f);
        p.setShader(new LinearGradient(r.left,r.top,r.left,r.bottom,new int[]{0x65ffffff,0x15ffffff,0x38ffffff},null,Shader.TileMode.CLAMP));
        c.drawRoundRect(r,radius,radius,p);p.setShader(null);p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(r.left+radius,r.top,r.right-radius,r.top,new int[]{0x00ffffff,0x45ffffff,0x00ffffff},null,Shader.TileMode.CLAMP));
        c.drawRoundRect(r.left+radius,r.top+1,r.right-radius,r.top+2,1,1,p);p.setShader(null);
    }

    private void text(Canvas c,String value,float x,float y,float size,int color,Typeface typeface,Paint.Align align) {
        p.setShader(null);p.setColor(color);p.setTextSize(size);p.setTypeface(typeface);p.setTextAlign(align);p.setStyle(Paint.Style.FILL);
        p.setShadowLayer(8,0,2,0x26000000);c.drawText(value,x,y,p);p.clearShadowLayer();
    }
    private static int withAlpha(int color,int alpha){return (color&0xffffff)|(Math.max(0,Math.min(255,alpha))<<24);}

    /** Three separable box passes approximate a Gaussian backdrop blur at 1/8 resolution. */
    private void blur(Bitmap source,Bitmap output) {
        source.getPixels(rgb,0,240,0,0,240,135);
        for(int pass=0;pass<3;pass++) {
            boxPass(rgb,scratch,240,135,5,true);
            boxPass(scratch,rgb,240,135,5,false);
        }
        output.setPixels(rgb,0,240,0,0,240,135);
    }
    private static void boxPass(int[] input,int[] output,int w,int h,int radius,boolean horizontal) {
        int major=horizontal?h:w,minor=horizontal?w:h,span=radius*2+1;
        for(int line=0;line<major;line++) {
            int rr=0,gg=0,bb=0;
            for(int k=-radius;k<=radius;k++) {
                int pos=Math.max(0,Math.min(minor-1,k));int color=input[horizontal?line*w+pos:pos*w+line];
                rr+=(color>>16)&255;gg+=(color>>8)&255;bb+=color&255;
            }
            for(int at=0;at<minor;at++) {
                output[horizontal?line*w+at:at*w+line]=0xff000000|((rr/span)<<16)|((gg/span)<<8)|(bb/span);
                int leave=Math.max(0,at-radius),enter=Math.min(minor-1,at+radius+1);
                int a=input[horizontal?line*w+leave:leave*w+line],b=input[horizontal?line*w+enter:enter*w+line];
                rr+=((b>>16)&255)-((a>>16)&255);gg+=((b>>8)&255)-((a>>8)&255);bb+=(b&255)-(a&255);
            }
        }
    }
}
