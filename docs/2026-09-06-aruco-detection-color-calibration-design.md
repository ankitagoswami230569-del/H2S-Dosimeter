# ArUco Detection + Per-Photo Colour Calibration Design

Date: 2026-09-06
Branch: `Lovekush`
Status: Implemented (scaffold) - swatch colours and env-compensation
coefficients are documented PLACEHOLDERS pending lab/chamber validation.

## 1. Problem

The original colour-analysis pipeline (`ColourAnalysisService` +
`ReferenceCorrectionService`) located the badge sensor strip and a small
"reference white" patch using **fixed fractional crops of the photo**
(e.g. "centre 20% x 20%"). This has two structural weaknesses:

1. **Framing-dependent.** If the badge is not held at exactly the expected
   position/scale/angle, the crops sample the wrong thing (background,
   skin, shadow).
2. **Lighting-dependent, weakly corrected.** The "reference white" patch
   correction is a single-point grey-world assumption (`corrected = raw *
   255/ref` per channel). It cannot correct for non-linear camera response,
   coloured ambient light that isn't reproduced by a single white point, or
   exposure differences, and it silently produces garbage if the reference
   patch itself wasn't sampled correctly.

## 2. Design: printed reference card + per-photo affine correction

A printed card is introduced (`ReferenceCardSpec`) carrying:

- **4 ArUco fiducial markers** (dictionary `DICT_4X4_50`, ids `0..3` in
  canonical corner order TL/TR/BR/BL). These give robust, sub-pixel-ish
  geometric registration regardless of camera angle/distance, using
  OpenCV's `ArucoDetector`.
- **A reaction-strip placement region** - where the user positions the
  badge's colour-changing strip against the card.
- **N reference colour swatches** (placeholder colours approximating a
  lead-acetate strip darkening from pale cream to near-black) with known
  dose-scale indices.
- **An expiry indicator patch.**

All regions are defined in **normalised card coordinates** (`[0,1]`,
origin top-left, measured on the rectified card) - a single source of
truth shared conceptually between the app and the printed card artwork.

### 2.1 Pipeline stages

```
Bitmap (raw photo)
   |
   v
CardDetector            <- the ONLY OpenCV-dependent stage
   - ArucoDetector.detectMarkers() with DICT_4X4_50
   - requires all 4 canonical marker ids present
   - Calib3d.findHomography(detected centres -> canonical centres)
   - reject if homography is empty / singular (|det| < 1e-9)
   - Imgproc.warpPerspective() -> fixed-size rectified card bitmap
   - returns null on any failure (fail CLEAN, not throw)
   |
   v
RegionSampler            <- samples average RGB of a normalised region
   - reaction strip, N swatches, expiry patch (all from ReferenceCardSpec)
   |
   v
ColorCalibrator.fit()    <- pure-Java, PER-PHOTO
   - captured swatch RGBs (this photo) vs. known reference swatch RGBs
   - least-squares affine fit: [R' G' B']^T = M * [R G B 1]^T (M is 3x4)
   - one 4x4 normal-equations system (A^T A), solved for 3 RHS columns
   - MIN_SWATCHES = 4 (4 unknowns per channel); returns null if singular
   |
   v
ColorCalibrator.apply()  <- corrects the strip, swatches and expiry patch
   |
   v
ScaleReader.read()        <- pure-Java
   - ColorMath: sRGB -> CIELab (D65), CIE76 delta-E
   - match corrected strip Lab against corrected swatch Labs
   - nearestIndex / nearestDeltaE = single closest swatch
   - scalePosition = continuous interpolation between the nearest swatch
     and whichever neighbour is the second-best match, weighted by
     relative delta-E
   |
   v
ColourAnalysisResult (SUCCESS, cardDetected, detectionConfidence,
                       scalePosition, nearestSwatchDeltaE, expiry RGB,
                       corrected strip RGB, legacy colourDifference)
```

### 2.2 Why an affine (not just multiplicative) correction

The previous grey-world correction only scaled each channel
(`raw * 255/ref`), which cannot correct an additive offset (e.g. lens
flare, sensor black-level drift) or cross-channel colour casts. Fitting a
full 3x4 affine transform from >= 4 known swatch pairs lets the same
per-photo correction absorb:

- multiplicative gain per channel (white balance / exposure),
- additive offset per channel (black-level / flare), and
- (via the shared design matrix, not per-channel independently) any
  consistent cross-talk introduced by the camera's colour pipeline that
  happens to be linear in R/G/B.

