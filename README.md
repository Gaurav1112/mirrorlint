# mirrorlint

The bug pattern that hit vitest, playwright, and spring — as a linter.

Somewhere in almost every codebase, one type is the source of truth (a config
interface, an options union, an enum) and a second list hand-maintains a copy
of it — a CLI allowlist, a fixture list, a serialization map. They start in
sync. Nothing keeps them that way. mirrorlint finds the copies that have
drifted.

## Receipts

Each row below is mirrorlint run against a real, pinned commit of a real
project — not a synthetic fixture. `mvn test -Preceipts` reproduces it.

| repo | pinned SHA | bug | PR |
|---|---|---|---|
| [vitest-dev/vitest](https://github.com/vitest-dev/vitest) | [`58f02ae`](https://github.com/vitest-dev/vitest/commit/58f02ae3663435854902ca8c2de049e75c4610f9) | `maxWorkers` missing from `PROJECT_CLI_OVERRIDES` — `--maxWorkers` is silently dropped whenever `projects` is declared | fixed by [vitest-dev/vitest#11102](https://github.com/vitest-dev/vitest/pull/11102) (merged) |
| [microsoft/playwright](https://github.com/microsoft/playwright) | [`d1ead3e`](https://github.com/microsoft/playwright/commit/d1ead3ecca23182f2d06d761c28e3d4edafb6595) (main) | `screen` missing from the `TestOptions` fixture list — every device descriptor carries it, `test.use({...devices[x]})` silently drops it | reported as [microsoft/playwright#42679](https://github.com/microsoft/playwright/issues/42679) (fix pending) |

Both receipts are rediscoveries: the tool is pointed at the commit *before*
the fix landed (or, for playwright, at the tip where the fix hasn't landed
yet) and finds the same gap a human maintainer found by hand.

Beyond this repo's own receipts, the same author has merged unrelated fixes
elsewhere in the JS and Spring ecosystems: a sibling-method drift in
spring-kafka (landed on `main` via cherry-picked commit
[`e40a9797`](https://github.com/spring-projects/spring-kafka/commit/e40a97976ebf2bf1c0d7a2997b081ee9f4bafcb7),
superseding the closed [#4594](https://github.com/spring-projects/spring-kafka/pull/4594)),
and a constructor-dispatch fix in spring-amqp
([#3606](https://github.com/spring-projects/spring-amqp/pull/3606), merged).
Still open and under review: vitest
([#11109](https://github.com/vitest-dev/vitest/pull/11109)),
spring-integration
([GH-11415](https://github.com/spring-projects/spring-integration/pull/11415)),
and testcontainers-java
([#12073](https://github.com/testcontainers/testcontainers-java/pull/12073)).

Three negative controls are wired into the same receipts run and never fire
at default severity: `teardownTimeout`, `vmMemoryLimit`, and
`dangerouslyIgnoreUnhandledErrors` are all root-only vitest options that a
per-project CLI allowlist is *correct* to omit — mirrorlint has to tell that
apart from a real omission, not just flag every gap.

## Quick start

```bash
mvn -DskipTests package
java -jar target/mirrorlint-0.1.0-SNAPSHOT.jar scan <path> [--format human|sarif|json] [--all] [--config mirrorlint.toml]
```

- `--format` — `human` (default), `sarif`, or `json`.
- `--all` — also print INFO-severity findings (human format only).
- `--config` — path to a project `mirrorlint.toml` (see below).

Exit codes: `0` scan completed, no DEFAULT-severity findings · `1` scan
completed, at least one DEFAULT finding · `2` usage error, an unreadable or
nonexistent scan path, or a malformed `--config` file.

## How it works

mirrorlint first mines candidate truth↔mirror pairs across the scanned tree:
same-file-set pairs by Jaccard similarity, plus a subset-allowlist rule for
mirrors that only ever copy *some* of the truth's members (Jaccard alone
can't see a deliberate allowlist, so containment is scored separately). Each
candidate pair is then diffed to list the truth members the mirror omits.
Every omission is checked against usage: does that member show up, in real
code, in files that also use members the mirror *does* carry? If so, the
mirror's omission is silent drift, not a deliberate difference, and it's
reported; if the member is never used alongside the mirror's own territory,
it's left alone. `teardownTimeout` in vitest's project-override allowlist is
the canonical quiet case: it's consumed only at the root level, never
co-occurs with the allowed options, and is correctly left alone.

Subset mirrors need a second, sharper gate, because symmetric usage proves
nothing about a list that omits most of its truth *on purpose*. An allowlist
drawn from a config type omits options the codebase naturally uses wherever it
uses the allowed ones — at the vitest receipt SHA all 41 omissions of
`PROJECT_CLI_OVERRIDES` scored ≥ 0.94, so that signal is saturated and useless
there. What isn't saturated: the module that *declares* the curation is the
authority on what belongs in it. So for a subset-mined pair an omission is
drift only when the declaring file itself uses that member in the same
statement as a member the mirror does list, and the member's score clears the
raised `subset_min_score` floor. That is precisely the vitest bug's shape —
`resolveProjects.ts` declares `PROJECT_CLI_OVERRIDES` and then writes
`maxWorkers: config.fileParallelism === false ? 1 : clonedConfig.maxWorkers`,
treating `maxWorkers` as a sibling of an option it did list. Thirty-nine of
the other 40 omissions (`hookTimeout` among them) are never written that way
in that file at all, which is what an intentional omission looks like; the
fortieth, `name`, is separated by the score floor instead, since `name` is
used all over the tree in places with nothing to do with the mirror. The pair
now reports exactly one finding instead of 41.

## Config reference

An optional TOML file, passed via `--config`:

```toml
min_jaccard = 0.6          # twin-pair mining floor
min_shared = 4              # minimum shared members for a candidate pair
min_score = 0.5             # verifier confidence floor for a DEFAULT finding
min_containment = 0.8       # subset-mirror containment floor
min_subset_jaccard = 0.3    # subset-mirror's own (lower) jaccard floor
subset_min_score = 0.8      # raised score floor for a subset (allowlist) pair's omissions;
                            #   clamped up to min_score — the subset gate only ever tightens
subset_peer_window = 0      # lines of slack when looking for a mirror member used alongside
                            #   the omission inside the mirror's declaring file (0 = same line)
excludes = ["**/generated/**"]   # additive — appended to the built-in excludes, never replacing them

[[pairs]]
truth_file = "src/config.ts"
truth_id = "SerializedConfig"
mirror_file = "src/cli.ts"
mirror_id = "PROJECT_CLI_OVERRIDES"
```

Every field is optional and falls back to its default. Declared `[[pairs]]`
bypass the mining thresholds entirely — use them when you already know two
lists are truth and mirror and don't want to depend on the miner finding
them.

A single member can also be suppressed inline, next to the line that
intentionally omits it:

```ts
// 'internalOnlyOption', // mirrorlint:ignore internalOnlyOption
```

## Precision — where this stands today

mirrorlint's receipts prove it finds real, specific bugs at the file-pair
scale it was calibrated on. Repo-scale precision is still being hardened:
scanning a full repository currently produces noisy DEFAULT findings, mostly
from two sources — generated files (protocol definitions, other
machine-written sources checked into the tree) and a small number of giant
config types whose mirrors are large enough that the usage-co-occurrence
check saturates and stops discriminating. Neither is fixed yet.

The project's target is a published false-positive rate against a
methodology-defined audit sample; that number is **measurement in
progress**, not yet published. Interactive-scale performance on very large
repositories is also not yet tuned — the playwright receipt alone takes
roughly a minute and a half. This section will be updated as that work
lands; it is not being softened in the meantime.

## License

Apache-2.0. See [LICENSE](LICENSE).
