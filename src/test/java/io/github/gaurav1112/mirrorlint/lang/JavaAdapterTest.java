package io.github.gaurav1112.mirrorlint.lang;

import io.github.gaurav1112.mirrorlint.core.ShapeKind;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JavaAdapterTest {
    @Test
    void extractsEnumTruthArrayListAndUsages() throws Exception {
        String src = Files.readString(Path.of("src/test/resources/fixtures/java/Overrides.java"));
        FileFacts facts = new JavaAdapter().extract("Overrides.java", src);
        assertThat(facts.shapes()).anySatisfy(s -> {
            assertThat(s.kind()).isEqualTo(ShapeKind.TRUTH);
            assertThat(s.memberNames()).contains("max_workers", "hook_timeout", "teardown_timeout", "tags_filter");
        });
        assertThat(facts.shapes()).anySatisfy(s -> {
            assertThat(s.kind()).isEqualTo(ShapeKind.LIST);
            assertThat(s.memberNames()).contains("max_workers", "tags_filter", "file_parallelism");
        });
        assertThat(facts.usages()).extracting(u -> u.member()).contains("MAX_WORKERS", "HOOK_TIMEOUT");
    }
}
