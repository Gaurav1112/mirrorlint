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
     * its own 12-name pool, so twins and subset mirrors both occur within a cluster) plus 10
     * shapes whose member names appear nowhere else in the corpus. The inverted index the indexed
     * {@code mine()} builds must only ever compare shapes that share a member, so cross-cluster
     * and disjoint shapes generate zero candidate pairs by construction — while {@code mineNaive}
     * still walks every pair. This exercises both shapes: the mined pairs (does the index find the
     * same real work) and the never-compared pairs (does the index correctly skip the fake work).
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
            }
        }
        for (int d = 0; d < 10; d++) {
            shapes.add(shape("disjoint_" + d, ShapeKind.LIST,
                    "solo_" + d + "_a", "solo_" + d + "_b", "solo_" + d + "_c", "solo_" + d + "_d"));
        }
        return shapes; // 19 * 10 + 10 = 200
    }

    @Test
    void indexedMiningMatchesNaiveExactlyIncludingOrder() {
        List<Shape> shapes = mixedClusteredAndDisjointShapes();
        PairMiner miner = new PairMiner(0.6, 4);

        List<Pair> indexed = miner.mine(shapes);
        List<Pair> naive = miner.mineNaive(shapes);

        assertThat(indexed).containsExactlyElementsOf(naive);
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
