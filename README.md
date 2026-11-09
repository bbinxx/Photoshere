# PhotoSphere

An offline Android camera for capturing, stitching and viewing 360° photo spheres.

PhotoSphere guides you through overlapping captures with a live spherical target
overlay, records the device orientation for every frame, projects the photos onto
an equirectangular canvas, and writes GPano XMP metadata so compatible viewers
recognise the result as a photo sphere.

There is no account, no upload and no analytics. Everything runs on the device.

## Features

- Guided capture with centre target, moving alignment dot, stability ring,
  auto-capture, manual shutter and spherical progress.
- CameraX capture with lens selection, flash modes, torch and exposure/AF
  locks where the device supports them. Sphere capture keeps the selected lens
  at 1× so the capture grid and projection use consistent intrinsics.
- Rotation-vector sensor tracking with gyroscope stability detection and an
  accelerometer + magnetometer fallback.
- Sensor-pose spherical stitching: destination rays sample the captured images
  using measured field of view and recorded quaternions. Overlaps are feathered
  and exposure normalised; missing areas are reported rather than fabricated.
- GPano XMP metadata injection for Google Photos, Facebook and other panorama
  viewers.
- OpenGL ES 3 sphere viewer with drag, pinch zoom, double-tap reset and optional
  gyroscope look-around.
- Gallery with import, rename, delete, share, export file and save to Photos.
- Durable capture sessions that can be resumed after leaving the app.
- Material 3 dark UI with an edge-to-edge Compose layout.

## Requirements

- Android 10 (API 29) or newer.
- A rear camera and a rotation-vector sensor (or accelerometer + magnetometer
  fallback with reduced accuracy).
- OpenGL ES 3.0 for the viewer.
- ABI: `arm64-v8a` or `x86_64`.

## Build

The project uses Gradle 9.5, AGP 9.3, Kotlin 2.2 and compileSdk 37. Use a JDK
that AGP supports (17+). On this machine:

```bash
JAVA_HOME=/usr/lib/jvm/java-27-openjdk ./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Unit tests (pure JVM, no device required):

```bash
JAVA_HOME=/usr/lib/jvm/java-27-openjdk ./gradlew :app:testDebugUnitTest
```

The SDK location is read from `local.properties`.

## Project layout

```
app/src/main/java/com/bbinxx/photoshere/
├── camera/       CameraX controller and camera state
├── capture/      capture planner, alignment, coverage, view model
├── core/         immutable models, quaternion/vector maths, app container
├── data/         settings, durable session store, gallery repository
├── export/       GPano XMP writer and MediaStore/SAF exporter
├── navigation/   Compose Navigation host and routes
├── sensors/      orientation tracker and stability detector
├── stitching/    quality validation, preprocessing, spherical stitcher
├── ui/           Compose screens, theme, permissions, components
└── viewer/       OpenGL ES sphere renderer and touch/sensor view
```

### Coordinates

World frame: X = east, Y = north, Z = up. Device frame: X = right, Y = up,
Z = out of the screen; the rear camera looks along device -Z. Quaternions are
used everywhere for tracking; Euler angles are display-only.

### Preview and startup reliability

CameraX owns preview rendering and autofocus. Preview uses SDR and the
SurfaceView performance path, avoiding TextureView colour conversion on devices
that render it incorrectly. Exposure/white-balance locks are requested only after
Camera2 reports convergence; autofocus uses CameraX metering instead of forcing
infinity. Camera initialisation failures are shown as capture errors, and leaving
the screen does not cancel the shared CameraX provider future. Sensor samples
reach the UI at 20 Hz while tracking continues at the sensor rate. OpenCV loads
lazily when requested rather than during every app launch.

When running device tests, leave the app installed:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

### Free-direction capture

Point anywhere and tap the shutter to take the first photograph. Start with the
sky, ground or horizon; there is no mandatory starting direction. Subsequent
manual captures can also face any new area. With automatic capture enabled,
align any nearby suggested dot and hold steady. The next suggestion follows the
nearest uncaptured region rather than a fixed horizontal/vertical sequence.
Rotate around the camera's optical centre and overlap neighbouring photographs
to reduce parallax.

A field-of-view-dependent target grid supplies suggestions. Progress instead uses
a separate equal-area spherical coverage model built from accepted photographs'
actual shutter-time quaternions and horizontal/vertical fields of view. Rejected
photos add no coverage; repeated coverage is skipped. Sky and ground poles are
checked explicitly. Remaining sampled gaps generate additional suggestions, and
stitching becomes available only when the coverage model is complete. Coverage
is a finite sampling estimate, not a guarantee of pixel-perfect seams.

### Stitching

`SensorPoseStitchingEngine` inverse-projects each equirectangular destination
ray into each camera using the recorded quaternion and horizontal/vertical FOV.
It bilinearly samples source pixels and blends overlaps with cosine/edge weights
and a bounded exposure gain. Inverse mapping avoids the sparse pixel gaps of
forward splatting at large output sizes and at the poles. Unobserved regions
remain unfilled, and the measured coverage is shown with warnings. JPEG output
includes GPano metadata.

Android rotation vectors encode quaternion vector components as
`axis * sin(angle/2)`, with an optional scalar `cos(angle/2)`; the scalar's sign
must be preserved. Display rotation is a quarter-turn index (`Surface.ROTATION_*`),
not a degree value. The camera frame maps EXIF-normalised image right/up axes
back into the device frame, including front-camera facing. The guide projects
targets through the inverse camera quaternion so its direction follows screen
rotation. Source photographs are retained after stitching, including retries.

## Honest limitations

These are known and intentionally not presented as solved:

- Pose comes from the fused rotation vector. Gyro drift or magnetic disturbance
  can shift frames. There is no feature-based bundle adjustment yet.
- Lens distortion is not corrected without a calibration profile.
- Moving subjects can ghost; no temporal de-ghosting is applied.
- The stitcher is CPU-based; time and peak memory grow with output width.
- Full real-world spherical capture, front-camera projection and portrait/landscape
  changes during a session still need hands-on verification. Resuming assumes
  that the sensor world heading remains consistent with the saved session.
- The viewer decodes textures up to 4096 px on the long edge to stay within
  common GL texture limits; very large spheres are downsampled for viewing.
- OpenCV adds roughly 110 MB of debug AAR/native payload. Release builds should
  use R8/resource shrinking and may split or trim ABIs.

## Permissions

- `CAMERA` — capture.
- `VIBRATE` — optional haptic feedback.
- `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` (API 32 and below) are declared
  for media access. Imports use the Storage Access Framework, which does not
  require broad storage permission.

## Verification status

Build and checks:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The connected test device reports model **M2101K6P**, Android 17; it is not a
OnePlus 13. Device regressions cover actual rear-camera JPEG capture, live sensor
orientation, comparison with Android's rotation matrix, synthetic full-sphere
projection including zenith/nadir, GPano parsing with AndroidX ExifInterface,
byte-identical URI export, concurrent gallery saves and preservation of the old
sphere when replacement fails. An OpenGL regression checks texture rendering
before and after recreating the EGL context.

These checks do not establish real-world seam quality or OnePlus 13 compatibility.
The stitcher still lacks feature-based pose refinement, lens distortion correction,
seam optimisation and multiband blending. A complete guided sphere must be
captured by hand to assess these remaining quality limits.

## Third-party licenses

See [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
