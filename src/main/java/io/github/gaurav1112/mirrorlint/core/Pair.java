package io.github.gaurav1112.mirrorlint.core;

/**
 * A truth/mirror candidate.
 *
 * @param subset {@code true} when the pair was mined by the containment (subset-mirror) rule
 *     rather than by jaccard. The distinction matters downstream: a subset mirror omits most of
 *     its truth <em>by design</em> — an allowlist is a curation, so its omissions are mostly
 *     correct and the drift is the outlier among them — while a twin's omissions are all
 *     suspicious by construction. See {@code PairMiner} and {@code Verifier}.
 */
public record Pair(Shape truth, Shape mirror, double jaccard, boolean subset) {

    /** A twin pair (jaccard-mined): {@code subset} is false. */
    public Pair(Shape truth, Shape mirror, double jaccard) {
        this(truth, mirror, jaccard, false);
    }
}
