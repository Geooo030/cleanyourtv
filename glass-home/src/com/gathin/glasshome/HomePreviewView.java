package com.gathin.glasshome;

import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Read-only home information. Focus stays on the app card; only Confirm launches. */
final class HomePreviewView extends FrameLayout implements MusicController.Listener {
    static final float X=930,Y=260,W=850,H=390;
    private final GlassHomeActivity owner;
    private final float unit;
    private final FrameLayout videoPage,musicPage;
    private FeedPage feedPage,sparePage;
    private int feedOffset;
    private boolean sliding;
    private final Runnable rotate=()->rotateFeed();
    private final TextView heading,source,musicTitle,musicArtist,album;
    private final ImageView musicCover;
    private final MusicLyricsView lyrics;
    private final View progress;
    private final HashMap<String,JSONObject> feeds=new HashMap<>();
    private final HashSet<String> pending=new HashSet<>();
    private final HashMap<String,Long> fetched=new HashMap<>();
    private final PathInterpolator easing=new PathInterpolator(.16f,1f,.3f,1f);
    private int selected=-1,displayed=-1;
    private long artworkId=-1;
    private boolean homeVisible,closed;
    private final Runnable reveal=()->reveal();
    private final Runnable tick=new Runnable(){public void run(){if(closed||!homeVisible||displayed!=1||getVisibility()!=VISIBLE)return;progress.invalidate();postDelayed(this,400);}};

