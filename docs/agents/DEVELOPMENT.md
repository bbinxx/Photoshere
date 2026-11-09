# Development instructions for agents

These instructions preserve the user's project preferences across sessions.
Current explicit user instructions take precedence if they change these rules.

## Before work

1. Read the root `AGENTS.md`, the README, and relevant source files.
2. Inspect `git status`, the current branch, and recent history. Preserve changes
   already present; never discard user work to simplify a checkout or merge.
3. Treat `dev` as the integration baseline. Inspect configuration before changing
   SDK/JDK/Gradle versions; preserve package `com.bbinxx.photoshere`.
4. Consult the archived project brief for intended scope, but use source/tests
   and the README to determine implemented functionality.

## Git workflow required by the user

For each completed batch:

1. Start a descriptive `feature/<name>` branch from the current local `dev`.
   Every new feature and fix must use a fresh branch from latest `dev`; do not
   reuse an older feature branch. If changes already exist, preserve them with
   a temporary stash/checkpoint, integrate the completed baseline into `dev`,
   create the fresh branch from `dev`, then restore the unfinished changes.
2. Implement the requested changes and update documentation.
3. Run relevant build, unit, lint and available device checks. Fix failures before
   integrating. Distinguish a failed assertion from missing/disconnected hardware.
4. Review the diff, stage the intended source/docs only, and commit.
5. Switch to `dev` and merge the completed feature branch, resolving conflicts
   while preserving both changes. A merge commit is preferred for traceability.
6. Verify `dev` contains the feature commit and the worktree is clean.
7. Create the next feature branch from updated `dev`. If no next task is named,
   use a descriptive follow-up branch and leave it at the same commit as `dev`.
8. Report commit hashes, integrated branch and new active branch to the user.

Typical commands, after reviewing work and passing checks:

```bash
git switch dev
git switch -c feature/example
# implement, verify, stage and commit on the feature branch
git switch dev
git merge --no-ff feature/example -m "Merge example improvements"
git switch -c feature/next-task
```

The user has authorized this local commit/merge/new-feature workflow. Do not ask
again for routine local Git operations already covered by that authorization.
Execution-environment approval requirements still apply. Do not infer permission
to push, force-push, delete branches, rewrite commits, reset files, or merge to
`main`. Check remote divergence before a requested remote sync; do not assume
local `dev` is identical to `origin/dev`.

## Camera and capture

- Use CameraX for lifecycle, preview, JPEGs, autofocus and automatic metering;
  Camera2 interop is for supported controls and metadata. Prefer established
  libraries over implementing an alternative camera stack.
- Keep the preview at the delivered capture/sensor ratio, fitted without filling
  and cropping the entire phone screen. Controls can occupy the black margins.
- Provide real supported ultra-wide/main selection (0.5×/1× presets). Disable an
  unavailable lens. Do not create a fake ultra-wide image by scaling/cropping.
- Keep one lens fixed after the first accepted photograph in a sphere. Record
  the selected lens and each image's actual/estimated intrinsics and pose.
- Keep preview autofocus continuous; release per-shot metering/focus holds after
  capture unless the user enabled focus lock. Autofocus and adapt exposure/white
  balance to lighting before shots. Locks are
  optional; do not lock metering before convergence or force infinity by default.
- Manual capture can start facing any direction. Guide toward nearby uncaptured
  regions in any order, with appropriate overlap and stability for auto capture.
- Use real quaternions, display/camera axes and shutter-time orientation. Project
  targets using the active lens's FOV, not arbitrary pixel movements.
- Progress must come from accepted images' coverage, including both poles. Do
  not substitute a photo count or a timer for full 360° × 180° coverage.
- Allow one automatic attempt per stationary visit, including failed validation.
  Require actual movement before automatic rearming; cooldown expiry or jitter
  alone must not cause repeated photos. Keep explicit manual retries available.
- Automatic capture rejects unsuitable blur/exposure/movement and requests a
  retake. An explicit manual shutter press must take and accept a readable image,
  overriding quality, stability and duplicate-area advice, then advance. Do not
  keep retrying that dot after the user overrides. Missing/unreadable images or
  capture failures cannot count as completion. Persist overridden warnings.
- Preserve accepted and rejected source photos; no automatic source deletion
  after stitching. The user may explicitly confirm deletion of an unfinished
  capture and its source folder. Keep saved sphere JPEGs, exports and other
  sessions intact; deleting a gallery sphere alone still preserves originals.

## Stitching and viewer

- Generate real 2:1 equirectangular output from source image data and camera poses.
  Do not invent missing sky/ground or treat a horizontal panorama as a sphere.
- Correct EXIF orientation and account for lens-specific FOV, output crop,
  rotation and, when supported, lens calibration.
- Exposure compensation must compare matching overlap regions rather than
  forcing unrelated sky/ground photographs to the same overall brightness.
- Use background processing, cancellation and bounded image memory. Keep errors
  visible and preserve captures if processing/export fails.
- The current engine is sensor-based with feather blending. Do not claim feature
  refinement, multiband seams, distortion correction, or GCam-level results until
  implemented and tested. OpenCV is available for appropriate improvements.
- Viewer behavior must remain spherical (OpenGL), including texture restoration
  after context recreation. JPEG exports include GPano XMP metadata.

## Verification

Use meaningful tests for geometry, camera state/lifecycle and image processing.
Do not add trivial tests merely to mirror low-impact edits.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb devices
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

Use the configured, compatible JDK and SDK. If a device disconnects, retain the
build/test evidence and clearly identify which device checks remain unverified.
Do not silently skip a failing regression to make a suite appear green. Real
sphere/seam quality requires hands-on captures in addition to synthetic tests.
Identify the actual device model; OnePlus 13 is the intended target but the
connected device may differ.

When installation/device work is requested, leave the verified app installed.
Avoid tests unintentionally removing the app at the end of the run.

## Documentation and maintenance

- Keep the README current with setup, architecture, implemented features, test
  results, remaining work and honest limits. Do not present the archived prompt
  as a completed-feature checklist.
- Keep requirements in `docs/PROJECT_BRIEF.md` and agent instructions in this
  folder, with the root `AGENTS.md` pointing here for automatic discovery.
- Update third-party notices if dependencies change. Do not invent a project
  source license or claim a license based only on dependency notices.
- No unnecessary dependencies, Internet permission, analytics, accounts, uploads
  or cloud processing. Preserve offline behavior and user-controlled exports.
- Keep `.gradle`, `.kotlin`, SDK paths, build artifacts and device images out of
  commits. Do not commit local secrets or private captures.
