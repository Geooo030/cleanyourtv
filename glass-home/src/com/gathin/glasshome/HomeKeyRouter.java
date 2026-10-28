package com.gathin.glasshome;

/** Consume both halves of a Home press before the system launcher sees it. */
final class HomeKeyRouter {
    static final int PASS=0,CONSUME=1,RETURN_HOME=2;
    private int held=-1;private long downTime=-1;
    int key(int key,int action,int repeat,long time,boolean canceled,boolean enabled){
        if(key!=3&&key!=284)return PASS; // KEYCODE_HOME / KEYCODE_ALL_APPS
        boolean owns=held==key&&downTime==time;
        if(action==1){if(owns){held=-1;downTime=-1;return CONSUME;}return enabled?CONSUME:PASS;}
        if(action!=0)return owns||enabled?CONSUME:PASS;
        if(owns)return CONSUME;
        if(!enabled)return PASS;
        if(repeat>0||canceled)return CONSUME;
        held=key;downTime=time;return RETURN_HOME;
    }
}
