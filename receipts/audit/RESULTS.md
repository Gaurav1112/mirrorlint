# The 10-repo false-positive audit — first run

**Date:** 2026-09-13 · **mirrorlint:** `0.1.0-SNAPSHOT` at `e0ec439` · **Reproduce:** `receipts/audit/run-audit.sh`

**Overall audited precision: 2.1%** (equal-weighted across the seven repos that produced
DEFAULT findings). Pooled over the sample: 3 true / 112 audited = 2.7%.

**The launch gate (≥90%) is not met and stays closed.** The number is published as measured.

## Method

`run-audit.sh` shallow-clones the current HEAD of each of the ten spec repos, records the SHA
it got, and runs `mirrorlint scan <subpath> --format json --all` over one source subpath per
repo. The per-repo sample is the **first 20 DEFAULT findings in output order** (all of them if
fewer) — deterministic, not cherry-picked. Every sampled finding was verdicted by opening the
actual upstream truth site, mirror site, and at least one evidence site in the clone.

The verdict rule, applied identically everywhere:

- **TRUE** — a genuine drift: the mirror is a hand-maintained enumeration that plausibly
  *should* track the truth (nothing mechanical keeps them in sync), **and** the omitted member's
  consumption shows the omission matters. The vitest `maxWorkers` / `PROJECT_CLI_OVERRIDES`
  standard.
- **FALSE** — curated by design; two independently compiler-checked shapes that merely overlap;
  destructuring patterns (a partial destructure is a deliberate projection); unrelated shapes
  coincidentally paired; generated or vendored content; test fixtures; **or anything that could
  not be affirmatively verified.** Unverifiable counts as false. That rule cost findings.

## Scan inventory

| repo | HEAD | subpath | scan | total | DEFAULT | INFO |
|---|---|---|---|---|---|---|
| vitest-dev/vitest | `2ce29d5` | `packages/vitest/src` | 4s | 551 | 256 | 295 |
| microsoft/playwright | `d1ead3e` | `packages` | 122s | 26357 | 14474 | 11883 |
| nestjs/nest | `a3a31b9` | `packages` | 3s | 348 | 148 | 200 |
| honojs/hono | `8755b17` | `src` | 2s | 77 | 41 | 36 |
| fastify/fastify | `f1b1e1f` | `lib` | <1s | 24 | 7 | 17 |
| spring-projects/spring-kafka | `7276903` | `spring-kafka/src/main/java` | 3s | 0 | 0 | 0 |
| spring-projects/spring-integration | `fa6948a` | `spring-integration-core/src/main/java` | 4s | 0 | 0 | 0 |
| testcontainers/testcontainers-java | `8e54951` | `core/src/main/java` | 1s | 0 | 0 | 0 |
| micrometer-metrics/micrometer | `8a69589` | `micrometer-core/src/main/java` | 2s | 12 | 5 | 7 |
| prometheus/prometheus | `46ef370` | `web/ui` | 8s | 1319 | 672 | 647 |

No repo came near the 10-minute budget, so no subpath was narrowed for time. Subpaths are
per-repo judgment calls documented in the header of `run-audit.sh`; the Java repos are scanned
one module deep (the core module) because the full multi-module trees are mostly tests and
samples, and `prometheus/prometheus` is scanned at `web/ui` because Go is not a supported
language and the bundled React/TypeScript UI is the only scannable tree in it.

## Precision

| repo | sample | TRUE | FALSE | precision |
|---|---|---|---|---|
| vitest-dev/vitest | 20 | 3 | 17 | **15%** |
| microsoft/playwright | 20 | 0 | 20 | **0%** |
| nestjs/nest | 20 | 0 | 20 | **0%** |
| honojs/hono | 20 | 0 | 20 | **0%** |
| fastify/fastify | 7 | 0 | 7 | **0%** |
| micrometer-metrics/micrometer | 5 | 0 | 5 | **0%** |
| prometheus/prometheus | 20 | 0 | 20 | **0%** |
| spring-kafka / spring-integration / testcontainers-java | 0 | — | — | no findings, no sample |
| **overall (equal-weighted over the 7 sampled repos)** | **112** | **3** | **109** | **2.1%** |

