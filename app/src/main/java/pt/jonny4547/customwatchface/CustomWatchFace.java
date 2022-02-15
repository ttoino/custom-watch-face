package pt.jonny4547.customwatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationText;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class CustomWatchFace extends CanvasWatchFaceService {

    private static final String TAG = "Custom Watch Face";

    private static final int BACKGROUND_COMPLICATION_ID = 0;
    private static final int RANGE_COMPLICATION_ID = 1;
    private static final int TEXT_COMPLICATION_ID = 2;

    private static final int[] COMPLICATION_IDS = {
            BACKGROUND_COMPLICATION_ID, RANGE_COMPLICATION_ID, TEXT_COMPLICATION_ID
    };

    private static final int[][] COMPLICATION_SUPPORTED_TYPES = {
            {
                ComplicationData.TYPE_LARGE_IMAGE
            },
            {
                ComplicationData.TYPE_RANGED_VALUE
            },
            {
                ComplicationData.TYPE_SHORT_TEXT, ComplicationData.TYPE_LONG_TEXT
            }
    };

    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    private static final int MSG_UPDATE_TIME = 0;

    static int getComplicationId(CustomWatchFaceConfigActivity.ComplicationLocation complicationLocation) {
        switch (complicationLocation) {
            case BACKGROUND:
                return BACKGROUND_COMPLICATION_ID;
            case TOP:
                return TEXT_COMPLICATION_ID;
            case RANGE:
                return RANGE_COMPLICATION_ID;
            default:
                return -1;
        }
    }

    static int[] getComplicationIds() {
        return COMPLICATION_IDS;
    }

    static int[] getSupportedComplicationTypes(CustomWatchFaceConfigActivity.ComplicationLocation complicationLocation) {
        switch (complicationLocation) {
            case BACKGROUND:
                return COMPLICATION_SUPPORTED_TYPES[0];
            case RANGE:
                return COMPLICATION_SUPPORTED_TYPES[1];
            case TOP:
                return COMPLICATION_SUPPORTED_TYPES[2];
            default:
                return new int[] {};
        }
    }

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<Engine> mWeakReference;

        public EngineHandler(Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            Engine engine = mWeakReference.get();
            if (engine != null) {
                if (msg.what == MSG_UPDATE_TIME) {
                    engine.handleUpdateTimeMessage();
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private static final float SECOND_STROKE_WIDTH = 20f;
        private static final float TEXT_STROKE_WIDTH = 2f;
        private static final float SMALL_TEXT_STROKE_WIDTH = 1f;

        /* Handler to update the time once a second in interactive mode. */
        private final Handler updateTimeHandler = new EngineHandler(this);
        private Calendar calendar;
        private final BroadcastReceiver timeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean registeredTimeZoneReceiver = false;
        private boolean muteMode;
        private boolean ambient;

        private boolean secondMode = false;

        private float centerX;
        private float centerY;
        private float rangeRotation = -1;

        private String title = "";
        private String text = "";

        private Paint textPaint;
        private Paint smallTextPaint;
        private Paint secondPaint;

        private DateFormat timeFormat;

        private Drawable backgroundImage;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(CustomWatchFace.this)
                    .build());

            calendar = Calendar.getInstance();

            initializeComplications();
            initializeWatchFace();
        }

        private void initializeComplications() {
            setActiveComplications(COMPLICATION_IDS);
        }

        private void initializeWatchFace() {
            timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT);

            textPaint = new Paint();
            textPaint.setColor(0xAAFFFFFF);
            textPaint.setStrokeWidth(TEXT_STROKE_WIDTH);
            textPaint.setAntiAlias(true);
            textPaint.setStrokeCap(Paint.Cap.ROUND);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);

            smallTextPaint = new Paint();
            smallTextPaint.setColor(0x88FFFFFF);
            smallTextPaint.setStrokeWidth(SMALL_TEXT_STROKE_WIDTH);
            smallTextPaint.setAntiAlias(true);
            smallTextPaint.setStrokeCap(Paint.Cap.ROUND);
            smallTextPaint.setTextAlign(Paint.Align.CENTER);
            smallTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
            smallTextPaint.setLetterSpacing(0.025f);

            secondPaint = new Paint();
            secondPaint.setStyle(Paint.Style.STROKE);
            secondPaint.setColor(0x88FFFFFF);
            secondPaint.setStrokeWidth(SECOND_STROKE_WIDTH);
            secondPaint.setAntiAlias(true);
            secondPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public void onDestroy() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            secondMode = !secondMode;
            invalidate();
        }

        @Override
        public void onComplicationDataUpdate(int watchFaceComplicationId, ComplicationData data) {
            switch (watchFaceComplicationId) {
                case BACKGROUND_COMPLICATION_ID:
                    updateBackground(data);
                    break;
                case RANGE_COMPLICATION_ID:
                    updateRange(data);
                    break;
                case TEXT_COMPLICATION_ID:
                    updateText(data);
                    break;
            }

            invalidate();
        }

        private void updateBackground(ComplicationData data) {
            if (data == null)
                return;
            if (data.getType() != ComplicationData.TYPE_LARGE_IMAGE)
                Log.w(TAG, "Wrong background complication type: " + data.getType());

            Icon image = data.getLargeImage();
            if (image != null) {
                backgroundImage = image.loadDrawable(getApplicationContext());
                Rect bounds = backgroundImage.getBounds();
                float ar = bounds.width() == bounds.height() ? 1 : (float) bounds.width() / bounds.height();
                float newCX = centerX * ar;
                Rect newBounds = new Rect(0, 0, (int) (newCX * 2), (int) centerY * 2);
                newBounds.offsetTo((int) (centerX - newCX), 0);
                backgroundImage.setBounds(newBounds);
            }
        }

        private void updateRange(ComplicationData data) {
            if (data == null || data.getType() != ComplicationData.TYPE_RANGED_VALUE) {
                rangeRotation = -1;
                return;
            }

            float min = data.getMinValue();
            float max = data.getMaxValue();
            float value = data.getValue();
            rangeRotation = (value * 360)/(max-min);
        }

        private void updateText(ComplicationData data) {
            if (data == null || !(data.getType() == ComplicationData.TYPE_SHORT_TEXT || data.getType() == ComplicationData.TYPE_LONG_TEXT)) {
                text = "";
                title = "";
                return;
            }

            boolean isShort = data.getType() == ComplicationData.TYPE_SHORT_TEXT;
            long millis = calendar.getTimeInMillis();
            ComplicationText text = isShort ? data.getShortText() : data.getLongText();
            ComplicationText title = isShort ? data.getShortTitle() : data.getLongTitle();
            if (text != null) {
                this.text = text.getText(getApplicationContext(), millis).toString().trim();
            } else
                this.text = "";
            if (title != null) {
                this.title = title.getText(getApplicationContext(), millis).toString().trim();
                if (this.title.contains("("))
                    this.title = this.title.substring(0, this.title.lastIndexOf('(')).trim();
                if (this.title.contains(" - "))
                    this.title = this.title.substring(0, this.title.lastIndexOf('-')).trim();
            } else
                this.title = "";
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            ambient = inAmbientMode;

            updateStyle();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateStyle() {
            if (ambient) {
                textPaint.setAntiAlias(false);
                smallTextPaint.setAntiAlias(false);

                textPaint.setStyle(Paint.Style.STROKE);
                smallTextPaint.setStyle(Paint.Style.STROKE);
            } else {
                textPaint.setAntiAlias(true);
                smallTextPaint.setAntiAlias(true);

                textPaint.setStyle(Paint.Style.FILL);
                smallTextPaint.setStyle(Paint.Style.FILL);
            }
        }

        //TODO
        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (muteMode != inMuteMode) {
                muteMode = inMuteMode;
                secondPaint.setAlpha(inMuteMode ? 80 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            centerX = width / 2f;
            centerY = height / 2f;

            textPaint.setTextSize(height*.25f);
            smallTextPaint.setTextSize(height*.075f);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            calendar.setTimeInMillis(now);

            drawBackground(canvas);
            drawWatchFace(canvas);
        }

        private void drawBackground(Canvas canvas) {
            if (ambient || backgroundImage == null)
                canvas.drawColor(Color.BLACK);
            else {
                backgroundImage.draw(canvas);
                canvas.drawColor(0xAA000000);
            }
        }

        private void drawWatchFace(Canvas canvas) {
            final float centerY = this.centerY + canvas.getHeight()*.10f;
            canvas.drawText(timeFormat.format(calendar.getTime()).split(" ")[0], centerX, centerY, textPaint);

            if (!ambient) {
                canvas.drawText(title, centerX, centerY - canvas.getHeight()*.20f, smallTextPaint);
                canvas.drawText(text, centerX, centerY + canvas.getHeight()*.075f, smallTextPaint);

                float secondsRotation = calendar.get(Calendar.SECOND) * 6f;
                if (secondMode) {
                    secondsRotation = 360 - secondsRotation;
                }

                float s = SECOND_STROKE_WIDTH*.5f;

                canvas.save();
                canvas.rotate(-.5f*secondsRotation, centerX, this.centerY);
                canvas.drawArc(s, s, canvas.getWidth()-s, canvas.getHeight()-s, 0, secondsRotation, false, secondPaint);
                canvas.restore();

                if (rangeRotation >= 0) {
                    canvas.save();
                    canvas.rotate(90, centerX, this.centerY);
                    canvas.rotate(-.5f*rangeRotation, centerX, this.centerY);
                    canvas.drawArc(s, s, canvas.getWidth()-s, canvas.getHeight()-s, 0, rangeRotation, false, secondPaint);
                    canvas.restore();
                }
            } else {
                canvas.drawCircle(centerX, this.centerY, canvas.getWidth()*.5f-20f, smallTextPaint);
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void registerReceiver() {
            if (registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            CustomWatchFace.this.registerReceiver(timeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = false;
            CustomWatchFace.this.unregisterReceiver(timeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #updateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #updateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !ambient;
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
                updateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
