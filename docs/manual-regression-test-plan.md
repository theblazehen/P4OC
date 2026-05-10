# Manual Regression Test Plan

This checklist is for on-device validation of the current local changes against `origin/main`, including uncommitted work.

Use one connected Android phone and one reachable OpenCode server. The P0 section should take about 35 minutes. P1 adds about 25 minutes. P2 is optional.

## Setup

Prepare two server workspaces:

```sh
ssh box bash -lc '
mkdir -p ~/p4oc-A ~/p4oc-B
cd ~/p4oc-A && git init -q && \
  printf "fun main() { println(\"A\") }\n" > sample.kt && \
  printf "{\"name\":\"A\"}\n" > sample.json && \
  printf "# A\n\nhello\n" > README.md && \
  printf "print(\"A\")\n" > script.py && \
  printf "echo A\n" > run.sh && \
  printf "name = \"A\"\n" > config.toml && \
  printf "const x: string = \"A\"\n" > app.ts && \
  printf "<root>A</root>\n" > layout.xml && \
  printf "name: A\n" > config.yaml && \
  printf "TOKEN=A\n" > .env && \
  git add . && git commit -q -m init
cd ~/p4oc-B && git init -q && echo "B only" > b.txt && git add . && git commit -q -m init
'
```

Have a small image around 200 KB and a file larger than 2 MB in the phone's Downloads folder for upload testing.

Replace `ssh box` and paths with the actual test server details.

## P0: Must Pass

1. Connect to backend.
   Action: Launch app, connect to the test server, and land on Sessions.
   Expected: Connection dot is green/connected, no snackbar/error appears, and session list loads.

2. Confirm two workspaces are selectable.
   Action: Open Projects and verify `p4oc-A` and `p4oc-B` can be selected.
   Expected: Both workspaces appear and can be opened without errors.

3. SSE streaming smoke.
   Action: In `p4oc-A` chat, send `Reply with exactly: streaming-regression-ok`.
   Expected: Tokens stream live, the message persists, and the status/busy indicator returns idle without manual refresh.

4. Permission prompt flow.
   Action: In `p4oc-A` chat, ask to run a harmless command such as `pwd`.
   Expected: Permission/tool approval UI appears when required. Allow resumes and renders output. Deny aborts cleanly with a visible cancellation/error.

5. Background/resume while streaming.
   Action: Start a long command/response such as `sleep 5 && ls -R`, background the app for about 10 seconds, then reopen it.
   Expected: Output is intact, there are no duplicate messages or missing chunks, and the session is not stuck busy.

6. OFISH read and Sora open.
   Action: From `p4oc-A` chat, open Files and tap `sample.kt`.
   Expected: Files subtitle/workspace is `p4oc-A`; file opens read-only; filename and Kotlin language are shown; line numbers are visible; Kotlin syntax coloring is present.

7. Sora edit/save happy path.
   Action: In `sample.kt`, tap edit pencil, add `println("A saved from phone")`, tap save, confirm the Review changes dialog.
   Expected: Review changes dialog appears with a diff, Save succeeds, dirty dot clears, and no error toast appears.
   Server check: `ssh box grep "A saved from phone" ~/p4oc-A/sample.kt` passes.

8. Baseline hash refresh.
   Action: Without leaving the editor after the successful save, edit `sample.kt` again and save.
   Expected: It succeeds first try with no spurious File changed on disk conflict.

9. Save conflict Reload path.
   Action: Open `sample.json`, edit locally, but do not save. On the server run:

   ```sh
   ssh box "printf '{\"name\":\"server-conflict\"}\n' > ~/p4oc-A/sample.json"
   ```

   Then save in the app: Review changes, then Save.
   Expected: File changed on disk dialog appears with Reload and Overwrite. Tap Reload; content updates to `server-conflict`, and dirty dot clears.

10. Save conflict Overwrite path.
    Action: Edit `sample.json` to `{"name":"phone-overwrite"}`. On the server run:

    ```sh
    ssh box "printf '{\"name\":\"server-second\"}\n' > ~/p4oc-A/sample.json"
    ```

    Save in app. On File changed on disk, tap Overwrite.
    Expected: Save succeeds.
    Server check: `ssh box grep phone-overwrite ~/p4oc-A/sample.json` passes.

