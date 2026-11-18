# Comparisons

These pages compare this project with other generators for the same language, so that a question such as "does this lack a feature that tool X has" can be answered from a checked source. Each page is linked from the language page of the SDK it compares.

| File | Compared with | Language |
|---|---|---|
| [ogen.md](ogen.md) | [ogen](https://ogen.dev) | Go |
| [ogen-source-review.md](ogen-source-review.md) | ogen, at source level | Go |
| [orval.md](orval.md) | Orval | Node (TypeScript) |

Not written yet: progenitor for Rust (issue #134) and swift-openapi-generator for Swift (issue #153).

Each comparison ends in a verdict per feature. The Orval comparison uses these words:

* `adopt`: the feature is taken for all languages.
* `adopt-in-subset`: the feature is taken for part of the languages or part of its behaviour.
* `recipe-only`: the feature is documented as a recipe, and no API is added.
* `defer`: the feature needs an owner decision before anything is done.
* `decline`: the feature is not taken, and the page gives the reason.
* `already-covered`: this project already has the feature.

The ogen comparison uses `BETTER`, `EQUAL`, `DIFFERENT` and `N/A` for the same purpose, and the ogen source review uses `yes`, `partly` and `no` for whether the feature exists here.
