# SDK Harmonisation Audit — open findings (triage list)

Produced by the `PROMPT.md` audit. WONTFIX (per `AGENT.md`) excluded. Nothing
fixed yet — this is the worklist. For each: why it never surfaced + why tests
missed it.

## TODO

### D1 — Bearer & API-Key authenticators have no standalone test in 11/12 SDKs (DIVERGENCE, medium)
Only **java** has `BearerAuthenticatorTest` + `ApiKeyAuthenticatorTest`. The other 11
(kotlin csharp python node go rust ruby swift dart elixir php) bury bearer/api-key
coverage inside `client_test`. Source `auth/bearer_authenticator` + `auth/api_key_authenticator`
exist in all 12 ⇒ all 12 owe a co-located test. Canonical = java.
- why_not_surfaced: behaviour *is* asserted (embedded), CI green; only file structure diverges.
- why_not_caught: test-count parity counts assertions, not file↔unit mapping. Fix: add the two
  standalone tests (idiomatically named) to the 11.

### D2 — README `## Caveats` section absent in 5/12 SDKs (DIVERGENCE, low–medium)
Present: go node php ruby swift dart elixir. Absent: **java kotlin csharp python rust**.
Documented caveats (decimal/`format: number` IEEE-754 precision, etc.) apply to those 5 too.
Ruby has 4 caveat subsections vs 1 elsewhere — same gap from the other side.
- why_not_surfaced: caveats describe precision/format limits the petstore fixture never exercises.
- why_not_caught: invariant #2 (doc parity) has no enforcing test parsing the 12 READMEs.

### U1 — No dedicated `ServerConfiguration` / `ServerVariable` test in any SDK (UNIFORM-GAP, low)
Both are caller-visible source units (URL templating + variable-enum validation) tested only
indirectly via `ConfigurationTest`. No one-unit-one-file test anywhere.

### U2 — No dedicated `ApiResult` test in any SDK (UNIFORM-GAP, low)
The `api_result` wrapper has no co-located test in any of the 12. Same root cause as U1.

## Round 2 — transport/serde deep pass

Real transport/serde divergences re-surfaced this round. Gaps AJ/AK/AL/AM are
ALREADY documented in `AGENT.md` "cycle 18 — pending divergences" (deferred,
not WONTFIX-tagged; reopening needs owner sign-off). Listed here because the
owner is actively hunting transport/serde bugs.

### Gap AJ — JSON null on a required non-nullable field (DIVERGENCE, high)
`{"name": null}` for required `name`: **go** zero-inits (`""`), **python** (pydantic)
accepts, **kotlin** (`explicitNulls=false`) accepts → silent contract violation. Other 9 throw.
- why_not_surfaced: fixture never sends null for a required field; only malformed/hostile servers do.
- why_not_caught: no `rejects-null-on-required` test in any suite. Canonical = throw.

### Gap AL — Content-Encoding lie (server claims gzip, sends plaintext) (DIVERGENCE, high)
**dart** crashes unconditionally; **csharp kotlin node swift elixir** silently pass corrupted bytes;
**java python ruby go php rust** surface a decompression error. (Round-1 fixed py/php/ruby wrapping;
the other-6 split remains.)
- why_not_surfaced: needs a server that mislabels encoding — never in the fixture.
- why_not_caught: no malformed-Content-Encoding test. Canonical = wrap as ApiError.

### Gap AK — Java drops proxy userinfo → proxy-auth fails Java-only (DIVERGENCE, medium-high)
`java.net.http.HttpClient` silently drops `user:pass@` from a proxy URL; proxy auth fails in **java** only.
- why_not_surfaced: CI proxy tests use unauthenticated proxies.
- why_not_caught: no authenticated-proxy test. Canonical = Java pre-flight extract userinfo → Proxy-Authorization.

### Gap AM — TLS verifySsl=false divergent semantics (DIVERGENCE, medium/security)
Some langs disable chain + hostname verification; others keep the hostname check. Same flag, different security posture.
- why_not_surfaced: tests toggle the flag but don't assert which checks are bypassed.
- why_not_caught: no test asserting hostname-mismatch behaviour under verifySsl=false. Canonical = define + match one semantics.

### Gap AU-residual — missing-discriminator wrapping (DIVERGENCE, low-medium)
On a missing discriminator field, **python php** wrap the raw dict in a union container instead of throwing (the other 10 throw).
- why/caught: no missing-discriminator-field test; fixture always supplies it. Canonical = throw.

