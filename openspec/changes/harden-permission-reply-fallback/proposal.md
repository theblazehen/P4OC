# Change: Harden permission reply compatibility fallback

## Why

GitHub issue #65 identifies an unsafe compatibility decision in `WorkspaceClient.respondToPermission`. The session-scoped v2 reply route is currently allowed to fall back to the workspace-scoped legacy mutation whenever its response is labeled `text/html`, even if the response is an HTTP 5xx. A real authorization or server failure can therefore be obscured by a second mutation, and buffered error bodies on fallback and error paths are not explicitly closed.

The question compatibility path already distinguishes an absent route or a successful HTML compatibility page from a real HTTP failure. Permission replies need the same content-type convention and strict status-first classification without changing their tab-owned workspace routing or user-facing failure behavior.

## What Changes

Harden only the session-scoped v2-to-legacy permission-reply compatibility path:

- Retain the v2 transport's existing `Response<Unit>` declaration and follow Retrofit's ownership model. `UnitResponseBodyConverter` closes the original successful network body during conversion; the returned `response.raw().body` is a non-readable `NoContentResponseBody` metadata placeholder that retains content type and length. Classification may inspect its media type but must not read or close the placeholder.
- Classify one v2 permission-reply response with a route-specific disposition.
- Treat a 2xx response as success only when neither the `Content-Type` response header nor the raw metadata placeholder's media type is HTML.
- Fall back only for HTTP 404 or a 2xx HTML response.
- Detect `text/html` case-insensitively, after trimming the base media type and ignoring optional parameters, from either the header or raw metadata placeholder. Reuse the question compatibility convention rather than adding a second parser.
- Close every present buffered `errorBody()` before invoking legacy fallback for HTTP 404 or propagating another HTTP failure. Do not close the raw metadata placeholder. A successful HTML response needs no additional network-body closure because Retrofit's Unit converter closed the original body before returning it.
- Invoke the legacy permission mutation exactly once after a fallback disposition, forwarding the same request identifier and reply through the owning `WorkspaceClient`'s captured directory and explicit `workspace = null` routing.
- Propagate the original v2 `HttpException` for every non-2xx response except 404, including HTML 5xx, without invoking legacy.
- Preserve coroutine cancellation. Cancellation is not converted into compatibility fallback, success, or a user-visible raw exception.
- Keep failures on the existing concise, non-sensitive permission-reply error path; response bodies, stack traces, credential-bearing URLs, and arbitrary server exception text are not exposed.

## Classification Contract

The classifier evaluates the v2 response in this order:

1. HTTP 404: close the present buffered error body, then select legacy fallback.
2. Any other non-2xx status: close the present buffered error body, then throw the original `HttpException`.
3. A 2xx response identified as HTML by either supported media-type source: select legacy fallback; Retrofit has already closed the original successful network body during Unit conversion.
4. Any other 2xx response: succeed without a legacy call.

A fallback disposition permits one legacy mutation for that user reply. It is not a retry policy: a legacy result, failure, or cancellation is returned or propagated without a second legacy call.

## Workspace, Cancellation, and Error Constraints

The legacy fallback remains owned by the `WorkspaceClient` captured for the tab. Its `directory` argument is forwarded exactly, including an intentional `null`, and the existing explicit `workspace = null` argument remains explicit. The implementation must not inspect active-tab state, a session directory, settings, a last-used project, or any global/default workspace source.

Cancellation from either network call propagates as cancellation. It must not be caught as a reason to switch routes, restart a mutation, or publish a stale success. The compatibility classifier changes route selection only; it does not add retries, background work, or presentation state.

## Exclusions

This change does not alter permission discovery, question endpoint ordering, question reply/reject behavior, session lifecycle, permission dialog semantics, allowed reply values, or the legacy endpoint's response contract. It does not add fallback for redirects, authorization failures, rate limits, ordinary 4xx responses other than 404, 5xx responses, malformed responses, transport failures, or cancellation. It does not add a global/default workspace, active-tab lookup, directory fallback chain, duplicate mutation, automatic retry, telemetry, or new UI/error copy.

## Impact

- **Network/data:** retain `Response<Unit>`; inspect content type through the non-readable `response.raw().body` metadata placeholder without reading or closing it, rely on Unit conversion to close the original successful network body, close buffered `errorBody()` instances on HTTP fallback/failure paths, and then select at most one legacy call.
- **Workspace routing:** keep the existing tab-owned `WorkspaceClient` directory forwarding and explicit `workspace = null` legacy arguments unchanged.
- **Errors:** retain the original non-fallback HTTP status for existing safe, human-readable presentation without exposing response content.
- **Tests:** add focused `WorkspaceClientPermissionFallbackTest` regression coverage for classification, robust HTML matching, body closure, cancellation, single legacy mutation, and exact directory forwarding.
