package io.github.gaurav1112.mirrorlint.report;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.gaurav1112.mirrorlint.core.Finding;
import io.github.gaurav1112.mirrorlint.core.Member;
import io.github.gaurav1112.mirrorlint.core.Pair;
import io.github.gaurav1112.mirrorlint.core.Severity;
import io.github.gaurav1112.mirrorlint.core.Shape;
import io.github.gaurav1112.mirrorlint.core.ShapeKind;
import io.github.gaurav1112.mirrorlint.core.UsageSite;
import io.github.gaurav1112.mirrorlint.scan.ScanResult;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportersTest {
    private final Shape truth = new Shape("CliOption", ShapeKind.TRUTH, "types.ts", 1, List.of(
        new Member("maxWorkers", "types.ts", 1), new Member("hookTimeout", "types.ts", 2),
        new Member("teardownTimeout", "types.ts", 3), new Member("tagsFilter", "types.ts", 4)));
    private final Shape mirror = new Shape("PROJECT_CLI_OVERRIDES", ShapeKind.LIST, "overrides.ts", 10, List.of(
        new Member("maxWorkers", "overrides.ts", 11), new Member("tagsFilter", "overrides.ts", 12)));
    private final Pair pair = new Pair(truth, mirror, 0.6);

    private final Finding hookTimeoutFinding = new Finding(pair, new Member("hookTimeout", "types.ts", 2),
        Severity.DEFAULT, true, List.of(new UsageSite("runner.ts", 5, "hookTimeout")),
        List.of(new UsageSite("solo.ts", 9, "hookTimeout")));
    private final Finding teardownTimeoutFinding = new Finding(pair, new Member("teardownTimeout", "types.ts", 3),
        Severity.INFO, true, List.of(), List.of());

    private final ScanResult result = new ScanResult(List.of(hookTimeoutFinding, teardownTimeoutFinding), 2);

    @Test
    void humanReportRendersTheDefaultFindingWithItsEvidence() {
        String rendered = new HumanReporter().render(result, false);
        assertThat(rendered).contains("missing `hookTimeout`");
        assertThat(rendered).contains("runner.ts:5");
    }

    @Test
    void humanReportShowsOtherSitesAsAlsoUsedInWhenNonEmpty() {
        String rendered = new HumanReporter().render(result, false);
        assertThat(rendered).contains("evidence: consumed alongside mirror members in:");
        assertThat(rendered).contains("runner.ts:5");
        assertThat(rendered).contains("also used in:");
        assertThat(rendered).contains("solo.ts:9");
    }

    @Test
    void humanReportOmitsAlsoUsedInSectionWhenOtherSitesEmpty() {
        Finding noOtherSites = new Finding(pair, new Member("hookTimeout", "types.ts", 2),
            Severity.DEFAULT, true, List.of(new UsageSite("runner.ts", 5, "hookTimeout")), List.of());
        ScanResult onlyThis = new ScanResult(List.of(noOtherSites), 1);
        String rendered = new HumanReporter().render(onlyThis, false);
        assertThat(rendered).doesNotContain("also used in:");
    }

    @Test
    void humanReportHidesInfoFindingsUnlessRequested() {
        String withoutInfo = new HumanReporter().render(result, false);
        assertThat(withoutInfo).doesNotContain("teardownTimeout");

        String withInfo = new HumanReporter().render(result, true);
        assertThat(withInfo).contains("teardownTimeout");
    }

    @Test
    void sarifReportHasOneResultForTheDefaultFindingOnly() {
        String sarif = new SarifReporter().render(result);
        JsonObject root = JsonParser.parseString(sarif).getAsJsonObject();
        JsonArray results = root.getAsJsonArray("runs").get(0).getAsJsonObject().getAsJsonArray("results");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getAsJsonObject().get("ruleId").getAsString()).isEqualTo("mirror-drift");
    }
}