    HomePreviewView(GlassHomeActivity owner,float unit,int screenW,int screenH){
        super(owner);this.owner=owner;this.unit=unit;setVisibility(GONE);setFocusable(false);setClickable(false);setClipChildren(true);
        setBackground(new GlassPanelDrawable(unit,screenW,screenH,owner.scene).preview().at(px(X),px(Y)));
        heading=text(25,Color.WHITE,600);addView(heading,box(26,15,440,40));
        source=text(14,0x99ffffff,400);source.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);addView(source,box(470,16,352,38));
        videoPage=new FrameLayout(owner);addView(videoPage,box(0,0,W,H));
        feedPage=new FeedPage();sparePage=new FeedPage();videoPage.addView(feedPage,box(0,0,W,H));videoPage.addView(sparePage,box(0,0,W,H));sparePage.setVisibility(GONE);
        musicPage=new FrameLayout(owner);addView(musicPage,box(0,0,W,H));
        musicCover=image(musicPage,26,82,240,240);
        musicTitle=text(29,Color.WHITE,600);musicTitle.setMaxLines(2);musicPage.addView(musicTitle,box(294,77,524,74));
        musicArtist=text(20,0xbfffffff,400);musicArtist.setSingleLine();musicPage.addView(musicArtist,box(296,153,520,36));
        lyrics=new MusicLyricsView(owner,unit*.65f);musicPage.addView(lyrics,box(290,197,530,100));
        album=text(21,0x99ffffff,400);album.setMaxLines(2);musicPage.addView(album,box(296,207,514,82));
        progress=new Progress();musicPage.addView(progress,box(296,315,510,55));
        for(String provider:new String[]{"bilibili","aurora"})try{JSONObject value=new JSONObject(owner.getSharedPreferences("home_preview",0).getString(provider,""));if(value.optJSONArray("items")!=null)feeds.put(provider,value);}catch(Exception ignored){}
        owner.music.observe(this);
    }
    private int px(float value){return Math.round(value*unit);}
    private FrameLayout.LayoutParams box(float x,float y,float w,float h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(px(w),px(h));p.leftMargin=px(x);p.topMargin=px(y);return p;}
    private TextView text(float size,int color,int weight){TextView v=new TextView(owner);v.setTypeface(GlassFonts.create(owner,weight));v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(size));v.setTextColor(color);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);v.setEllipsize(TextUtils.TruncateAt.END);v.setFocusable(false);return v;}
    private GradientDrawable placeholder(){GradientDrawable d=new GradientDrawable();d.setColor(0x16ffffff);d.setCornerRadius(px(22));d.setStroke(Math.max(1,px(1)),0x22ffffff);return d;}
    private ImageView image(FrameLayout page,float x,float y,float w,float h){ImageView view=new ImageView(owner);view.setScaleType(ImageView.ScaleType.CENTER_CROP);view.setBackground(placeholder());view.setClipToOutline(true);view.setFocusable(false);page.addView(view,box(x,y,w,h));return view;}
    private boolean eligible(){return selected==0||selected==1||selected==3;}
    void focus(int index){selected=index;removeCallbacks(reveal);if(!eligible()){hide(true);return;}if(homeVisible)postDelayed(reveal,150);}
    void setHomeVisible(boolean value){if(closed)return;homeVisible=value;removeCallbacks(reveal);if(value&&eligible())postDelayed(reveal,150);else hide(false);}
    private void hide(boolean animate){removeCallbacks(tick);stopCarousel();lyrics.stop();animate().cancel();if(!animate){setVisibility(GONE);setAlpha(0);return;}animate().alpha(0).translationY(px(10)).setDuration(170).setInterpolator(easing).withEndAction(()->{if(!eligible()||!homeVisible)setVisibility(GONE);}).start();}
    private void reveal(){
        if(closed||!homeVisible||!eligible())return;
        boolean opening=getVisibility()!=VISIBLE;boolean switched=displayed!=selected;
        if(switched){stopCarousel();feedOffset=0;}
        displayed=selected;animate().cancel();setVisibility(VISIBLE);
        if(opening){setAlpha(0);setScaleX(.975f);setScaleY(.975f);setTranslationY(px(14));owner.requestHomePreviewBackdrop();}
        if(displayed==1){stopCarousel();videoPage.setVisibility(GONE);musicPage.setVisibility(VISIBLE);renderMusic();}
        else{lyrics.stop();removeCallbacks(tick);musicPage.setVisibility(GONE);videoPage.setVisibility(VISIBLE);String provider=displayed==0?"bilibili":"aurora";renderFeed(provider);loadFeed(provider);}
        if(switched&&!opening){FrameLayout page=displayed==1?musicPage:videoPage;page.animate().cancel();page.setAlpha(.2f);page.setTranslationX(px(12));page.animate().alpha(1).translationX(0).setDuration(260).setInterpolator(easing).start();}
        animate().alpha(1).scaleX(1).scaleY(1).translationY(0).setDuration(opening?340:220).setInterpolator(easing).start();
    }
    private void loadFeed(String provider){
        long now=android.os.SystemClock.elapsedRealtime();if(pending.contains(provider)||now-fetched.getOrDefault(provider,-300001L)<300000)return;
        pending.add(provider);owner.music.fetch("/api/home-preview/"+provider,(value,error)->{pending.remove(provider);if(closed)return;
            if(value!=null){feeds.put(provider,value);fetched.put(provider,android.os.SystemClock.elapsedRealtime());owner.getSharedPreferences("home_preview",0).edit().putString(provider,value.toString()).apply();}
            if(displayed==(provider.equals("bilibili")?0:3)){renderFeed(provider);if(error!=null&&feeds.get(provider)==null){source.setText("推荐内容暂时无法更新");feedPage.titles[0].setText("稍后再来看看");}}
        });
    }
    private void renderFeed(String provider){
        heading.setText(provider.equals("bilibili")?"云视听小电视 · 热门视频":"云视听极光 · 热播剧集");
        JSONObject feed=feeds.get(provider);
        source.setText(feed==null?"正在更新推荐内容…":feed.optString("source")+" · "+new SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date(feed.optLong("updatedAt")))+(feed.optBoolean("stale")?" 缓存":""));
        feedPage.bind(feed,feedOffset);scheduleCarousel();
    }
    private final class FeedPage extends FrameLayout {
        final ImageView[] posters=new ImageView[2];final TextView[] titles=new TextView[2],captions=new TextView[2];
        FeedPage(){super(owner);setFocusable(false);for(int i=0;i<2;i++){float x=26+i*409;posters[i]=image(this,x,70,389,219);titles[i]=text(21,Color.WHITE,500);titles[i].setMaxLines(2);addView(titles[i],box(x,299,389,53));captions[i]=text(14,0xaaffffff,400);captions[i].setSingleLine();addView(captions[i],box(x,356,389,22));}}
        void bind(JSONObject feed,int offset){JSONArray list=feed==null?null:feed.optJSONArray("items");int count=list==null?0:list.length();for(int i=0;i<2;i++){JSONObject item=count==0?null:list.optJSONObject((offset+i)%count);ImageView poster=posters[i];titles[i].setText(item==null?(i==0?"精彩，即将呈现":""):item.optString("title"));captions[i].setText(item==null?"":item.optString("subtitle"));String route=item==null?"":item.optString("cover");if(route.equals(poster.getTag()))continue;poster.setTag(route);poster.setImageDrawable(null);if(!route.isEmpty())owner.music.previewImage(route,bitmap->{if(!closed&&route.equals(poster.getTag())&&bitmap!=null)poster.setImageBitmap(bitmap);});}}
    }
    private JSONObject activeFeed(){return feeds.get(displayed==0?"bilibili":"aurora");}
    private void scheduleCarousel(){removeCallbacks(rotate);JSONObject feed=activeFeed();JSONArray items=feed==null?null:feed.optJSONArray("items");if(homeVisible&&getVisibility()==VISIBLE&&displayed!=1&&items!=null&&items.length()>2){int next=(feedOffset+2)%items.length();sparePage.bind(feed,next);postDelayed(rotate,7000);}}
    private void stopCarousel(){removeCallbacks(rotate);if(feedPage==null)return;feedPage.animate().cancel();sparePage.animate().cancel();feedPage.setTranslationX(0);feedPage.setVisibility(VISIBLE);sparePage.setVisibility(GONE);sliding=false;}
    private void rotateFeed(){
        JSONObject feed=activeFeed();JSONArray items=feed==null?null:feed.optJSONArray("items");if(closed||!homeVisible||displayed==1||getVisibility()!=VISIBLE||items==null||items.length()<3)return;
        feedOffset=(feedOffset+2)%items.length();if(!android.animation.ValueAnimator.areAnimatorsEnabled()){feedPage.bind(feed,feedOffset);scheduleCarousel();return;}
        sliding=true;FeedPage outgoing=feedPage,incoming=sparePage;incoming.bind(feed,feedOffset);incoming.setTranslationX(-px(W));incoming.setVisibility(VISIBLE);
        outgoing.animate().translationX(px(W)).setDuration(620).setInterpolator(easing).start();incoming.animate().translationX(0).setDuration(620).setInterpolator(easing).withEndAction(()->{if(!sliding)return;outgoing.setVisibility(GONE);outgoing.setTranslationX(0);feedPage=incoming;sparePage=outgoing;sliding=false;scheduleCarousel();}).start();
    }
    private void renderMusic(){
        MusicController music=owner.music;MusicController.Track track=music.previewTrack();
        heading.setText(music.playing?"Apple Music · 正在播放":music.current()!=null?"Apple Music · 已暂停":"Apple Music · 上次播放");source.setText("网易云音乐");
        musicTitle.setText(track==null?"你的音乐":track.name);musicArtist.setText(track==null?"Apple Music":track.artist);album.setText(track==null?"还没有播放记录":track.album);
        album.setVisibility(music.playing?GONE:VISIBLE);lyrics.setVisibility(music.playing?VISIBLE:GONE);progress.setVisibility(music.current()==null?GONE:VISIBLE);
        if(track==null){artworkId=-1;musicCover.setImageDrawable(new android.graphics.drawable.Drawable(){public void draw(Canvas c){Rect b=getBounds();MusicArtwork.draw(c,b.left,b.top,b.width());}public void setAlpha(int a){}public void setColorFilter(ColorFilter filter){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}});}
        else if(artworkId!=track.id){artworkId=track.id;musicCover.setImageDrawable(null);long id=track.id;music.previewCover(track,bitmap->{if(!closed&&artworkId==id&&bitmap!=null)musicCover.setImageBitmap(bitmap);});}
        removeCallbacks(tick);if(homeVisible&&getVisibility()==VISIBLE&&displayed==1){progress.invalidate();postDelayed(tick,400);lyrics.update();if(music.playing)lyrics.start();else lyrics.stop();}
    }
    @Override public void changed(){if(!closed&&displayed==1)renderMusic();}
    void updateBackdrop(){if(!closed)getBackground().invalidateSelf();}
    String snapshot(){JSONObject feed=feeds.get(displayed==0?"bilibili":"aurora");return "selected="+selected+" displayed="+displayed+" visible="+(homeVisible&&getVisibility()==VISIBLE)+" items="+(feed==null||feed.optJSONArray("items")==null?0:feed.optJSONArray("items").length())+" offset="+feedOffset+" sliding="+sliding+" artwork="+artworkId;}
    void close(){closed=true;stopCarousel();removeCallbacks(reveal);removeCallbacks(tick);animate().cancel();videoPage.animate().cancel();musicPage.animate().cancel();lyrics.stop();owner.music.remove(this);}
    private final class Progress extends View {
        private final Paint paint=new Paint(3);Progress(){super(owner);setFocusable(false);paint.setTypeface(GlassFonts.create(owner,400));}
        @Override protected void onDraw(Canvas c){int duration=owner.music.duration(),position=owner.music.playhead();float w=getWidth(),end=duration<=0?0:w*Math.min(1,position/(float)duration);paint.setColor(0x26ffffff);c.drawRoundRect(0,px(7),w,px(11),px(2),px(2),paint);paint.setColor(0xdfffffff);c.drawRoundRect(0,px(7),end,px(11),px(2),px(2),paint);paint.setTextSize(px(14));paint.setColor(0x99ffffff);paint.setTextAlign(Paint.Align.LEFT);c.drawText(time(position),0,px(36),paint);paint.setTextAlign(Paint.Align.RIGHT);c.drawText(time(duration),w,px(36),paint);}
        private String time(int ms){return String.format(Locale.US,"%d:%02d",Math.max(0,ms)/60000,Math.max(0,ms)/1000%60);}
    }
}