The three Java repos that emitted nothing are excluded from the average: precision is undefined
over an empty sample. Reporting them as 100% would be dishonest in the other direction, and
they are a recall problem, not a precision one (see Next, item 5).

---

## Per-repo verdicts

### vitest-dev/vitest — 3/20

| # | mirror | member | verdict | reason |
|---|---|---|---|---|
| 1 | birpc opts `node/pools/poolRunner.ts:104` | `deserialize` | FALSE | Two unrelated birpc option bags (websocket API vs worker pool); the evidence site `this.worker.deserialize(...)` is a different symbol entirely. |
| 2 | same | `serialize` | FALSE | Same pair; the worker channel deliberately takes birpc's default serializer. |
| 3 | `eventNames` `api/setup.ts:193` | `onSpecsCollected` | **TRUE** | `eventNames` is a hand-written list of `WebSocketEvents` keys; `api/setup.ts:245` calls `client.onSpecsCollected?.()` exactly as it calls the listed `onCollected`. |
| 4 | same | `onTestAnnotate` | **TRUE** | Called at `api/setup.ts:255` in the identical `client.X?.(…)?.catch?.(noop)` idiom as listed members; the list was not updated when the event was added. |
| 5 | same | `onTestArtifactRecord` | **TRUE** | Called at `api/setup.ts:265` the same way; same list, same omission shape. |
| 6 | destructure `runtime/runners/test.ts:192` | `currentTestName` | FALSE | A partial destructure of the matcher state — a deliberate projection of the five fields that function needs. |
| 7 | `overrideGlobals` `integrations/env/jsdom.ts:153` | `FormData` | FALSE | Curated: jsdom handles FormData separately via `NodeFormData_`; the truth is a *different environment's* key list. |
| 8 | same | `Request` | FALSE | The list literally comments the member out — `// URL and Request is overridden with a compat one`. Curated by design. |
| 9 | `integrations/env/edge-runtime.ts:5` | `prewarmModules` | FALSE | `prewarmModules?: boolean` is an optional `Environment` property; only `node` opts out (`false`), everyone else takes the default. |
| 10 | `integrations/env/happy-dom.ts:17` | `prewarmModules` | FALSE | Same optional-property default. |
| 11 | `integrations/env/jsdom.ts:71` | `prewarmModules` | FALSE | Same optional-property default. |
| 12 | `toMatchSnapshotImpl` param type | `adapter` | FALSE | Two different functions' parameter types; `adapter` is domain-snapshot-only, `properties` is plain-snapshot-only. Both compiler-checked. |
| 13 | `chai.ts:465` call site | `hint` | FALSE | Inline snapshots take no hint; the optional param is absent by construction. |
| 14 | `matchDomain(...)` `chai.ts:220` | `interval` | FALSE | Poll-only option on the `pollMatchDomain` variant. |
| 15 | same | `poll` | FALSE | `poll` is replaced by `received` in the non-poll call — that *is* the difference between them. |
| 16 | same | `timeout` | FALSE | Same poll-only option. |
| 17 | `match(...)` `chai.ts:273` | `adapter` | FALSE | `matchDomain` vs `match` — two different snapshot-client methods. |
| 18 | `chai.ts:185` param type | `properties` | FALSE | #12 reversed. |
| 19 | destructure `typecheck/typechecker.ts:156` | `filepath` | FALSE | `const { file, definitions, map, parsed } = …` — a destructuring projection. |
| 20 | `runtime/runner/suite.ts:498` | `dynamic` | FALSE | Two independently typed Suite constructors (`ParsedSuite` vs runtime `Suite`); `each` carries the same information, and no evidence site is in the mirror's file. |

The three TRUEs are one finding repeated over one list. vitest's own receipt bug is fixed
upstream at this HEAD — `maxWorkers` is now present in `PROJECT_CLI_OVERRIDES` — and mirrorlint
correctly reports nothing for it.

