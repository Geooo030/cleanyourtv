package com.gathin.glasshome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Deterministic lifecycle regression tests; no Android device or audio file needed. */
public final class MusicVisibilityTest {
    private static final class Clock implements MusicVisibility.Scheduler {
        long now;
        final Map<Runnable,Long> jobs=new HashMap<>();
        public void later(Runnable action,long delay){jobs.put(action,now+delay);}
        public void cancel(Runnable action){jobs.remove(action);}
        void advance(long duration){
            now+=duration;
            for(Runnable job:new ArrayList<>(jobs.keySet()))if(jobs.get(job)<=now){jobs.remove(job);job.run();}
        }
    }
    private static final class Player {
        boolean playing=true,loading;
        int pauses;
        final long song=1329999687L;
        int position=23000;
        final ArrayList<Boolean> transitions=new ArrayList<>();
        void foreground(boolean value){transitions.add(value);if(!value){playing=false;loading=false;pauses++;}}
    }
    private static void check(boolean result,String message){if(!result)throw new AssertionError(message);}

    public static void main(String[] args){
        Clock clock=new Clock();Player player=new Player();MusicVisibility state=new MusicVisibility(clock,player::foreground);
        state.resumed();
        // singleTask onNewIntent: onPause -> onNewIntent -> onResume, without onStop.
        state.resumed();clock.advance(1000);
        check(player.playing&&player.pauses==0,"Home intent must not pause playback");
        // Xiaomi can briefly visit the stock launcher before routing back to Glass.
        state.stopped();clock.advance(250);state.resumed();clock.advance(1000);
        check(player.playing&&player.song==1329999687L&&player.position==23000,"Home handoff must preserve song and progress");
        check(!state.pending()&&player.transitions.isEmpty(),"Home must cancel the pending background pause");
        // Press Home while a track is buffering: it should finish, not be cancelled.
        player.loading=true;state.stopped();clock.advance(150);state.resumed();clock.advance(1000);
        check(player.loading&&player.pauses==0,"Home must preserve an in-flight song load");
        // Leaving for a real application or standby still relinquishes audio.
        state.stopped();clock.advance(399);check(player.playing,"Do not pause before the handoff grace expires");
        clock.advance(1);check(!player.playing&&!player.loading&&player.pauses==1,"A stopped home must pause exactly once");
        clock.advance(1000);check(player.pauses==1,"Do not repeat the background pause");
        state.resumed();check(!player.playing&&player.transitions.get(1),"Returning from another app must not force autoplay");
        state.stopped();state.resumed();state.stopped();clock.advance(400);
        check(player.pauses==2,"Rapid visibility changes must keep only the latest pending pause");
        state.resumed();state.stopped();state.close();clock.advance(1000);
        check(player.pauses==2&&!state.pending(),"Destroy must cancel callbacks into a released player");
        state.resumed();state.stopped();clock.advance(1000);
        check(player.pauses==2,"A destroyed visibility controller must remain inert");
        System.out.println("PASS: Home intent, launcher handoff, buffering, app exit, rapid transitions, destruction");
    }
}
