package com.example.android.sunshine.app;

import android.app.Application;

import com.facebook.stetho.Stetho;

/**
 * stetho inspect
 */

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Stetho.initializeWithDefaults(this);
    }
}
