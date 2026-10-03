# Changelog

All notable changes to LogicForge are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and LogicForge
follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html). The version itself is set in
`gradle.properties`.

## [Unreleased]

## [1.0.0] - 2026-10-03

First tagged release: a self-contained Linux desktop application.

### Fixed

- A component that rescheduled its own wakeup to a different time while an earlier one was
  still pending could be evaluated an extra time, at the stale original time. No shipped
  component triggers this today (only `ClockBehavior` uses wakeups, and it never reschedules
  while one is outstanding), but the scheduler itself no longer has the bug.

### Added

- Self-contained Linux packages: a jpackage application image with a private Java runtime and
  JavaFX, a `.deb` for Ubuntu 24.04 and Debian 13, and a strictly confined snap (`core24`).
  Neither needs a separately installed JDK or JavaFX.
- Desktop integration: desktop entry, application icons in all common sizes, the
  `application/x-logicforge-project` file type for `*.logic`, AppStream metadata and a man page.
- `logicforge FILE` opens a project from the command line or the file manager, through the same
  path as File → Open; `--version` and `--help`.
- Help → About LogicForge, a Save As button, tooltips on the toolbar and a "Physical ICs"
  example category.
- A global error handler: unexpected errors are shown in a dialog with folded-away details and
  logged to `$XDG_STATE_HOME/logicforge/logicforge.log`. No telemetry, no network access.
- Gradle tasks `packageLinuxImage`, `stageLinuxRoot`, `packageDeb`, `verifyDeb` and
  `packageSnap`; `scripts/smoke-test-linux.sh` for installed-package smoke tests; CI for tests,
  examples, the UI check under Xvfb and the `.deb`; a release workflow for version tags.

### Changed

- Projects are saved atomically (temporary file, flush, atomic rename) and the previous version
  is kept as `<name>.logic.bak`. Save As adds a missing `.logic` extension and asks before
  replacing an existing file under the completed name.
- A project is named after its file when it is opened, so the window title shows the file.
- The toolbars move controls that do not fit into an overflow menu instead of cutting every
  label short; dialogs are readable in the dark theme and never truncate their message.
- Closing the main window ends the application, including open Study and memory windows.

### Fixed

- An inverted (active-low) push button was inverted twice and drove the wrong level.
- Damaged or foreign project files (duplicate ids, invalid rotations, deeply nested JSON,
  broken escapes, non-UTF-8 content, huge files) are reported as unreadable projects instead of
  escaping as internal errors.
- Saving a memory image no longer overwrites the target file non-atomically, and loading one
  reads at most as much as fits into the memory.

[Unreleased]: https://github.com/MichaelWeissDEV/LogicForge/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/MichaelWeissDEV/LogicForge/releases/tag/v1.0.0
