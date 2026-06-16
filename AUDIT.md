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

## Pending
- Round 3 (final deep pass) queued, same transport/serde bias.
