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
}
