package com.gathin.glasshome;
public final class HomeKeyRouterTest {
    static void check(int actual,int expected,String message){if(actual!=expected)throw new AssertionError(message+": "+actual);}
    public static void main(String[] args){
        HomeKeyRouter keys=new HomeKeyRouter();
        check(keys.key(3,0,0,100,false,true),2,"Home routes on initial down");
        check(keys.key(3,0,0,100,false,true),1,"duplicate down cannot relaunch");
        check(keys.key(3,0,1,100,false,true),1,"held Home cannot reach system long press");
        check(keys.key(22,0,0,110,false,true),0,"directions pass through during Home");
        check(keys.key(3,1,0,100,false,true),1,"Home up must remain consumed after Glass resumes");
        check(keys.key(3,0,0,200,false,true),2,"another press routes once");
        check(keys.key(3,1,0,200,true,false),1,"owned canceled up consumed even after disable");
        check(keys.key(3,0,0,300,false,false),0,"disabled routing leaves system Home intact");
        check(keys.key(3,1,0,300,false,false),0,"disabled up passes through");
        check(keys.key(284,0,0,400,false,true),2,"alternate Home button");
        check(keys.key(284,1,0,400,false,true),1,"alternate Home up");
        check(keys.key(26,0,0,500,false,true),0,"power key unchanged");
        check(keys.key(24,0,0,500,false,true),0,"volume key unchanged");
        check(keys.key(3,0,0,600,true,true),1,"canceled down cannot open Home");
        check(keys.key(3,1,0,600,true,true),1,"canceled up cannot launch stock Home");
        System.out.println("PASS: direct Home, balanced down/up, holds, duplicate delivery, disable and unrelated keys");
    }
}
