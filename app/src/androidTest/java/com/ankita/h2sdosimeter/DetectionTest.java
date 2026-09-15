package com.ankita.h2sdosimeter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.ankita.h2sdosimeter.analysis.MarkedBadgeDetector;
import com.ankita.h2sdosimeter.analysis.StripColourMeasurement;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

/**
 * Instrumented test that runs MarkedBadgeDetector on badge photos pushed to
 * /data/local/tmp/ and logs the detection and ΔE₀₀ for each.
 *
 * Push photos:  adb push marked_badge_01.jpeg /data/local/tmp/
 * Run with:
 *   adb shell am instrument -w \
 *     com.ankita.h2sdosimeter.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4.class)
public class DetectionTest {

    private static final String TAG = "DetectionTest";

    private static final String[] PHOTOS = {
        "marked_badge_01.jpeg",
    };

    @Test
    public void testAllPhotos() {
        for (String photo : PHOTOS) {
            String path = "/data/local/tmp/" + photo;
            Bitmap bmp = BitmapFactory.decodeFile(path);
            if (bmp == null) {
                Log.e(TAG, "FAILED TO DECODE: " + path);
                continue;
            }
            int w = bmp.getWidth(), h = bmp.getHeight();
            int[] pixels = new int[w * h];
            bmp.getPixels(pixels, 0, w, 0, 0, w, h);
            bmp.recycle();

            MarkedBadgeDetector.Result result = MarkedBadgeDetector.detect(pixels, w, h);
            Log.i(TAG, photo + " " + w + "x" + h + " detected=" + result.detected);
            Log.i(TAG, "  " + result.debug);
            if (result.detected) {
                StripColourMeasurement m = StripColourMeasurement.measure(result);
                Log.i(TAG, String.format(Locale.US, "  deltaE00=%.2f deltaL=%.2f", m.deltaE00, m.deltaL));
            } else {
                Log.i(TAG, "  message=" + result.message);
            }
        }
    }
}
