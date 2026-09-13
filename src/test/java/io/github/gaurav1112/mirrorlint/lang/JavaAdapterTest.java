package io.github.gaurav1112.mirrorlint.lang;

import io.github.gaurav1112.mirrorlint.core.Shape;
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

    /**
     * {@code collectEnum} passes the {@code enum_declaration} node itself to
     * {@code TypeScriptAdapter.idFor}, the same helper whose parent-only walk once made an
     * interface's shape id fall back to {@code file:line}. Enums hit that bug in a second way:
     * even after the walk checks the node itself, {@code enum_declaration} was never in the set
     * of matched declaration types, so the enum's name was never read.
     */
    @Test
    void enumDeclarationIdIsTheEnumName() throws Exception {
        String src = Files.readString(Path.of("src/test/resources/fixtures/java/Overrides.java"));
        FileFacts facts = new JavaAdapter().extract("Overrides.java", src);

        assertThat(facts.shapes()).filteredOn(s -> s.kind() == ShapeKind.TRUTH)
            .extracting(Shape::id)
            .contains("CliOption");
    }
}
