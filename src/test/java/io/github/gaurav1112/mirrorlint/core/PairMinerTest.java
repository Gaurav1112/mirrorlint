package io.github.gaurav1112.mirrorlint.core;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PairMinerTest {
    private Shape shape(String id, ShapeKind k, String... names) {
        return new Shape(id, k, id + ".ts", 1,
            java.util.Arrays.stream(names).map(n -> new Member(n, id + ".ts", 1)).toList());
    }

    @Test
    void pairsTypedTruthWithOverlappingList() {
        Shape truth = shape("T", ShapeKind.TRUTH, "a", "b", "c", "d", "e");
        Shape list = shape("M", ShapeKind.LIST, "a", "b", "c", "d");
        Shape unrelated = shape("U", ShapeKind.LIST, "x", "y", "z", "w");
        List<Pair> pairs = new PairMiner(0.6, 4).mine(List.of(truth, list, unrelated));
        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).truth().id()).isEqualTo("T");
        assertThat(pairs.get(0).mirror().id()).isEqualTo("M");
        assertThat(pairs.get(0).jaccard()).isBetween(0.79, 0.81); // 4/5
        assertThat(pairs.get(0).subset()).isFalse(); // jaccard-mined: a twin, not a curation
    }

    @Test
    void belowThresholdsIsNotPaired() {
        Shape a = shape("A", ShapeKind.LIST, "a", "b", "c");
        Shape b = shape("B", ShapeKind.LIST, "a", "b", "c"); // only 3 shared < minShared 4
        assertThat(new PairMiner(0.6, 4).mine(List.of(a, b))).isEmpty();
    }

    /**
     * The vitest receipt in miniature: an allowlist naming a handful of a big config type's
     * options. Jaccard is 6/18 = 0.33 — far under 0.6 — because the truth is deliberately bigger.
     */
    @Test
    void allowlistDrawnFromABigTypeIsASubsetMirror() {
        Shape truth = shape("SerializedConfig", ShapeKind.TRUTH,
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r");
        Shape allowlist = shape("OVERRIDES", ShapeKind.LIST, "a", "b", "c", "d", "e", "f");

        List<Pair> pairs = new PairMiner(0.6, 4).mine(List.of(truth, allowlist));

        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).truth().id()).isEqualTo("SerializedConfig");
        assertThat(pairs.get(0).mirror().id()).isEqualTo("OVERRIDES");
        assertThat(pairs.get(0).subset()).isTrue(); // mined by containment, so tagged for the stricter gate
    }

    /**
     * An object literal's keys are a value, not a copy of a member list: a defaults table or a
     * serializer output carries a subset of its type on purpose. Pairing those produced every
     * negative-control violation the receipts corpus caught.
     */
    @Test
    void objectLiteralIsNeverTheSubsetHalf() {
        Shape truth = shape("InlineConfig", ShapeKind.TRUTH,
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r");
        Shape defaults = shape("configDefaults", ShapeKind.LITERAL, "a", "b", "c", "d", "e", "f");

        assertThat(new PairMiner(0.6, 4).mine(List.of(truth, defaults))).isEmpty();
    }

    @Test
    void subsetCoveringTooLittleOfTheTruthIsNotAMirror() {
        Shape truth = shape("Kitchen", ShapeKind.TRUTH,
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r",
            "s", "t", "u", "v", "w", "x", "y", "z");
        Shape tuple = shape("TUPLE", ShapeKind.LIST, "a", "b", "c", "d"); // 4/26 = 0.15 jaccard

        assertThat(new PairMiner(0.6, 4).mine(List.of(truth, tuple))).isEmpty();
    }

    @Test
    void looselyOverlappingListsAreNotSubsetMirrors() {
        Shape truth = shape("T", ShapeKind.TRUTH, "a", "b", "c", "d", "e", "f", "g");
        Shape other = shape("M", ShapeKind.LIST, "a", "b", "c", "d", "x", "y"); // containment 4/6
        assertThat(new PairMiner(0.6, 4).mine(List.of(truth, other))).isEmpty();
    }

    @Test
    void largerListBecomesTruthBetweenTwoLists() {
        Shape big = shape("BIG", ShapeKind.LIST, "a", "b", "c", "d", "e");
        Shape small = shape("SMALL", ShapeKind.LIST, "a", "b", "c", "d");
        List<Pair> pairs = new PairMiner(0.6, 4).mine(List.of(small, big));
        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).truth().id()).isEqualTo("BIG");
    }

    /**
     * 190 shapes in 19 member-disjoint clusters of 10 (each cluster draws overlapping subsets of
     * its own 12-name pool, so twins occur within a cluster — every shape in a cluster lands at
     * exactly 8 members, so same-size jaccard twins and equal-size bidirectional pairs both occur,
     * but nothing here alone produces a differing-size pair), plus a truncated LIST-kind variant
     * (first 4 members) emitted after every 4th shape overall, which — when its parent happens to
     * be TRUTH-kind (8 members) — is a strict subset of differing size: containment 4/4 = 1.0
     * clears {@code minContainment}, jaccard 4/8 = 0.5 clears {@code minSubsetJaccard} but sits
     * below the {@code minJaccard} twin floor, so it can only ever be mined via the subset-mirror
     * branch. This is on top of 10 shapes whose member names appear nowhere else in the corpus.
     * The inverted index the indexed {@code mine()} builds must only ever compare shapes that
     * share a member, so cross-cluster and disjoint shapes generate zero candidate pairs by
     * construction — while {@code mineNaive} still walks every pair. This exercises all three
     * shapes: the mined twin/subset pairs (does the index find the same real work), the
     * never-compared pairs (does the index correctly skip the fake work), and the subset-mirror
     * branch specifically (does an actual differing-size candidate reach it).
     */
    private List<Shape> mixedClusteredAndDisjointShapes() {
        List<Shape> shapes = new java.util.ArrayList<>();
        for (int c = 0; c < 19; c++) {
            for (int s = 0; s < 10; s++) {
                List<String> names = new java.util.ArrayList<>();
                for (int m = 0; m < 12; m++) {
                    if ((m + s) % 3 != 0) {
                        names.add("c" + c + "_m" + m);
                    }
                }
                ShapeKind kind = switch (s % 3) {
                    case 0 -> ShapeKind.TRUTH;
                    case 1 -> ShapeKind.LIST;
                    default -> ShapeKind.LITERAL;
                };
                shapes.add(shape("c" + c + "_s" + s, kind, names.toArray(new String[0])));

                // Every 4th shape overall gets a truncated companion: the first 4 of its own
                // members, re-tagged LIST. When the parent is TRUTH-kind this is a differing-size
                // strict subset that clears containment but not the twin jaccard floor — the only
                // way the subset-mirror branch in PairMiner#consider gets exercised. When the
                // parent isn't TRUTH-kind the subset gate rejects it (superset must be TRUTH), so
                // the truncation is harmless there too — it just adds index-equivalence coverage.
                if (shapes.size() % 4 == 0) {
                    List<String> truncated = names.subList(0, Math.min(4, names.size()));
                    shapes.add(shape("c" + c + "_s" + s + "_trunc", ShapeKind.LIST,
                            truncated.toArray(new String[0])));
                }
            }
        }
        for (int d = 0; d < 10; d++) {
            shapes.add(shape("disjoint_" + d, ShapeKind.LIST,
                    "solo_" + d + "_a", "solo_" + d + "_b", "solo_" + d + "_c", "solo_" + d + "_d"));
        }
        return shapes;
    }

    @Test
    void indexedMiningMatchesNaiveExactlyIncludingOrder() {
        List<Shape> shapes = mixedClusteredAndDisjointShapes();
        PairMiner miner = new PairMiner(0.6, 4);

        List<Pair> indexed = miner.mine(shapes);
        List<Pair> naive = miner.mineNaive(shapes);

        assertThat(indexed).containsExactlyElementsOf(naive);

        // Pin down that the corpus actually reaches both branches PairMiner#consider can take,
        // not just the jaccard-twin one — a corpus that silently collapsed to all-equal-size
        // shapes (as this one once did) would still pass the equivalence check above while never
        // touching the subset-mirror path.
        long subsetPairCount = indexed.stream().filter(Pair::subset).count();
        long equalSizeBidirectionalPairCount = indexed.stream()
            .filter(p -> !p.subset() && p.truth().memberNames().size() == p.mirror().memberNames().size())
            .count();
        assertThat(subsetPairCount)
            .as("corpus must exercise the subset-mirror (containment) branch, not just jaccard twins")
            .isGreaterThan(0);
        assertThat(equalSizeBidirectionalPairCount)
            .as("corpus must still exercise the equal-size bidirectional twin branch")
            .isGreaterThan(0);
    }

    /**
     * Smoke test, not a benchmark: 2000 shapes with mutually disjoint 3-member sets means the
     * inverted index builds zero candidate pairs, so this should be near-instant. The naive O(n^2)
     * walk would also comfortably clear this generous 5s bound locally (2000^2 / 2 is ~2M cheap
     * comparisons), so passing this doesn't by itself prove the indexed path is faster — it only
     * guards against the indexed path regressing into something pathologically slow, e.g. a hidden
     * O(n^2) cost while building the index itself.
     */
    @Test
    void twoThousandDisjointShapesMineWellUnderFiveSeconds() {
        List<Shape> shapes = new java.util.ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            shapes.add(shape("d" + i, ShapeKind.LIST, "d" + i + "_a", "d" + i + "_b", "d" + i + "_c"));
        }

        long startNanos = System.nanoTime();
        List<Pair> pairs = new PairMiner(0.6, 4).mine(shapes);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        assertThat(pairs).isEmpty();
        assertThat(elapsedMs).isLessThan(5000);
    }
}
