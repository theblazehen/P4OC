# Testing Guide — Play Store Review Fixes

How to verify all 23 changes from the Play Store review analysis, build, and install.

## Prerequisites

- Java 17: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk`
- Android device connected via USB or WiFi (`adb devices` should show it)
- An OpenCode server running somewhere reachable from the device

## Quick build + install

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

# Run unit tests
./gradlew :app:testDebugUnitTest

# Build debug APK
./gradlew :app:assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug package name: `dev.blazelight.p4oc.debug`

One-liner to build + install:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk && \
  ./gradlew :app:assembleDebug && \
  adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Test checklist

### P0 — Background disconnect (oa-v5os, oa-az3q, oa-2afu)

These three tickets fixed the SSE reconnection cascade. The app used to
immediately kick you to the server screen on any connection hiccup.

| # | Step | Expected |
|---|------|----------|
| 1 | Connect to a server, open a session | Chat loads normally |
| 2 | Switch to another app for ~10 seconds, then come back | App reconnects automatically — you should NOT see the server screen. A brief "Reconnecting…" state is OK |
| 3 | Put the app in background for ~30 seconds, come back | Same — auto-reconnect, no server screen |
| 4 | Kill the server process, wait 10s, then restart it | App should try reconnecting. After the grace period (~45s for errors), if the server is back, it should reconnect. Only if the server stays down past the grace period should you land on the server screen |
| 5 | Check logcat: `adb logcat -s OpenCodeEventSource` | Should see "Reconnecting SSE" messages, not immediate "Escalating to disconnected" |

### P0 — PTY WebSocket reconnection (oa-xxuy)

| # | Step | Expected |
|---|------|----------|
| 1 | Open a terminal session, run a command | Terminal works normally |
| 2 | Background the app for 15 seconds, return | Terminal reconnects. You may see a brief "reconnecting" state but it should resume without needing to reopen |
| 3 | Check logcat: `adb logcat -s PtyWebSocket` | Should see reconnection attempts with exponential backoff (1s, 2s, 4s…) |

### P1 — Non-functional toggles (oa-nbhb, oa-orcl)

| # | Step | Expected |
|---|------|----------|
| 1 | Go to Settings → Agents | Agent cards should NOT have toggle switches. Each card shows the agent name and description |
| 2 | Go to Settings → Skills | Skill cards should NOT have toggle switches. Each card shows the skill name and MCP server status (e.g., "connected", "disconnected") instead of "MCP Server: skillname" |

### P1 — Branding (oa-8589, oa-n41k)

| # | Step | Expected |
|---|------|----------|
| 1 | Check app name in launcher | Shows "OpenCode", not "Pocket Code" |
| 2 | Check server setup help text | References "OpenCode" everywhere |
| 3 | Go to Settings → About | Shows "OpenCode v0.4.0-debug" (dynamic from BuildConfig, with -debug suffix in debug builds) |
| 4 | Check Settings → About dialog | Shows version, build number, server URL, and GitHub link |

### P2 — Custom directory for sessions (oa-j64w, oa-7c8o)

| # | Step | Expected |
|---|------|----------|
| 1 | Go to sessions list, tap "New Session" | Dropdown should show projects AND a "Custom directory" option |
| 2 | Select "Custom directory" | A text input appears where you can type an arbitrary path (e.g., `/home/user/myproject`) |
| 3 | Enter a valid path and confirm | Session creates in that directory |
| 4 | Select "Global" from dropdown | Session creates without a specific directory. No confusion about what "Global" means |

### P2 — Visual Settings cleanup (oa-1gjd, oa-aj2a)

| # | Step | Expected |
|---|------|----------|
| 1 | Go to Settings → Visual Settings | All labels should be localized (no hardcoded English if you change locale). Labels like "Font Size", "Theme" etc. come from string resources |
| 2 | Change your device language to another locale | Strings fall back to English (no crashes, no missing text) — this is expected since we only have English strings, but the point is they ARE in strings.xml for future translation |

### P2 — Hardcoded strings cleanup (oa-iy7q)

| # | Step | Expected |
|---|------|----------|
| 1 | Attach a file in chat | File info text (size, type) uses string resources, not hardcoded English |
| 2 | Check context usage display | Token usage text uses string resources |
| 3 | Expand a tool widget | Output text uses string resources |

### P2 — Shape consistency (oa-47su)

| # | Step | Expected |
|---|------|----------|
| 1 | Look at cards, dialogs, buttons throughout the app | Everything should have sharp rectangular corners (TUI style), no rounded Material shapes |
| 2 | Specific spots: agent cards, skill cards, model selector, code blocks | All rectangular — no `RoundedCornerShape` visible |

### P2 — Theme color fix (oa-uj8m)

| # | Step | Expected |
|---|------|----------|
| 1 | Check TuiComponents (buttons, text fields) | Accent colors should match the current OpenCode theme, not Material default purple |

### P2 — About section (oa-g6kr)

| # | Step | Expected |
|---|------|----------|
| 1 | Go to Settings → tap "About" | Opens a dialog (not a dead end) |
| 2 | Dialog contents | Shows: version, build info, connected server URL, "View on GitHub" link |
| 3 | Tap "View on GitHub" | Opens browser to `https://github.com/sst/opencode` |