11. Unsaved edit guard.
    Action: Edit any file, do not save, then tap exit-edit/back.
    Expected: Discard unsaved changes? dialog appears with Discard and Cancel. Cancel keeps the edit and editor open. Discard exits without writing the server file.

12. Multi-tab setup.
    Action: In the tab bar tap `+`, choose New Sessions tab, select `p4oc-B`, and open/create a session.
    Expected: Tab 1 remains `p4oc-A`; tab 2 shows `p4oc-B`.

13. Multi-tab file isolation.
    Action: In tab 1/A open Files; in tab 2/B open Files; switch back and forth.
    Expected: A lists `sample.kt`, `sample.json`, etc. B lists only `b.txt`. File lists and workspace subtitles do not swap or bleed.

14. Multi-tab chat/event isolation.
    Action: In A chat ask to create `a-only.txt` with content `A`. In B chat ask to create `b-only.txt` with content `B`.
    Expected: Each tab streams only its own session. A Files shows `a-only.txt` and not `b-only.txt`. B Files shows `b-only.txt` and not `a-only.txt`.

15. New Files tab inherits active workspace.
    Action: From the B tab `+` menu choose New Files tab.
    Expected: File Explorer opens scoped to `p4oc-B`, and subtitle/workspace label confirms B.

16. Closing tabs does not break active workspace.
    Action: Close the B tab.
    Expected: A tab still works; session list/chat/files render; live updates still work; no crash occurs.

17. Existing file attachment.
    Action: In `p4oc-A` chat, tap attach, open Attach Files dialog, select `sample.kt`, and tap `[Attach]`.
    Expected: Attachment chip/row appears in composer, and the remove control removes it.

18. Attachment server handoff.
    Action: Attach `sample.kt` and send `Reply with the path of the attached file you received.`
    Expected: Assistant/tool sees the correct project-relative path, not an empty path, malformed URL, or wrong project. No send failure snackbar appears.

19. Removed chat UI sanity.
    Action: Send `Show a code block, then run pwd, then mention a non-existent file error.`
    Expected: Normal text, code block highlighting, tool call widget/output, permission UI if needed, and error text all render. There are no blank cards, placeholders, or crashes.

## P1: High Value

20. SAF upload small file from Files.
    Action: In `p4oc-A` Files, tap upload icon and pick a small local text/image.
    Expected: Upload progress sheet appears, progresses to complete with 1 uploaded/0 failed, Dismiss works, and the file appears in the list.
    Server check: `ssh box ls -la ~/p4oc-A/<filename>` shows the uploaded file.

21. SAF upload large/multiple files.
    Action: From Files, upload the small image plus the file larger than 2 MB.
    Expected: Items upload sequentially, progress updates, both complete, app remains responsive, and server file sizes match local sizes.

22. Upload cancel cleanup.
    Action: Start a large upload from Files and tap Cancel before completion.
    Expected: In-flight item becomes cancelled/failed.
    Server check: `ssh box "ls -la ~/p4oc-A/.ofish.upload.* 2>/dev/null"` returns no temp upload files.

23. Upload retry.
    Action: Disable Wi-Fi, start an upload, wait for failure, re-enable Wi-Fi, and tap Retry failed.
    Expected: Only failed items retry, successful items are not duplicated, and final sheet shows success.

24. Chat picker `[Upload]` flow.
    Action: In chat attach dialog, tap `[Upload]` and pick a local file from the Android picker.
    Expected: Upload sheet completes, the file is auto-selected/attached, and tapping `[Attach]` leaves a chip in the composer.

25. Uploaded chat attachment readable.
    Action: Upload/attach a local text file through the chat picker and send `Read the attached file and reply with the first line.`
    Expected: Assistant reads the correct content. There is no file-not-found, empty, or malformed URL error.

26. Chat uploaded MIME sanity.
    Action: Upload/attach one image and one text file through the chat picker, then send a message referencing both.
    Expected: Send succeeds and assistant/tool can distinguish/use both attachments. There is no MIME/send error.

