package com.gathin.glasshome;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.text.*;
import android.view.View;
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
    private final float density;
    private static class Line {int start,end;String text;JSONArray words;StaticLayout layout;float top,height;}
    MusicLyricsView(GlassHomeActivity owner,float unit){super(owner);music=owner.music;density=unit;paint.setTypeface(GlassFonts.create(owner,600));setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
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
        active=-2;scroll=0;emphasis=new float[lines.size()];
    }
    private void track(int position){
        int selected=-1;for(int i=0;i<lines.size();i++){if(lines.get(i).start<=position)selected=i;else break;}
        if(selected==active)return;boolean jump=active==-2||Math.abs(selected-active)>2;active=selected;
        if(movement!=null)movement.cancel();float target=selected<0?0:lines.get(selected).top;
        if(jump){scroll=target;for(int i=0;i<emphasis.length;i++)emphasis[i]=i==active?1:0;return;}
        final float fromScroll=scroll,toScroll=target;final float[] from=emphasis.clone();
        movement=ValueAnimator.ofFloat(0,1);movement.setDuration(640);movement.setInterpolator(new PathInterpolator(.2f,.75f,.15f,1));movement.addUpdateListener(a->{float t=(float)a.getAnimatedValue();scroll=fromScroll+(toScroll-fromScroll)*t;for(int i=0;i<emphasis.length;i++)emphasis[i]=from[i]+((i==active?1:0)-from[i])*t;invalidate();});movement.start();
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
        if(running&&isShown()&&(music.playing||movement!=null&&movement.isRunning()))postInvalidateOnAnimation();
    }
}
