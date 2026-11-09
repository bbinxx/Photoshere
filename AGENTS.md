# PhotoSphere agent instructions

Read [docs/agents/DEVELOPMENT.md](docs/agents/DEVELOPMENT.md) before changing this
repository. It contains the project's engineering rules, verification requirements,
and the user's Git workflow. Read [README.md](README.md) for actual implementation
status. [docs/PROJECT_BRIEF.md](docs/PROJECT_BRIEF.md) is the archived product brief,
not a claim that every requested feature is complete.

## Required branch workflow

`dev` is the current integration branch and must receive every completed batch.
Every new feature or fix must use a NEW feature branch created from the latest
local `dev`; do not reuse an older feature branch. After implementing
and verifying the work, commit it, merge it into `dev`, then create the next
feature branch from the updated `dev`. Do not leave completed work only on an
old feature branch. Do not merge into `main` or push remote branches unless the
user requests it or the session already authorizes it.

Preserve source photographs and existing user changes. Never claim camera,
coverage, stitching quality, or device compatibility beyond the actual evidence.
Use the existing CameraX/OpenCV stack where appropriate. Do not simulate sensors,
coverage, ultra-wide lenses, or a full sphere.
