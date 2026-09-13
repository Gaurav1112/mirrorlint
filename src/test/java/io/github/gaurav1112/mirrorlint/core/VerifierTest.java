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
}
