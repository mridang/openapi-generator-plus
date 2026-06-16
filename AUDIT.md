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

## Pending rounds
- Two more thorough audit rounds queued, biased toward **HTTP transport** and **serde**
  (model + value) per the PROMPT.md high-yield hint. Append new findings here.
