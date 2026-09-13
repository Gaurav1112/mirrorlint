package io.github.gaurav1112.mirrorlint.report;

import io.github.gaurav1112.mirrorlint.core.Finding;
import io.github.gaurav1112.mirrorlint.core.Pair;
import io.github.gaurav1112.mirrorlint.core.Severity;
import io.github.gaurav1112.mirrorlint.core.UsageSite;
import io.github.gaurav1112.mirrorlint.scan.ScanResult;
import java.util.List;
import java.util.Locale;

/**
 * Renders a {@link ScanResult} as evidence-first plain text for humans (spec §4):
 *
 * <pre>
 * DRIFT overrides.ts:10  mirror `PROJECT_CLI_OVERRIDES` is missing `hookTimeout`
 *   truth  types.ts:1  `CliOption` (4 members, jaccard 0.60)
 *   evidence: consumed alongside mirror members in:
 *     runner.ts:5  config.hookTimeout
 * </pre>
 */
public class HumanReporter {
    public String render(ScanResult result, boolean includeInfo) {
        StringBuilder sb = new StringBuilder();
        for (Finding finding : result.findings()) {
            if (finding.severity() != Severity.DEFAULT && !includeInfo) {
                continue;
            }
            appendFinding(sb, finding);
        }
        return sb.toString();
    }

    private void appendFinding(StringBuilder sb, Finding finding) {
        Pair pair = finding.pair();
        String label = finding.severity() == Severity.DEFAULT ? "DRIFT" : "INFO";
        String verb = finding.omission() ? "is missing" : "has extra";

        sb.append(label).append(' ')
            .append(pair.mirror().file()).append(':').append(pair.mirror().line())
            .append("  mirror `").append(pair.mirror().id()).append("` ")
            .append(verb).append(" `").append(finding.member().name()).append('`').append('\n');

        sb.append("  truth  ").append(pair.truth().file()).append(':').append(pair.truth().line())
            .append("  `").append(pair.truth().id()).append("` (")
            .append(pair.truth().members().size()).append(" members, jaccard ")
            .append(String.format(Locale.ROOT, "%.2f", pair.jaccard())).append(")\n");

        if (!finding.evidence().isEmpty()) {
            sb.append("  evidence: consumed alongside mirror members in:\n");
            appendSites(sb, finding.evidence());
        }

        if (!finding.otherSites().isEmpty()) {
            sb.append("  also used in:\n");
            appendSites(sb, finding.otherSites());
        }

        sb.append('\n');
    }

    private void appendSites(StringBuilder sb, List<UsageSite> sites) {
        for (UsageSite site : sites) {
            sb.append("    ").append(site.file()).append(':').append(site.line())
                .append("  ").append(site.member()).append('\n');
        }
    }
}
