# Manual Regression UIAutomator Results

Date: 2026-05-08
Target package: `dev.blazelight.p4oc.debug`
APK: `app/build/outputs/apk/debug/app-debug.apk`

## Reset Notice

The earlier run in this file was invalidated because it accidentally launched `dev.blazelight.p4oc` instead of the debug package `dev.blazelight.p4oc.debug`. Those results were removed and this file now tracks debug-package-only testing.

No app data reset was performed.

## Running Results

### Debug Identity

- PASS: Launched `dev.blazelight.p4oc.debug` with `adb shell monkey -p dev.blazelight.p4oc.debug 1`.
- PASS: `run-as dev.blazelight.p4oc.debug true` succeeded, confirming the launched app is debuggable.
- PASS: Fresh UIAutomator hierarchy nodes reported package `dev.blazelight.p4oc.debug`.
- NOTE: No app data reset was performed.

### Start State

- PASS: Debug app launched into an existing restored chat session without crashing.
- PASS: `+` tab menu was reachable and exposed `New Sessions tab`, `New Files tab`, and `New Terminal tab`.
- PASS: A new Sessions tab opened without clearing previous restored tabs.

### Projects And Workspace Selection

- PASS: Projects screen opened in the debug package.
- PASS: Projects list included `talkie` at `/tmp/talkie` and `HRM` at `/tmp/HRM`.
- PASS: Selecting `talkie` opened a `Sessions · talkie` scoped tab.
- PASS: Selecting `HRM` opened a `Sessions · HRM` scoped tab.

### Chat And SSE

- PASS: Existing `talkie` chat session opened in the debug package and rendered prior streamed assistant content.
- NOT COMPLETED: Exact fresh streaming prompt was not re-run in debug because ADB text injection previously inserted `%20` literally for prompts containing spaces.

### Files And OFISH Read Path

- PASS: Opening Files from the `talkie` session created a `talkie` Files tab.
- PASS: File list was scoped to `talkie` and showed seeded files including `sample.kt`, `sample.json`, and `README-p4oc.md`.
- PASS: Opening `sample.kt` showed the file content `fun main() { println("A") }`, filename `sample.kt`, language `kotlin`, and line number `1`.
- PASS: Debug hierarchy exposed editor actions `Toggle line numbers` and `Edit file`.
- PASS: Line-number toggle was available in the editor toolbar.

### Editor Save Flow

- PASS: Tapping `Edit file` entered edit mode.
- PASS: Edit mode exposed `Save file` and `View file` actions.
- PASS: Typing into the editor marked the title dirty as `● sample.kt`.
- PASS: Tapping Save opened the `Review changes` dialog with `sample.kt`, `+1`, `-1`, `Cancel`, and `Save`.
- PASS: Confirming Save updated the server-side file `/tmp/talkie/sample.kt`.
- PASS: A second edit/save succeeded without a spurious conflict, validating baseline hash refresh at a smoke level.
- NOTE: ADB inserted `%0A` literally in the file, so the saved test content was `fun main() { println("A") }%0A//debug-save-test-again` rather than a newline-separated edit.
- NOT COMPLETED: Conflict Reload/Overwrite path. A coordinate sequence intended to open `sample.json` stayed on `sample.kt`, so no valid conflict result was collected.

### Multi-Tab Workspace Behavior

- PASS: Creating a new Files tab while active in `talkie` opened another Files tab scoped to `talkie`.
- PASS: The inherited Files tab listed `talkie` files and showed `talkie` in the tab/header.
- PARTIAL: `HRM` could be selected as its own scoped Sessions tab.
- BLOCKED/FAIL CANDIDATE: Attempting to open a new Files tab from the `HRM` context caused subsequent `uiautomator dump` and `dumpsys window` calls to time out. The debug process was still alive via `pidof`, but UI hierarchy capture stopped responding. Treat this as an apparent UI/dump hang until reproduced manually.

### Uploads

- PASS: Files toolbar exposed `Upload files here` in the debug package.
- PASS: Tapping upload launched Android DocumentsUI under package `com.android.documentsui`.
- NOT COMPLETED: Actual file selection/upload was intentionally skipped to avoid selecting arbitrary personal files from the phone.

### Terminal

- PASS: New Terminal tab opened from the `talkie` context and showed `Terminal t0DO · talkie`.
- PARTIAL: Terminal extra-keys UI rendered (`ESC`, `/`, `HOME`, arrows, `PGUP`, etc.).
- BLOCKED: Terminal buffer text was not exposed in UIAutomator XML, so command output could not be asserted from hierarchy dumps.

### Known Invalidated Results

- INVALIDATED: Earlier findings about `New with custom directory` and About/Licenses were observed in `dev.blazelight.p4oc`, not `dev.blazelight.p4oc.debug`. They must be retested in debug before counting.

### Current Stop Reason

- Stopped after the HRM Files-tab attempt because multiple ADB UI hierarchy/window commands timed out while `dev.blazelight.p4oc.debug` was still running. No app data reset was performed.
