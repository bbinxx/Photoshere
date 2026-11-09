> Archived original product requirements. For implemented features, verification, and current limitations, see [README.md](../README.md).

# Project: PhotoSphere
## Native Android Open-Source 360° Photo Sphere Camera

Act as a senior Android engineer, computer vision engineer, camera systems engineer, and UI/UX designer.

Your task is to design and develop a production-quality, fully functional Android application for capturing, stitching, viewing, managing, and sharing 360-degree Photo Spheres.

This must be a real camera application, not a UI prototype or a basic panorama application.

The capture experience should be inspired by the guided Photo Sphere capture experience of Google Pixel Camera, particularly its centre alignment target, moving alignment dot, automatic image capture, circular progress indicator, and guided spherical capture sequence.

Do not copy Google's branding, proprietary assets, or source code. Implement an original interface with similar interaction principles.

---

# 1. EXISTING PROJECT INFORMATION

Project name: PhotoSphere

Package name: com.bbinxx.photoshere

Language: Kotlin

UI framework: Jetpack Compose

Build system: Gradle Kotlin DSL

Minimum Android SDK: API 29

Development device: OnePlus 13

Primary testing device: OnePlus 13 through USB debugging

Existing project already contains:
- Android Gradle Plugin configuration.
- Kotlin configuration.
- Jetpack Compose.
- Material 3.
- Version catalog (libs.versions.toml).

IMPORTANT:
- Inspect the existing project before modifying anything.
- Preserve the existing package name.
- Preserve compatible existing dependencies.
- Do not unnecessarily replace the project structure.
- Resolve Gradle and JDK compatibility before implementation.
- Use Java 17 compatibility where supported by the selected Android Gradle Plugin.
- Keep compileSdk and targetSdk compatible with installed SDKs and dependencies.
- Do not arbitrarily upgrade or downgrade Gradle plugins.
- Use stable dependency versions rather than experimental releases unless justified.

---

# 2. PRIMARY OBJECTIVE

Build a complete Android application capable of capturing a full spherical 360° × 180° photograph.

The application must:
1. Capture multiple overlapping photographs.
2. Track phone orientation using sensors.
3. Guide the user to capture all required directions.
4. Automatically capture when alignment and stability conditions are met.
5. Estimate camera poses.
6. Stitch photographs into a spherical panorama.
7. Generate an equirectangular image.
8. Export a standard 360° JPEG with GPano metadata.
9. Display the finished Photo Sphere in an interactive 360° viewer.
10. Save, manage, export and share captured Photo Spheres.

The output should be a real spherical photograph, not a horizontal panorama with an artificially extended sky and ground.

---

# 3. TECHNOLOGY STACK

Use the following architecture.

## Android application
- Kotlin
- Jetpack Compose
- Material 3
- Coroutines
- StateFlow
- ViewModel
- Navigation Compose

## Camera
- CameraX
- Camera2 Interop for supported advanced camera controls
- ImageCapture
- Preview
- ImageAnalysis where necessary

## Motion tracking
- Android SensorManager
- TYPE_GYROSCOPE
- TYPE_ROTATION_VECTOR
- TYPE_ACCELEROMETER
- Sensor fusion
- Quaternion-based orientation calculations

## Computer vision
- OpenCV Android SDK
- OpenCV C++ where native processing is necessary
- JNI for native integration
- Feature detection and matching
- Camera pose estimation
- Spherical projection
- Image warping
- Seam optimization
- Exposure compensation
- Image blending

## Rendering
- OpenGL ES 3.0 where supported
- Custom spherical mesh
- Equirectangular texture mapping

## Export
- JPEG
- XMP GPano metadata
- Android MediaStore
- Storage Access Framework

## Optional enhancement
- ARCore for camera pose estimation and visual-inertial tracking.
- ARCore must remain optional.
- The core application must work without requiring ARCore.
- Do not depend exclusively on proprietary Google services.

Use open-source dependencies wherever possible. Maintain a THIRD_PARTY_LICENSES.md file documenting dependencies and their licenses.

---

# 4. CAMERA INTERFACE — MOST IMPORTANT FEATURE

The camera interface is the most important part of this application.

Create a clean, minimal, premium camera interface inspired by Google Pixel Photo Sphere capture.

The screen should contain:

