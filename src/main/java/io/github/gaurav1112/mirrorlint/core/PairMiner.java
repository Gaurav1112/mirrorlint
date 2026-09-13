package io.github.gaurav1112.mirrorlint.core;

import io.github.gaurav1112.mirrorlint.config.Config;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    /**
     * Indexed mining: a pair with zero shared members can never clear {@code minShared >= 1}
     * ({@link SetOverlap} requires it for both the jaccard and subset rules), so the only pairs
     * worth ever calling {@link #consider} on are ones that share at least one member. (This holds
     * even if a caller configures {@code minShared} down to 0, since {@code minJaccard} and
     * {@code minContainment} are never simultaneously 0 either, so a zero-overlap pair still can't
     * clear both checks in {@link #consider}.) An inverted index (normalized member name → shape
     * indices) turns "all shape pairs" into "shape pairs that co-occur in some member's bucket,"
     * which is the set {@link #mineNaive} would eventually reach anyway minus all the
     * guaranteed-zero-overlap comparisons — same results, less work.
     *
     * <p>Candidate index pairs are deduplicated (a shape pair can share several members, i.e.
     * appear in several buckets) and then walked in the same ascending (i, j) order the naive
     * double loop uses, so {@link #consider} runs in an identical sequence and the trailing sort
     * — whose comparator does not fully order every pair — resolves equal-key ties identically via
     * stable-sort input order in both implementations.
     */
    public List<Pair> mine(List<Shape> shapes) {
        int n = shapes.size();
        Map<String, List<Integer>> membersToShapes = new HashMap<>();
        for (int i = 0; i < n; i++) {
            for (String member : shapes.get(i).memberNames()) {
                membersToShapes.computeIfAbsent(member, k -> new ArrayList<>()).add(i);
            }
        }

        Set<Long> candidates = new HashSet<>();
        for (List<Integer> bucket : membersToShapes.values()) {
            for (int a = 0; a < bucket.size(); a++) {
                for (int b = a + 1; b < bucket.size(); b++) {
                    int i = bucket.get(a), j = bucket.get(b);
                    if (i > j) { int t = i; i = j; j = t; }
                    candidates.add(((long) i << 32) | (j & 0xFFFFFFFFL));
                }
            }
        }
        List<Long> ordered = new ArrayList<>(candidates);
        ordered.sort(null); // ascending: (i << 32 | j) orders by i then j, matching the naive loop

        List<Pair> out = new ArrayList<>();
        for (long key : ordered) {
            int i = (int) (key >> 32);
            int j = (int) key;
            consider(shapes.get(i), shapes.get(j), out);
        }
        sortPairs(out);
        return out;
    }

    /**
     * The pre-indexing implementation, kept for {@link PairMinerTest}'s equivalence check against
     * {@link #mine}. O(n^2) over all shape pairs regardless of overlap — this is exactly the cost
     * the inverted index in {@link #mine} exists to avoid.
     */
    List<Pair> mineNaive(List<Shape> shapes) {
        List<Pair> out = new ArrayList<>();
        for (int i = 0; i < shapes.size(); i++) {
            for (int j = i + 1; j < shapes.size(); j++) {
                consider(shapes.get(i), shapes.get(j), out);
            }
        }
        sortPairs(out);
        return out;
    }

    private static void sortPairs(List<Pair> out) {
        out.sort(Comparator
            .comparing((Pair p) -> p.truth().file()).thenComparingInt(p -> p.truth().line())
            .thenComparing(p -> p.mirror().file()).thenComparingInt(p -> p.mirror().line()));
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
