---
id: opencode_android-26i
status: closed
deps: []
links: []
created: 2026-01-30T20:53:00.151467885+02:00
type: bug
priority: 2
---
# API: PtyDto.pid is non-nullable but server may return null

In Dtos.kt:965, PtyDto.pid: Int is non-nullable. If server returns null before process starts, deserialization fails. Make nullable: Int? = null