- Full-screen camera preview.
- Centre alignment target.
- Moving alignment dot.
- Circular progress indicator.
- Capture progress.
- Current capture position.
- Cancel capture control.
- Flash control where supported.
- Camera settings.
- Guidance text.
- Capture completion indicator.

## 4.1 Centre alignment target

Display a fixed target at the centre of the screen.

The target should consist of:
- A small central circle.
- A visible alignment boundary.
- A clear visual indication when the camera is correctly aligned.

Display a second dot representing the target orientation relative to the current phone orientation.

The moving dot must respond to the phone's actual orientation.

When the phone is rotated, the moving dot should move smoothly towards the centre target.

When the moving dot reaches the target:
- Change the target appearance.
- Indicate successful alignment.
- Check whether the phone is stable.
- Automatically capture the photograph.

Do not use touchscreen position or arbitrary animation to simulate alignment. All alignment must be calculated from real sensor and camera orientation data.

## 4.2 Automatic alignment detection

Implement:
- Quaternion-based orientation tracking.
- Relative angular difference calculation.
- Yaw and pitch error calculation.
- Angular velocity detection.
- Stability detection.
- Alignment tolerance.
- Automatic capture triggering.

Initial configurable values:
- Alignment tolerance: approximately 2–4 degrees.
- Stability requirement: approximately 400–800 milliseconds.
- Minimum angular velocity: configurable.
- Capture cooldown: configurable.

These values must be adjustable through camera settings or internal configuration.

Use sensor timestamps and appropriate filtering.

Prevent repeated captures at the same orientation.

## 4.3 Circular progress indicator

Create a visually attractive circular progress guide.

The guide should show:
- Required capture positions.
- Completed positions.
- Current target position.
- Remaining positions.
- Overall completion percentage.

The progress should advance automatically when an image is successfully captured.

Use distinct visual states:
- Not captured.
- Target position.
- Aligned.
- Capturing.
- Captured.
- Failed or retry required.

The circular progress indicator must reflect actual capture state, not an arbitrary timer.

Provide smooth animations without interfering with camera performance.

## 4.4 Guided capture instructions

Display concise instructions such as:

- Align the dot.
- Hold steady.
- Capturing...
- Turn slowly.
- Move upward.
- Capture the sky.
- Capture the ground.
- Photo Sphere completed.

Make instructions context-aware.

---

# 5. FULL SPHERICAL CAPTURE SYSTEM

Implement a guided capture system that covers the complete sphere.

The application must capture:
- Front.
- Back.
- Left.
- Right.
- Intermediate horizontal directions.
- Upper hemisphere.
- Zenith (directly above).
- Lower hemisphere.
- Nadir (directly below).

Do not implement only a horizontal 360-degree panorama.

## 5.1 Capture grid

Create a configurable spherical capture grid.

The capture grid should use yaw and pitch coordinates.

For example, use:
- Multiple horizontal capture positions.
- Several vertical capture rows.
- Additional upper and lower coverage.
- Dedicated zenith and nadir positions.

Capture spacing must be selected based on camera field of view and required image overlap.

Aim for approximately 30–50% overlap between neighbouring images, with configuration options.

Do not hardcode a capture count without considering the actual camera field of view.

## 5.2 Capture ordering

Design a natural capture sequence.

The user should not have to guess where to point the camera.

Show the next recommended target using the moving dot.

The sequence should:
1. Start near the horizon.
2. Capture surrounding horizontal positions.
3. Progress to upper angles.
4. Capture zenith.
5. Progress to lower angles.
6. Capture nadir.
7. Verify coverage.

Use a spherical coverage model rather than relying solely on a fixed number of photographs.

## 5.3 Capture validation

Before accepting an image:
- Verify orientation.
- Verify stability.
- Check image sharpness.
- Check exposure.
- Check capture success.
- Check whether the image adds useful coverage.
- Avoid duplicate or nearly identical photographs.

If an image fails validation, request another capture.

Allow manual capture as an alternative.

---

# 6. CAMERA ENGINE

Implement a dedicated CameraController.

Requirements:

- Camera permission handling.
- Camera lifecycle management.
- Camera preview.
- Image capture.
- Main rear camera selection.
- Front camera selection.
- Available camera identification.
- Resolution selection.
- Focus management.
- Exposure control.
- White balance handling.
- Flash control.
- Zoom controls.
- Image orientation correction.
- Capture metadata storage.

