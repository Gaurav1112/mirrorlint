package io.github.gaurav1112.mirrorlint.scan;

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
        assertThat(result.findings()).anySatisfy(f -> {
            assertThat(f.member().name()).isEqualTo("hookTimeout");
            assertThat(f.severity()).isEqualTo(Severity.DEFAULT);
        });
        assertThat(result.findings()).noneMatch(f -> f.pair().mirror().file().contains("node_modules"));
    }
}
