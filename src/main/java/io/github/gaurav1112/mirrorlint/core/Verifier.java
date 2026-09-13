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
 * The symmetric-usage discriminator: separates real drift (a mirror member is genuinely
 * omitted where the mirror's other members are actually used) from noise (a truth member
 * that nobody who touches the mirror's territory ever reaches for).
 *
 * <p>Subset-mined pairs get a second, stricter gate on top — see
 * {@link #curatorTreatsItAsAPeer}. A twin's omissions are all suspicious by construction, so
 * symmetric usage is the whole question there. A subset mirror is a <em>curation</em>: it omits
 * most of its truth on purpose, so symmetric usage proves nothing on its own. Measured at the
 * vitest receipt SHA, every one of the 41 omissions of {@code PROJECT_CLI_OVERRIDES} scored
 * {@code >= 0.94} — an allowlist drawn from a config type is omitting options that the codebase
 * naturally uses wherever it uses the allowed ones. The signal has to be sharper than
 * co-occurrence-in-some-file, and it is: the module that <em>declares</em> the curation is the
 * authority on what belongs in it, so an omission is drift only when that module itself handles
 * the member in the same breath as a member it did list.
 */
public class Verifier {
    private final double minScore;
    private final double subsetMinScore;
    private final int subsetPeerWindow;

    public Verifier(double minScore) {
        this(minScore, Config.DEFAULT_SUBSET_MIN_SCORE, Config.DEFAULT_SUBSET_PEER_WINDOW);
    }

    public Verifier(double minScore, double subsetMinScore, int subsetPeerWindow) {
        this.minScore = minScore;
        // The subset gate may only ever tighten. A config that sets subset_min_score below
        // min_score would otherwise make curated mirrors *noisier* than twins, which inverts
        // the whole point of the rule.
        this.subsetMinScore = Math.max(minScore, subsetMinScore);
        this.subsetPeerWindow = Math.max(0, subsetPeerWindow);
    }

    public List<Finding> verify(Pair pair, Differ.DiffResult diff, List<UsageSite> allUsages) {
        Set<String> mirrorMemberNames = pair.mirror().memberNames();

        // Per-file: distinct normalized mirror members used there.
        Map<String, Set<String>> mirrorMembersByFile = new HashMap<>();
        // Lines of the mirror's own declaring file on which a mirror member is used.
        Set<Integer> peerLines = new HashSet<>();
        for (UsageSite usage : allUsages) {
            String normalized = Shape.normalize(usage.member());
            if (mirrorMemberNames.contains(normalized)) {
                mirrorMembersByFile.computeIfAbsent(usage.file(), f -> new HashSet<>()).add(normalized);
                if (usage.file().equals(pair.mirror().file())) {
                    peerLines.add(usage.line());
                }
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

            boolean drift = score >= minScore && !sites.isEmpty();
            if (drift && pair.subset()) {
                drift = score >= subsetMinScore && curatorTreatsItAsAPeer(sites, pair, peerLines);
            }

            findings.add(new Finding(pair, omission, drift ? Severity.DEFAULT : Severity.INFO, true, sites));
        }

        for (Member surplus : diff.surplus()) {
            findings.add(new Finding(pair, surplus, Severity.INFO, false, List.of()));
        }

        return findings.stream()
            .sorted(Comparator.<Finding, Boolean>comparing(f -> f.severity() != Severity.DEFAULT)
                .thenComparing(f -> Shape.normalize(f.member().name())))
            .toList();
    }

    /**
     * The subset-pair discriminator. True when the omitted member is used inside the file that
     * declares the mirror, on a line where a member the mirror <em>does</em> list is also used
     * (within {@code subsetPeerWindow} lines of slack, 0 by default — the same statement).
     *
     * <p>This is the vitest bug's own shape. {@code resolveProjects.ts} declares
     * {@code PROJECT_CLI_OVERRIDES} and then writes
     * {@code maxWorkers: config.fileParallelism === false ? 1 : clonedConfig.maxWorkers} — the
     * curating module treating {@code maxWorkers} as a sibling of {@code fileParallelism}, which
     * the allowlist lists. None of the other 40 omissions is handled that way anywhere in that
     * file: they are consumed only by the rest of the codebase, which is exactly what an
     * intentional omission looks like.
     */
    private boolean curatorTreatsItAsAPeer(List<UsageSite> sites, Pair pair, Set<Integer> peerLines) {
        String home = pair.mirror().file();
        for (UsageSite site : sites) {
            if (!site.file().equals(home)) continue;
            for (int line = site.line() - subsetPeerWindow; line <= site.line() + subsetPeerWindow; line++) {
                if (peerLines.contains(line)) return true;
            }
        }
        return false;
    }

    private double weightOf(String file, Map<String, Set<String>> mirrorMembersByFile) {
        Set<String> members = mirrorMembersByFile.get(file);
        return (members != null && members.size() >= 2) ? 2.0 : 1.0;
    }
}
