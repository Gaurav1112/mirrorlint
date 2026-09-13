package io.github.gaurav1112.mirrorlint.core;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DifferTest {
    private Member m(String n) { return new Member(n, "f.ts", 1); }
    private Shape shape(String id, ShapeKind k, String... names) {
        return new Shape(id, k, "f.ts", 1, java.util.Arrays.stream(names).map(this::m).toList());
    }

    @Test
    void findsOmissionsAndSurplus() {
        Shape truth = shape("T", ShapeKind.TRUTH, "alpha", "beta", "gamma", "delta");
        Shape mirror = shape("M", ShapeKind.LIST, "alpha", "beta", "epsilon");
        var result = new Differ().diff(new Pair(truth, mirror, 0.6));
        assertThat(result.omissions()).extracting(Member::name).containsExactly("delta", "gamma");
        assertThat(result.surplus()).extracting(Member::name).containsExactly("epsilon");
    }

    @Test
    void comparisonIsCaseInsensitiveButPreservesOriginal() {
        Shape truth = shape("T", ShapeKind.TRUTH, "Alpha");
        Shape mirror = shape("M", ShapeKind.LIST, "alpha");
        var result = new Differ().diff(new Pair(truth, mirror, 1.0));
        assertThat(result.omissions()).isEmpty();
        assertThat(result.surplus()).isEmpty();
    }

    @Test
    void orderingIsStableAlphabetical() {
        Shape truth = shape("T", ShapeKind.TRUTH, "zeta", "alpha", "mid");
        Shape mirror = shape("M", ShapeKind.LIST);
        var r = new Differ().diff(new Pair(truth, mirror, 0.0));
        assertThat(r.omissions()).extracting(Member::name).containsExactly("alpha", "mid", "zeta");
    }
}
