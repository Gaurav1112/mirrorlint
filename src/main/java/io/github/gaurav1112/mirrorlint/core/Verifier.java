package io.github.gaurav1112.mirrorlint.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The symmetric-usage discriminator: separates real drift (a mirror member is genuinely
 * omitted where the mirror's other members are actually used) from noise (a truth member
 * that nobody who touches the mirror's territory ever reaches for).
 */
public class Verifier {
    private final double minScore;

    public Verifier(double minScore) {
        this.minScore = minScore;
    }

    public List<Finding> verify(Pair pair, Differ.DiffResult diff, List<UsageSite> allUsages) {
        Set<String> mirrorMemberNames = pair.mirror().memberNames();

        // Per-file: distinct normalized mirror members used there.
        Map<String, Set<String>> mirrorMembersByFile = new HashMap<>();
        for (UsageSite usage : allUsages) {
            String normalized = Shape.normalize(usage.member());
            if (mirrorMemberNames.contains(normalized)) {
                mirrorMembersByFile.computeIfAbsent(usage.file(), f -> new HashSet<>()).add(normalized);
            }
        }
        Set<String> mirrorFiles = mirrorMembersByFile.keySet();

        List<Finding> findings = new ArrayList<>();

        for (Member omission : diff.omissions()) {
            String normalized = Shape.normalize(omission.name());
            List<UsageSite> sites = allUsages.stream()
                .filter(u -> Shape.normalize(u.member()).equals(normalized))
                .sorted(Comparator.comparing(UsageSite::file).thenComparingInt(UsageSite::line))
                .toList();

            Set<String> files = new HashSet<>();
            for (UsageSite site : sites) {
                files.add(site.file());
            }

            double numerator = 0.0;
            double denominator = 0.0;
            for (String file : files) {
                double weight = weightOf(file, mirrorMembersByFile);
                denominator += weight;
                if (mirrorFiles.contains(file)) {
                    numerator += weight;
                }
            }
            double score = denominator == 0.0 ? 0.0 : numerator / denominator;

            Severity severity = (score >= minScore && !sites.isEmpty()) ? Severity.DEFAULT : Severity.INFO;
            findings.add(new Finding(pair, omission, severity, true, sites));
        }

        for (Member surplus : diff.surplus()) {
            findings.add(new Finding(pair, surplus, Severity.INFO, false, List.of()));
        }

        return findings.stream()
            .sorted(Comparator.<Finding, Boolean>comparing(f -> f.severity() != Severity.DEFAULT)
                .thenComparing(f -> Shape.normalize(f.member().name())))
            .toList();
    }

    private double weightOf(String file, Map<String, Set<String>> mirrorMembersByFile) {
        Set<String> members = mirrorMembersByFile.get(file);
        return (members != null && members.size() >= 2) ? 2.0 : 1.0;
    }
}
