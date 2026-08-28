# Permission Reply Compatibility Specification

## ADDED Requirements

### Requirement: Retrofit-owned success body and inspectable raw metadata

The v2 permission-reply result SHALL retain the existing `Response<Unit>` declaration and Retrofit ownership model. On a successful response, `UnitResponseBodyConverter` closes the original network body before the response reaches `WorkspaceClient`. Retrofit replaces that body in `response.raw()` with a non-readable `NoContentResponseBody` metadata placeholder that retains content type and length. Classification SHALL use the placeholder only for media-type metadata and SHALL NOT read or close it. An implementation SHALL NOT rely on the decoded `Unit` body for media-type detection, decode the placeholder into a new response contract, or expose it to presentation. A non-2xx response's buffered `errorBody()` remains a distinct closeable body owned by the client.

#### Scenario: Successful conversion closes the network body and retains metadata

- **GIVEN** the v2 permission-reply endpoint returns a 2xx response with a body media type
- **WHEN** Retrofit returns the response to `WorkspaceClient`
- **THEN** Retrofit's Unit converter SHALL already have closed the original successful network body
- **AND** the client SHALL inspect the media type through the non-readable `response.raw().body` metadata placeholder
- **AND** the client SHALL NOT read or close that placeholder

### Requirement: Exact v2 permission-reply classification

The application SHALL classify the session-scoped v2 permission-reply response before deciding whether the reply succeeded or may use the legacy route. A 2xx response SHALL succeed only when it is not HTML. HTTP 404 SHALL select legacy fallback. A 2xx HTML response SHALL select legacy fallback. Every other non-2xx response, including an HTML response, SHALL propagate the original `HttpException` and SHALL NOT invoke the legacy route.

Classification SHALL evaluate HTTP 404 before other failures, other non-2xx responses before successful HTML, and successful non-HTML only after those cases. Redirects and all non-404 4xx/5xx statuses are failures, not compatibility signals.

#### Scenario: Successful non-HTML v2 response

- **GIVEN** the v2 permission-reply route returns a 2xx response
- **AND** neither its `Content-Type` header nor its raw metadata placeholder identifies HTML
- **WHEN** the response is classified
- **THEN** the permission reply SHALL succeed
- **AND** the legacy permission route SHALL NOT be called

#### Scenario: Missing v2 route

- **GIVEN** the v2 permission-reply route returns HTTP 404
- **WHEN** the response is classified
- **THEN** the response SHALL select legacy fallback
- **AND** no other HTTP status SHALL be generalized as a missing-route signal

#### Scenario: Successful HTML compatibility page

- **GIVEN** the v2 permission-reply route returns a 2xx response
- **AND** either its response header or raw metadata placeholder identifies HTML
- **WHEN** the response is classified
- **THEN** the response SHALL select legacy fallback

#### Scenario: HTML server failure

- **GIVEN** the v2 permission-reply route returns HTTP 500
- **AND** its response header or raw metadata placeholder identifies HTML
- **WHEN** the response is classified
- **THEN** the original HTTP 500 `HttpException` SHALL be propagated
- **AND** the legacy permission route SHALL NOT be called

#### Scenario: Ordinary non-404 HTTP failure

- **GIVEN** the v2 permission-reply route returns a redirect, authorization failure, rate-limit response, another non-404 4xx, or a 5xx response
- **WHEN** the response is classified
- **THEN** its original `HttpException` SHALL be propagated
- **AND** the legacy permission route SHALL NOT be called

### Requirement: Shared robust HTML media-type matching

Permission response classification SHALL reuse the question compatibility path's HTML media-type convention. It SHALL compare the base media type to `text/html` case-insensitively after trimming whitespace and ignoring optional parameters. It SHALL inspect both the response `Content-Type` header and the raw metadata placeholder's media type; a match from either source SHALL identify HTML. Missing or non-HTML media types SHALL NOT independently trigger fallback.

#### Scenario: Header uses case and parameters

- **GIVEN** a 2xx v2 response has `Content-Type: Text/HTML; Charset=UTF-8`
- **WHEN** its permission response is classified
- **THEN** it SHALL be identified as HTML
- **AND** it SHALL select legacy fallback

#### Scenario: Raw metadata identifies HTML

- **GIVEN** a 2xx v2 response has a missing or non-HTML `Content-Type` header
- **AND** its raw metadata placeholder's media type is `text/html` with or without parameters
- **WHEN** its permission response is classified
- **THEN** it SHALL be identified as HTML
- **AND** it SHALL select legacy fallback

#### Scenario: Non-HTML media type is not broadened

- **GIVEN** a 2xx v2 response has only missing, JSON, plain-text, or another non-HTML media type
- **WHEN** its permission response is classified
- **THEN** it SHALL succeed as non-HTML
- **AND** media-type uncertainty SHALL NOT trigger legacy fallback

### Requirement: Terminal response-body ownership

The application SHALL follow Retrofit's response-body ownership model. Unit conversion SHALL have closed the original successful network body before `WorkspaceClient` receives a 2xx response. The raw response body is a non-readable metadata placeholder, not the original network body, and the application SHALL NOT read or close it. Before HTTP 404 selects legacy fallback or another non-2xx response propagates an HTTP failure, the application SHALL close every present buffered `errorBody()`. Error-body closure SHALL happen before the legacy call or exception propagation and SHALL NOT replace the original `HttpException`.

