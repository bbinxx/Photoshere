# PhotoSphere

A native Android app for capturing, stitching, viewing, and sharing full
**360° × 180° photo spheres**, with local processing and no account or upload.

**Current status:** free-direction capture, spherical coverage guidance, camera
controls, gallery/export, and the interactive viewer are implemented. Stitching
works from recorded sensor poses but still needs visual pose refinement and
better seam handling. It is not yet equivalent to Google Camera's stitching
quality. The project is under active development.

The original requirements are preserved in
[docs/PROJECT_BRIEF.md](docs/PROJECT_BRIEF.md). That document describes the target
product; this README describes the implemented application and remaining work.

## Contents

- [Capture workflow](#capture-workflow)
- [Implemented features](#implemented-features)
- [Build and install](#build-and-install)
- [Testing and verification](#testing-and-verification)
- [Development progress](#development-progress)
- [Architecture](#architecture)
- [Algorithms and camera geometry](#algorithms-and-camera-geometry)
- [Storage, permissions, and privacy](#storage-permissions-and-privacy)
- [Known limitations and next steps](#known-limitations-and-next-steps)
- [Dependencies and licenses](#dependencies-and-licenses)
- [Agent instructions and Git workflow](#agent-instructions-and-git-workflow)

## Capture workflow

1. Open **New photo sphere** and allow camera access.
2. Choose **0.5×** ultra-wide or **1×** main camera before the first accepted
   photo. Ultra-wide availability depends on what the device exposes to CameraX.
3. Point anywhere—horizon, sky, or ground—and tap the shutter to start.
4. Move toward nearby uncaptured areas. You can capture manually in any order,
   or align a suggested dot and hold steady for automatic capture.
5. Keep overlapping neighbouring views. Rotate around the camera lens rather
   than walking around or swinging the phone at arm's length.
6. Continue around, above, and below you. The percentage represents estimated
   spherical area covered by accepted photographs, not a timer or photo count.
7. Once coverage includes the full sampled sphere and both poles, stitch the
   photos. With auto stitching enabled, processing opens automatically.
8. Explore the result in the sphere viewer, then save, export, or share it.

The lens stays fixed after the first accepted photograph. To use another lens,
start another sphere. Leaving an incomplete session preserves its source photos
so you can return to it.

### Preview and aiming

The preview is fitted inside a capture-ratio rectangle with black space for
controls. It shows the full delivered JPEG field of view instead of cropping it
to fill a tall phone screen. Portrait captures are normally 3:4 and landscape
captures 4:3; actual dimensions come from the bound CameraX capture stream.
This concerns JPEG framing, not RAW access to every sensor photosite.

Aiming dots are projected through the selected lens's horizontal and vertical
field of view and the phone's real orientation. Nearby pending targets appear in
the viewfinder. The main dot points toward the nearest uncaptured suggestion;
you do not have to follow a fixed numbered sequence.

### Focus and lighting

- CameraX handles automatic focus, exposure, ISO/shutter decisions, and white
  balance through the camera's supported automatic controls.
- Preview uses CameraX continuous autofocus. Per-shot focus holds are released
  immediately after capture unless the focus-lock setting is enabled.
- Before each shot, the app requests centre focus/exposure/white-balance
  metering where supported and allows metering time to settle.
- Still capture favors image quality over minimum shutter latency.
- Focus and exposure locks are optional settings. Automatic adjustment is the
  default; existing installations migrate once to these automatic defaults.
- Blur, very dark images, and overexposure are checked after capture. Failed
  photographs are retained and the app requests a retake.
- Flash and torch controls are available, subject to lens/device support.

Automatic metering cannot recover missing light or remove motion blur in every
scene. For cleaner results, use a well-lit, mostly static scene and hold still.

### Delete a saved sphere

Tap the trash icon on a gallery card, or open the sphere and tap **Delete sphere**
in the viewer toolbar. Confirm **Delete**, or choose **Cancel** to keep it. This
removes the app's gallery JPEG and index entry; it does not remove original
capture photographs or copies previously exported to Photos or another folder.
The viewer returns after deletion. A refreshed gallery does not rediscover the
deleted JPEG. Deletion errors are shown rather than reported as success.

### Automatic capture retries

After any capture attempt, including a rejected or failed image, automatic
capture waits for real movement away from the attempted viewing direction.
Staying aligned, sensor jitter, target changes and cooldown expiry cannot trigger
an endless retake loop. After a failure, adjust and realign or explicitly tap the
shutter to retry. Accepted near-identical directions must add substantial new
coverage, while genuine small gaps remain capturable.

## Implemented features

| Area | Current implementation |
| --- | --- |
| Camera | CameraX lifecycle/preview/JPEG capture, lens discovery, supported 0.5×/1× selection, automatic metering, flash/torch |
| Guidance | Quaternion orientation, gyro stability detection, nearest-gap suggestions, manual and automatic capture |
| Coverage | Accepted-image camera footprints on an equal-area sphere, duplicate-area checks, explicit zenith/nadir checks |
| Image validation | OpenCV sharpness/exposure checks with a bitmap fallback |
| Stitching | EXIF normalization, lens-specific spherical inverse projection, bilinear sampling, feather blending, overlap exposure compensation |
| Export | 2:1 JPEG, GPano XMP, MediaStore save, Storage Access Framework export, Android sharing |
| Viewer | OpenGL ES 3 inside-facing sphere, drag, pinch zoom, reset, optional sensor viewing |
| Gallery | Thumbnails, import, rename, delete, share, export, saved capture sessions |
| Reliability | Capture busy/state guards, lifecycle recovery, retained originals, background processing/cancellation, gallery write serialization |

## Build and install

### Requirements

- Android SDK platform **37**, plus SDK tools required by Gradle.
- A JDK compatible with the checked-in Gradle/AGP versions. The local builds
  described here use **OpenJDK 27.0.1**. Java source/target compatibility is 17;
  that setting alone does not specify the JDK required to run Gradle.
- Android Studio or command-line SDK tools.
- A device with Android **10 / API 29** or newer, a camera, and OpenGL ES 3.0.
- A rotation-vector sensor is preferred. Accelerometer + magnetometer fallback
  is supported with reduced accuracy; gyro data improves stability detection.
- Supported packaged ABIs: `arm64-v8a`, `x86_64`.

### Checked-in toolchain

| Component | Version / setting |
| --- | --- |
| Application ID / Kotlin package | `com.bbinxx.photoshere` |
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Gradle wrapper | 9.5.0 |
| Android Gradle Plugin | 9.3.3 |
| Kotlin Compose plugin | 2.2.10 |
| Compile / target SDK | 37 / 37 |
| Minimum SDK | 29 |
| Java bytecode compatibility | 17 |

Versions are defined in [gradle/libs.versions.toml](gradle/libs.versions.toml).
Use the checked-in wrapper rather than changing plugins to match an arbitrary
system Gradle installation.

### Local SDK configuration

Create an untracked `local.properties` with your own SDK location:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

Then build:

```bash
./gradlew :app:assembleDebug
```

If needed, set `JAVA_HOME` to your compatible local JDK before invoking Gradle.
The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

For a connected phone with USB debugging enabled:

```bash
adb devices
./gradlew :app:installDebug
adb shell am start -W -n com.bbinxx.photoshere/.MainActivity
```

The app itself works offline. A first development build may need network access
to download Gradle and dependencies. Release signing/distribution is not set up
as a production release workflow.

## Testing and verification

Build, unit tests, and Android lint:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Device tests, leaving the APKs installed afterward:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

### Latest verified result

On 2026-11-10, debug assembly and lint passed, along with **40 JVM tests and
10 device tests**, with no failures. The connected M2101K6P did not expose an
ultra-wide option to this application, so actual 0.5× capture remains unverified
on hardware that exposes that lens. Main-camera capture and preview/JPEG ratio
matching passed. Real-world full-sphere seam quality remains a manual check.

### Automated coverage

Unit regressions cover:

- Quaternion rotation-vector decoding, scalar sign, roll, and orientation maths.
- Alignment thresholds and screen-axis direction after rolling the phone.
- Movement rearming after capture attempts, stationary/jitter suppression,
  near-duplicate filtering and final coverage gap completion.
- Field-of-view-based capture planning and nearest uncaptured targets.
- Free-order coverage, duplicate areas, rejected photos, sky/ground, and poles.
- Sensor cropping, portrait/landscape FOV, ultra-wide geometry, aiming projection.
- Robust overlap exposure gains, clipped samples, outliers, and sparse overlaps.
- GPano metadata generation/injection.

Device regressions cover:

- Launch, manual capture, live preview, settings navigation, activity recreation,
  and background/foreground return.
- Real rear-camera JPEG capture and shutter-time pose notification.
- Preview aspect ratio matching the EXIF-normalized JPEG; ultra-wide capture
  is also exercised when the device exposes a supported ultra-wide option.
- Real sensor output and comparison against Android's rotation-vector matrix.
- Synthetic full-sphere projection including both poles and independent XMP
  parsing with AndroidX ExifInterface.
- URI export, concurrent gallery saves, and preservation after failed replacement.
- Viewer delete/cancel confirmation, return after deletion, durable gallery removal,
  and preservation of original photos/exported copies.
- GPU sphere rendering before and after recreating the EGL context.
- Rejection of dark, clipped, and textureless synthetic photographs and acceptance
  of a sharp, normally exposed test pattern.

Reports are written under `app/build/reports/`, including
`tests/testDebugUnitTest/`, `androidTests/connected/debug/`, and
`lint-results-debug.html`.

The connected test device in this development session reports **M2101K6P,
Android 17**. The originally planned development target was OnePlus 13; results
on this connected device do not establish OnePlus 13 compatibility.

Automated synthetic tests do not establish real-world seam quality. Hands-on
full-sphere testing remains necessary for calibration, low-light performance,
parallax, moving subjects, and portrait/landscape changes during capture.

## Development progress

| Milestone from the original brief | Status |
| --- | --- |
| 1. Project foundation | Implemented: app structure, navigation, settings, permissions, build |
| 2. Camera | Implemented: preview, source JPEG capture, lifecycle, supported lens selection and metering |
| 3. Sensor tracking | Implemented and covered by mathematical/device regressions |
| 4. Guided spherical capture | Implemented: free-direction start, nearest-gap guidance, stability and coverage model |
| 5. Stitching | Partial: spherical output works; feature-based pose refinement and advanced seam handling remain |
| 6. Viewer | Implemented; GPU rendering/context-recreation regression included |
| 7. Export and gallery | Implemented; GPano parsing/export and gallery regressions included |
| 8. Optimization and testing | In progress; real-world quality, calibration, thermal/memory profiling remain |

### Recent changes

- Added visible gallery/viewer deletion with a shared confirmation and preserved originals.
- Prevented repeated automatic attempts while holding at the same dot, including
  validation failures and tiny drift at image edges.

- Corrected Android rotation-vector decoding and camera/display coordinate use.
- Added any-direction manual capture and coverage-based completion.
- Recorded orientation at the actual CameraX shutter callback.
- Hardened camera initialization/cancellation and preview lifecycle handling.
- Used SDR SurfaceView preview to address device preview compatibility risks.
- Preserved source photos and protected existing gallery results during replacement.
- Added supported ultra-wide/main selection and per-lens geometry for stitching.
- Fitted preview to the capture ratio and projected aiming dots using intrinsics.
- Added per-shot autofocus/light metering and quality-priority still capture.
- Replaced whole-frame brightness normalization with matching-overlap estimates.
- Moved the original prompt into `docs/PROJECT_BRIEF.md`.

The prior capture/reliability checkpoint is commit `be40370`. Consult Git history
for subsequent implementation and documentation checkpoints.

## Architecture

```text
app/src/main/java/com/bbinxx/photoshere/
├── camera/       CameraX controller, lens discovery, capture state and metering
├── capture/      Session controller, planner, alignment and spherical coverage
├── core/         Models, quaternion/vector/geometry maths and app container
├── data/         Settings, durable session store and gallery repository
├── export/       GPano XMP, MediaStore and URI export
├── navigation/   Compose navigation routes
├── sensors/      Orientation tracker and stability detector
├── stitching/    Preprocessing, quality checks, projection and exposure blending
├── ui/           Home, capture, processing, viewer, gallery and settings screens
└── viewer/       OpenGL ES renderer and touch/sensor interaction
```

Camera, sensor, storage, and stitching responsibilities are kept separate from
Compose rendering. ViewModels expose StateFlow, and background work uses
coroutines. OpenCV is initialized lazily for image validation; it is not required
to initialize during every app launch.

## Algorithms and camera geometry

### Orientation and alignment

World coordinates are X east, Y north, Z up. Android device coordinates are
X right, Y toward the top edge, Z out of the screen. The rear camera looks along
-Z. Quaternions map local camera rays into world directions.

Android rotation vectors store `axis * sin(angle/2)`, with an optional scalar
`cos(angle/2)`. The implementation preserves the scalar sign. Display rotation
uses `Surface.ROTATION_*` quarter-turn indices rather than degree values.
EXIF-normalized image axes and front-camera facing are accounted for in the
camera frame. Sensor tracking continues at the sensor delivery rate; the UI
consumes sampled updates at 20 Hz.

Alignment uses great-circle angular error. Target locations in the preview use
perspective projection through the active FOV, rather than a fixed pixels-per-
degree scale. Offscreen targets are constrained to the guidance boundary.

### Lens discovery and intrinsics

The default rear camera is identified through CameraX. Other exposed rear lenses
are classified using focal length relative to sensor width. Where ultra-wide is
available only through a logical camera's sub-1× zoom range, the app exposes that
option and prefers readable physical ultra-wide sensor characteristics.

`CameraGeometry` computes rectilinear FOV after centred sensor cropping to the
capture buffer aspect ratio, then applies the camera's relative output rotation.
The actual bound stream determines preview ratio. Each accepted image stores
its own horizontal/vertical FOV and shutter quaternion, so projection uses that
image's lens geometry.

When physical ultra-wide characteristics are unavailable, FOV is approximated
from the logical camera's zoom range. Manufacturer crop, stabilization,
distortion correction, focus breathing, or inaccurate calibration can still
introduce errors. The 0.5× UI denotes the ultra-wide preset; exact hardware zoom
ratios can differ. An unavailable preset is disabled rather than simulated.

### Coverage

The coverage model samples 36 equal-area latitude bands with 72 longitudes each,
plus explicit north/south pole checks. Accepted camera frusta cover those rays
with a small edge margin. Rejected photos do not advance progress. Nearest-gap
suggestions supplement the initial FOV-derived target grid.

Completion requires every sample and both poles. This is a finite geometric
estimate, not proof that every output pixel has valid, aligned image data.
Stitching independently reports real output pixel coverage.

### Stitching and exposure

1. Read source JPEGs, normalize EXIF orientation, and decode bounded working copies.
2. Construct each output equirectangular ray in world coordinates.
3. Transform it into each source camera using its saved quaternion and FOV.
4. Bilinearly sample valid source pixels and feather their contributions.
5. Balance exposure from shared scene points in overlapping images. The median
   reference/source luminance ratio rejects many outliers; clipped/dark samples
   are excluded, at least 32 valid observations are required, and gain is bounded
   to 0.7–1.4. Without sufficient overlap, gain remains 1.
6. Encode a 2:1 JPEG with GPano XMP metadata and store it in the gallery.

Unobserved areas are not filled with invented sky/ground. The current pipeline
uses sensor poses. An ORB/RANSAC helper exists but is **not integrated into global
pose refinement**. Current blending is feathering, not multiband or graph-cut
seam optimization. Stitching runs off the main thread with cancellation checks
and out-of-memory handling.

## Storage, permissions, and privacy

App-private data:

```text
files/sessions/<session-id>/session.json    Capture poses, targets and state
files/sessions/<session-id>/raw_*.jpg       Original accepted/rejected photographs
files/sessions/<session-id>/stitched.jpg   Generated intermediate panorama
files/spheres/                            Gallery JPEGs
files/gallery_index.json                  Gallery metadata
```

Source photographs are retained after stitching. Exports through MediaStore are
saved in `Pictures/PhotoSphere`; URI exports use a user-selected destination.
Sharing grants temporary URI access through FileProvider. Imported spheres are
expected to be compatible equirectangular JPEGs; arbitrary flat images and cropped
panoramas are not a reliable import workflow.

| Permission | Purpose |
| --- | --- |
| Camera | Live preview and photographs |
| Vibration | Optional haptic capture feedback |
| Media/storage read declarations | Present in manifest; document imports use SAF and do not need broad storage access |

There is no Internet permission, account, advertising, analytics, required cloud
service, or automatic upload. Android backup is currently enabled in the
manifest; device/OS backup behavior is separate from the app's offline workflow.

## Known limitations and next steps

The stitched photo is the main remaining quality gap:

- Sensor drift/magnetic disturbance can offset neighbouring frames. Integrate
  visual feature matching, robust camera rotation estimation and global refinement.
- Add lens distortion calibration/correction, especially for ultra-wide images.
- Add better seams, multiband blending and moving-object/ghosting handling.
- Calibrate actual JPEG FOV and logical-camera crop on each target phone.
- Verify full real-world spheres, zenith/nadir coverage and orientation changes;
  validate GPano exports in an independent interactive 360° viewer.
- Resume currently assumes the saved and current sensor world headings agree.
- The CPU stitcher uses memory proportional to output area. Profile large outputs,
  thermal behavior and long sessions on OnePlus 13 and other devices.
- Viewer textures are downsampled to a maximum long edge of 4096 pixels.
- Add a user-controlled source cleanup workflow and production release packaging.

Aiming and projection geometry are improved, but these changes do not remove
parallax from translating the camera or promise GCam-level stitching quality.

## Dependencies and licenses

The app uses existing AndroidX CameraX, Compose, Material 3, Lifecycle,
Navigation, ExifInterface, Kotlin coroutines, and OpenCV 4.11.0 libraries.
CameraX 1.6.1 is the camera backend. Library versions are centralized in the
version catalog; no extra camera/stitching dependency is needed for the current
changes. Device/JVM tests use AndroidX Test, Compose Test, Espresso, and JUnit.

See [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) for dependency notices.
Those notices do not define a license for PhotoSphere's own source code.

## Agent instructions and Git workflow

Agents start with [AGENTS.md](AGENTS.md), which points to detailed project rules
in [docs/agents/DEVELOPMENT.md](docs/agents/DEVELOPMENT.md). These cover camera
behavior, original-photo retention, offline processing, verification and honest
feature reporting.

`dev` is the integration branch. Create feature branches from its latest local
commit, implement/test/commit the batch, merge the feature back into `dev`, then
create the next feature branch from updated `dev`. Completed work must not be
left exclusively on an older feature branch. Remote pushes and changes to `main`
are separate actions and require authorization.
