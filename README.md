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
- CameraX capture with lens selection, flash modes, torch, zoom, exposure/AF
  locks where the device supports them.
- Rotation-vector sensor tracking with gyroscope stability detection and an
  accelerometer + magnetometer fallback.
- Sensor-pose spherical stitching: source pixels are projected with the measured
  field of view and recorded quaternion, blended with edge feathering, exposure
  normalised, gap-filled, then saved as an equirectangular JPEG.
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

### Capture plan

The planner derives ring count and spacing from the measured horizontal and
vertical field of view plus the requested overlap. A wide lens needs fewer
photos; a narrow lens needs more. Zenith and nadir are always included.
Captures are processed in plan order, which starts at the horizon and moves up,
over the zenith, down and around, then to the nadir.

### Stitching

`SensorPoseStitchingEngine` builds an equirectangular accumulator. For each
source pixel it reconstructs a camera ray using the horizontal FOV, rotates it
with the capture quaternion, and splats it into the panorama. Overlaps use a
cosine/edge weight, each frame receives a bounded exposure gain, uncovered
pixels are filled from valid neighbours, and the JPEG is written with GPano
metadata.

## Honest limitations

These are known and intentionally not presented as solved:

- Pose comes from the fused rotation vector. Gyro drift or magnetic disturbance
  can shift frames. There is no feature-based bundle adjustment yet.
- Lens distortion is not corrected without a calibration profile.
- Moving subjects can ghost; no temporal de-ghosting is applied.
- The stitcher is CPU-based; time and peak memory grow with output width.
- Alignment-dot direction conventions and Camera2 interop behaviour have not
  been verified on physical hardware in this workspace.
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

- `:app:compileDebugKotlin` passes.
- `:app:assembleDebug` passes.
- 19 JVM unit tests pass (quaternion maths, alignment, planner/coverage, GPano
  XMP injection).
- Camera, sensors, automatic capture, stitching quality and the GL viewer have
  not been exercised on a physical device in this workspace. Test on your target
  hardware before making quality claims.

## Third-party licenses

See [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
