package com.ankita.h2sdosimeter.analysis;

/**
 * ColorCalibrator - pure-Java, per-photo colour correction.
 *
 * WHAT THIS DOES:
 * Given the colours of the printed reference swatches AS PHOTOGRAPHED
 * (captured) and their known printed colours (reference), fits an affine
 * RGB transform:
 *
 *   [R']   [m00 m01 m02 m03]   [R]
 *   [G'] = [m10 m11 m12 m13] * [G]
 *   [B']   [m20 m21 m22 m23]   [B]
 *                              [1]
 *
 * i.e. M is a 3x4 matrix. Applying M to ANY colour sampled from the same
 * photo (e.g. the reaction strip) cancels that photo's lighting colour cast
 * and exposure/white-balance bias, because M was fit specifically to make
 * the captured swatches match their known printed colours in this photo.
 *
 * FITTING METHOD:
 * Least squares. All three output channels (R', G', B') share the same
 * design matrix A = [captured_R, captured_G, captured_B, 1] (one row per
 * swatch), so the normal equations A^T A x = A^T y are built ONCE (a single
 * 4x4 system) and solved three times (once per RHS column: reference R, G,
 * B). This is fit fresh for every photo - there is no pre-trained model or
 * dataset involved.
 *
 * No Android or OpenCV imports - safe for plain JVM unit tests.
 */
public final class ColorCalibrator {

    /** Minimum number of swatch pairs required to fit (4 unknowns per channel). */
    public static final int MIN_SWATCHES = 4;

    /** m[channel][coef] where channel = 0(R)/1(G)/2(B), coef = 0(R)/1(G)/2(B)/3(const). */
    private final double[][] m;

    private ColorCalibrator(double[][] m) {
        this.m = m;
    }

    /**
     * Fits an affine RGB correction from captured vs. reference swatch
     * colours.
     *
     * @param captured  N x 3 array of swatch colours as photographed
     *                  ({r,g,b}, 0-255 each), N &gt;= {@link #MIN_SWATCHES}.
     * @param reference N x 3 array of the SAME swatches' known reference
     *                  colours, in the same order as {@code captured}.
     * @return a fitted ColorCalibrator, or {@code null} if there are fewer
     *         than MIN_SWATCHES pairs, the arrays are malformed/mismatched,
     *         or the normal system is singular (e.g. all swatches sampled
     *         the same colour).
     */
    public static ColorCalibrator fit(int[][] captured, int[][] reference) {
        if (captured == null || reference == null) return null;
        int n = captured.length;
        if (n < MIN_SWATCHES || reference.length != n) return null;

        // Design matrix A: n x 4 -> [capturedR, capturedG, capturedB, 1]
        double[][] a = new double[n][4];
        for (int i = 0; i < n; i++) {
            if (captured[i] == null || captured[i].length < 3) return null;
            if (reference[i] == null || reference[i].length < 3) return null;
            a[i][0] = captured[i][0];
            a[i][1] = captured[i][1];
            a[i][2] = captured[i][2];
            a[i][3] = 1.0;
        }

        // Normal equations: (A^T A) x = A^T y - one 4x4 system, three RHS columns.
        double[][] ata = new double[4][4];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                double sum = 0.0;
                for (int i = 0; i < n; i++) sum += a[i][r] * a[i][c];
                ata[r][c] = sum;
            }
        }

        double[][] atb = new double[4][3]; // columns: target R, G, B
        for (int r = 0; r < 4; r++) {
            for (int ch = 0; ch < 3; ch++) {
                double sum = 0.0;
                for (int i = 0; i < n; i++) sum += a[i][r] * reference[i][ch];
                atb[r][ch] = sum;
            }
        }

        double[][] solved = solve4x4(ata, atb); // 4 x 3, or null if singular
        if (solved == null) return null;

        // Transpose solved (coef x channel) into m (channel x coef)
        double[][] m = new double[3][4];
        for (int ch = 0; ch < 3; ch++) {
            for (int coef = 0; coef < 4; coef++) {
                m[ch][coef] = solved[coef][ch];
            }
        }
        return new ColorCalibrator(m);
    }

    /** Applies the fitted correction to a sampled colour, clamped to 0-255. */
    public int[] apply(int r, int g, int b) {
        int[] out = new int[3];
        for (int ch = 0; ch < 3; ch++) {
            double v = m[ch][0] * r + m[ch][1] * g + m[ch][2] * b + m[ch][3];
            out[ch] = (int) Math.round(clamp(v, 0.0, 255.0));
        }
        return out;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * Solves the 4x4 linear system {@code ata * x = atb} (atb may carry
     * multiple RHS columns) via Gauss-Jordan elimination with partial
     * pivoting.
     *
     * @return the solved matrix (same shape as atb), or {@code null} if
     *         {@code ata} is singular / near-singular.
     */
    private static double[][] solve4x4(double[][] ata, double[][] atb) {
        final int n = 4;
        final int rhsCols = atb[0].length;

        double[][] aug = new double[n][n + rhsCols];
        for (int i = 0; i < n; i++) {
            System.arraycopy(ata[i], 0, aug[i], 0, n);
            System.arraycopy(atb[i], 0, aug[i], n, rhsCols);
        }

        for (int col = 0; col < n; col++) {
            int pivotRow = col;
            double maxAbs = Math.abs(aug[col][col]);
            for (int row = col + 1; row < n; row++) {
                double v = Math.abs(aug[row][col]);
                if (v > maxAbs) {
                    maxAbs = v;
                    pivotRow = row;
                }
            }
            if (maxAbs < 1e-9) return null; // singular / under-determined

            if (pivotRow != col) {
                double[] tmp = aug[col];
                aug[col] = aug[pivotRow];
                aug[pivotRow] = tmp;
            }

            double pivot = aug[col][col];
            for (int c = col; c < n + rhsCols; c++) aug[col][c] /= pivot;

            for (int row = 0; row < n; row++) {
                if (row == col) continue;
                double factor = aug[row][col];
                if (factor == 0.0) continue;
                for (int c = col; c < n + rhsCols; c++) {
                    aug[row][c] -= factor * aug[col][c];
                }
            }
        }

        double[][] result = new double[n][rhsCols];
        for (int i = 0; i < n; i++) {
            System.arraycopy(aug[i], n, result[i], 0, rhsCols);
        }
        return result;
    }
}
