package pt.jonny4547.customwatchface;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.Nullable;
import android.support.wearable.complications.ComplicationHelperActivity;
import android.support.wearable.complications.ComplicationProviderInfo;
import android.support.wearable.complications.ProviderChooserIntent;
import android.support.wearable.complications.ProviderInfoRetriever;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

import java.util.concurrent.Executors;

public class CustomWatchFaceConfigActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "Custom Watch Face Config";
    static final int COMPLICATION_CONFIG_REQUEST_CODE = 1201;

    public enum ComplicationLocation {
        BACKGROUND,
        RANGE,
        TOP
    }

    private int backgroundComplicationId;
    private int rangeComplicationId;
    private int textComplicationId;

    private int selectedComplicationId;

    private ComponentName watchFaceComponentName;

    private ProviderInfoRetriever providerInfoRetriever;

    private ImageButton backgroundComplication;
    private ImageButton rangeComplication;
    private ImageButton textComplication;

    private Drawable defaultAddComplicationDrawable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_config);

        defaultAddComplicationDrawable = getDrawable(R.drawable.ic_add_white_24dp);

        selectedComplicationId = -1;

        backgroundComplicationId = CustomWatchFace.getComplicationId(ComplicationLocation.BACKGROUND);
        rangeComplicationId = CustomWatchFace.getComplicationId(ComplicationLocation.RANGE);
        textComplicationId = CustomWatchFace.getComplicationId(ComplicationLocation.TOP);

        backgroundComplication = findViewById(R.id.background_complication);
        backgroundComplication.setOnClickListener(this);
        rangeComplication = findViewById(R.id.range_complication);
        rangeComplication.setOnClickListener(this);
        textComplication = findViewById(R.id.text_complication);
        textComplication.setOnClickListener(this);

        watchFaceComponentName = new ComponentName(getApplicationContext(), CustomWatchFace.class);

        providerInfoRetriever = new ProviderInfoRetriever(getApplicationContext(), Executors.newCachedThreadPool());
        providerInfoRetriever.init();

        retrieveInitialComplicationsData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        providerInfoRetriever.release();
    }

    public void retrieveInitialComplicationsData() {
        final int[] complicationIds = CustomWatchFace.getComplicationIds();

        providerInfoRetriever.retrieveProviderInfo(
                new ProviderInfoRetriever.OnProviderInfoReceivedCallback() {
                    @Override
                    public void onProviderInfoReceived(int watchFaceComplicationId, @Nullable ComplicationProviderInfo info) {
                        Log.d(TAG, "onProviderInfoReceived: " + info);
                        updateComplicationViews(watchFaceComplicationId, info);
                    }
                },
                watchFaceComponentName,
                complicationIds
        );
    }

    @Override
    public void onClick(View view) {
        if (view == backgroundComplication) {
            launchComplicationHelperActivity(ComplicationLocation.BACKGROUND);
        }
        if (view == rangeComplication) {
            launchComplicationHelperActivity(ComplicationLocation.RANGE);
        }
        if (view == textComplication) {
            launchComplicationHelperActivity(ComplicationLocation.TOP);
        }
    }

    private void launchComplicationHelperActivity(ComplicationLocation complicationLocation) {
        selectedComplicationId = CustomWatchFace.getComplicationId(complicationLocation);

        if (selectedComplicationId >= 0) {
            int[] supportedTypes = CustomWatchFace.getSupportedComplicationTypes(complicationLocation);

            startActivityForResult(ComplicationHelperActivity.createProviderChooserHelperIntent(
                    getApplicationContext(),
                    watchFaceComponentName,
                    selectedComplicationId,
                    supportedTypes
            ), COMPLICATION_CONFIG_REQUEST_CODE);
        } else {
            Log.d(TAG, "Complication not supported");
        }
    }

    public void updateComplicationViews(int watchFaceComplicationId, ComplicationProviderInfo complicationProviderInfo) {
        Log.d(TAG, "updateComplicationViews(): id: " + watchFaceComplicationId);
        Log.d(TAG, "\tinfo: " + complicationProviderInfo);

        if (watchFaceComplicationId == backgroundComplicationId) {
            if (complicationProviderInfo != null) {
                backgroundComplication.setImageIcon(complicationProviderInfo.providerIcon);
            } else {
                backgroundComplication.setImageDrawable(defaultAddComplicationDrawable);
            }
        } else if (watchFaceComplicationId == rangeComplicationId) {
            if (complicationProviderInfo != null) {
                rangeComplication.setImageIcon(complicationProviderInfo.providerIcon);
            } else {
                rangeComplication.setImageDrawable(defaultAddComplicationDrawable);
            }
        } else if (watchFaceComplicationId == textComplicationId) {
            if (complicationProviderInfo != null) {
                textComplication.setImageIcon(complicationProviderInfo.providerIcon);
            } else {
                textComplication.setImageDrawable(defaultAddComplicationDrawable);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == COMPLICATION_CONFIG_REQUEST_CODE && resultCode == RESULT_OK) {
            ComplicationProviderInfo complicationProviderInfo = data.getParcelableExtra(ProviderChooserIntent.EXTRA_PROVIDER_INFO);
            Log.d(TAG, "Provider: " + complicationProviderInfo);

            if (selectedComplicationId >= 0) {
                updateComplicationViews(selectedComplicationId, complicationProviderInfo);
            }
        }
    }

}
