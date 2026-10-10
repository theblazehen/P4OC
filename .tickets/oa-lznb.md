---
id: oa-lznb
status: closed
deps: []
links: []
created: 2026-09-24T15:04:43Z
type: feature
priority: 2
assignee: Jasmin Le Roux
---
# Render OpenCode v2 ephemeral assistant deltas live

Problem: OpenCode v2.0.6 publishes session.text.delta and session.reasoning.delta as ephemeral events, while its REST message window projects durable started/ended content. A REST refresh per token cannot recover the fragment and can starve work. Evidence: pinned v2.0.6 session-event schema marks deltas ephemeral; P4OC V2EventMapper now ignores ephemeral fragments and refreshes at durable started/ended boundaries; OpenCodeEventSourceTest asserts that contract. UX Constraint: chat must show coherent prefix during generation and recover final full text after reconnect without duplicate/missing tokens, while preserving tab-owned workspace scope and generation fences. Expected Behavior: buffer/apply v2 deltas to the exact synthesized message part identity after durable bootstrap, with sequence/order handling and final ended snapshot reconciliation; do not poll REST on every token. Acceptance Criteria: live text/reasoning streams visibly advance at token cadence, final text equals authoritative REST, reconnect recovers missed ephemeral frames, and sessions across directories never cross. Verification: isolated event sequence test plus real v2.0.6 assistant turn against a configured model, in Crabbox.

