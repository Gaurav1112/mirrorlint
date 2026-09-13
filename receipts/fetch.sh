#!/bin/bash
# Idempotent shallow fetch of every repo/SHA named in manifest.json.
#
# Each receipt lands in receipts/cache/<owner>__<repo>-<sha8>/, checked out at the pinned SHA.
# Re-running is a no-op once the checkout matches the manifest's sparse_paths, so the receipts
# test can call this unconditionally.
#
# A blobless partial clone plus a sparse checkout of sparse_paths keeps this cheap: the receipt
# repos are large monorepos and each receipt only ever scans a couple of packages.
set -euo pipefail
cd "$(dirname "$0")"

while IFS=$'\t' read -r repo sha paths; do
  dir="cache/${repo//\//__}-${sha:0:8}"
  stamp="$dir/.mirrorlint-fetched"
  if [ -f "$stamp" ] && [ "$(cat "$stamp")" = "$sha $paths" ]; then
    echo "receipts: cached $repo@${sha:0:8}"
    continue
  fi
  echo "receipts: fetching $repo@${sha:0:8} [$paths]"
  rm -rf "$dir"
  mkdir -p "$dir"
  (
    cd "$dir"
    git init -q
    git remote add origin "https://github.com/$repo"
    git config extensions.partialClone origin
    # shellcheck disable=SC2086 -- paths is a deliberately word-split list
    git sparse-checkout set $paths
    git fetch -q --depth 1 --filter=blob:none origin "$sha"
    git checkout -q FETCH_HEAD
  )
  printf '%s %s' "$sha" "$paths" > "$stamp"
done < <(python3 -c "
import json
m = json.load(open('manifest.json'))
seen = {}
for r in m['receipts'] + m['negative_controls']:
    key = (r['repo'], r['sha'])
    seen.setdefault(key, set()).update(r.get('sparse_paths') or [r['subpath']])
for (repo, sha), paths in seen.items():
    print('\t'.join([repo, sha, ' '.join(sorted(paths))]))
")
