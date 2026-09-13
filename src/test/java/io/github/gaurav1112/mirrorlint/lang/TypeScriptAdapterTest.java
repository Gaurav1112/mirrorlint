package io.github.gaurav1112.mirrorlint.lang;

import io.github.gaurav1112.mirrorlint.core.Shape;
import io.github.gaurav1112.mirrorlint.core.ShapeKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TypeScriptAdapterTest {

    @Test
    void extractsListTruthAndUsages() throws Exception {
        String src = Files.readString(Path.of("src/test/resources/fixtures/ts/overrides.ts"));
        FileFacts facts = new TypeScriptAdapter().extract("overrides.ts", src);

        assertThat(facts.shapes()).anySatisfy(s -> {
            assertThat(s.kind()).isEqualTo(ShapeKind.TRUTH);
            assertThat(s.memberNames()).containsExactlyInAnyOrder("maxworkers", "hooktimeout", "teardowntimeout", "tagsfilter");
        });
        assertThat(facts.shapes()).anySatisfy(s -> {
            assertThat(s.kind()).isEqualTo(ShapeKind.LIST);
            assertThat(s.memberNames()).containsExactlyInAnyOrder("maxworkers", "tagsfilter", "fileparallelism");
        });
        assertThat(facts.usages()).extracting(u -> u.member())
            .contains("maxWorkers", "hookTimeout", "tagsFilter");
    }

    @Test
    void chainedUnionEmitsExactlyOneTruthShape() {
        String src = "export type T = 'a' | 'b' | 'c' | 'd';";
        FileFacts facts = new TypeScriptAdapter().extract("t.ts", src);

        List<Shape> truthShapes = facts.shapes().stream()
            .filter(s -> s.kind() == ShapeKind.TRUTH)
            .toList();

        assertThat(truthShapes).hasSize(1);
        assertThat(truthShapes.get(0).memberNames()).containsExactlyInAnyOrder("a", "b", "c", "d");
    }
}
