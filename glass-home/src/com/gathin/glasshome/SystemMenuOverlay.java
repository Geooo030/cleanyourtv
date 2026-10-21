package com.gathin.glasshome;

import android.graphics.Color;
import android.view.*;
import android.widget.*;

/** Wi-Fi and settings use the same anchored glass dropdown as HDMI. */
final class SystemMenuOverlay extends OptionsOverlay {
    private final boolean wifi;
    SystemMenuOverlay(GlassHomeActivity owner,boolean wifi){super(owner,wifi?6:7,false,1248,212,530,wifi?350:382);this.wifi=wifi;}
    @Override void buildPanel(){
        panel.addView(label(wifi?"Wi-Fi":"设置",23,Color.WHITE,medium),box(28,20,460,44));
        if(wifi){
            panel.addView(label(owner.networkSummary(),25,Color.WHITE,medium),box(30,92,460,45));
            panel.addView(label("IP  "+owner.networkAddress(),18,0xbbffffff,regular),box(30,150,460,38));
            panel.addView(label("选择网络或输入密码，请打开网络设置",16,0x99ffffff,regular),box(30,203,465,32));
            row("网络设置",R.drawable.ic_wifi,260,()->owner.openSettings(true)).requestFocus();
        }else{
            View a=row("壁纸",0,82,()->owner.activate(5));View b=row("全部应用",0,170,()->owner.activate(4));View c=row("电视系统设置",R.drawable.ic_settings,258,()->owner.openSettings(false));
            a.setNextFocusDownId(b.getId());b.setNextFocusUpId(a.getId());b.setNextFocusDownId(c.getId());c.setNextFocusUpId(b.getId());panel.post(a::requestFocus);
        }
    }
    private View row(String title,int iconRes,float y,Runnable action){
        FrameLayout row=new FrameLayout(owner);row.setId(View.generateViewId());row.setFocusable(true);row.setClickable(true);row.setContentDescription(title);panel.addView(row,box(12,y,506,74));
        TextView text=label(title,24,Color.WHITE,medium);panelX(row,text,74,12,385,50);
        ImageView icon=new ImageView(owner);if(iconRes!=0)icon.setImageResource(iconRes);else icon.setImageResource(title.equals("壁纸")?R.drawable.ic_wallpaper:R.drawable.ic_apps);panelX(row,icon,23,22,28,28);
        row.setOnFocusChangeListener((v,f)->{row.setBackground(surface(f?0xeeffffff:0,0,px(22)));text.setTextColor(f?0xff18202a:Color.WHITE);icon.setColorFilter(f?0xff18202a:Color.WHITE);});row.setOnClickListener(v->{afterClose=action;dismiss();});return row;
    }
    private void panelX(FrameLayout parent,View child,float x,float y,float w,float h){parent.addView(child,box(x,y,w,h));}
}
