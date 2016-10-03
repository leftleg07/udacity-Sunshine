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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunShineWatchFace extends CanvasWatchFaceService  {

    private static final String LOG_TAG = SunShineWatchFace.class.getSimpleName();


    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }


    private static class EngineHandler extends Handler {
        private final WeakReference<SunShineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunShineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunShineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }


    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks, CapabilityApi.CapabilityListener {
        private final String TAG = Engine.class.getSimpleName();

        /* name of the capability that the phone side provides */
        private final String CONFIRMATION_HANDLER_CAPABILITY_NAME = "confirmation_handler";
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mIconPaint;
        Paint mDayTextPaint;
        Paint mTimeTextPaint;
        Paint mConditionTextPaint;
        Paint mMaxTextPaint;
        Paint mMinTextPaint;


        boolean mAmbient;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mDayYOffset;
        float mTimeYOffset;
        float mIconYOffset;
        float mConditionYOffset;
        float mMaxXOffset;
        float mMaxYOffset;
        float mMinYOffset;
        float mMinXOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        private int mWeatherId = 200;
        private double mMaxTemperature = 1f;
        private double mMinTemperature = 0f;

        private GoogleApiClient mGoogleApiClient;
        /* the preferred note that can handle the confirmation capability */
        private Node mConfirmationHandlerNode;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                    .addApi(Wearable.API)
                    .addOnConnectionFailedListener(this)
                    .addConnectionCallbacks(this)
                    .build();

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunShineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunShineWatchFace.this.getResources();
            mDayYOffset = resources.getDimension(R.dimen.day_y_offset);
            mTimeYOffset = resources.getDimension(R.dimen.digital_time_y_offset);

            mIconYOffset = resources.getDimension(R.dimen.icon_y_offset);
            mConditionYOffset = resources.getDimension(R.dimen.condition_y_offset);
            mMaxYOffset = resources.getDimension(R.dimen.max_y_offset);
            mMinYOffset = resources.getDimension(R.dimen.min_y_offset);
            mMaxXOffset = resources.getDimension(R.dimen.max_x_offset);
            mMinXOffset = resources.getDimension(R.dimen.min_x_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mIconPaint = new Paint();
            mIconPaint.setColor(resources.getColor(R.color.background));

            mDayTextPaint = new Paint();
            mDayTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mTimeTextPaint = new Paint();
            mTimeTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mConditionTextPaint = new Paint();
            mConditionTextPaint = createTextPaint(resources.getColor(R.color.secondary_text));

            mMaxTextPaint = new Paint();
            mMaxTextPaint = createTextPaint(resources.getColor(R.color.primary_text));

            mMinTextPaint = new Paint();
            mMinTextPaint = createTextPaint(resources.getColor(R.color.secondary_text));

            mCalendar = Calendar.getInstance();
        }


        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if(mGoogleApiClient.isConnected()) {
                mGoogleApiClient.disconnect();
            }
            super.onDestroy();
        }

        private Collection<String> getNodes() {
            HashSet<String> results = new HashSet<>();
            NodeApi.GetConnectedNodesResult nodes =
                    Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();

            for (Node node : nodes.getNodes()) {
                results.add(node.getId());
            }

            return results;
        }

        @Override
        public void onConnected(Bundle connectionHint) {
            Log.d(TAG, "onConnected(): Successfully connected to Google API client");
            setupConfirmationHandlerNode();
                sendStartActivityMessage();
        }

        private static final String START_ACTIVITY_PATH = "/start-activity";

        private void sendStartActivityMessage() {
            if (mConfirmationHandlerNode != null) {
                Wearable.MessageApi.sendMessage(mGoogleApiClient, mConfirmationHandlerNode.getId(),
                        START_ACTIVITY_PATH, new byte[0])
                        .setResultCallback(getSendMessageResultCallback(mConfirmationHandlerNode));
            } else {
                Toast.makeText(getApplicationContext(), R.string.no_device_found, Toast.LENGTH_SHORT).show();
            }
        }
        private ResultCallback<MessageApi.SendMessageResult> getSendMessageResultCallback(
                final Node node) {
            return new ResultCallback<MessageApi.SendMessageResult>() {
                @Override
                public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                    if (!sendMessageResult.getStatus().isSuccess()) {
                        Log.e(TAG, "Failed to send message with status "
                                + sendMessageResult.getStatus());
                    } else {
                        Log.d(TAG, "Sent confirmation message to node " + node.getDisplayName());
                    }
                }
            };
        }

        private void setupConfirmationHandlerNode() {
            Wearable.CapabilityApi.addCapabilityListener(
                    mGoogleApiClient, this, CONFIRMATION_HANDLER_CAPABILITY_NAME);

            Wearable.CapabilityApi.getCapability(
                    mGoogleApiClient, CONFIRMATION_HANDLER_CAPABILITY_NAME,
                    CapabilityApi.FILTER_REACHABLE).setResultCallback(
                    new ResultCallback<CapabilityApi.GetCapabilityResult>() {
                        @Override
                        public void onResult(CapabilityApi.GetCapabilityResult result) {
                            if (!result.getStatus().isSuccess()) {
                                Log.e(TAG, "setupConfirmationHandlerNode() Failed to get capabilities, "
                                        + "status: " + result.getStatus().getStatusMessage());
                                return;
                            }
                            updateConfirmationCapability(result.getCapability());
                        }
                    });
        }

        private void updateConfirmationCapability(CapabilityInfo capabilityInfo) {
            Set<Node> connectedNodes = capabilityInfo.getNodes();
            if (connectedNodes.isEmpty()) {
                mConfirmationHandlerNode = null;
            } else {
                mConfirmationHandlerNode = pickBestNode(connectedNodes);
            }
        }

        /**
         * We pick a node that is capabale of handling the confirmation. If there is more than one,
         * then we would prefer the one that is directly connected to this device. In general,
         * depending on the situation and requirements, the "best" node might be picked based on other
         * criteria.
         */
        private Node pickBestNode(Set<Node> connectedNodes) {
            Node best = null;
            if (connectedNodes != null) {
                for (Node node : connectedNodes) {
                    if (node.isNearby()) {
                        return node;
                    }
                    best = node;
                }
            }
            return best;
        }
        @Override
        public void onConnectionSuspended(int cause) {
            mConfirmationHandlerNode = null;
            Log.d(TAG, "onConnectionSuspended(): Connection to Google API client was suspended");
        }

        @Override
        public void onConnectionFailed(ConnectionResult result) {
            Log.e(TAG, "onConnectionFailed(): Failed to connect, with result: " + result);
        }

        @Override
        public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
            updateConfirmationCapability(capabilityInfo);
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                if(!mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.connect();
                }
                registerReceiver();


                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                if(mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.disconnect();
                }
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunShineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunShineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunShineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float dayTextSize = resources.getDimension(isRound
                    ? R.dimen.day_text_size_round : R.dimen.day_text_size);
            mDayTextPaint.setTextSize(dayTextSize);

            float timeTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_time_text_size_round : R.dimen.digital_time_text_size);
            mTimeTextPaint.setTextSize(timeTextSize);

            float conditionTextSize = resources.getDimension(isRound
                    ? R.dimen.condition_text_size_round : R.dimen.condition_text_size);
            mConditionTextPaint.setTextSize(conditionTextSize);

            float maxTextSize = resources.getDimension(isRound
                    ? R.dimen.max_text_size_round : R.dimen.max_text_size);
            mMaxTextPaint.setTextSize(maxTextSize);

            float minTextSize = resources.getDimension(isRound
                    ? R.dimen.min_text_size_round : R.dimen.min_text_size);
            mMinTextPaint.setTextSize(minTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mDayTextPaint.setAntiAlias(!inAmbientMode);
                    mTimeTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
                String day = Utility.getFriendlyDayString(now);
                canvas.drawText(day, mXOffset, mDayYOffset, mDayTextPaint);
                int iconId = Utility.getIconResourceForWeatherCondition(mWeatherId);
                Bitmap icon = BitmapFactory.decodeResource(getResources(), iconId);
                canvas.drawBitmap(icon, mXOffset, mIconYOffset, mIconPaint);

                String condition = Utility.getStringForWeatherCondition(getApplicationContext(), mWeatherId);
                canvas.drawText(condition, mXOffset, mConditionYOffset, mConditionTextPaint);

                String formattedMaxTemperature = Utility.formatTemperature(getApplicationContext(), mMaxTemperature);
                canvas.drawText(formattedMaxTemperature, mMaxXOffset, mMaxYOffset, mMaxTextPaint);

                String formattedMixTemperature = Utility.formatTemperature(getApplicationContext(), mMinTemperature);
                canvas.drawText(formattedMixTemperature, mMinXOffset, mMinYOffset, mMinTextPaint);
            }


            String time = mAmbient
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%d:%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));
            canvas.drawText(time, mXOffset, mTimeYOffset, mTimeTextPaint);


        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }



    }
}
