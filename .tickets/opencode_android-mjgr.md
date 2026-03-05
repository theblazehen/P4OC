---
id: opencode_android-mjgr
status: closed
deps: []
links: []
created: 2026-01-30T22:33:55.161128356+02:00
type: task
priority: 4
---
# Remove unused ChatInputBarWithAttachments component

FileAttachment.kt contains ChatInputBarWithAttachments which is defined but never used. The actual input uses ChatInputBar. Remove dead code to reduce maintenance burden.


