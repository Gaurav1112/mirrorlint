package io.github.gaurav1112.mirrorlint.core;

import io.github.gaurav1112.mirrorlint.config.Config;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Finds truth/mirror candidates among extracted shapes.
 *
 * <p>Two relationships qualify, because real mirrors come in two shapes:
 *
 * <ul>
 *   <li><b>Twins</b> — two near-identical member sets (jaccard {@code >= minJaccard}). A DTO copied
 *       into another language, an enum re-listed as strings.
 *   <li><b>Subset mirrors</b> — a deliberate sub-selection of a larger declaration: an allowlist, a
 *       projection, a serializer's key list. Here jaccard is structurally low (the truth is
 *       bigger on purpose) but <em>containment</em>, {@code shared / |smaller|}, is near 1. Judging
 *       these by jaccard means never seeing the class of bug where one entry was left out of the
 *       allowlist — which is precisely the drift the tool exists to catch. The superset is always
 *       the truth in this direction, since only its extra members can be omissions.
 * </ul>
 */
public class PairMiner {
    private final double minJaccard;
    private final int minShared;
    private final double minContainment;
    private final double minSubsetJaccard;

    public PairMiner(double minJaccard, int minShared) {
        this(minJaccard, minShared, Config.DEFAULT_MIN_CONTAINMENT, Config.DEFAULT_MIN_SUBSET_JACCARD);
    }

    public PairMiner(double minJaccard, int minShared, double minContainment, double minSubsetJaccard) {
        this.minJaccard = minJaccard;
        this.minShared = minShared;
        this.minContainment = minContainment;
        this.minSubsetJaccard = minSubsetJaccard;
    }

    /**
     * The containment floor a subset-mirror candidate must clear. Exposed so a caller building a
     * manually declared {@code [[pairs]]} entry (which bypasses mining) can still classify it by
     * the same containment test used here, instead of duplicating the threshold.
     */
    public double minContainment() {
        return minContainment;
    }

    public List<Pair> mine(List<Shape> shapes) {
        List<Pair> out = new ArrayList<>();
        for (int i = 0; i < shapes.size(); i++) {
            for (int j = i + 1; j < shapes.size(); j++) {
                consider(shapes.get(i), shapes.get(j), out);
            }
        }
        out.sort(Comparator
            .comparing((Pair p) -> p.truth().file()).thenComparingInt(p -> p.truth().line())
            .thenComparing(p -> p.mirror().file()).thenComparingInt(p -> p.mirror().line()));
        return out;
    }

    private void consider(Shape a, Shape b, List<Pair> out) {
        Set<String> an = a.memberNames(), bn = b.memberNames();
        SetOverlap.Result overlap = SetOverlap.of(an, bn);
        if (overlap.union() == 0 || overlap.intersection() < minShared) return;

        double jaccard = overlap.jaccard();
        double containment = overlap.containment();

        if (jaccard >= minJaccard) {
            boolean aTruth = a.kind() == ShapeKind.TRUTH, bTruth = b.kind() == ShapeKind.TRUTH;
            if (aTruth && !bTruth) out.add(new Pair(a, b, jaccard));
            else if (bTruth && !aTruth) out.add(new Pair(b, a, jaccard));
            else if (an.size() > bn.size()) out.add(new Pair(a, b, jaccard));
            else if (bn.size() > an.size()) out.add(new Pair(b, a, jaccard));
            else { out.add(new Pair(a, b, jaccard)); out.add(new Pair(b, a, jaccard)); }
            return;
        }

        // Subset mirror: the superset is the truth; equal sizes cannot be a subset relationship.
        // The jaccard floor still applies, just a lower one: without it every four-field tuple
        // that happens to sit inside a hundred-field kitchen-sink type pairs with it, and the
        // resulting "omissions" are the ninety-six fields the subset was never meant to carry.
        if (containment < minContainment || jaccard < minSubsetJaccard || an.size() == bn.size()) return;
        Shape larger = an.size() > bn.size() ? a : b;
        Shape smaller = larger == a ? b : a;
        // Only a hand-maintained enumeration drawn from a declared type earns the subset rule.
        // A type that narrows another type is a deliberate, compiler-checked projection: its
        // "missing" members are the whole point of it, and treating them as drift floods the
        // report. A literal list of names copied out of a type is what nothing checks.
        if (larger.kind() != ShapeKind.TRUTH || smaller.kind() != ShapeKind.LIST) return;
        out.add(new Pair(larger, smaller, jaccard, true));
    }
}
