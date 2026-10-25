package com.gathin.glasshome;

import android.content.Context;
import android.graphics.Color;
import android.media.tv.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Reference-style dropdown, populated from this television's real HDMI inputs. */
final class InputsOverlay extends OptionsOverlay {
    private final List<TvInputInfo> inputs;
    private final TvInputManager manager;
    private final List<TextView> states=new ArrayList<>();
    private final TvInputManager.TvInputCallback callback=new TvInputManager.TvInputCallback(){@Override public void onInputStateChanged(String id,int state){refreshStates();}};
    static List<TvInputInfo> available(GlassHomeActivity owner){
        TvInputManager m=(TvInputManager)owner.getSystemService(Context.TV_INPUT_SERVICE);List<TvInputInfo> result=new ArrayList<>();
        if(m!=null)for(TvInputInfo info:m.getTvInputList())if(info.getType()==TvInputInfo.TYPE_HDMI&&!info.isHidden(owner)&&info.getParentId()==null)result.add(info);
        result.sort((a,b)->a.getId().compareTo(b.getId()));return result;
    }
    InputsOverlay(GlassHomeActivity owner,List<TvInputInfo> inputs){super(owner,9,false,1248,212,530,112+Math.max(1,inputs.size())*82);this.inputs=inputs;manager=(TvInputManager)owner.getSystemService(Context.TV_INPUT_SERVICE);}
    @Override void buildPanel(){
        hint("HDMI 信号源 · 方向键选择，确认切换，返回关闭");
        panel.addView(label("HDMI 信号源",21,0xdfffffff,medium),box(28,20,460,42));
        if(inputs.isEmpty()){panel.addView(label("暂未发现可用的 HDMI 输入",19,0xbbffffff,regular),box(28,90,470,60));return;}
        View first=null,previous=null;
        for(int i=0;i<inputs.size();i++){
            TvInputInfo input=inputs.get(i);FrameLayout row=new FrameLayout(owner);row.setId(View.generateViewId());row.setFocusable(true);row.setClickable(true);panel.addView(row,box(12,80+i*82,506,74));
            ImageView icon=new ImageView(owner);icon.setImageResource(R.drawable.ic_input);row.addView(icon,box(22,20,30,32));
            CharSequence custom=input.loadCustomLabel(owner);CharSequence title=custom!=null?custom:input.loadLabel(owner);
            TextView name=label(title.toString(),24,Color.WHITE,medium);row.addView(name,box(74,12,265,50));row.setContentDescription(title+" 信号源");
            TextView state=label("",16,0x99ffffff,regular);state.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);row.addView(state,box(331,12,150,50));states.add(state);
            row.setOnFocusChangeListener((v,focused)->{row.setBackground(surface(focused?0xeeffffff:0x00ffffff,focused?0x88ffffff:0x00ffffff,px(22)));name.setTextColor(focused?0xff18202a:Color.WHITE);icon.setColorFilter(focused?0xff18202a:Color.WHITE);if(focused)hint(title+" · 确认切换信号源，返回关闭");});
            row.setOnClickListener(v->{afterClose=()->owner.openInput(input.getId());dismiss();});
            if(previous!=null){previous.setNextFocusDownId(row.getId());row.setNextFocusUpId(previous.getId());}else first=row;previous=row;
        }
        refreshStates();manager.registerCallback(callback,new android.os.Handler(android.os.Looper.getMainLooper()));
        View initial=first;panel.post(()->initial.requestFocusFromTouch());
    }
    private void refreshStates(){if(manager==null)return;for(int i=0;i<states.size();i++){int state=manager.getInputState(inputs.get(i).getId());boolean connected=state==TvInputManager.INPUT_STATE_CONNECTED;states.get(i).setText(connected?"● 已连接":state==TvInputManager.INPUT_STATE_CONNECTED_STANDBY?"待机":"未连接");states.get(i).setTextColor(connected?0xff56d48a:0xffa8afb5);}}
    @Override void onClosed(){if(manager!=null)manager.unregisterCallback(callback);}
}