Use CameraX as the primary camera framework.

Use Camera2 Interop for advanced controls when supported.

Prefer the main rear camera for Photo Sphere capture.

Do not switch between physical lenses during a single sphere capture because different camera intrinsics and colour characteristics can introduce stitching errors.

Provide a camera selection screen for compatible lenses, but ensure that the entire capture session uses one consistent camera.

Implement:
- Automatic exposure.
- Exposure lock where supported.
- Focus lock where supported.
- White balance consistency where supported.

Avoid abrupt changes in exposure between consecutive images.

Preserve sufficient image quality for stitching.

---

# 7. SENSOR AND ORIENTATION ENGINE

Implement a separate OrientationTracker.

Responsibilities:
- Read rotation vector.
- Read gyroscope.
- Read accelerometer.
- Calculate device orientation.
- Calculate relative camera orientation.
- Calculate yaw.
- Calculate pitch.
- Calculate roll.
- Detect phone movement.
- Detect stability.
- Smooth noisy orientation measurements.

Use quaternions internally to avoid Euler-angle instability and gimbal lock.

Account for:
- Device rotation.
- Display orientation.
- Camera sensor orientation.
- Rear camera coordinate systems.
- Device mounting orientation.

The capture guide must remain accurate regardless of portrait or landscape device orientation.

Do not rely on accelerometer-only orientation tracking.

Use appropriate filtering and sensor fusion.

---

# 8. IMAGE STITCHING ENGINE

This is the core computer vision component.

Create a dedicated stitching module.

Do not assume that OpenCV's basic Stitcher class alone is sufficient for complete Photo Sphere generation.

Implement or integrate a pipeline containing:

## 8.1 Image preparation
- Load captured photographs.
- Correct orientation.
- Read camera metadata.
- Normalize image dimensions where appropriate.
- Handle EXIF orientation.
- Estimate camera intrinsics.
- Apply lens distortion correction when calibration data is available.
- Handle exposure differences.

## 8.2 Feature detection
Use suitable feature detection methods such as:
- SIFT.
- ORB.
- AKAZE.

Select based on performance, licensing and image quality.

## 8.3 Feature matching
- Match neighbouring images.
- Filter incorrect matches.
- Apply ratio tests where appropriate.
- Use RANSAC.
- Estimate relative camera poses.
- Detect inconsistent alignment.

## 8.4 Camera pose estimation
- Estimate camera orientation.
- Use sensor orientation as an initial estimate.
- Refine orientation using image features.
- Detect accumulated drift.
- Optimize global alignment.

## 8.5 Spherical projection

Generate a proper equirectangular spherical projection.

The output must represent:
- 360 degrees horizontally.
- 180 degrees vertically.

Implement spherical warping using the estimated camera poses and camera intrinsics.

## 8.6 Seam optimization
- Find appropriate image boundaries.
- Minimize visible stitching seams.
- Handle moving objects.
- Detect ghosting.
- Correct exposure inconsistencies.
- Apply multiband blending where appropriate.

## 8.7 Zenith and nadir correction

Pay particular attention to the top and bottom of the sphere.

Avoid:
- Black holes.
- Missing areas.
- Incorrect stretching.
- Uncovered regions.
- Obvious stitching artefacts.

Provide a manual correction option if complete coverage cannot be reconstructed.

## 8.8 Processing performance

- Perform stitching in background threads.
- Do not block the main UI thread.
- Provide processing progress.
- Support cancellation.
- Handle out-of-memory conditions.
- Resize working copies when necessary.
- Preserve original captured photographs.
- Use native processing selectively.

Stitching must be based on actual image data and camera poses.

---

# 9. 360° VIEWER

Build an interactive spherical Photo Sphere viewer.

Requirements:
- Full-screen immersive viewer.
- Drag to rotate the view.
- Pinch to zoom.
- Double-tap to reset.
- Gyroscope viewing mode.
- Smooth movement.
- Horizontal and vertical navigation.
- Full-screen display.
- Reset orientation control.

Use an OpenGL ES sphere with an equirectangular texture.

The viewer must correctly map the entire 360° × 180° image onto an inside-facing sphere.

