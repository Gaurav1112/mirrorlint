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
    }

    @Test
    void belowThresholdsIsNotPaired() {
        Shape a = shape("A", ShapeKind.LIST, "a", "b", "c");
        Shape b = shape("B", ShapeKind.LIST, "a", "b", "c"); // only 3 shared < minShared 4
        assertThat(new PairMiner(0.6, 4).mine(List.of(a, b))).isEmpty();
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
