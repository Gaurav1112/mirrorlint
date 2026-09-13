package io.github.gaurav1112.mirrorlint;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class MainTest {

    @Test
    void scanningMixedFixtureWithAllReportsHookTimeoutDriftAndExitsOne() {
        // The "mixed" fixture's truth/mirror pair shares only 3 members (see task-7-brief.md's note:
        // "minShared 3 here because the fixture truth/mirror share 2-3 members; the CLI default stays 4"),
        // so it needs this lower minShared to clear the mining threshold and produce a finding.
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int exitCode = Main.run(
            new String[] {
                "scan", "src/test/resources/fixtures/mixed", "--all",
                "--config", "src/test/resources/fixtures/mixed.toml"
            },
            new PrintStream(buffer, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isEqualTo(1);
        assertThat(buffer.toString(StandardCharsets.UTF_8)).contains("hookTimeout");
    }

    @Test
    void scanningAnEmptyDirectoryFindsNoDriftAndExitsZero(@TempDir Path emptyDir) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int exitCode = Main.run(
            new String[] {"scan", emptyDir.toString()},
            new PrintStream(buffer, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    void scanningANonexistentPathExitsTwo() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int exitCode = Main.run(
            new String[] {"scan", "src/test/resources/fixtures/does-not-exist"},
            new PrintStream(buffer, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isEqualTo(2);
    }

    @Test
    void malformedTomlConfigExitsTwo(@TempDir Path tempDir) throws Exception {
        Path badConfig = tempDir.resolve("mirrorlint.toml");
        Files.writeString(badConfig, "min_jaccard = [this is not valid toml");

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int exitCode = Main.run(
            new String[] {"scan", "src/test/resources/fixtures/mixed", "--config", badConfig.toString()},
            new PrintStream(buffer, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isEqualTo(2);
    }
}