Because it is refit **fresh for every photo** from that photo's own
swatches, it needs no training data or per-device profiling, and
automatically cancels whatever that specific shot's lighting did to both
the strip and the swatches equally.

### 2.3 Why CIELab / CIE76 instead of RGB Euclidean distance

RGB Euclidean distance weights the three channels equally regardless of
human colour perception, so it can call two swatches "close" when a human
(and the printed scale) would consider them clearly different, or vice
versa. Converting to CIELab and using CIE76 delta-E (`ColorMath`) gives a
perceptually-uniform-ish distance metric appropriate for a small, coarse
reference scale, without the extra parameters CIE94/CIEDE2000 would need.

### 2.4 Scale position vs. ppm.hr

`ScaleReader` outputs a **scale position** (continuous dose-index units,
e.g. `2.35`) - NOT a validated concentration. Converting that to ppm.hr
still requires user-entered calibration points
(`CalibrationCurve.convertByScalePosition`), exactly as the legacy
colour-difference path required `CalibrationCurve.convert`. Scale position
is preferred when available because it is lighting-independent by
construction (both strip and swatches were corrected together); the
colour-difference method remains as a fallback for scans where the
reference card wasn't detected, or for the initial extrapolated 2-point
worker calibration set (`CalibrationStore.loadPoints` vs.
`loadPointsByScale`).

## 3. Temperature / humidity compensation

`EnvCompensation` applies a first-order linear model:

```
factor            = 1 + K_TEMP*(T - 25) + K_RH*(RH - 50)
normalisedReading = rawReading / factor
```

`K_TEMP = 0.030` and `K_RH = 0.005` are documented **placeholders** - not
derived from chamber testing of the actual badge chemistry. At the
reference condition (25 C, 50% RH) `factor == 1`, so a scan with no
environmental sensor data is a safe no-op rather than a corrupted value.
The factor is floored at `0.10` to avoid a divide-by-zero or sign flip at
unrealistic inputs.

This normalisation is applied to the raw reading (scale position or
colour difference) **before** it is handed to `CalibrationCurve`, on both
the on-device (`ProcessingActivity`) and result-display
(`ScanResultActivity`) code paths.

## 4. Failure modes

| Condition                                   | Result status      |
|----------------------------------------------|--------------------|
| Image too dark/bright/blurry                  | `QUALITY_REJECTED` |
| OpenCV unavailable, <4 markers, degenerate H  | `CARD_NOT_DETECTED`|
| Swatch colour fit singular/under-determined   | `ERROR`            |
| Scale match fails (< 2 swatches)              | `ERROR`            |
| Everything above passes                       | `SUCCESS`          |

`CARD_NOT_DETECTED` is a new, distinct status from the legacy
`REGION_NOT_FOUND` (which is retained for the old heuristic-crop code
path but is no longer produced by `BadgeAnalysisPipeline`). The result
message tells the user to re-align the badge so all four corner markers
are fully visible.

## 5. What is NOT implemented / left as placeholders

- **Swatch reference colours** (`ReferenceCardSpec.SCALE_SWATCHES`) are
  illustrative, not spectrophotometer-measured against real printed card
  stock.
- **Env-compensation coefficients** (`K_TEMP`, `K_RH`) are illustrative,
  not chamber-derived.
- **No live temperature/humidity sensor** is wired into the capture flow
  yet; `EnvCompensation.normalise(double)` (single-arg overload) is used,
  which is equivalent to the reference condition (no-op) until a sensor
  input is added.
- **Backend calibration entities** were not changed to carry scale
  position - only the Android-local `CalibrationPoint`/`CalibrationStore`
  gained it in this change. Server-side calibration by scale position is
  future work.

## 6. Compile-time separation of concerns

To keep the algorithmic core unit-testable on a plain JVM (no Android
runtime, no OpenCV native library required):

- `ReferenceCardSpec`, `ColorMath`, `ColorCalibrator`, `ScaleReader`,
  `EnvCompensation`, `CalibrationCurve` have **no Android or OpenCV
  imports**.
- `RegionSampler` imports `android.graphics.Bitmap`/`Color` only (no
  OpenCV) - it reads pixels from a bitmap already produced by
  `CardDetector`.
- `CardDetector` is the **only** class that imports OpenCV
  (`org.opencv.*`) - all ArUco detection and homography/warp logic lives
  there, isolated behind a simple `Detection detect(Bitmap)` API that
  returns `null` on any failure.
