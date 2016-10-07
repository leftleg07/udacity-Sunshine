/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.net.Uri;
import android.util.Log;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

import org.greenrobot.eventbus.EventBus;

import static org.greenrobot.eventbus.EventBus.TAG;

/**
 * Listens data api
 */
public class WearableDataListenerService extends WearableListenerService {
    private static final String LOG_TAG = WearableDataListenerService.class.getSimpleName();

    private static final String WEATHER_PATH = "/weather";
    private static final String WEATHER_ID_KEY = "id";
    private static final String HIGH_KEY = "high";
    private static final String LOW_KEY = "low";
    private static final String DESC_KEY = "desc";
    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        super.onDataChanged(dataEventBuffer);
        Log.i(LOG_TAG, "onDataChanged");
        if (dataEventBuffer == null) {
            return;
        }
        for (DataEvent dataEvent : dataEventBuffer) {
            Uri uri = dataEvent.getDataItem().getUri();
            String path = uri.getPath();
            if (path.startsWith(WEATHER_PATH)) {
                DataItem weatherDataItem = dataEvent.getDataItem();
                DataMapItem weatherDataMapItem = DataMapItem.fromDataItem(weatherDataItem);
                DataMap weatehrDataMap = weatherDataMapItem.getDataMap();

                int weatherId = weatehrDataMap.getInt(WEATHER_ID_KEY);
                double high = weatehrDataMap.getDouble(HIGH_KEY);
                double low = weatehrDataMap.getDouble(LOW_KEY);
                String desc = weatehrDataMap.getString(DESC_KEY);
                Log.i(TAG, "weatherId: " + weatherId + ", high: "+ high + ", low: "  + low + ", desc: " + desc);

                EventBus.getDefault().post(new SunShineWatchFace.WeatherEvent(weatherId, high, low, desc));
            }
        }
    }
}
