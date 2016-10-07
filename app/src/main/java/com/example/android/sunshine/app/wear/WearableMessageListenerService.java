package com.example.android.sunshine.app.wear;


import android.util.Log;

import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * message api listener
 */

public class WearableMessageListenerService extends WearableListenerService {
    private static final String LOG_TAG = WearableMessageListenerService.class.getSimpleName();

    private static final String SYNC_WEATHER_PATH = "/sync-weather";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        Log.i(LOG_TAG, "onMessageReceived");
        if(messageEvent.getPath().equals(SYNC_WEATHER_PATH)) {
            SunshineSyncAdapter.syncImmediately(getApplicationContext());
        }

    }


}
