package io.github.gaurav1112.mirrorlint.core;

import java.util.HashSet;
import java.util.Set;

/**
 * Shared set-overlap arithmetic for truth/mirror candidates: jaccard (symmetric similarity,
 * {@code shared / union}) and containment (asymmetric coverage, {@code shared / smaller}).
 *
 * <p>{@link PairMiner} needs both when mining twins and subset mirrors; {@code Scanner} needs the
 * same arithmetic when scoring a manually declared {@code [[pairs]]} entry, since a declared pair
 * bypasses the mining thresholds but must still be classified the same way a mined one would be.
 * This is the one place the computation lives, so the two callers can never drift apart.
 */
public final class SetOverlap {
    private SetOverlap() {}

    public record Result(int intersection, int union, double jaccard, double containment) {}

    public static Result of(Set<String> a, Set<String> b) {
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        double jaccard = union.isEmpty() ? 0.0 : (double) inter.size() / union.size();
        int smaller = Math.min(a.size(), b.size());
        double containment = smaller == 0 ? 0.0 : (double) inter.size() / smaller;
        return new Result(inter.size(), union.size(), jaccard, containment);
    }
}
