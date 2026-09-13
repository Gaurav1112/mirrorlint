package io.github.gaurav1112.mirrorlint.core;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VerifierTest {
    private final Shape truth = new Shape("T", ShapeKind.TRUTH, "types.ts", 1, List.of(
        new Member("maxWorkers", "types.ts", 1), new Member("hookTimeout", "types.ts", 2),
        new Member("teardownTimeout", "types.ts", 3), new Member("tagsFilter", "types.ts", 4)));
    private final Shape mirror = new Shape("M", ShapeKind.LIST, "overrides.ts", 10, List.of(
        new Member("maxWorkers", "overrides.ts", 11), new Member("tagsFilter", "overrides.ts", 12),
        new Member("fileParallelism", "overrides.ts", 13)));
    private final Pair pair = new Pair(truth, mirror, 0.6);

    private UsageSite u(String file, String member) { return new UsageSite(file, 5, member); }

    @Test
    void omissionUsedAlongsideMirrorMembersIsDefaultSeverity() {
        var usages = List.of(u("runner.ts", "maxWorkers"), u("runner.ts", "tagsFilter"),
                             u("runner.ts", "hookTimeout"));
        var diff = new Differ().diff(pair);
        var findings = new Verifier(0.5).verify(pair, diff, usages);
        var hook = findings.stream().filter(f -> f.member().name().equals("hookTimeout")).findFirst().orElseThrow();
        assertThat(hook.severity()).isEqualTo(Severity.DEFAULT);
        assertThat(hook.evidence()).extracting(UsageSite::file).contains("runner.ts");
    }

    @Test
    void omissionUsedOnlyWhereNoMirrorMemberAppearsIsInfo() {
        var usages = List.of(u("runner.ts", "maxWorkers"), u("runner.ts", "tagsFilter"),
                             u("core.ts", "teardownTimeout"));
        var findings = new Verifier(0.5).verify(pair, new Differ().diff(pair), usages);
        var td = findings.stream().filter(f -> f.member().name().equals("teardownTimeout")).findFirst().orElseThrow();
        assertThat(td.severity()).isEqualTo(Severity.INFO); // the root-only discriminator, forever
    }

    @Test
    void neverUsedOmissionIsInfo() {
        var findings = new Verifier(0.5).verify(pair, new Differ().diff(pair), List.of(u("runner.ts", "maxWorkers"), u("runner.ts", "tagsFilter")));
        assertThat(findings).allSatisfy(f -> {
            if (f.member().name().equals("hookTimeout")) assertThat(f.severity()).isEqualTo(Severity.INFO);
        });
    }

    @Test
    void deterministicOrdering() {
        var usages = List.of(u("runner.ts", "maxWorkers"), u("runner.ts", "tagsFilter"), u("runner.ts", "hookTimeout"));
        var a = new Verifier(0.5).verify(pair, new Differ().diff(pair), usages);
        var b = new Verifier(0.5).verify(pair, new Differ().diff(pair), usages);
        assertThat(a).isEqualTo(b);
        assertThat(a.get(0).severity()).isEqualTo(Severity.DEFAULT);
    }

    // ---- the subset-pair discriminator ------------------------------------------------
    //
    // The vitest receipt in miniature. `overrides.ts` declares the allowlist; `hookTimeout` is
    // an omission the allowlist makes on purpose, `teardownTimeout` is the drift. Both are used
    // symmetrically with the allowlist's members across the codebase — that's the saturation the
    // discriminator exists to cut through — but only `teardownTimeout` is handled by the
    // declaring module itself, in the same statement as a member the allowlist does list.

    private final Pair subsetPair = new Pair(truth, mirror, 0.3, true);

    private UsageSite at(String file, int line, String member) { return new UsageSite(file, line, member); }

    /** Both omissions symmetric; only the drift one is co-used inside the mirror's own file. */
    private List<UsageSite> curatedUsages(int driftLine) {
        return List.of(
            at("runner.ts", 5, "maxWorkers"), at("runner.ts", 6, "tagsFilter"),
            at("runner.ts", 7, "hookTimeout"), at("runner.ts", 8, "teardownTimeout"),
            at("core.ts", 3, "fileParallelism"), at("core.ts", 4, "hookTimeout"),
            at("core.ts", 5, "teardownTimeout"),
            at("overrides.ts", 40, "tagsFilter"),
            at("overrides.ts", driftLine, "teardownTimeout"));
    }

    @Test
    void subsetOmissionTheCuratingModuleTreatsAsAPeerIsDefault() {
        var findings = new Verifier(0.5).verify(subsetPair, new Differ().diff(subsetPair), curatedUsages(40));
        var drift = findings.stream()
            .filter(f -> f.member().name().equals("teardownTimeout")).findFirst().orElseThrow();
        assertThat(drift.severity()).isEqualTo(Severity.DEFAULT);
    }

    @Test
    void subsetOmissionTheCuratingModuleNeverTouchesIsInfo() {
        var findings = new Verifier(0.5).verify(subsetPair, new Differ().diff(subsetPair), curatedUsages(40));
        var curated = findings.stream()
            .filter(f -> f.member().name().equals("hookTimeout")).findFirst().orElseThrow();
        // Symmetric usage alone no longer earns DEFAULT: this is the 40-of-41 case.
        assertThat(curated.severity()).isEqualTo(Severity.INFO);
    }

    @Test
    void twinPairIgnoresTheSubsetGateEntirely() {
        var twin = new Pair(truth, mirror, 0.3, false);
        var findings = new Verifier(0.5).verify(twin, new Differ().diff(twin), curatedUsages(40));
        assertThat(findings).filteredOn(f -> f.member().name().equals("hookTimeout"))
            .allMatch(f -> f.severity() == Severity.DEFAULT);
    }

    @Test
    void subsetOmissionOnANeighbouringLineNeedsTheWindowWidened() {
        var usages = curatedUsages(41); // one line below the mirror member's usage
        var name = "teardownTimeout";
        var tight = new Verifier(0.5).verify(subsetPair, new Differ().diff(subsetPair), usages);
        assertThat(tight).filteredOn(f -> f.member().name().equals(name))
            .allMatch(f -> f.severity() == Severity.INFO);

        var loose = new Verifier(0.5, 0.8, 1).verify(subsetPair, new Differ().diff(subsetPair), usages);
        assertThat(loose).filteredOn(f -> f.member().name().equals(name))
            .allMatch(f -> f.severity() == Severity.DEFAULT);
    }

    @Test
    void subsetOmissionBelowTheRaisedScoreFloorIsInfo() {
        // Same peer evidence, but half of teardownTimeout's usage is outside the mirror's
        // territory, dragging its score under the subset floor while staying over min_score.
        var usages = new java.util.ArrayList<>(curatedUsages(40));
        usages.add(at("unrelated-a.ts", 1, "teardownTimeout"));
        usages.add(at("unrelated-b.ts", 1, "teardownTimeout"));
        usages.add(at("unrelated-c.ts", 1, "teardownTimeout"));
        var findings = new Verifier(0.5).verify(subsetPair, new Differ().diff(subsetPair), usages);
        assertThat(findings).filteredOn(f -> f.member().name().equals("teardownTimeout"))
            .allMatch(f -> f.severity() == Severity.INFO);
    }

    /** A subset floor set below min_score must not make curated mirrors noisier than twins. */
    @Test
    void subsetFloorNeverWeakensBelowMinScore() {
        var usages = curatedUsages(40);
        var findings = new Verifier(0.9, 0.5, 0).verify(subsetPair, new Differ().diff(subsetPair), usages);
        assertThat(findings).filteredOn(f -> f.member().name().equals("hookTimeout"))
            .allMatch(f -> f.severity() == Severity.INFO);
    }

    /**
     * I3: "right line, wrong file". `teardownTimeout` shares a line with a mirror member
     * (`tagsFilter`) — but in {@code runner.ts}, not {@code overrides.ts}, the mirror's own
     * declaring file. Symmetric usage alone clears both the ordinary and raised score floors, so
     * this must be held to INFO purely by the declaring-file restriction in
     * {@code curatorTreatsItAsAPeer}. Verified (by hand, restored immediately after) that
     * commenting out that file-equality check turns this into a DEFAULT finding — so this test is
     * not vacuous, it is actually pinned on the restriction.
     */
    @Test
    void subsetOmissionOnTheSameLineInADifferentFileIsInfo() {
        var usages = List.of(
            at("runner.ts", 10, "tagsFilter"), at("runner.ts", 10, "teardownTimeout"),
            at("overrides.ts", 40, "tagsFilter"));
        var findings = new Verifier(0.5).verify(subsetPair, new Differ().diff(subsetPair), usages);
        var drift = findings.stream()
            .filter(f -> f.member().name().equals("teardownTimeout")).findFirst().orElseThrow();
        assertThat(drift.severity()).isEqualTo(Severity.INFO);
    }

    /**
     * M5: {@code subset_peer_window} arrives from TOML as a plain {@code int} with no floor of
     * its own (see {@code Config.load}) — a negative value is a config author typo, not a
     * deliberate "narrower than same-line" request, which doesn't exist. The {@code Verifier}
     * constructor clamps it to 0, so a negative window must behave identically to window 0: the
     * neighbouring-line peer here must NOT be picked up.
     */
    @Test
    void negativeSubsetPeerWindowClampsToZero() {
        var usages = curatedUsages(41); // one line below the mirror member's usage
        var findings = new Verifier(0.5, 0.8, -5).verify(subsetPair, new Differ().diff(subsetPair), usages);
        assertThat(findings).filteredOn(f -> f.member().name().equals("teardownTimeout"))
            .allMatch(f -> f.severity() == Severity.INFO);
    }
}