### N1 — Node refuses body-replay on ALL redirect statuses, not just 307/308 (DIVERGENCE, low)
11 SDKs guard the HTTPS→HTTP body-replay only on 307/308 (301/302/303 force GET, body dropped anyway); **node** guards every status → throws where others proceed.
- why/caught: no 301/302 HTTPS→HTTP-with-body test. Canonical = guard 307/308 only.

## Dropped as false positives this round (verified against source)
- **tilde over-encode (C#/Node/PHP):** their encoders (`Uri.EscapeDataString`/`encodeURIComponent`/`rawurlencode`) keep `~` literal by spec; only java/kotlin/ruby need the `%7E` restore. Not a bug.
- **Java/Dart timeout only-connect:** Java sets request `.timeout()` (line 423) + redirect timeout too; agent stopped at the connect line. Not a bug.
- discriminator/oneOf routing — uniform across all 12.

## Rounds 3 & 4 — parallel independent deep passes (adversarial + feature-coverage)

Both ran 5 agents each across transport/serde/auth/errors/docs/tests. Result: **no
net-new confirmed defects.** Strong loop-until-dry signal — the behavioural surface
is harmonised; remaining work is the Round-1/2 list above.

### B1 — OAuth2 expiry clock source: Rust monotonic vs 11 wall-clock (borderline, low)
**rust** tracks token expiry off `Instant::now()` (monotonic); the other 11 use wall-clock
(`Instant.now()`/`DateTimeOffset.UtcNow`/`time.Now()`/…). Under a backward clock adjustment the
wall-clock 11 can mis-time a refresh. Borderline: marginal caller-visibility, debatable canonical
(storing an absolute expiry timestamp is itself wall-clock-shaped), large change to harmonise.
Recorded for owner judgement, not a confirmed defect.

## Dropped as false positives — rounds 3 & 4 (verified against source)
- **go simple-style path scalar not encoded:** Go DOES encode — `value = encodePathSegment(stringify(value))`
  at value_serializer.mustache:153 runs BEFORE the style switch; the agent read only line 241. Test passes.
- **Accept-Encoding header value/order divergence:** already W7 WONTFIX (cosmetic; each lib advertises what it can decode).
- README section ordering: idiomatic per AGENT.md accepted non-divergences.

## Canonical test scenarios (identical across all 12 — per PROMPT.md fix-time parity rule)

Each test below is added to ALL 12 SDKs (translated to idiom), red-before-fix in the
buggy ones, green in the rest. Co-located test file per Structural Invariant #1.

- **AJ** (`ObjectSerializer` test): deserialize `{"name": null, "photoUrls": ["u"]}` into `Pet`
  (name is required + non-nullable) → MUST throw the SDK's deserialization error. Fix in go/python/kotlin.
- **AL** (`DefaultApiClientUnit` test): stub a response with header `Content-Encoding: gzip` and a body of
  non-gzip plain bytes → client MUST surface `ApiError`/`ApiException` (no crash, no corrupt passthrough).
  Fix in dart/csharp/kotlin/node/swift/elixir.
- **AK** (`DefaultApiClient` test): configure proxy URL `http://user:pass@127.0.0.1:3128` → the client MUST
  carry the proxy credentials (Proxy-Authorization / userinfo honoured), not drop them. Fix in java.
- **AU-resid** (`ObjectSerializer` test): deserialize a `PetFood` payload missing the `foodType` discriminator
  property (e.g. `{"weightKg": 5.0}`) → MUST throw (not wrap the raw dict in a union container). Fix in python/php.
- **N1** (`DefaultApiClient` test): on a 302 HTTPS→HTTP redirect carrying a body → MUST proceed (body dropped,
  per RFC the request becomes GET); on a 307/308 HTTPS→HTTP redirect with a body → MUST throw. Fix in node.

## Audit status: COMPLETE (4 rounds)
Confirmed open: **D1, D2, U1, U2** (structural) + **AJ, AK, AL, AM, AU-residual, N1** (transport/serde,
already tracked in AGENT.md cycle-18 as deferred-pending). B1 borderline. Every agent "high-severity"
behavioural claim in rounds 2-4 (tilde, java-timeout, go-simple-path) was a verified misread — net-new
behavioural defects from the deep passes: zero.