Support:
- JPEG images.
- GPano metadata.
- Previously exported Photo Spheres.
- Imported compatible Photo Spheres.

Do not simply display the equirectangular image as a flat photograph.

---

# 10. PHOTO SPHERE EXPORT

Implement standard Photo Sphere export.

Requirements:
- Equirectangular JPEG.
- 2:1 image aspect ratio.
- GPano XMP metadata.
- Correct full panorama dimensions.
- Correct cropped-area dimensions when applicable.
- Correct pose heading and orientation metadata where available.
- Appropriate image quality.
- EXIF metadata preservation where possible.

Use the GPano XMP namespace.

Support metadata fields including:
- GPano:ProjectionType = equirectangular
- GPano:UsePanoramaViewer = True
- GPano:FullPanoWidthPixels
- GPano:FullPanoHeightPixels
- GPano:CroppedAreaImageWidthPixels
- GPano:CroppedAreaImageHeightPixels
- GPano:CroppedAreaLeftPixels
- GPano:CroppedAreaTopPixels

Verify the exported files with an independent GPano-compatible viewer.

Save through MediaStore.

Provide:
- Save to gallery.
- Share.
- Export to file.
- Delete.
- Rename.

---

# 11. GALLERY AND PHOTO MANAGEMENT

Build an integrated Photo Sphere gallery.

Features:
- Grid layout.
- Thumbnail previews.
- Capture date.
- Image resolution.
- File size.
- Processing status.
- Open in 360° viewer.
- Delete.
- Share.
- Export.
- Rename.

Store project-specific capture information separately from the final JPEG.

Allow reopening incomplete capture sessions when possible.

Do not delete original source images automatically after successful stitching.

Provide a user-controlled cleanup option.

---

# 12. USER INTERFACE AND DESIGN

Design a premium, minimalist camera interface.

Visual direction:
- Inspired by Google Pixel camera interactions.
- Original visual identity.
- Dark camera interface.
- Material 3.
- Subtle animations.
- Clear visual hierarchy.
- Minimal controls.
- Large camera preview.
- Accessible controls.

## Main screens

1. Home screen.
2. Camera capture screen.
3. Capture settings.
4. Processing screen.
5. 360° viewer.
6. Gallery.
7. Photo details.
8. App settings.
9. Permissions screen.
10. About and open-source licenses.

## Camera screen layout

Top:
- Close button.
- Flash setting.
- Camera settings.

Centre:
- Live camera preview.
- Fixed alignment target.
- Moving alignment dot.
- Circular capture progress.
- Contextual instructions.

Bottom:
- Manual capture control.
- Automatic capture toggle.
- Current capture count.
- Cancel and finish controls.

Make the alignment target and moving dot visible against different backgrounds.

Use a combination of contrast, outline and subtle shadows.

Do not obstruct the camera preview unnecessarily.

---

# 13. CAPTURE SETTINGS

Provide configurable settings.

- Automatic capture.
- Manual capture.
- Alignment sensitivity.
- Stability threshold.
- Capture delay.
- Image resolution.
- Image quality.
- Spherical capture density.
- Overlap percentage.
- Processing quality.
- Exposure lock.
- Focus lock.
- Gyroscope guidance.
- Haptic feedback.
- Sound feedback.
- Save original photographs.
- Auto stitching.
- Auto export.

Provide sensible defaults.

Avoid exposing complicated computer vision parameters to ordinary users.

---

# 14. ERROR HANDLING

Handle the following situations:

- Camera permission denied.
- Camera unavailable.
- Camera disconnected.
- Camera initialization failure.
- Unsupported resolution.
- Sensor unavailable.
- Sensor calibration problems.
- Excessive phone movement.
- Blurry photograph.
- Low-light capture.
- Incorrect exposure.
- Duplicate photograph.
- Insufficient spherical coverage.
- Stitching failure.
- Missing image.
- Out-of-memory condition.
- Storage unavailable.
- Export failure.
- Interrupted processing.

Provide understandable error messages and recovery options.

Never silently discard photographs.

---

# 15. PERFORMANCE AND BATTERY

Optimize the application for the OnePlus 13.