### microsoft/playwright — 0/20

The deterministic first-20 is degenerate: **all twenty findings are the same member (`color`)
of the same truth (`RectAnnotation = {x, y, width, height, text, color}` in
`dashboard/src/annotationImage.ts:17`)**, paired against twenty different `{x, y, width, height}`
geometry shapes across the monorepo. Verified individually; all FALSE, for one of four reasons:

| # | mirror | reason class |
|---|---|---|
| 1–5 | `Rect` / rect literals in `dashboard/src/annotations.tsx` | Local `type Rect = {x,y,width,height}` for drag handling — a geometry rect, not an annotation. |
| 6–7 | `AnnotationData` `dashboardChannel.ts:33`, `annotations` `dashboardModel.ts:329` | Wire/model shapes that carry `text` but assign `color` elsewhere; unrelated pairing. |
| 8–13 | `injected/src/*` rects, `AriaNodeJSON` | Independent geometry types in the injected script and aria snapshot. |
| 14–17 | `playwright-client/types/types.d.ts` (`ElementHandle`, `Locator`, `AndroidElementInfo`, `PageScreenshotOptions`) | **Generated file** — header reads `// This file is generated by /utils/generate_types/index.js`. |
| 18–20 | `bidiPage.ts` box literal, `crPage.ts` literal, `protocol.d.ts` `Rect` | `protocol.d.ts` is also **generated** (`// This is generated from /utils/protocol-types-generator/index.js`); the others are CDP/BiDi geometry. |

Every evidence site for `color` is an unrelated `color` (`utils/debugLogger.ts:59`,
`trace-viewer/.../consoleTab.tsx:247`, `agents/generateAgents.ts:84`).

### nestjs/nest — 0/20

| # | mirror | member | verdict | reason |
|---|---|---|---|---|
| 1 | `FactoryProvider` `provider.interface.ts:116` | `useClass` | FALSE | Sibling variants of the `Provider<T>` discriminated union; `ClassProvider` even declares `inject?: never` with a comment that the option is factory-only. |
| 2 | destructure `module.ts:411` | `useClass` | FALSE | `const { useFactory, inject, scope, durable, provide } = provider` inside `addCustomFactory` — a destructure over a different union variant. |
| 3 | `ClassProvider` `provider.interface.ts:36` | `useFactory` | FALSE | #1 reversed. |
| 4 | `MulterModuleAsyncOptions` | `provideInjectionTokensFrom` | FALSE | Closest near-miss in the whole audit — the module *is* a hand-fork of the builder's async-options shape — but all consumption sits in the truth's own file and none in the mirror's territory, so "should track" is unverifiable → FALSE. |
| 5 | `DEFAULT_LOG_LEVELS` | `setLogLevels` | FALSE | Truth is the `LoggerService` interface; mirror is a list of log *levels*. `setLogLevels` is a method, not a level. |
| 6 | `LOG_LEVELS` | `setLogLevels` | FALSE | Same coincidence, and `LogLevel` is derived mechanically via `(typeof LOG_LEVELS)[number]`. |
| 7 | `LOG_LEVEL_VALUES` | `setLogLevels` | FALSE | Declared `Record<LogLevel, number>` — compiler-enforced exhaustively; evidence site is an unrelated method call. |
| 8–9 | `new InstanceWrapper` `module.ts:207` | `metatype` | FALSE | Constructor takes `Partial<InstanceWrapper<T>>`; this call site passes an already-built instance with `isResolved: true`. |
| 10–14 | `new InstanceWrapper` (`module.ts:179/193/391/444`, `testing-injector.ts:92`) | `durable` | FALSE | `durable?` is meaningful only with `Scope.REQUEST`; value providers and alias wrappers have no such member at all. |
| 15 | `wrapper` `module-ref.ts:168` | `instance` | FALSE | Set immediately after via `setInstanceByContextId` at lines 175–181. |
| 16–19 | `new InstanceWrapper` (`module.ts:179/193/391/444`) | `scope` | FALSE | `scope?` is an optional property left at its `Scope.DEFAULT` default. |
| 20 | `wrapper` `module-ref.ts:168` | `subtype` | FALSE | `subtype?: EnhancerSubtype` applies only to enhancer registrations. |

