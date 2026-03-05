---
id: opencode_android-wue
status: closed
deps: []
links: []
created: 2026-01-30T20:52:18.964481973+02:00
type: bug
priority: 1
---
# Type converter exception handling missing - crashes on malformed JSON

In Converters.kt:10-14, toStringList() throws SerializationException if stored JSON is malformed. App crashes when reading corrupted data. Need try-catch with fallback.


