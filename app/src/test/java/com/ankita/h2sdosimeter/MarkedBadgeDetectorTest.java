package com.ankita.h2sdosimeter;

import com.ankita.h2sdosimeter.analysis.MarkedBadgeDetector;
import com.ankita.h2sdosimeter.analysis.StripColourMeasurement;

import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;

/**
 * MarkedBadgeDetectorTest — runs the real detector on real badge photos.
 *
 * marked_badge_01.ppm.gz : strip framed by 4 red corner marks, reference card above it.
 * unmarked_0N.ppm.gz     : older photos (¼ size) without marks or card (must be rejected).
 */
public class MarkedBadgeDetectorTest {

    private static final String MARKED = "/badges/marked_badge_01.ppm.gz";
    private static final int UNMARKED_COUNT = 6;

    /** Same strip, different lighting / orientation → ΔE₀₀ must stay within this. */
    private static final double ORIENTATION_TOLERANCE = 0.5;
    private static final double LIGHTING_TOLERANCE    = 1.0;

    private static final class Photo {
        final int[] px; final int w, h;
        Photo(int[] px, int w, int h) { this.px = px; this.w = w; this.h = h; }
    }

    /**
     * Loads a gzipped binary PPM (P6). Photos are stored as PPM because JVM unit
     * tests compile against android.jar, which has no JPEG decoder.
     */
    private static Photo load(String resource) throws Exception {
        try (InputStream raw = MarkedBadgeDetectorTest.class.getResourceAsStream(resource)) {
            assertNotNull("Missing test resource " + resource, raw);
            InputStream in = new BufferedInputStream(new GZIPInputStream(raw));
            assertEquals("P6", token(in));
            int w = Integer.parseInt(token(in));
            int h = Integer.parseInt(token(in));
            assertEquals("255", token(in));
            int[] px = new int[w * h];
            for (int i = 0; i < px.length; i++) {
                int r = in.read(), g = in.read(), b = in.read();
                if (b < 0) throw new EOFException(resource);
                px[i] = 0xFF000000 | r << 16 | g << 8 | b;
            }
            return new Photo(px, w, h);
        }
    }

    private static String token(InputStream in) throws IOException {
        int c;
        do {
            c = in.read();
        } while (c != -1 && Character.isWhitespace(c));
        StringBuilder sb = new StringBuilder();
        while (c != -1 && !Character.isWhitespace(c)) {
            sb.append((char) c);
            c = in.read();
        }
        return sb.toString();
    }

    private static MarkedBadgeDetector.Result detect(Photo p) {
        return MarkedBadgeDetector.detect(p.px, p.w, p.h);
    }

    private static double deltaE(Photo p) {
        MarkedBadgeDetector.Result r = detect(p);
        assertTrue("Expected detection: " + r.debug, r.detected);
        return StripColourMeasurement.measure(r).deltaE00;
    }

    // ------------------------------------------------------------------
    // Detection
    // ------------------------------------------------------------------

    @Test
    public void markedBadge_stripAndReferenceCardDetected() throws Exception {
        MarkedBadgeDetector.Result r = detect(load(MARKED));
        assertTrue(r.debug, r.detected);

        // Strip corners lie on the red marks (x 58–179, y 600–858 in this photo)
        for (int i = 0; i < 4; i++) {
            double x = r.stripCorners[2 * i], y = r.stripCorners[2 * i + 1];
            assertTrue("corner x " + x, x >= 50 && x <= 190);
            assertTrue("corner y " + y, y >= 590 && y <= 870);
        }
        assertTrue("aspect " + r.stripAspectRatio,
                r.stripAspectRatio > 2.0 && r.stripAspectRatio < 2.8);

        assertTrue("white brighter than light patch", luma(r.whiteRgb) > luma(r.lightRgb));
        assertTrue("light brighter than dark patch",  luma(r.lightRgb) > luma(r.darkRgb) + 40);
    }

    @Test
    public void unmarkedPhotos_rejected() throws Exception {
        for (int i = 1; i <= UNMARKED_COUNT; i++) {
            MarkedBadgeDetector.Result r = detect(load("/badges/unmarked_0" + i + ".ppm.gz"));
            assertFalse("unmarked_0" + i + " must not be detected", r.detected);
            assertEquals("MARKS_NOT_FOUND", r.failureCode);
        }
    }

    @Test
    public void referenceCardCovered_rejected() throws Exception {
        Photo p = load(MARKED);
        int bandGrey = 0xFF748B93;
        paint(p, 96, 400, 162, 598, bandGrey);   // cover the reference card with strap colour
        MarkedBadgeDetector.Result r = detect(p);
        assertFalse("No reference card → no result", r.detected);
        assertEquals("REFERENCE_NOT_FOUND", r.failureCode);
    }