27. Sora/TextMate grammar sweep.
    Action: From `p4oc-A` Files, open `sample.kt`, `sample.json`, `README.md`, `script.py`, `run.sh`, `config.toml`, `app.ts`, `layout.xml`, `config.yaml`, and `.env`.
    Expected: Each shows the correct language subtitle and visible syntax coloring. No crash or plain fallback for these types.

28. Save several file types.
    Action: Edit and save `README.md`, `script.py`, and `.env`.
    Expected: For each, Review changes dialog appears, Save succeeds, dirty dot clears, and the server file contains the phone edit.

29. Dirty buffer survives rotation.
    Action: Edit `sample.kt`, type `// rotation test`, do not save, then rotate the phone.
    Expected: Still in edit mode, dirty dot remains visible, and typed unsaved text is preserved.

30. Session Changes screen.
    Action: In A chat with edits, open overflow and choose View Changes.
    Expected: Session Changes loads, lists changed files such as `sample.kt`, `sample.json`, and `a-only.txt`, summary counts look reasonable, and added/removed lines are colored.

31. Per-file Diff Viewer.
    Action: From Session Changes, tap a file.
    Expected: Unified diff opens with filename, +/- lines, and line gutters. Toggle side-by-side; panes align and scroll without blank/parse errors.

32. Licenses from Settings.
    Action: Open Settings, then Open source licenses.
    Expected: Screen lists Apache-2.0, GPL-3.0, LGPL-2.1, and MIT entries. Tap GPL-3.0; long full text scrolls; back returns.

33. Licenses from About.
    Action: Settings, About, Licenses button.
    Expected: It opens the same Open source licenses screen, and back navigation works.

34. Agent-driven delete stays scoped.
    Action: In A chat, ask to delete `a-only.txt` and approve if prompted.
    Expected: A Files no longer shows `a-only.txt`, and B Files remains unchanged.

35. Restart persistence.
    Action: Force-quit and relaunch.
    Expected: Tabs restore safely or show an explicit mismatch. If restored, A tab remains bound to `p4oc-A` and B tab to `p4oc-B`; there is no silent wrong-workspace restore.

## P2: Optional

36. Connection loss/recovery.
    Action: Stop or pause backend, then restore it.
    Expected: Connection dot changes away from green and a sanitized error appears if needed. After restore, the app reconnects or recovers without crash.

37. Symbol search.
    Action: In Files, tap symbol search and query `main`.
    Expected: Results render and tapping one opens the correct file. No-match shows No symbols found. Backend error shows Symbol search failed, not blank UI.

38. File search and breadcrumbs.
    Action: In Files, search `sample`, then a nonsense string. Enter a subfolder and tap the root breadcrumb.
    Expected: Search filters results, nonsense shows `-- no matching files --`, and breadcrumb returns to workspace root with the correct label.

39. Line-number toggle.
    Action: Open `sample.kt` and tap the line-number toggle.
    Expected: Gutter hides/shows immediately in read/edit mode, scroll position is preserved, and file content is unchanged.

40. Terminal tab.
    Action: From `+` menu, open Terminal and run `echo hi`. Force-quit and relaunch.
    Expected: Output renders. After relaunch, terminal tab is not resurrected as a persisted tab.

41. No-changes save guard.
    Action: Enter edit mode without changing text and try save.
    Expected: Save is disabled or shows No changes to save. No empty write/diff occurs.

42. Empty diff state.
    Action: Open View Changes on a brand-new session with no file edits.
    Expected: `No file changes in this session`, not a blank screen or crash.

43. Theme switch.
    Action: Change theme in Settings/Visual.
    Expected: Editor, diff viewer, upload sheet, and chat recolor cleanly with no leftover default Material colors.

44. Queued message during streaming.
    Action: While assistant is streaming, attempt to send another message.
    Expected: Designed queue/disabled behavior occurs, no message is lost, and input is usable after the run finishes.

## Cleanup

```sh
ssh box "rm -rf ~/p4oc-A ~/p4oc-B"
```
