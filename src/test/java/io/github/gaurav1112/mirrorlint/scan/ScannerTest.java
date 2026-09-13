package io.github.gaurav1112.mirrorlint.scan;

import io.github.gaurav1112.mirrorlint.config.Config;
import io.github.gaurav1112.mirrorlint.core.*;
import io.github.gaurav1112.mirrorlint.lang.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ScannerTest {
    @Test
    void scansMixedTreeFindsHookTimeoutAndSkipsNodeModules() {
        Scanner scanner = new Scanner(
            List.of(new TypeScriptAdapter(), new JavaAdapter()),
            new PairMiner(0.6, 3), new Verifier(0.5),
            List.of("**/node_modules/**"));
        var result = scanner.scan(Path.of("src/test/resources/fixtures/mixed"));
        assertThat(result.filesScanned()).isEqualTo(2);
        assertThat(result.findings()).anySatisfy(f -> {
            assertThat(f.member().name()).isEqualTo("hookTimeout");
            assertThat(f.severity()).isEqualTo(Severity.DEFAULT);
        });
        assertThat(result.findings()).noneMatch(f -> f.pair().mirror().file().contains("node_modules"));
        assertThat(result.findings()).noneMatch(f -> f.pair().truth().file().contains("node_modules"));
    }

    @Test
    void scanningASingleFileDirectlyScansJustThatFile() {
        Scanner scanner = new Scanner(
            List.of(new TypeScriptAdapter(), new JavaAdapter()),
            new PairMiner(0.6, 3), new Verifier(0.5),
            List.of());
        var result = scanner.scan(Path.of("src/test/resources/fixtures/mixed/overrides.ts"));
        assertThat(result.filesScanned()).isEqualTo(1);
    }

    @Test
    void suppressesFindingWithInlineIgnoreComment() {
        Scanner scanner = new Scanner(
            List.of(new TypeScriptAdapter(), new JavaAdapter()),
            new PairMiner(0.6, 3), new Verifier(0.5),
            List.of());
        var result = scanner.scan(Path.of("src/test/resources/fixtures/suppress"));
        assertThat(result.filesScanned()).isEqualTo(2);
        assertThat(result.findings())
            .noneMatch(f -> Shape.normalize(f.member().name()).equals("hooktimeout"));
    }

    @Test
    void byteIdenticalDuplicateFileIsSkippedEntirely() {
        Scanner scanner = new Scanner(
            List.of(new TypeScriptAdapter(), new JavaAdapter()),
            new PairMiner(0.6, 3), new Verifier(0.5),
            List.of());
        var result = scanner.scan(Path.of("src/test/resources/fixtures/precision/dedupe"));

        // truth.ts + exactly one of the byte-identical a/mirror.ts, b/mirror.ts (first path wins).
        assertThat(result.filesScanned()).isEqualTo(2);
        assertThat(result.findings())
            .filteredOn(f -> Shape.normalize(f.member().name()).equals("hooktimeout"))
            .hasSize(1)
            .allSatisfy(f -> assertThat(f.pair().mirror().file()).isEqualTo("a/mirror.ts"));
    }

    @Test
    void generatedFileContributesNoShapes() {
        Scanner scanner = new Scanner(
            List.of(new TypeScriptAdapter(), new JavaAdapter()),
            new PairMiner(0.6, 3), new Verifier(0.5),
            List.of());
        var result = scanner.scan(Path.of("src/test/resources/fixtures/precision/generated"));

        assertThat(result.filesScanned()).isEqualTo(2);
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void dtsFileContributesTruthShapesOnlyNotListOrLiteral() {
        Scanner scanner = new Scanner(
            List.of(new TypeScriptAdapter(), new JavaAdapter()),
            new PairMiner(0.6, 3), new Verifier(0.5),
            List.of());
        var result = scanner.scan(Path.of("src/test/resources/fixtures/precision/dts"));

        assertThat(result.filesScanned()).isEqualTo(2);
        // The truth interface in types.d.ts must still pair with the legitimate mirror.ts array —
        // proving the TRUTH shape survived extraction from the .d.ts file.
        assertThat(result.findings())
            .filteredOn(f -> Shape.normalize(f.member().name()).equals("hooktimeout"))
            .hasSize(1)
            .allSatisfy(f -> assertThat(f.pair().mirror().file()).isEqualTo("mirror.ts"));
        // The decoy array literal declared inside types.d.ts must never surface as a mirror.
        assertThat(result.findings()).noneMatch(f -> f.pair().mirror().file().equals("types.d.ts"));
    }

    @Test
    void generatedDtsFileStillContributesTruthShapesButDropsTheList() {
        Scanner scanner = new Scanner(
            List.of(new TypeScriptAdapter(), new JavaAdapter()),
            new PairMiner(0.6, 3), new Verifier(0.5),
            List.of());
        var result = scanner.scan(Path.of("src/test/resources/fixtures/precision/generated-dts"));

        assertThat(result.filesScanned()).isEqualTo(2);
        // types.d.ts is BOTH generated AND a .d.ts: the declaration-file rule must win, keeping
        // the TRUTH interface so it still pairs with the legitimate mirror.ts array.
        assertThat(result.findings())
            .filteredOn(f -> Shape.normalize(f.member().name()).equals("hooktimeout"))
            .hasSize(1)
            .allSatisfy(f -> assertThat(f.pair().mirror().file()).isEqualTo("mirror.ts"));
        // The decoy array literal inside the generated types.d.ts must never surface as a mirror.
        assertThat(result.findings()).noneMatch(f -> f.pair().mirror().file().equals("types.d.ts"));
    }

    @Test
    void generatedFileUsagesStillCountTowardOmissionScoring() {
        Scanner scanner = new Scanner(
            List.of(new TypeScriptAdapter(), new JavaAdapter()),
            new PairMiner(0.6, 3), new Verifier(0.5),
            List.of());
        var result = scanner.scan(Path.of("src/test/resources/fixtures/precision/generated-usage"));

        assertThat(result.filesScanned()).isEqualTo(3);
        // "delta" is used only inside a @generated file, alongside 2 of the mirror's 3 members.
        // That usage must be collected — it's the only thing that can push the omission's score
        // to/above minScore — or this finding would read as INFO noise instead of DEFAULT.
        assertThat(result.findings())
            .filteredOn(f -> Shape.normalize(f.member().name()).equals("delta"))
            .hasSize(1)
            .allSatisfy(f -> {
                assertThat(f.severity()).isEqualTo(Severity.DEFAULT);
                assertThat(f.omission()).isTrue();
            });
    }

    /**
     * I2: a declared {@code [[pairs]]} entry bypasses the mining thresholds, but when the mirror
     * is structurally a subset list (strictly smaller than the truth, containment over the
     * configured floor, and a LIST-kind shape) it must still be tagged {@code subset=true} so the
     * sharper subset discriminator applies — otherwise a user-declared allowlist floods findings
     * the miner-path would have gated. `delta` is co-used with an allowed member on the same line
     * inside the mirror's own declaring file (mirror.ts) and must read DEFAULT; `epsilon` clears
     * the ordinary symmetric-usage score (it's used in other.ts alongside `alpha`) but is never
     * touched by mirror.ts itself, so the subset gate must hold it at INFO.
     */
    @Test
    void declaredSubsetPairIsTaggedSubsetAndGatedByTheDiscriminator() {
        Config config = new Config(
            Config.DEFAULT_MIN_JACCARD, Config.DEFAULT_MIN_SHARED, Config.DEFAULT_MIN_SCORE,
            Config.DEFAULT_MIN_CONTAINMENT, Config.DEFAULT_MIN_SUBSET_JACCARD,
            Config.DEFAULT_SUBSET_MIN_SCORE, Config.DEFAULT_SUBSET_PEER_WINDOW,
            Config.DEFAULT_EXCLUDES,
            List.of(new Config.DeclaredPair("truth.ts", "Config", "mirror.ts", "ALLOWED")));
        Scanner scanner = new Scanner(List.of(new TypeScriptAdapter(), new JavaAdapter()), config);
        var result = scanner.scan(Path.of("src/test/resources/fixtures/declared-subset"));

        assertThat(result.findings())
            .filteredOn(f -> Shape.normalize(f.member().name()).equals("delta"))
            .hasSize(1)
            .allSatisfy(f -> {
                assertThat(f.pair().subset()).isTrue();
                assertThat(f.severity()).isEqualTo(Severity.DEFAULT);
            });

        assertThat(result.findings())
            .filteredOn(f -> Shape.normalize(f.member().name()).equals("epsilon"))
            .hasSize(1)
            .allSatisfy(f -> assertThat(f.severity()).isEqualTo(Severity.INFO));
    }
}