### honojs/hono — 0/20

| # | mirror | member | verdict | reason |
|---|---|---|---|---|
| 1 | `requestInit` `method-override/index.ts:127` | `signal` | FALSE | Two independent `RequestInit` literals in unrelated middleware; `signal` is an optional DOM-standard property. |
| 2–9 | eight JSX component nodes in `components.ts` | `key` | FALSE | `key` is stripped from props and assigned by the `jsx`/`createElement` factory — a component never sets its own key. |
| 10 | `jsxDEV` `jsx-dev-runtime.ts:14` | `f` | FALSE | Internal dirty-flag carried over from a previously built node; a fresh factory node cannot have one. |
| 11 | same | `o` | FALSE | The reconciler's original-node backref, set only in `render.ts`. |
| 12 | `BearerAuthOptions` arm `:45` | `token` | FALSE | Two arms of a discriminated union; `token` XOR `verifyToken` is the point, narrowed at `:196`. |
| 13 | `BearerAuthOptions` arm `:23` | `verifyToken` | FALSE | Same union, other arm. |
| 14–15 | `opts` defaults table `cors/index.ts:64` | `credentials`, `maxAge` | FALSE | A defaults table; both values arrive through `...options` and are read with `!= null` guards. Nothing is dropped. |
| 16–20 | `unauthorizedResponse` call sites in `jwk.ts` / `jwt.ts` | `statusText` | FALSE | `statusText?: string` is an optional parameter; two call sites of one function are not truth and mirror. |

### fastify/fastify — 0/7

| # | mirror | member | verdict | reason |
|---|---|---|---|---|
| 1–6 | `routerOptions` `config-validator.js:1158` | `constraints`, `forceCloseConnections`, `http2`, `https`, `ignoreDuplicateSlashes`, `ignoreTrailingSlash` | FALSE | Both sides live in `lib/config-validator.js`, whose first line reads `// This file is autogenerated by build/build-validation.js, do not edit`. CI enforces it byte-for-byte via `test:validator:integrity`. |
| 7 | `route.js:454` | `handler` | FALSE | The auto-HEAD registration passes the handler inside `options` (`headOpts`); `prepareRoute` falls back to `options.handler` at `:146`. |

### micrometer-metrics/micrometer — 0/5

| # | mirror | member | verdict | reason |
|---|---|---|---|---|
| 1 | `ApacheHttpClientKeyNames` (hc 4.x) | `EXCEPTION` | FALSE | `@Deprecated` in favour of hc5 and internally consistent — its convention class records no exception at all. |
| 2 | `OkHttpLegacyLowCardinalityTags` | `EXCEPTION` | FALSE | A different binder's frozen "Legacy" tag set; the overlap is generic HTTP tag names. |
| 3–5 | `JerseyLegacyLowCardinalityTags` | `TARGET_HOST`, `TARGET_PORT`, `TARGET_SCHEME` | FALSE | Jersey is a *server*-side binder; `target.*` are client-side concepts and no evidence site is in a Jersey file. |

### prometheus/prometheus — 0/20