Requirements:
- Avoid unnecessary camera restarts.
- Efficient sensor processing.
- Controlled image memory usage.
- Background stitching.
- GPU acceleration where useful.
- Avoid excessive allocations.
- Avoid unnecessary bitmap copies.
- Use appropriate image resolutions.
- Handle large spherical images.
- Monitor thermal conditions.
- Avoid overheating during long captures.

Do not process full-resolution images repeatedly on the main thread.

Use bounded concurrency for image processing.

---

# 16. PRIVACY AND SECURITY

This application should operate offline by default.

Requirements:
- No account required.
- No mandatory cloud services.
- No analytics by default.
- No advertising.
- No unnecessary internet permission.
- Local image processing.
- User-controlled exports.
- Explicit camera and storage permissions.
- No automatic upload.
- No hidden image transmission.

The app should work without an internet connection.

---

# 17. TESTING

Implement unit tests and instrumentation tests.

Test:
- Quaternion calculations.
- Orientation tracking.
- Alignment tolerance.
- Stability detection.
- Capture sequencing.
- Capture state transitions.
- Coverage calculations.
- Duplicate detection.
- Image matching.
- Spherical projection.
- GPano metadata.
- Export functionality.
- Viewer orientation.

Test on a physical OnePlus 13.

Pay particular attention to:
- Actual camera sensor orientation.
- Main camera selection.
- Camera preview rotation.
- Gyroscope accuracy.
- Alignment dot movement.
- Automatic capture timing.
- Full spherical coverage.
- Stitching quality.
- Zenith and nadir reconstruction.
- Memory consumption.

Do not claim a feature works until it has been tested.

---

# 18. ARCHITECTURE AND CODE ORGANIZATION

Use a modular architecture.

Suggested structure:

app/
  src/main/
    java/com/bbinxx/photoshere/

      core/
        common/
        model/
        permissions/
        storage/

      camera/
        CameraController.kt
        CameraProviderManager.kt
        CameraCaptureManager.kt
        CameraSettings.kt

      sensors/
        OrientationTracker.kt
        GyroscopeManager.kt
        QuaternionUtils.kt
        StabilityDetector.kt

      capture/
        CaptureController.kt
        CaptureSession.kt
        CapturePlanner.kt
        CaptureTarget.kt
        AlignmentDetector.kt
        CoverageTracker.kt

      stitching/
        StitchingEngine.kt
        ImagePreprocessor.kt
        FeatureMatcher.kt
        CameraPoseEstimator.kt
        SphericalWarper.kt
        ImageBlender.kt
        StitchingResult.kt

      native/
        OpenCVBridge.kt

      viewer/
        SphereViewer.kt
        SphereRenderer.kt
        SphereTexture.kt

      export/
        GpanoMetadataWriter.kt
        PhotoSphereExporter.kt

      gallery/
        GalleryRepository.kt
        PhotoSphereEntity.kt

      data/
        repository/
        database/
        preferences/

      ui/
        home/
        camera/
        processing/
        viewer/
        gallery/
        settings/
        components/
        theme/

      navigation/

Use interfaces between major components.

Keep camera logic separate from Compose UI.

Keep sensor processing separate from image stitching.

Use dependency injection if it provides genuine architectural value.

Use structured concurrency and lifecycle-aware collection.

Avoid giant ViewModels and giant composable functions.

---

# 19. CAPTURE STATE MACHINE

Implement a proper capture state machine.

Suggested states:

IDLE
INITIALIZING_CAMERA
CALIBRATING
READY
SEARCHING_ALIGNMENT
ALIGNED
WAITING_FOR_STABILITY
CAPTURING
VALIDATING_IMAGE
CAPTURE_COMPLETED
MOVING_TO_NEXT_TARGET
COVERAGE_CHECK
STITCHING
EXPORTING
COMPLETED
PAUSED
CANCELLED
ERROR

Implement valid transitions.

Prevent:
- Multiple simultaneous captures.
- Duplicate target captures.
- Capturing while camera is busy.
- Starting stitching before capture is complete.
- Invalid state transitions.

Expose capture state through StateFlow.

---

# 20. DEVELOPMENT MILESTONES

Do not attempt to implement everything in one uncontrolled code-generation operation.

Build the application incrementally.

## Milestone 1 — Project foundation
- Inspect existing project.
- Verify dependencies.
- Configure Gradle.
- Configure permissions.
- Create architecture.
- Create navigation.
- Build initial UI.

