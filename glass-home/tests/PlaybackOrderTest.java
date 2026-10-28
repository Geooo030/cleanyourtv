package com.gathin.glasshome;
import java.util.*;
public final class PlaybackOrderTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        PlaybackOrder order=new PlaybackOrder(new Random(42));
        order.mode(PlaybackOrder.SEQUENTIAL);check(order.next(2,3,true)==-1,"sequence stops at end");check(order.next(0,3,true)==1,"sequence advances");
        order.mode(PlaybackOrder.LOOP);check(order.next(2,3,true)==0,"loop wraps");check(order.previous(0,3)==2,"previous wraps");
        order.mode(PlaybackOrder.ONE);check(order.next(1,3,true)==1,"repeat one completion");check(order.next(1,3,false)==2,"manual next escapes repeat one");
        order.mode(PlaybackOrder.SHUFFLE);int current=0;Set<Integer> heard=new HashSet<>();heard.add(current);
        for(int i=0;i<19;i++){int next=order.next(current,20,true);check(!heard.contains(next),"shuffle visits every song once");heard.add(next);current=next;}
        check(heard.size()==20,"full queue shuffled");int last=current;int previous=order.previous(current,20);check(previous!=last,"shuffle history previous");check(order.next(previous,20,false)==last,"shuffle history forward");
        check(order.next(last,20,true)!=last,"new shuffle cycle avoids immediate repeat");order.reset();check(order.next(0,1,true)==0,"one song shuffle");check(order.next(0,0,true)==-1,"empty queue");
        order.mode(99);check(order.mode()==PlaybackOrder.LOOP,"invalid persisted mode");
        System.out.println("Playback order: sequence, loop, repeat, shuffle coverage/history and empty queues passed");
    }
}