| # | mirror | member | verdict | reason |
|---|---|---|---|---|
| 1–10 | `&:before` tooltip-arrow rules in `codemirror/theme.ts` (×3) and legacy `CMTheme.tsx` (×2) | `left`, `right`, `top` | FALSE | Sibling CSS-in-JS style objects: `.cm-completionInfo-right` / `-left` / `-right-narrow` differ *by* their anchoring property. Cross-multiplied between the two UI trees, one design pattern becomes ten findings. Evidence sites are `left`/`top` in **vendored** `react-app/src/vendor/flot/jquery.flot*.js`. |
| 11 | `RuleState` `rules.ts:1` | `total` | FALSE | A string-literal union of rule *states* paired with a *counts* object; `total` is not a state. |
| 12–14 | `globalCounts` / `groupCounts` in `AlertsPage` | `total` | FALSE | Sibling shapes inside one compiler-checked type declaration; the evidence (`groupCounts.total++`) is the truth's own use. |
| 15 | `UPlotChart` param destructure `:32` | `showExemplars` | FALSE | Function-parameter destructure; every evidence site is in the *legacy* react-app tree. |
| 16 | `axes` `uPlotChartHelpers.ts:385` | `size` | FALSE | X-axis vs Y-axis entries of uPlot's own `axes[]`, both typed `uPlot.Axis`; `size: autoPadLeft` is Y-only by design. |
| 17 | `matching` `VectorVector.tsx:291` | `card` | FALSE | Mis-extracted shape: that literal is a spread fallback and `card` is supplied six lines below in the enclosing object. |
| 18–20 | `effectiveExpr` / `selector` / `VectorSelector` | `range` | FALSE | The code deliberately converts a `MatrixSelector` to a `VectorSelector`; "has a range" is precisely what separates the two AST node types. |

---

## Recall probe

Spec amendment: the DEFAULT-sample audit measures precision only and is structurally blind to
over-demotion, so each repo with subset pairs also had up to 10 INFO-demoted subset-pair
omissions audited for **missed true drift**. Sample spread across distinct mirrors (max 2 per
mirror).

| repo | subset omissions demoted to INFO | sampled | missed true drift |
|---|---|---|---|
| vitest-dev/vitest | 68 | 10 | **0** |
| microsoft/playwright | 1020 | 10 | **0** |
| nestjs/nest | 15 | 6 | **0** |
| honojs/hono | 6 | 2 | **0** |
| prometheus/prometheus | 43 | 10 | **0** |
| fastify, micrometer, the three Java repos | 0 | — | no subset pairs |

**Verdict: the subset demotion is not degenerate.** Zero missed true drift across 38 audited
demotions, and each demotion was correct for a checkable reason, not by accident:

- **vitest.** `PROJECT_CLI_OVERRIDES` — the receipt pair — demotes `_diffOptions` and `api`,
  both root-only options, which is exactly the negative-control behaviour. `THRESHOLD_KEYS` is
  declared `Readonly<Threshold[]>` and demotes `autoUpdate`/`perFile`, which are not thresholds.
  The `ProjectsResolutionContext` destructure demotes `ancestors`/`chain`, both of which the same
  function reads directly off `context.` six lines later.
- **playwright — the motivating case.** The 52 subset pairs demoting 212→0 was the datum a
  zero-survivor mechanism had to justify. It holds up: `kAriaDisabledRoles` correctly excludes
  non-widget roles, `_combinedContextOptions` correctly excludes launch-only options (`args`,
  `artifactsDir`), and BiDi's PDF option list correctly excludes header/footer options BiDi
  cannot express. **And the playwright receipt itself survives at repo scale**: the `screen`
  omission still fires at DEFAULT through twin pairs
  (`BrowserContextOptions` → `PlaywrightTestOptions`, jaccard 0.74; → `_combinedContextOptions`,
  jaccard 0.81). The demotion is not eating the known true positive.
- **nest.** `supportedHooksWithPayload` / `supportedHooksWithoutPayload` are a *partition* whose
  spread union is `supportedHooks`, so each list's "omissions" are the other's members.
- **hono.** The cookie-attribute allowlist demotes `expires` (a `Date`) and `httpOnly` (a
  `boolean`) — neither can carry the `[;\r\n]` header-injection characters the allowlist guards.
- **prometheus.** Correct demotions, but the probe cannot credit the mechanism much here: it is
  mostly suppressing pairs that should never have been mined (cross-app mantine-ui ↔ react-app
  pairings).

One genuine gap surfaced, in normalization rather than demotion: playwright's
`kSupportedAttributes` **does** contain the omitted member, spelled `'include-hidden'`, while
the truth spells it `includeHidden`. `Shape.normalize` only lowercases, so kebab-case and
camelCase never match. The finding was demoted (right outcome), but the omission was not real
in the first place. See Next, item 4.

