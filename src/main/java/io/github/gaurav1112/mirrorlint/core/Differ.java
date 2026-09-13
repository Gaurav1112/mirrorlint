package io.github.gaurav1112.mirrorlint.core;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class Differ {
    public record DiffResult(List<Member> omissions, List<Member> surplus) {}

    public DiffResult diff(Pair pair) {
        return new DiffResult(
            missingFrom(pair.truth(), pair.mirror().memberNames()),
            missingFrom(pair.mirror(), pair.truth().memberNames()));
    }

    private List<Member> missingFrom(Shape source, Set<String> other) {
        return source.members().stream()
            .filter(m -> !other.contains(Shape.normalize(m.name())))
            .sorted(Comparator.comparing(m -> Shape.normalize(m.name())))
            .toList();
    }
}