### P2 — Hardcoded dp values (oa-m10r)

| # | Step | Expected |
|---|------|----------|
| 1 | Visual inspection | Spacing should look consistent — this is mostly a code quality fix. Key places: ProjectsScreen, InlineDiffViewer use `Spacing.*` / `Sizing.*` tokens |

### P3 — Notification settings (oa-erzs)

| # | Step | Expected |
|---|------|----------|
| 1 | Go to Settings → Notifications | Shows 3 toggles: Enable notifications, Permission requests, Questions |
| 2 | Disable "Enable notifications" | No more notifications from the app at all |
| 3 | Disable "Permission requests" only | Agent permission dialogs still work, but no notification sound/popup when one arrives |
| 4 | Toggle settings survive app restart | Settings persist via DataStore |

### P3 — Connection settings (oa-zzfm)

| # | Step | Expected |
|---|------|----------|
| 1 | Go to Settings (scroll down past other items) | "Connection" section visible with: Auto-reconnect toggle, Reconnect timeout slider |
| 2 | Disable auto-reconnect | App should NOT try to reconnect on disconnect — goes straight to server screen |
| 3 | Adjust timeout slider (e.g., 10s vs 60s) | Affects how long the app waits before giving up reconnection |
| 4 | Settings survive app restart | Persist via DataStore |

### P3 — testTag coverage (oa-mpqq)

This is for UI test automation, not manual testing. Verify with layout inspector or a test:

| Tag | Location |
|-----|----------|
| `tab_bar` | Tab bar container |
| `tab_bar_add_button` | New tab "+" button |
| `server_url_input` | Server URL text field |
| `server_connect_button` | Connect button |
| `server_settings_button` | Settings gear on server screen |
| `sessions_list` | Session list |
| `sessions_new_button` | New session button |
| `sessions_settings_button` | Settings gear on sessions screen |
| `message_list` | Chat message list |
| `send_button` | Send message button |
| `chat_input` | Chat text field |
| `chat_attach_button` | File attach button |
| `chat_queue_button` | Queue message button (when busy) |
| `chat_abort_button` | Stop/abort button |
| `chat_commands_button` | Slash commands button |
| `chat_files_button` | Files browser button |
| `settings_*` | Various settings items |
| `notification_*` | Notification settings toggles |
| `agent_selector` | Model/agent selector bar |
| `abort_summary_card` | Abort summary after Stop |

---

## Unit tests

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew :app:testDebugUnitTest
```

7 test files cover:
- `ChatViewModelTest` — core chat logic
- `MessageStoreTest` — message state management
- `DialogQueueManagerTest` — permission/question dialog queue
- `ModelAgentManagerTest` — model/agent selection
- `MapperTests` — DTO → domain mapping
- `EventMapperTest` — SSE event mapping
- `ThemeLoaderTest` — theme loading

All should pass (currently green).

---

## Logcat filters for debugging

```bash
# SSE reconnection
adb logcat -s OpenCodeEventSource

# PTY WebSocket
adb logcat -s PtyWebSocket

# Connection manager
adb logcat -s ConnectionManager

# General app logs
adb logcat | grep -i "p4oc"
```

---

## Full release build (signed)

Requires `local.properties` with signing config:

```properties
RELEASE_STORE_FILE=/path/to/keystore.jks
RELEASE_STORE_PASSWORD=****
RELEASE_KEY_ALIAS=****
RELEASE_KEY_PASSWORD=****
```

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew :app:assembleRelease
# APK at: app/build/outputs/apk/release/app-release.apk
```

GitHub release build (signed with debug key, minified):

```bash
./gradlew :app:assembleGithubRelease
# APK at: app/build/outputs/apk/githubRelease/app-githubRelease.apk
```
