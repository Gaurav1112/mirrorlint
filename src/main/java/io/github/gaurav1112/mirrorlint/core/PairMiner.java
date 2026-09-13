package io.github.gaurav1112.mirrorlint.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PairMiner {
    private final double minJaccard;
    private final int minShared;

    public PairMiner(double minJaccard, int minShared) {
        this.minJaccard = minJaccard;
        this.minShared = minShared;
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
        Set<String> inter = new HashSet<>(an); inter.retainAll(bn);
        Set<String> union = new HashSet<>(an); union.addAll(bn);
        if (union.isEmpty()) return;
        double jaccard = (double) inter.size() / union.size();
        if (inter.size() < minShared || jaccard < minJaccard) return;
        boolean aTruth = a.kind() == ShapeKind.TRUTH, bTruth = b.kind() == ShapeKind.TRUTH;
        if (aTruth && !bTruth) out.add(new Pair(a, b, jaccard));
        else if (bTruth && !aTruth) out.add(new Pair(b, a, jaccard));
        else if (an.size() > bn.size()) out.add(new Pair(a, b, jaccard));
        else if (bn.size() > an.size()) out.add(new Pair(b, a, jaccard));
        else { out.add(new Pair(a, b, jaccard)); out.add(new Pair(b, a, jaccard)); }
    }
}
