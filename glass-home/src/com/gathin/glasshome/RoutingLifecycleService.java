package com.gathin.glasshome;

import android.service.notification.NotificationListenerService;

/** System-bound lifecycle anchor. Does not inspect, store, or handle notifications. */
public final class RoutingLifecycleService extends NotificationListenerService {
    @Override public void onListenerConnected(){HomeRouting.ensure(this,"system lifecycle binding");}
}
