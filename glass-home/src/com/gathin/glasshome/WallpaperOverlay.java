package com.gathin.glasshome;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.widget.*;
import java.util.*;

/** Two fixed pages share one interruptible horizontal animation; covers never play video. */
final class WallpaperOverlay extends OptionsOverlay {
    private TextView name,description,kind,status,rotation,close;
    private FrameLayout details,strip;
    private View indicator;
    private final TextView[] tabs=new TextView[2];
    private final FrameLayout[] pages=new FrameLayout[2];
    private final List<FrameLayout> dynamicCards=new ArrayList<>(),staticCards=new ArrayList<>();
    private final List<TextView> dynamicBadges=new ArrayList<>(),staticBadges=new ArrayList<>();
    private final List<WallpaperCatalog.Item> dynamicItems=WallpaperCatalog.list(true),staticItems=WallpaperCatalog.list(false);
    private WallpaperCatalog.Item selected;
    private boolean dynamicTab;
    private int lastDynamicIndex=1;
    private float tabPosition;
    private ValueAnimator tabMotion;
    private String lastApplied="";
    WallpaperOverlay(GlassHomeActivity owner){super(owner,5,true,195,120,1530,840);dynamicTab=owner.dynamicWallpaper();tabPosition=dynamicTab?0:1;selected=WallpaperCatalog.find(owner.selectedWallpaper());}
    @Override void buildPanel(){
        panel.addView(label("壁纸",29,Color.WHITE,medium),box(60,42,400,50));close=closeButton();
        details=new FrameLayout(owner);panel.addView(details,box(60,185,570,450));
        kind=label("",14,0xcaffffff,medium);details.addView(kind,box(4,0,560,30));
        name=label("",48,Color.WHITE,light);name.setMaxLines(2);details.addView(name,box(0,50,570,125));
        description=label("",22,0xcfffffff,regular);description.setGravity(Gravity.TOP);description.setLineSpacing(px(10),1);details.addView(description,box(4,200,550,150));
        status=label("",18,0xcfffffff,regular);details.addView(status,box(4,385,550,62));
        rotation=label("",21,Color.WHITE,medium);rotation.setPadding(px(24),0,px(24),0);rotation.setFocusable(true);rotation.setClickable(true);rotation.setId(View.generateViewId());rotation.setContentDescription("动态壁纸轮播开关");panel.addView(rotation,box(64,660,540,64));
        rotation.setOnClickListener(v->{owner.setWallpaperRotation(!owner.wallpaperRotation());refreshCards();showDetails();});rotation.setOnFocusChangeListener((v,f)->{refreshCards();if(f)hint("动态壁纸轮播 · 确认切换开启或关闭");});
        View divider=new View(owner);divider.setBackgroundColor(0x20ffffff);panel.addView(divider,box(672,170,1,560));
        indicator=new View(owner);indicator.setBackground(surface(0x39ffffff,0x25ffffff,px(26)));panel.addView(indicator,box(716,104,172,52));
        for(int i=0;i<2;i++){
            final int tab=i;TextView t=label(i==0?"动态":"静态",21,Color.WHITE,medium);tabs[i]=t;t.setGravity(Gravity.CENTER);t.setFocusable(true);t.setClickable(true);t.setId(View.generateViewId());panel.addView(t,box(716+i*190,104,172,52));
            t.setOnFocusChangeListener((v,f)->{if(f){switchTab(tab==0,false);hint((tab==0?"动态壁纸":"静态壁纸")+" · 左右切换分类，向下选择壁纸");}styleTabs();});t.setOnClickListener(v->{t.requestFocusFromTouch();switchTab(tab==0,false);});
        }
        tabs[0].setNextFocusRightId(tabs[1].getId());tabs[1].setNextFocusLeftId(tabs[0].getId());
        FrameLayout viewport=new FrameLayout(owner);viewport.setClipChildren(true);viewport.setClipToPadding(true);panel.addView(viewport,box(706,180,758,543));
        strip=new FrameLayout(owner);strip.setClipChildren(false);viewport.addView(strip,new FrameLayout.LayoutParams(px(1516),px(543)));
        for(int i=0;i<2;i++){pages[i]=new FrameLayout(owner);pages[i].setClipChildren(false);strip.addView(pages[i],box(i*758,0,758,543));buildPage(i);}
        tabs[0].setNextFocusDownId(dynamicCards.get(0).getId());tabs[1].setNextFocusDownId(staticCards.get(0).getId());close.setNextFocusDownId(tabs[1].getId());
        rotation.setNextFocusRightId(dynamicCards.get(0).getId());
        applyTab(tabPosition);activatePage();refreshCards();styleTabs();showDetails();
        panel.post(()->{List<WallpaperCatalog.Item> items=dynamicTab?dynamicItems:staticItems;int target=0;for(int i=0;i<items.size();i++)if(items.get(i).id.equals(owner.selectedWallpaper()))target=i;(dynamicTab?dynamicCards:staticCards).get(target).requestFocusFromTouch();});
    }
    private void buildPage(int page){
        List<WallpaperCatalog.Item> items=page==0?dynamicItems:staticItems;List<FrameLayout> cards=page==0?dynamicCards:staticCards;List<TextView> badges=page==0?dynamicBadges:staticBadges;
        for(int i=0;i<items.size();i++){
            final int position=i;final WallpaperCatalog.Item item=items.get(i);FrameLayout card=new FrameLayout(owner);cards.add(card);card.setFocusable(true);card.setClickable(true);card.setId(View.generateViewId());card.setContentDescription(item.name);pages[page].addView(card,box(10+(i%2)*374,15+(i/2)*260,352,238));
            ImageView cover=new ImageView(owner);cover.setImageBitmap(owner.wallpaperCover(item));cover.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.setClipToOutline(true);cover.setBackground(surface(0x10ffffff,0,px(17)));cover.setOutlineProvider(ViewOutlineProvider.BACKGROUND);card.addView(cover,box(14,14,324,147));
            card.addView(label(item.name,23,Color.WHITE,medium),box(18,172,316,35));TextView badge=label("",15,0xccffffff,regular);badges.add(badge);card.addView(badge,box(18,211,316,22));
            card.setOnFocusChangeListener((v,f)->{if(f){selected=item;if(page==0)lastDynamicIndex=position;showDetails();hint(item.name+" · 确认应用，左右浏览，返回关闭");}card.animate().cancel();card.animate().scaleX(f?1.025f:1).scaleY(f?1.025f:1).setDuration(200).setInterpolator(new PathInterpolator(.16f,1,.3f,1)).start();refreshCards();});
            card.setOnClickListener(v->{card.requestFocusFromTouch();selected=item;owner.chooseWallpaper(item);showDetails();refreshCards();});
            card.setOnHoverListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_HOVER_ENTER&&dynamicTab==(page==0))card.requestFocusFromTouch();return false;});
            card.setOnKeyListener((v,key,e)->{
                if(key<KeyEvent.KEYCODE_DPAD_UP||key>KeyEvent.KEYCODE_DPAD_RIGHT)return false;
                if(e.getAction()!=KeyEvent.ACTION_DOWN)return true;
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(page==0&&(position%2==1||position+1>=cards.size()))switchTab(false,true);else if(position%2==0&&position+1<cards.size())cards.get(position+1).requestFocus();}
                else if(key==KeyEvent.KEYCODE_DPAD_LEFT){if(page==1&&position%2==0)switchTab(true,true);else if(position%2==1)cards.get(position-1).requestFocus();else rotation.requestFocus();}
                else if(key==KeyEvent.KEYCODE_DPAD_UP){if(position<2)tabs[page].requestFocus();else cards.get(position-2).requestFocus();}
                else if(key==KeyEvent.KEYCODE_DPAD_DOWN&&position+2<cards.size())cards.get(position+2).requestFocus();return true;
            });
        }
    }
    private void switchTab(boolean dynamic,boolean focusCard){
        if(strip==null)return;
        boolean changed=dynamicTab!=dynamic;dynamicTab=dynamic;
        if(changed){activatePage();List<WallpaperCatalog.Item> items=dynamic?dynamicItems:staticItems;selected=items.get(dynamic?Math.min(lastDynamicIndex,items.size()-1):0);showDetails();details.animate().cancel();details.setAlpha(0);details.animate().alpha(1).setDuration(260).start();}
        if(tabMotion!=null){tabMotion.removeAllListeners();tabMotion.cancel();}
        float target=dynamic?0:1;
        if(ValueAnimator.areAnimatorsEnabled()&&Math.abs(tabPosition-target)>.001f){
            owner.setAppsMotion(true);tabMotion=ValueAnimator.ofFloat(tabPosition,target);tabMotion.setDuration(340);tabMotion.setInterpolator(new PathInterpolator(.22f,1,.36f,1));tabMotion.addUpdateListener(a->applyTab((float)a.getAnimatedValue()));tabMotion.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){owner.setAppsMotion(false);}});tabMotion.start();
        }else{applyTab(target);owner.setAppsMotion(false);}
        if(focusCard)(dynamic?dynamicCards:staticCards).get(dynamic?Math.min(lastDynamicIndex,dynamicCards.size()-1):0).requestFocus();
        styleTabs();refreshCards();
    }
    private void applyTab(float value){tabPosition=value;strip.setTranslationX(-px(758)*value);indicator.setTranslationX(px(190)*value);}
    private void activatePage(){for(int i=0;i<2;i++){boolean active=dynamicTab==(i==0);pages[i].setDescendantFocusability(active?android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS:android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);pages[i].setImportantForAccessibility(active?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}rotation.setFocusable(dynamicTab);rotation.setClickable(dynamicTab);rotation.animate().alpha(dynamicTab?1:0).setDuration(200).start();}
    private void styleTabs(){for(int i=0;i<2;i++)tabs[i].setBackground(surface(0,tabs[i].hasFocus()?0xaaffffff:0x25ffffff,px(26)));}
    private void showDetails(){kind.setText(selected.tag);name.setText(selected.name);description.setText(selected.detail);boolean current=owner.selectedWallpaper().equals(selected.id);status.setText(!selected.available(owner)?"素材尚未就绪":current?"✓ 正在使用  ·  "+(selected.dynamic?(owner.wallpaperRotation()?"整段播完后切换下一张":"单张循环播放"):"静态壁纸不参与轮播"):"尚未应用");}
    private void refreshCards(){lastApplied=owner.selectedWallpaper();for(int page=0;page<2;page++){List<FrameLayout> cards=page==0?dynamicCards:staticCards;List<WallpaperCatalog.Item> items=page==0?dynamicItems:staticItems;List<TextView> badges=page==0?dynamicBadges:staticBadges;for(int i=0;i<cards.size();i++){FrameLayout card=cards.get(i);card.setBackground(surface(card.hasFocus()?0x22ffffff:0x08ffffff,card.hasFocus()?0x99ffffff:0x20ffffff,px(23)));WallpaperCatalog.Item item=items.get(i);badges.get(i).setText(!item.available(owner)?"素材尚未就绪":owner.selectedWallpaper().equals(item.id)?"✓ 正在使用":item.dynamic?"动态 · Apple TV 航拍":"静态 · 图片");}}rotation.setText("动态壁纸轮播                  "+(owner.wallpaperRotation()?"开启 ●":"关闭 ○"));rotation.setBackground(surface(rotation.hasFocus()?0x30ffffff:0x0fffffff,rotation.hasFocus()?0xaaffffff:0x25ffffff,px(24)));}
    @Override void updateBackdrop(){super.updateBackdrop();if(status!=null&&!lastApplied.equals(owner.selectedWallpaper())){refreshCards();showDetails();}}
    @Override void onClosed(){if(tabMotion!=null){tabMotion.removeAllListeners();tabMotion.cancel();}details.animate().cancel();owner.setAppsMotion(false);}
}
