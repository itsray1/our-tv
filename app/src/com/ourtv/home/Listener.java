package com.ourtv.home;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so the TV lets Our TV see what is playing (Spotify's title and artist).
 * Notifications themselves are never read.
 */
public class Listener extends NotificationListenerService {
}
