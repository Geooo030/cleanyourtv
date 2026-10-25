package com.gathin.glasshome;

import java.util.function.Consumer;

/** A brief Home / launcher handoff must not interrupt the home-owned player. */
final class MusicVisibility {
    interface Scheduler {
        void later(Runnable action,long delay);
        void cancel(Runnable action);
    }
    static final long BACKGROUND_DELAY=400;
    private final Scheduler scheduler;
    private final Consumer<Boolean> foreground;
    private boolean visible,background,closed,pending;
    private final Runnable confirmBackground=this::confirmBackground;

    MusicVisibility(Scheduler scheduler,Consumer<Boolean> foreground){this.scheduler=scheduler;this.foreground=foreground;}

    void resumed(){
        if(closed)return;
        visible=true;pending=false;scheduler.cancel(confirmBackground);
        if(background){background=false;foreground.accept(true);}
    }
    void stopped(){
        if(closed)return;
        visible=false;scheduler.cancel(confirmBackground);
        if(!background){pending=true;scheduler.later(confirmBackground,BACKGROUND_DELAY);}
    }
    private void confirmBackground(){
        pending=false;
        if(!closed&&!visible&&!background){background=true;foreground.accept(false);}
    }
    boolean pending(){return pending;}
    void close(){closed=true;pending=false;scheduler.cancel(confirmBackground);}
}