---

## Next

2.1% is far below the 90% gate. The audit is not close, and the gap is not a threshold-tuning
problem — it is a **what counts as a mirror** problem. Every one of the 109 false positives was
classified; the distribution is the ranking:

| cause | count | share of 109 FP |
|---|---|---|
| compiler-checked shape (typed literal, param type, union arm, optional property, `Partial<T>`, `Record<K,V>`) | 42 | 39% |
| unrelated shapes coincidentally paired | 35 | 32% |
| framework/runtime-managed field, or a mis-extracted shape | 13 | 12% |
| generated or vendored file | 11 | 10% |
| destructuring pattern | 4 | 4% |
| curated by design (explicit comment / opt-out) | 4 | 4% |

Ranked by how much of the audited sample each fix would erase, these are v1.2's input:

1. **Compiler-checked shapes are not mirrors — 46 of the 112 audited findings (41%),
   counting the four destructures.** The top source in four of the seven sampled repos
   (nest 17/20, hono 8/20, prometheus 8/20, vitest 13/17 FALSE). Object literals checked against
   a declared type, function-parameter types, sibling arms of a discriminated union, optional
   properties left at their default, `Partial<T>` constructor arguments, `Record<K, V>` tables —
   in all of these the compiler *already* enforces the relationship, so there is no
   hand-maintained copy to drift. The premise in the README ("a second list hand-maintains a copy
   of it") is right; the extractor does not yet encode it. Candidate rule: a mirror must be an
   **unchecked enumeration** — a literal array of strings, an object literal with no contextual
   type — and an omission of a member the truth declares **optional** is never drift by itself.
   Sub-case worth its own rule: **destructuring patterns are not shapes at all.**
2. **Per-pair and per-truth fan-out — 20 of 20 playwright findings, 10 of 20 prometheus.**
   One truth shape (`RectAnnotation`) times every `{x,y,width,height}` in the monorepo produced
   an entire 20-finding sample from a single mistake; prometheus's tooltip-arrow CSS rule did the
   same thing ten times over. Deduping at the (truth, member) level, and capping the mirrors a
   single truth may be paired with, would both shrink the output by an order of magnitude and
   make the deterministic first-20 sample representative instead of degenerate. Note this also
   means the playwright and prometheus numbers are **low-confidence**: they measure one
   pathological cluster each, not the repo.
3. **The generated-file detector misses the real-world banners — 11 findings across playwright
   and fastify.** `MARKERS` is case-sensitive and matches only `@generated`, `Generated by`,
   `DO NOT EDIT`, `Code generated`. It does not catch
   `// This file is generated by /utils/generate_types/index.js` (lowercase `generated by`),
   `// This is generated from /utils/protocol-types-generator/index.js` (`generated from`), or
   `// This file is autogenerated by build/build-validation.js, do not edit`
   (`autogenerated`, lowercase `do not edit`). Case-insensitive matching plus the
   `autogenerated` / `generated from` variants is a small, high-yield fix. Related: **vendored
   trees** (`web/ui/react-app/src/vendor/**`, `playwright-core/bundles/**`) should be excluded
   by default — they polluted evidence lists for common names like `left`, `top`, `size`.
4. **Member-name normalization is lowercase-only.** `include-hidden` ≠ `includeHidden`,
   and by the same token `snake_case` ≠ `camelCase`. Real allowlists spell members in the wire
   or attribute convention, not the type's. Stripping `-` and `_` before comparing would remove a
   class of phantom omissions — this one surfaced in the recall probe, so it is currently
   producing *both* false omissions and mis-scored pairs.
5. **Zero findings on all three Java repos** (`spring-kafka`, `spring-integration`,
   `testcontainers-java`) — including `spring-integration`, where the same author has an open
   drift PR. That is a recall failure, not a precision success, and it means the Java adapter's
   shape extraction is the least-exercised path in the tool. It needs its own investigation
   before the next audit; a precision fix that shrinks output further will not help it.