Deliverable: application builds and launches.

## Milestone 2 — Camera
- CameraX integration.
- Rear camera preview.
- Capture photographs.
- Save original photographs.
- Camera lifecycle.

Deliverable: working camera.

## Milestone 3 — Sensor tracking
- Gyroscope.
- Rotation vector.
- Quaternion calculations.
- Orientation tracking.
- Alignment target.

Deliverable: moving dot responds to actual phone movement.

## Milestone 4 — Pixel-style guided capture
- Spherical capture grid.
- Circular progress indicator.
- Alignment detection.
- Stability detection.
- Automatic capture.
- Capture sequence.
- Retry and manual capture.

Deliverable: complete guided capture experience.

## Milestone 5 — Stitching engine
- Image preprocessing.
- Feature matching.
- Camera pose estimation.
- Spherical projection.
- Blending.
- Coverage validation.

Deliverable: generated spherical image.

## Milestone 6 — Viewer
- OpenGL ES sphere.
- Touch interaction.
- Gyroscope mode.
- Zoom.
- Orientation reset.

Deliverable: interactive Photo Sphere viewer.

## Milestone 7 — Export and gallery
- GPano metadata.
- JPEG export.
- MediaStore integration.
- Gallery.
- Sharing.
- Importing existing Photo Spheres.

Deliverable: complete photo management.

## Milestone 8 — Optimization and testing
- OnePlus 13 testing.
- Memory optimization.
- Battery optimization.
- Stitching improvements.
- Error recovery.
- Accessibility.
- Documentation.

Deliverable: stable application.

---

# 21. IMPORTANT ENGINEERING RULES

1. Do not build a fake Photo Sphere using a flat panorama.
2. Do not simulate sensor movement.
3. Do not use arbitrary coordinates for automatic capture.
4. Do not use a timer as a substitute for alignment detection.
5. Do not hardcode capture positions without accounting for camera orientation.
6. Do not assume every camera lens has the same calibration.
7. Do not discard source photographs.
8. Do not block the main thread during image processing.
9. Do not use proprietary cloud stitching.
10. Do not leave nonfunctional buttons or placeholder screens.
11. Do not silently ignore errors.
12. Do not claim complete spherical capture if zenith or nadir coverage is missing.
13. Do not introduce unnecessary dependencies.
14. Do not modify working project configurations without justification.
15. Add documentation for all important algorithms and coordinate transformations.

For any complex algorithm, explain the implementation and its limitations.

If a feature cannot be implemented reliably with the selected technology, explain why and propose an alternative instead of pretending it works.

---

# 22. EXPECTED FINAL PRODUCT

The finished application should behave like a dedicated Photo Sphere camera.

A typical user workflow:

1. Open PhotoSphere.
2. Select Capture 360°.
3. Camera opens.
4. Application initializes sensors and camera.
5. A fixed centre target appears.
6. A moving dot indicates the direction to rotate.
7. User aligns the dot with the target.
8. Application detects stability.
9. Application automatically takes a photograph.
10. The progress indicator advances.
11. Application guides the user to the next direction.
12. User captures the complete sphere.
13. Application validates coverage.
14. Stitching begins.
15. Application generates the equirectangular image.
16. The finished Photo Sphere opens in the interactive viewer.
17. User saves or shares the result.

The final product must be a functional, offline-first, open-source Android application.

---

# 23. FIRST TASK — START IMPLEMENTATION

Before coding:

1. Inspect all existing source files.
2. Inspect settings.gradle.kts.
3. Inspect root build.gradle.kts.
4. Inspect app/build.gradle.kts.
5. Inspect gradle/libs.versions.toml.
6. Inspect AndroidManifest.xml.
7. Inspect the current Compose application.
8. Check available SDKs and Gradle compatibility.

Then produce:
- Existing project analysis.
- Proposed architecture.
- Dependency plan.
- Implementation plan.
- Potential compatibility issues.

After inspection, begin implementing Milestone 1.

Do not stop after producing a design document. Actually implement, compile and test the project.

Work incrementally. After each milestone:
- Run Gradle build.
- Fix compilation errors.
- Report completed features.
- Identify untested functionality.
- Preserve a working application.

Start with Milestone 1 now.
