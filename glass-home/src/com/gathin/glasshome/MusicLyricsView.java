package com.gathin.glasshome;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.text.*;
import android.view.View;
import android.view.KeyEvent;
import android.os.SystemClock;
import android.view.animation.PathInterpolator;
import org.json.*;
import java.util.ArrayList;

/** Cached text layouts; only the lyric viewport moves. Word highlighting uses source timestamps. */
final class MusicLyricsView extends View {
    private final MusicController music;
    private final TextPaint paint=new TextPaint(3);
    private final Paint fade=new Paint(3);
    private final ArrayList<Line> lines=new ArrayList<>();
    private JSONArray source;
    private int layoutWidth,active=-2;
    private float scroll;
    private float[] emphasis=new float[0];
    private ValueAnimator movement;
    private boolean running;
    private int browsing=-1;
    private long browseUntil;
    private final PathInterpolator glide=new PathInterpolator(.2f,.75f,.15f,1);
    private final float density;
    private static class Line {int start,end;String text;JSONArray words;StaticLayout layout;float top,height;}
    MusicLyricsView(GlassHomeActivity owner,float unit){super(owner);music=owner.music;density=unit;paint.setTypeface(GlassFonts.create(owner,600));setFocusable(true);setClickable(true);setContentDescription("歌词");setOnClickListener(v->seekSelected());}
    String snapshot(){return "active:"+active+",browse:"+browsing+",scroll:"+Math.round(scroll);}
    void start(){running=true;invalidate();}
    void stop(){running=false;if(movement!=null)movement.cancel();}
    void update(){if(source!=music.lyricLines){source=music.lyricLines;rebuild();}invalidate();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){layoutWidth=w;rebuild();}
    private void rebuild(){
        if(layoutWidth<=0)return;if(movement!=null)movement.cancel();lines.clear();paint.setTextSize(40*density);paint.setColor(Color.WHITE);
        float top=0;
        if(source!=null)for(int i=0;i<source.length();i++){
            JSONObject value=source.optJSONObject(i);if(value==null)continue;Line line=new Line();line.start=value.optInt("start");line.end=value.optInt("end");line.text=value.optString("text");line.words=value.optJSONArray("words");
            line.layout=StaticLayout.Builder.obtain(line.text,0,line.text.length(),paint,Math.max(1,(int)((layoutWidth-24*density)/1.085f))).setIncludePad(false).setLineSpacing(5*density,1).build();
            line.top=top;line.height=line.layout.getHeight();top+=line.height+32*density;lines.add(line);
        }
        active=-2;browsing=-1;browseUntil=0;scroll=0;emphasis=new float[lines.size()];
    }
    private void animateTo(float target,boolean jump){
        if(movement!=null)movement.cancel();
        if(jump||!ValueAnimator.areAnimatorsEnabled()){scroll=target;for(int i=0;i<emphasis.length;i++)emphasis[i]=i==active?1:0;invalidate();return;}
        final float fromScroll=scroll;final float[] from=emphasis.clone();
        movement=ValueAnimator.ofFloat(0,1);movement.setDuration(560);movement.setInterpolator(glide);
        movement.addUpdateListener(a->{float t=(float)a.getAnimatedValue();scroll=fromScroll+(target-fromScroll)*t;for(int i=0;i<emphasis.length;i++)emphasis[i]=from[i]+((i==active?1:0)-from[i])*t;invalidate();});movement.start();
    }
    private float target(int row){return row<0||row>=lines.size()?0:lines.get(row).top;}
    private void track(int position){
        int selected=-1;for(int i=0;i<lines.size();i++){if(lines.get(i).start<=position)selected=i;else break;}
        boolean returning=browsing>=0&&SystemClock.uptimeMillis()>=browseUntil;
        if(returning)browsing=-1;
        if(selected==active&&!returning)return;
        boolean jump=active==-2||browsing<0&&!returning&&Math.abs(selected-active)>2;active=selected;
        animateTo(target(browsing>=0?browsing:active),jump);
    }
    private void seekSelected(){if(browsing>=0&&browsing<lines.size()){music.seek(lines.get(browsing).start-music.position());browsing=-1;browseUntil=0;active=-2;invalidate();}}
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){
            if(lines.isEmpty())return super.onKeyDown(key,event);
            int row=browsing>=0?browsing:Math.max(0,active),next=Math.max(0,Math.min(lines.size()-1,row+(key==KeyEvent.KEYCODE_DPAD_DOWN?1:-1)));
            if(next==row&&key==KeyEvent.KEYCODE_DPAD_UP)return super.onKeyDown(key,event);
            browsing=next;browseUntil=SystemClock.uptimeMillis()+5000;animateTo(target(next),false);return true;
        }
        return super.onKeyDown(key,event);
    }
    @Override protected void onDraw(Canvas canvas){
        if(source!=music.lyricLines){source=music.lyricLines;rebuild();}
        int position=music.playhead();track(position);
        if(lines.isEmpty()){
            paint.setColor(0x99ffffff);paint.setTextSize(28*density);canvas.drawText(music.lyricMessage,8*density,getHeight()*.45f,paint);paint.setTextSize(40*density);
        }else{
            float anchor=getHeight()*.36f;
            for(int i=0;i<lines.size();i++){
                Line line=lines.get(i);float y=anchor+line.top-scroll;if(y+line.height<0||y>getHeight())continue;
                float distance=Math.abs(y-anchor)/Math.max(1,getHeight()*.7f);
                float weight=emphasis[i],zoom=1+.085f*weight;
                int alpha=Math.round((115+31*weight)*Math.max(.18f,1-distance*(1-weight)));
                canvas.save();canvas.translate(8*density,y);canvas.scale(zoom,zoom,0,line.height*.5f);paint.setColor(Color.WHITE);paint.setAlpha(alpha);line.layout.draw(canvas);
                if(isFocused()&&i==browsing&&i!=active){paint.setAlpha(210);line.layout.draw(canvas);}
                if(i==active){
                    paint.setAlpha(255);
                    if(line.words!=null&&line.words.length()>0){
                        float chars=0;
                        for(int n=0;n<line.words.length();n++){JSONObject word=line.words.optJSONObject(n);if(word==null)continue;String text=word.optString("text");int start=word.optInt("start"),end=word.optInt("end");float amount=Math.max(0,Math.min(1,(position-start)/(float)Math.max(1,end-start)));chars+=text.length()*amount;}
                        int whole=Math.min(line.text.length(),(int)chars);float fraction=chars-whole;
                        for(int n=0;n<line.layout.getLineCount();n++){
                            int begin=line.layout.getLineStart(n),end=line.layout.getLineEnd(n);if(whole<begin)break;
                            float right=whole>=end?line.layout.getWidth():line.layout.getPrimaryHorizontal(whole);
                            if(whole<end&&whole<line.text.length())right+=(line.layout.getPrimaryHorizontal(whole+1)-right)*fraction;
                            canvas.save();canvas.clipRect(0,line.layout.getLineTop(n),Math.max(0,right),line.layout.getLineBottom(n));line.layout.draw(canvas);canvas.restore();
                        }
                    }else{paint.setAlpha(180+Math.round(75*Math.min(1,Math.max(0,position-line.start)/220f)));line.layout.draw(canvas);}
                }
                canvas.restore();
            }
        }
        if(running&&isShown()&&(music.playing||browsing>=0||movement!=null&&movement.isRunning()))postInvalidateOnAnimation();
    }
}
