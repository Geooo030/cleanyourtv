package com.gathin.glasshome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/** Queue decisions stay separate from MediaPlayer, pagination and view focus. */
final class PlaybackOrder {
    static final int SEQUENTIAL=0,LOOP=1,ONE=2,SHUFFLE=3;
    private final Random random;
    private final ArrayList<Integer> remaining=new ArrayList<>(),history=new ArrayList<>();
    private int mode=LOOP,knownCount=-1,historyIndex=-1;
    PlaybackOrder(Random random){this.random=random;}
    int mode(){return mode;}
    void mode(int value){mode=value>=0&&value<=3?value:LOOP;reset();}
    void reset(){remaining.clear();history.clear();knownCount=-1;historyIndex=-1;}
    private void refill(int current,int count){remaining.clear();for(int i=0;i<count;i++)if(i!=current)remaining.add(i);Collections.shuffle(remaining,random);knownCount=count;}
    int next(int current,int count,boolean completed){
        if(count<=0)return -1;
        if(mode==ONE&&completed)return Math.max(0,current);
        if(mode!=SHUFFLE){int next=current+1;return next<count?next:mode==SEQUENTIAL?-1:0;}
        if(historyIndex>=0&&historyIndex+1<history.size())return history.get(++historyIndex);
        if(knownCount!=count||remaining.isEmpty())refill(current,count);
        if(historyIndex<0){history.add(Math.max(0,current));historyIndex=0;}
        int selected=remaining.isEmpty()?Math.max(0,current):remaining.remove(remaining.size()-1);
        history.add(selected);historyIndex++;return selected;
    }
    int previous(int current,int count){
        if(count<=0)return -1;
        if(mode==SHUFFLE)return historyIndex>0?history.get(--historyIndex):Math.max(0,current);
        return current>0?current-1:mode==SEQUENTIAL?0:count-1;
    }
}
