package io.github.gaurav1112.mirrorlint.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigTest {
    @Test
    void loadsFromTomlAndMergesExcludesWithDefaults(@TempDir Path tempDir) throws IOException {
        Path toml = tempDir.resolve("mirrorlint.toml");
        Files.writeString(toml, """
            min_jaccard = 0.7
            min_shared = 2
            min_score = 0.4
            excludes = ["**/generated/**"]

            [[pairs]]
            truth_file = "src/types.ts"
            truth_id = "CliOption"
            mirror_file = "src/overrides.ts"
            mirror_id = "PROJECT_CLI_OVERRIDES"
            """);

        Config config = Config.load(toml);

        assertThat(config.minJaccard()).isEqualTo(0.7);
        assertThat(config.minShared()).isEqualTo(2);
        assertThat(config.minScore()).isEqualTo(0.4);

        assertThat(config.excludes()).contains("**/generated/**");
        assertThat(config.excludes()).containsAll(Config.DEFAULT_EXCLUDES);
        assertThat(config.excludes()).hasSize(Config.DEFAULT_EXCLUDES.size() + 1);

        assertThat(config.declaredPairs()).hasSize(1);
        Config.DeclaredPair pair = config.declaredPairs().get(0);
        assertThat(pair.truthFile()).isEqualTo("src/types.ts");
        assertThat(pair.truthId()).isEqualTo("CliOption");
        assertThat(pair.mirrorFile()).isEqualTo("src/overrides.ts");
        assertThat(pair.mirrorId()).isEqualTo("PROJECT_CLI_OVERRIDES");
    }

    @Test
    void nullPathYieldsAllDefaults() {
        Config config = Config.load(null);

        assertThat(config.minJaccard()).isEqualTo(0.6);
        assertThat(config.minShared()).isEqualTo(4);
        assertThat(config.minScore()).isEqualTo(0.5);
        assertThat(config.excludes()).isEqualTo(Config.DEFAULT_EXCLUDES);
        assertThat(config.declaredPairs()).isEmpty();
    }

    @Test
    void absentPathYieldsAllDefaults(@TempDir Path tempDir) {
        Config config = Config.load(tempDir.resolve("does-not-exist.toml"));

        assertThat(config).isEqualTo(Config.load(null));
    }
}
