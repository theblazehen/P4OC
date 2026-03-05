---
id: opencode_android-ruzx
status: closed
deps: []
links: []
created: 2026-01-31T12:31:50.637699919+02:00
type: feature
priority: 1
---
# Migrate to mikepenz/multiplatform-markdown-renderer

Replace 25K lines of forked Java (FluidMarkdown/Markwon) with mikepenz/multiplatform-markdown-renderer.

## Context
- PR submitted: https://github.com/mikepenz/multiplatform-markdown-renderer/pull/501
- Using our fork until merged: theblazehen/multiplatform-markdown-renderer@fix/streaming-conflate
- Benchmark: 2% → 36% render success rate during SSE streaming

## Tasks
1. Add JitPack + fork dependency
2. Create StreamingMarkdown.kt wrapper with conflate
3. Create OpenCodeMarkdownTheme.kt (color/typography mapping)
4. Delete 6 libs/ modules (fluid-markdown, markwon-*)
5. Update settings.gradle.kts
6. Test with real SSE streaming