#### Scenario: 404 body closes before fallback

- **GIVEN** a v2 HTTP 404 response owns an error body
- **WHEN** it selects legacy fallback
- **THEN** the error body SHALL be closed before the legacy request begins

#### Scenario: Successful HTML body was closed during conversion

- **GIVEN** Retrofit returns a v2 2xx HTML `Response<Unit>`
- **WHEN** it selects legacy fallback
- **THEN** the original successful network body SHALL already have been closed by Unit conversion
- **AND** the raw metadata placeholder SHALL NOT be read or closed

#### Scenario: HTTP error body closes before propagation

- **GIVEN** a non-404 v2 HTTP failure has a buffered `errorBody()`
- **WHEN** the failure is propagated
- **THEN** the buffered error body SHALL be closed first
- **AND** the raw metadata placeholder SHALL NOT be closed
- **AND** closing the error body SHALL NOT replace or obscure the response's original `HttpException`

### Requirement: At most one exact legacy permission mutation

For one `respondToPermission` invocation, a fallback disposition SHALL invoke the existing workspace-scoped legacy permission mutation exactly once. The call SHALL forward the same request identifier and permission reply without transformation. A legacy success or Boolean result SHALL be returned according to the existing contract; a legacy failure or cancellation SHALL propagate without retry. A v2 success or non-fallback failure SHALL issue zero legacy mutations.

#### Scenario: One fallback produces one legacy mutation

- **GIVEN** the v2 response is HTTP 404 or successful HTML
- **WHEN** the application falls back
- **THEN** it SHALL invoke the legacy permission route exactly once with the original request identifier and reply
- **AND** it SHALL NOT retry or invoke another mutation route

#### Scenario: Legacy call fails

- **GIVEN** an eligible fallback has invoked the legacy permission route
- **WHEN** that legacy call fails or returns its existing false result
- **THEN** the failure or result SHALL be propagated according to the existing legacy contract
- **AND** the application SHALL NOT issue a second legacy mutation

### Requirement: Exact tab-owned workspace routing

The legacy fallback SHALL use only the immutable workspace identity captured by the owning tab's `WorkspaceClient`. It SHALL forward that client's `Workspace.directory` exactly, including an intentional `null`, and SHALL continue to pass the explicit `workspace = null` API argument. It SHALL NOT derive a directory from active-tab state, the session, settings, a last-used project, a global/default workspace, or any fallback chain.

#### Scenario: Captured directory is forwarded

- **GIVEN** the owning `WorkspaceClient` was created for directory `/work/alpha`
- **AND** another active or recently used tab points at `/work/beta`
- **WHEN** an eligible v2 response falls back to the legacy permission route
- **THEN** the legacy call SHALL use `directory=/work/alpha` and explicit `workspace=null`
- **AND** it SHALL NOT inspect or use `/work/beta`

#### Scenario: Intentional null directory is preserved

- **GIVEN** the owning `WorkspaceClient` has `Workspace.directory == null`
- **WHEN** an eligible v2 response falls back to the legacy permission route
- **THEN** the legacy call SHALL pass the intentional null directory and explicit `workspace=null`
- **AND** it SHALL NOT substitute any guessed directory

### Requirement: Cancellation and non-sensitive failure behavior

Coroutine cancellation from the v2 call, classification, or the legacy call SHALL propagate as cancellation. It SHALL NOT be converted to success, compatibility fallback, an automatic retry, or an ordinary user-visible error, and cancellation before or during a legacy call SHALL NOT cause another mutation.

Non-fallback HTTP failures SHALL retain their original status for the existing error-handling path. User-visible permission-reply failure text SHALL remain concise and human-readable and SHALL NOT include response bodies, stack traces, credential-bearing URLs, serialized request data, or arbitrary server exception text.

#### Scenario: V2 call is cancelled

- **GIVEN** the v2 permission-reply request is cancelled before it produces a response
- **WHEN** cancellation propagates
- **THEN** the legacy permission route SHALL NOT be called
- **AND** cancellation SHALL NOT be converted into a compatibility or presentation error

#### Scenario: Legacy fallback is cancelled

- **GIVEN** an eligible response has selected and started the one legacy fallback
- **WHEN** the legacy call is cancelled
- **THEN** cancellation SHALL propagate
- **AND** no second legacy mutation SHALL be attempted

#### Scenario: HTTP failure reaches presentation

- **GIVEN** a non-fallback v2 HTTP failure is propagated
- **WHEN** the existing permission-reply error path reports it to the user
- **THEN** the user SHALL receive the existing concise retry guidance
- **AND** raw response or exception details SHALL NOT be displayed

### Requirement: Permission-only compatibility scope

This compatibility classifier SHALL apply only to v2 permission replies. It SHALL NOT change question route ordering or classification, permission discovery, allowed permission reply values, session lifecycle, dialog semantics, or the legacy response contract. It SHALL NOT add transport-failure fallback, automatic retry, background work, or any global workspace mechanism.

#### Scenario: Unrelated compatibility paths remain unchanged

- **GIVEN** a question reply, question rejection, question listing, or permission-listing operation runs
- **WHEN** this permission-reply hardening is present
- **THEN** that operation SHALL retain its existing route order, classification, routing, and failure behavior
- **AND** it SHALL NOT use the permission-reply classifier as a broadened fallback policy