    // ------------------------------------------------------------------
    // Measurement
    // ------------------------------------------------------------------

    @Test
    public void unexposedStrip_smallDeltaE() throws Exception {
        double de = deltaE(load(MARKED));
        assertTrue("unexposed strip ΔE₀₀ " + de, de < StripColourMeasurement.MINIMAL_CHANGE_MAX_DE);
    }

    @Test
    public void rotation_doesNotChangeDeltaE() throws Exception {
        Photo p = load(MARKED);
        double base = deltaE(p);
        Photo r90 = rotate90(p);
        Photo r180 = rotate90(rotate90(p));
        assertEquals("90°",  base, deltaE(r90),  ORIENTATION_TOLERANCE);
        assertEquals("180°", base, deltaE(r180), ORIENTATION_TOLERANCE);
    }

    @Test
    public void lighting_doesNotChangeDeltaE() throws Exception {
        Photo p = load(MARKED);
        double base = deltaE(p);
        assertEquals("warm light",   base, deltaE(relight(p, 1.00, 0.85, 0.65, 0)),  LIGHTING_TOLERANCE);
        assertEquals("dim light",    base, deltaE(relight(p, 0.60, 0.60, 0.60, 0)),  LIGHTING_TOLERANCE);
        assertEquals("flare / haze", base, deltaE(relight(p, 0.90, 0.90, 0.90, 25)), LIGHTING_TOLERANCE);
        assertEquals("cool light",   base, deltaE(relight(p, 1.05, 1.15, 1.25, 0)),  LIGHTING_TOLERANCE);
    }

    @Test
    public void darkerStrip_higherDeltaE() throws Exception {
        double unexposed = deltaE(load(MARKED));

        Photo light = load(MARKED);
        tint(light, 80, 645, 160, 815, 0.85, 0.78, 0.70);
        double lightDe = deltaE(light);

        Photo heavy = load(MARKED);
        tint(heavy, 80, 645, 160, 815, 0.60, 0.50, 0.40);
        double heavyDe = deltaE(heavy);

        assertTrue("light exposure > unexposed: " + lightDe + " vs " + unexposed, lightDe > unexposed + 5);
        assertTrue("heavy exposure > light: " + heavyDe + " vs " + lightDe, heavyDe > lightDe + 5);
    }

    @Test
    public void darkerStrip_sameDeltaEUnderDifferentLighting() throws Exception {
        Photo exposed = load(MARKED);
        tint(exposed, 80, 645, 160, 815, 0.70, 0.60, 0.50);
        double base = deltaE(exposed);
        assertEquals("warm light", base, deltaE(relight(exposed, 1.00, 0.85, 0.65, 0)), LIGHTING_TOLERANCE);
        assertEquals("dim light",  base, deltaE(relight(exposed, 0.60, 0.60, 0.60, 0)), LIGHTING_TOLERANCE);
    }

    // ------------------------------------------------------------------
    // Image helpers
    // ------------------------------------------------------------------

    private static double luma(double[] rgb) {
        return 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2];
    }

    private static Photo rotate90(Photo p) {
        int[] out = new int[p.w * p.h];
        for (int y = 0; y < p.h; y++)
            for (int x = 0; x < p.w; x++)
                out[x * p.h + (p.h - 1 - y)] = p.px[y * p.w + x];
        return new Photo(out, p.h, p.w);
    }

    /** Simulates a different light: per-channel gain plus additive flare. */
    private static Photo relight(Photo p, double kr, double kg, double kb, int flare) {
        int[] out = new int[p.px.length];
        for (int i = 0; i < out.length; i++) {
            int c = p.px[i];
            out[i] = 0xFF000000
                    | clamp(((c >> 16) & 255) * kr + flare) << 16
                    | clamp(((c >> 8) & 255) * kg + flare) << 8
                    | clamp((c & 255) * kb + flare);
        }
        return new Photo(out, p.w, p.h);
    }

    /** Simulates H2S exposure: darkens / browns the strip area. */
    private static void tint(Photo p, int x0, int y0, int x1, int y1, double kr, double kg, double kb) {
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                int i = y * p.w + x, c = p.px[i];
                p.px[i] = 0xFF000000
                        | clamp(((c >> 16) & 255) * kr) << 16
                        | clamp(((c >> 8) & 255) * kg) << 8
                        | clamp((c & 255) * kb);
            }
    }

    private static void paint(Photo p, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++)
                p.px[y * p.w + x] = argb;
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }
}
