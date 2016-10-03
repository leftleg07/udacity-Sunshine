package com.example.android.sunshine.app.wear;


import android.util.Log;

import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by heim on 10/2/16.
 */

public class WearableMessageListenerService extends WearableListenerService {
    private static final String LOG_TAG = WearableMessageListenerService.class.getSimpleName();

    private static final String START_ACTIVITY_PATH = "/start-activity";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        Log.i(LOG_TAG, "onMessageReceived");
        SunshineSyncAdapter.syncImmediately(getApplicationContext());

    }


}
