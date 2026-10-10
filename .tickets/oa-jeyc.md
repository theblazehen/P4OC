---
id: oa-jeyc
status: closed
deps: []
links: []
created: 2026-09-24T14:45:42Z
type: chore
priority: 2
assignee: Jasmin Le Roux
---
# Resolve OpenCode v2 adapter Detekt findings

Problem: OpenCode v2 compatibility adds new static-analysis findings. Evidence: Crabbox ./gradlew :app:testDebugUnitTest :app:detekt --no-daemon compiled and ran the unit suite, but :app:detekt failed with 257 weighted issues on 2026-09-24. Findings span V2SessionOpenCodeApi, V2WorkspaceOpenCodeApi, V2EventMapper, InlineV2FormCard, PtyWebSocketClient, ChatViewModel and nearby changed integration code; examples include wildcard imports, long expressions, complexity and long parameter lists. UX Constraint: retain workspace-scoped v2 behavior and all v1 compatibility; do not delete Compose/serialization/reflection-linked code to silence analysis. Expected Behavior: clean adapter structure and formatting while preserving live v2 API and form/terminal behavior. Acceptance Criteria: :app:detekt succeeds without blindly baselining new findings; focused live OpenCodeV2LiveIntegrationTest and full :app:testDebugUnitTest still pass. Verification: run both in Crabbox against the pinned v2.0.6 server, with OPENCODE_V2_URL and scoped directory env.

