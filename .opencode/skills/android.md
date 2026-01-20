# Android Automation Skill

Use this skill for Android device automation, UI testing, and diagnostics.

## Prerequisites

- Device connected: `adb devices` should show your device
- App installed: `./gradlew installDebug`
- For Appium MCP: session must be created first

## Available Tools

### Appium MCP Tools (UI Automation)
When the `appium` MCP is connected, you have access to:

- `appium_set_platform` - Set platform to "android"
- `appium_create_session` - Create automation session with device capabilities
- `appium_delete_session` - End current session
- `appium_find_element` - Find element by locator strategy
- `appium_click` - Click/tap an element
- `appium_send_keys` - Type text into element
- `appium_clear` - Clear text field
- `appium_scroll` - Scroll in a direction
- `appium_get_page_source` - Dump current UI hierarchy (XML)
- `appium_install_app` / `appium_terminate_app` - App lifecycle

### Bash ADB Commands (Diagnostics + Lifecycle)
Use bash for these common operations:

```bash
# Device info
adb devices
adb shell getprop ro.product.model

# App lifecycle
adb shell am start -n com.pocketcode.debug/com.pocketcode.MainActivity
adb shell am force-stop com.pocketcode.debug
adb shell pm clear com.pocketcode.debug

# Logs
adb logcat -c  # clear
adb logcat -d --pid=$(adb shell pidof com.pocketcode.debug) | grep TAG

# Diagnostics
adb shell dumpsys activity top | head -20
adb shell dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'

# UI hierarchy (alternative to Appium)
adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml

# File operations
adb pull /data/data/com.pocketcode.debug/files/
```

## Locator Strategies (Priority Order)

1. **resource-id** (most stable) - Use when app has `testTag` or view IDs
   ```
   using: "id", value: "server_url_field"
   ```

2. **text** (good for buttons/labels)
   ```
   using: "text", value: "Connect"
   ```

3. **content-desc** (good for icons)
   ```
   using: "accessibility id", value: "Settings"
   ```

4. **class + text combo** (xpath, less stable)
   ```
   using: "xpath", value: "//android.widget.EditText[@text='']"
   ```

5. **class + index** (last resort, fragile)
   ```
   using: "xpath", value: "(//android.widget.EditText)[1]"
   ```

## Common Workflows

### Start Fresh Session
```
1. adb shell am force-stop com.pocketcode.debug
2. adb shell am start -n com.pocketcode.debug/com.pocketcode.MainActivity
3. appium_set_platform("android")
4. appium_create_session with capabilities:
   {
     "platformName": "Android",
     "appium:automationName": "UiAutomator2",
     "appium:deviceName": "Android",
     "appium:appPackage": "com.pocketcode.debug",
     "appium:appActivity": "com.pocketcode.MainActivity",
     "appium:noReset": true
   }
```

### Connect to Remote Server (PocketCode)
```
1. Find and tap "Remote" mode button
2. Find EditText for URL → send keys "http://192.168.24.25:4096"
3. Find and tap "Connect" button
4. Wait for "Sessions" text to appear (confirms navigation)
```

### Capture Diagnostics Bundle (Text-Only)
When debugging issues, capture:
```bash
# 1. Logs from app
PID=$(adb shell pidof com.pocketcode.debug)
adb logcat -d --pid=$PID > /tmp/app_logs.txt

# 2. Current activity
adb shell dumpsys activity top | grep -A5 "ACTIVITY" | head -10

# 3. Window focus
adb shell dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'

# 4. UI hierarchy
adb shell uiautomator dump /sdcard/ui.xml
adb pull /sdcard/ui.xml /tmp/ui_hierarchy.xml
```

### Run Instrumentation Tests
```bash
# Single test
./gradlew :app:connectedDebugAndroidTest --tests "*.ConnectSmokeTest"

# All tests
./gradlew :app:connectedDebugAndroidTest

# With specific device
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.device=SERIAL
```

## Troubleshooting

### Appium session fails to start
- Check device is connected: `adb devices`
- Kill stale Appium: `pkill -f appium`
- Check UiAutomator2 server: `adb shell pm list packages | grep uiautomator`

### Element not found
- Dump page source to see actual hierarchy
- Check if element is scrolled off-screen
- Wait for animations/loading to complete
- Try different locator strategy

### Clicks not registering
- Element might be covered by another view
- Try scrolling element into view first
- Check if element is actually clickable (dump hierarchy, check `clickable="true"`)

## Key TestTags in PocketCode App

When available, prefer these stable selectors:

| Screen | Element | testTag |
|--------|---------|---------|
| Server | URL field | `server_url_field` |
| Server | Connect button | `connect_button` |
| Chat | Message input | `chat_input` |
| Chat | Send button | `send_button` |

(Add testTags to app code as needed for stable automation)
