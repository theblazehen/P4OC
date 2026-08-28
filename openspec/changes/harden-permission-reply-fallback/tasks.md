# Implementation Tasks

## 1. Permission response classification and ownership

- [x] 1.1 Retain `Response<Unit>` and use `response.raw().body` only as Retrofit's non-readable content-type metadata placeholder. Rely on Unit conversion to close the original successful network body; never read or close the placeholder, and own `errorBody()` separately.
- [x] 1.2 Reuse the question compatibility path's case-insensitive, parameter-tolerant `text/html` matching convention; do not add a second content-type parser.
- [x] 1.3 Add one route-specific permission-response classifier ordered as: 404 fallback, other non-2xx `HttpException`, 2xx HTML fallback, then 2xx non-HTML success.
- [x] 1.4 Close every present buffered `errorBody()` before returning a 404 fallback disposition or propagating another HTTP failure, without reading or closing the raw metadata placeholder and without replacing the original `HttpException`.
- [x] 1.5 On fallback, invoke the existing legacy permission mutation exactly once with the original request identifier and reply; do not retry a false result, failure, or cancellation.
- [x] 1.6 Keep legacy routing on the owning `WorkspaceClient`'s exact captured directory, including intentional null, with the existing explicit `workspace = null`; add no active/global/default workspace lookup or directory fallback.
- [x] 1.7 Preserve coroutine cancellation from v2 and legacy calls without converting it to fallback, success, retry, or a second mutation.
- [x] 1.8 Preserve the existing concise, non-sensitive permission-reply presentation error; do not surface response bodies, stack traces, credential-bearing URLs, serialized requests, or arbitrary exception text.

## 2. Focused regression coverage

- [x] 2.1 Add `WorkspaceClientPermissionFallbackTest` coverage proving a 2xx non-HTML v2 response succeeds with zero legacy calls.
- [x] 2.2 Test HTTP 404 fallback, buffered error-body closure before fallback, and exactly one legacy call with the original request identifier and reply.
- [x] 2.3 Test successful HTML fallback from both the response header and raw metadata placeholder, including mixed case, surrounding whitespace, optional parameters, and a conflicting/missing non-HTML source.
- [x] 2.4 Test that HTML 5xx closes every present buffered error body, propagates the original-status `HttpException`, and makes zero legacy calls.
- [x] 2.5 Test redirects, authorization/rate-limit responses, other non-404 4xx responses, and ordinary 5xx responses propagate without legacy fallback and close every present buffered error body.
- [x] 2.6 Test a fallback invokes legacy at most once when legacy returns false, throws, or is cancelled.
- [x] 2.7 Test cancellation of the v2 call propagates and makes zero legacy calls.
- [x] 2.8 Test eligible fallback forwards the owning client's exact non-null directory and explicit `workspace = null`, ignoring other tab/workspace state.
- [x] 2.9 Test eligible fallback preserves an intentionally null captured directory without substituting a global, session, settings, or last-used directory.
- [x] 2.10 Confirm the focused change does not alter question route order/classification, permission discovery, reply values, session/dialog behavior, or the legacy response contract.

## 3. Runtime verification

- [x] 3.1 Run the focused `WorkspaceClientPermissionFallbackTest` permission-reply regression suite.
- [x] 3.2 Run `./gradlew :app:compileDebugKotlin`.
- [x] 3.3 Run `./gradlew :app:compileDebugAndroidTestKotlin`.
- [x] 3.4 Run `./gradlew :app:detekt`.
- [x] 3.5 Run `./gradlew :app:testDebugUnitTest`.
- [x] 3.6 Obtain an independent Sol review of the final permission-reply classifier, body ownership, mutation count, cancellation, and workspace routing.
- [x] 3.7 Verify GitHub Actions succeeds for the branch before merge.
