#!/bin/bash
# The 10-repo false-positive audit (spec: "The audit (the gate itself)").
#
# Shallow-clones the current HEAD of each of the ten audit repos into
# receipts/audit/cache/ (gitignored), records the HEAD SHA it actually got, then runs the
# shaded jar's `scan` over one sensibly-chosen source subpath per repo with
# `--format json --all` so the output carries INFO findings too (the recall probe needs
# them; `Pair.subset` is already serialized, so subset-pair omissions are filterable).
#
# Outputs, all under receipts/audit/:
#   HEADS.tsv    repo <TAB> sha <TAB> subpath          (committed — this is what pins the audit)
#   COUNTS.tsv   repo <TAB> subpath <TAB> seconds <TAB> total <TAB> default <TAB> info
#                     <TAB> default_omissions <TAB> subset_info_omissions   (committed)
#   <owner>__<repo>.json   the raw finding list (gitignored — playwright's alone is 294 MB;
#                          re-run this script against the pinned SHAs in HEADS.tsv to rebuild)
#
# Re-running is cheap: an existing clone whose recorded SHA matches is reused. Pass
# --refetch to force fresh clones (i.e. to re-pin against newer upstream HEADs).
#
# Subpath choices are per-repo judgment, aimed at "the hand-written source of this project"
# and at keeping a single scan under the 10-minute budget:
#   vitest              packages/vitest/src        the package the receipt lives in
#   playwright          packages                   CLI takes one path; parent of playwright/src
#                                                  + playwright-core/src (bundles/ is vendored)
#   nest                packages                   monorepo of the framework's own packages
#   hono                src                        the whole library
#   fastify             lib                        hand-written core (root fastify.js aside)
#   spring-kafka        spring-kafka/src/main/java the module, minus tests
#   spring-integration  spring-integration-core/src/main/java   core module only; the repo has
#                                                  ~30 modules and scanning all of them blows
#                                                  the time budget
#   testcontainers-java core/src/main/java         core module only, same reason
#   micrometer          micrometer-core/src/main/java           core module only, same reason
#   prometheus          web/ui                     Go is not a supported language; the bundled
#                                                  React/TypeScript UI is the only scannable tree
set -euo pipefail
cd "$(dirname "$0")"

REFETCH=0
[ "${1:-}" = "--refetch" ] && REFETCH=1

JAR=$(ls ../../target/mirrorlint-*.jar 2>/dev/null | grep -v original | head -1 || true)
if [ -z "$JAR" ]; then
  echo "audit: no shaded jar; run: mvn -DskipTests package" >&2
  exit 1
fi
JAR=$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")

REPOS=(
  "vitest-dev/vitest|packages/vitest/src"
  "microsoft/playwright|packages"
  "nestjs/nest|packages"
  "honojs/hono|src"
  "fastify/fastify|lib"
  "spring-projects/spring-kafka|spring-kafka/src/main/java"
  "spring-projects/spring-integration|spring-integration-core/src/main/java"
  "testcontainers/testcontainers-java|core/src/main/java"
  "micrometer-metrics/micrometer|micrometer-core/src/main/java"
  "prometheus/prometheus|web/ui"
)

mkdir -p cache
: > HEADS.tsv
printf 'repo\tsubpath\tseconds\ttotal\tdefault\tinfo\tdefault_omissions\tsubset_info_omissions\n' > COUNTS.tsv

for entry in "${REPOS[@]}"; do
  repo="${entry%%|*}"
  subpath="${entry##*|}"
  slug="${repo//\//__}"
  dir="cache/$slug"

  if [ "$REFETCH" = "1" ] || [ ! -d "$dir/.git" ]; then
    echo "audit: cloning $repo"
    rm -rf "$dir"
    git clone -q --depth 1 "https://github.com/$repo" "$dir"
  else
    echo "audit: cached $repo"
  fi
  sha=$(git -C "$dir" rev-parse HEAD)
  printf '%s\t%s\t%s\n' "$repo" "$sha" "$subpath" >> HEADS.tsv

  if [ ! -d "$dir/$subpath" ]; then
    echo "audit: WARNING $repo has no $subpath — skipping scan" >&2
    continue
  fi

  echo "audit: scanning $repo/$subpath"
  start=$(date +%s)
  # exit 1 just means "found DEFAULT findings"; exit 2 is a real failure.
  set +e
  java -jar "$JAR" scan "$dir/$subpath" --format json --all > "$slug.json" 2> "$slug.stderr"
  rc=$?
  set -e
  if [ "$rc" -gt 1 ]; then
    echo "audit: scan FAILED for $repo (exit $rc)" >&2
    cat "$slug.stderr" >&2
    continue
  fi
  end=$(date +%s)

  python3 - "$repo" "$subpath" "$((end - start))" "$slug.json" >> COUNTS.tsv <<'PY'
import json, sys
repo, subpath, secs, path = sys.argv[1:5]
f = json.load(open(path))
d = [x for x in f if x["severity"] == "DEFAULT"]
i = [x for x in f if x["severity"] == "INFO"]
print("\t".join(str(v) for v in (
    repo, subpath, secs, len(f), len(d), len(i),
    sum(1 for x in d if x["omission"]),
    sum(1 for x in i if x["omission"] and x["pair"]["subset"]),
)))
PY
done

echo
echo "audit: done"
column -t -s $'\t' COUNTS.tsv
