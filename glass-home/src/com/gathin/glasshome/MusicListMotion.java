package com.gathin.glasshome;

import android.content.Context;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;

/** Scroll the viewport before committing selection, avoiding AbsListView's edge jump. */
final class MusicListMotion {
    private final AbsListView list;
    private final Runnable changed;
    private int pending=-1;
    private long until;
    MusicListMotion(AbsListView list,Runnable changed){this.list=list;this.changed=changed;list.setOverScrollMode(View.OVER_SCROLL_NEVER);}
    static MusicListMotion of(AbsListView list){return list instanceof MusicSongList?((MusicSongList)list).motion:list instanceof MusicPlaylistGrid?((MusicPlaylistGrid)list).motion:null;}
    static int selected(AbsListView list){MusicListMotion motion=of(list);return motion!=null&&motion.pending>=0?motion.pending:list.getSelectedItemPosition();}
    static View selectedView(AbsListView list){int index=selected(list)-list.getFirstVisiblePosition();return index>=0&&index<list.getChildCount()?list.getChildAt(index):null;}
    private final Runnable frame=new Runnable(){public void run(){
        if(pending<0)return;if(!list.isShown()){stop();return;}
        changed.run();if(SystemClock.uptimeMillis()<until){list.postOnAnimation(this);return;}
        int target=pending;View row=selectedView(list);pending=-1;
        if(target>=0&&target<list.getCount()){
            if(list instanceof ListView&&row!=null)((ListView)list).setSelectionFromTop(target,row.getTop()-list.getPaddingTop());else list.setSelection(target);
        }
        list.post(changed);
    }};
    boolean key(int key){
        if(pending>=0&&(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER)){
            int target=pending;View row=selectedView(list);if(row==null)return true;stop();
            if(target>=0&&target<list.getCount()){list.setSelection(target);list.performItemClick(row,target,list.getAdapter().getItemId(target));}return true;
        }
        int current=selected(list),count=list.getCount(),columns=list instanceof GridView?((GridView)list).getNumColumns():1;
        boolean vertical=key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_UP;
        boolean horizontal=columns>1&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT);
        if(!vertical&&!horizontal)return false;
        if(current<0||current>=count||list.getChildCount()==0)return false;
        boolean down=key==KeyEvent.KEYCODE_DPAD_DOWN;
        if(vertical&&(down&&current/columns==(count-1)/columns||!down&&current<columns))return pending>=0;
        int next=horizontal?current+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1):down?Math.min(count-1,current+columns):current-columns;
        if(next<0||next>=count||horizontal&&next/columns!=current/columns)return false;
        View first=list.getChildAt(0);int firstIndex=list.getFirstVisiblePosition();
        int stride=first.getHeight();
        if(list.getChildCount()>columns)stride=list.getChildAt(columns).getTop()-first.getTop();
        else if(list instanceof GridView)stride+=((GridView)list).getVerticalSpacing();
        else stride+=((ListView)list).getDividerHeight();
        int top=first.getTop()+(next/columns-firstIndex/columns)*stride;
        int bottom=top+first.getHeight(),delta=top<list.getPaddingTop()?top-list.getPaddingTop():Math.max(0,bottom-(list.getHeight()-list.getPaddingBottom()));
        list.removeCallbacks(frame);list.smoothScrollBy(0,0);pending=next;
        if(delta==0||!android.animation.ValueAnimator.areAnimatorsEnabled()){
            pending=-1;if(list instanceof ListView)((ListView)list).setSelectionFromTop(next,top-list.getPaddingTop());else list.setSelection(next);list.post(changed);
        }else{
            // Native scrolling keeps recycled rows and image caches intact while flinging.
            list.smoothScrollBy(delta,400);until=SystemClock.uptimeMillis()+440;list.postOnAnimation(frame);
        }
        return true;
    }
    void stop(){list.removeCallbacks(frame);pending=-1;list.smoothScrollBy(0,0);}
}
final class MusicSongList extends ListView {
    final MusicListMotion motion;
    MusicSongList(Context context,Runnable changed){super(context);motion=new MusicListMotion(this,changed);}
    @Override public boolean onKeyDown(int key,KeyEvent event){return motion.key(key)||super.onKeyDown(key,event);}
    @Override protected void onDetachedFromWindow(){motion.stop();super.onDetachedFromWindow();}
}
final class MusicPlaylistGrid extends GridView {
    final MusicListMotion motion;
    MusicPlaylistGrid(Context context,Runnable changed){super(context);motion=new MusicListMotion(this,changed);}
    @Override public boolean onKeyDown(int key,KeyEvent event){return motion.key(key)||super.onKeyDown(key,event);}
    @Override protected void onDetachedFromWindow(){motion.stop();super.onDetachedFromWindow();}
}
