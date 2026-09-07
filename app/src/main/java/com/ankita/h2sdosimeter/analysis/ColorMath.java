package com.ankita.h2sdosimeter.analysis;

/**
 * ColorMath - pure-Java colour-space helpers.
 *
 * Converts sRGB colours to CIELab (D65 reference white) and computes the
 * CIE76 colour difference (delta-E), which approximates perceptual colour
 * distance far better than raw Euclidean RGB distance - important for
 * matching a photographed strip colour against a printed reference scale
 * under imperfect (but corrected) lighting.
 *
 * No Android or OpenCV imports - safe for plain JVM unit tests.
 */
public final class ColorMath {

    private ColorMath() { /* static helpers only */ }

    // D65 reference white (CIE 1931 2-degree observer), scaled 0-100
    private static final double REF_X = 95.047;
    private static final double REF_Y = 100.000;
    private static final double REF_Z = 108.883;

    private static final double XYZ_EPSILON = 0.008856;
    private static final double XYZ_KAPPA   = 903.3;

    /** A colour in the CIELab space: L* in [0,100], a* and b* roughly [-128,127]. */
    public static final class Lab {
        public final double l;
        public final double a;
        public final double b;

        public Lab(double l, double a, double b) {
            this.l = l;
            this.a = a;
            this.b = b;
        }

        @Override
        public String toString() {
            return String.format("Lab(%.2f, %.2f, %.2f)", l, a, b);
        }
    }

    /** Converts an sRGB colour (0-255 per channel) to CIELab. */
    public static Lab rgbToLab(int r, int g, int b) {
        double[] xyz = rgbToXyz(r, g, b);
        return xyzToLab(xyz[0], xyz[1], xyz[2]);
    }

    /**
     * CIE76 colour difference: plain Euclidean distance in Lab space.
     * Simple and adequate for a small, well-separated reference scale
     * (unlike CIE94/CIEDE2000, it needs no extra parameters).
     */
    public static double deltaE76(Lab a, Lab b) {
        double dl = a.l - b.l;
        double da = a.a - b.a;
        double db = a.b - b.b;
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    // ------------------------------------------------------------------
    // sRGB -> XYZ -> Lab
    // ------------------------------------------------------------------

    private static double[] rgbToXyz(int r, int g, int b) {
        double rl = pivotRgb(clamp01(r / 255.0));
        double gl = pivotRgb(clamp01(g / 255.0));
        double bl = pivotRgb(clamp01(b / 255.0));

        double x = rl * 0.4124 + gl * 0.3576 + bl * 0.1805;
        double y = rl * 0.2126 + gl * 0.7152 + bl * 0.0722;
        double z = rl * 0.0193 + gl * 0.1192 + bl * 0.9505;

        return new double[]{x * 100.0, y * 100.0, z * 100.0};
    }

    private static double pivotRgb(double channel) {
        return (channel > 0.04045)
                ? Math.pow((channel + 0.055) / 1.055, 2.4)
                : channel / 12.92;
    }

    private static Lab xyzToLab(double x, double y, double z) {
        double fx = pivotXyz(x / REF_X);
        double fy = pivotXyz(y / REF_Y);
        double fz = pivotXyz(z / REF_Z);

        double l = 116.0 * fy - 16.0;
        double a = 500.0 * (fx - fy);
        double b = 200.0 * (fy - fz);
        return new Lab(l, a, b);
    }

    private static double pivotXyz(double t) {
        return (t > XYZ_EPSILON) ? Math.cbrt(t) : (XYZ_KAPPA * t + 16.0) / 116.0;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
