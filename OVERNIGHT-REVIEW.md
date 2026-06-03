# Overnight Harmonisation Review — 30 Rounds

Autonomous review-and-improve loop run unattended. Methodology: `PROMPT.md`
(fan out one agent per dimension; classify DIVERGENCE / UNIFORM-GAP / WONTFIX;
exclude the `AGENT.md` deny-list). Each round: review → triage → apply only
**verified** fixes (compile + spotbugs, and the relevant `*Spec` where a
behaviour changes) → commit → push → log here. CI must stay green; an
unverifiable or risky finding is logged, not pushed.

Dimensions rotate across rounds: (1) HTTP transport, (2) model serde,
(3) value serde, (4) base model, (5) OAuth2/token, (6) non-OAuth auth,
(7) error model, (8) config/servers/operations, plus cross-cutting
"improvement" passes (idiom, edge cases, test coverage, docs, error messages).

## Round log

| # | dimensions | findings (D=divergence, U=uniform-gap) | fixed | commit | CI |
|---|------------|----------------------------------------|-------|--------|----|
| 0 | setup | tracker created | — | — | — |
| 1 | transport, model-serde, value-serde, base-model | D: dart-decompress-throw, swift-redirect-301/302, node-missing-discriminator, kotlin-lenient-json (deferred). value-serde + base-model clean | dart+swift+node (3) | next | pending |
| 1.5 | recovery | reverted formatter/error-prone tool bumps that broke JavaBuildSpec+formatting on CI | java+kotlin tooling | dcffe595 | green(F/L) |
| 2 | oauth2, non-oauth-auth, error-model | D: csharp-refresh-fallback-catch-too-narrow (OAuth2TokenError not caught). non-oauth-auth + error-model clean | csharp (1) | next | pending |
