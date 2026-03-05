---
id: opencode_android-5zc
status: closed
deps: []
links: []
created: 2026-01-30T20:52:36.292048698+02:00
type: bug
priority: 2
---
# Missing permission check in TermuxBridge.runCommand() - SecurityException

In TermuxBridge.kt:110-117, runCommand() checks if Termux is installed but NOT if RUN_COMMAND permission is granted. Can cause SecurityException on Android 10+.


