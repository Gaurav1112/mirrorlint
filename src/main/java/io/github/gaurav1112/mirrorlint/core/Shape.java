package io.github.gaurav1112.mirrorlint.core;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record Shape(String id, ShapeKind kind, String file, int line, List<Member> members) {
    public static String normalize(String name) {
        String s = name.trim();
        if (s.length() >= 2 && (s.charAt(0) == '"' || s.charAt(0) == '\'' || s.charAt(0) == '`')
                && s.charAt(s.length() - 1) == s.charAt(0)) {
            s = s.substring(1, s.length() - 1);
        }
        return s.toLowerCase(java.util.Locale.ROOT);
    }
    public Set<String> memberNames() {
        return members.stream().map(m -> normalize(m.name())).collect(Collectors.toSet());
    }
}
