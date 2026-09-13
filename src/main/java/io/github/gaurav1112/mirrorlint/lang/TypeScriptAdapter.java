package io.github.gaurav1112.mirrorlint.lang;

import io.github.gaurav1112.mirrorlint.core.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.treesitter.TSNode;

public class TypeScriptAdapter implements LanguageAdapter {
    @Override public boolean handles(String filename) {
        return filename.endsWith(".ts") || filename.endsWith(".tsx")
            || filename.endsWith(".js") || filename.endsWith(".mjs") || filename.endsWith(".jsx");
    }

    @Override public FileFacts extract(String filename, String source) {
        TSNode root = TreeSitters.typescript().parseString(null, source).getRootNode();
        byte[] src = source.getBytes(StandardCharsets.UTF_8);
        List<Shape> shapes = new ArrayList<>();
        List<UsageSite> usages = new ArrayList<>();
        walk(root, src, filename, shapes, usages);
        return new FileFacts(shapes, usages);
    }

    private void walk(TSNode node, byte[] src, String file, List<Shape> shapes, List<UsageSite> usages) {
        String type = node.getType();
        switch (type) {
            case "array" -> collectStringArray(node, src, file, shapes);
            case "union_type" -> collectUnion(node, src, file, shapes);
            case "interface_declaration" -> collectInterface(node, src, file, shapes);
            case "object_type" -> collectObjectType(node, src, file, shapes);
            case "object" -> collectObjectLiteral(node, src, file, shapes);
            case "object_pattern" -> collectObjectPattern(node, src, file, shapes);
            case "member_expression" -> {
                TSNode prop = node.getChildByFieldName("property");
                if (prop != null && !prop.isNull()) usages.add(site(prop, src, file, text(prop, src)));
            }
            case "subscript_expression" -> {
                TSNode idx = node.getChildByFieldName("index");
                if (idx != null && !idx.isNull() && idx.getType().equals("string"))
                    usages.add(site(idx, src, file, Shape.normalizeRaw(text(idx, src))));
            }
            default -> {}
        }
        for (int i = 0; i < node.getChildCount(); i++) walk(node.getChild(i), src, file, shapes, usages);
    }

    private void collectStringArray(TSNode array, byte[] src, String file, List<Shape> shapes) {
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < node_named(array); i++) {
            TSNode c = array.getNamedChild(i);
            if (c.getType().equals("string"))
                members.add(new Member(Shape.normalizeRaw(text(c, src)), file, line(c)));
        }
        if (members.size() >= 3)
            shapes.add(new Shape(idFor(array, src, file), ShapeKind.LIST, file, line(array), members));
    }

    private void collectUnion(TSNode union, byte[] src, String file, List<Shape> shapes) {
        TSNode parent = union.getParent();
        if (parent != null && !parent.isNull() && parent.getType().equals("union_type")) return;

        List<Member> members = new ArrayList<>();
        flattenUnion(union, src, file, members);
        if (members.size() >= 3)
            shapes.add(new Shape(idFor(union, src, file), ShapeKind.TRUTH, file, line(union), members));
    }

    private void flattenUnion(TSNode n, byte[] src, String file, List<Member> members) {
        if (n.getType().equals("literal_type")) {
            TSNode lit = n.getNamedChild(0);
            if (lit != null && !lit.isNull() && lit.getType().equals("string"))
                members.add(new Member(Shape.normalizeRaw(text(lit, src)), file, line(lit)));
            return;
        }
        for (int i = 0; i < node_named(n); i++) flattenUnion(n.getNamedChild(i), src, file, members);
    }

    private void collectInterface(TSNode decl, byte[] src, String file, List<Shape> shapes) {
        TSNode body = decl.getChildByFieldName("body");
        if (body == null || body.isNull()) return;
        List<Member> members = propertySignatures(body, src, file);
        if (members.size() >= 3)
            shapes.add(new Shape(idFor(decl, src, file), ShapeKind.TRUTH, file, line(decl), members));
    }

    /**
     * A bare object type — {@code type Foo = { a: string; b: number }}, or an inline annotation.
     * The interface form has its own case above (an {@code interface_declaration}'s body IS an
     * {@code object_type}), so that parent is skipped here to avoid emitting the shape twice.
     */
    private void collectObjectType(TSNode objectType, byte[] src, String file, List<Shape> shapes) {
        TSNode parent = objectType.getParent();
        if (parent != null && !parent.isNull() && parent.getType().equals("interface_declaration")) return;
        List<Member> members = propertySignatures(objectType, src, file);
        if (members.size() >= 3)
            shapes.add(new Shape(idFor(objectType, src, file), ShapeKind.TRUTH, file, line(objectType), members));
    }

    private List<Member> propertySignatures(TSNode body, byte[] src, String file) {
        List<Member> members = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < node_named(body); i++) {
            TSNode c = body.getNamedChild(i);
            if (!c.getType().equals("property_signature") && !c.getType().equals("method_signature")) continue;
            TSNode name = c.getChildByFieldName("name");
            if (name == null || name.isNull()) continue;
            String raw = Shape.normalizeRaw(text(name, src));
            if (seen.add(Shape.normalize(raw))) members.add(new Member(raw, file, line(name)));
        }
        return members;
    }

    /**
     * An object literal's key set — {@code { a: 1, b: 2, ...rest }}. Recorded as
     * {@link ShapeKind#LITERAL}: useful as one half of a near-identical twin pair, but never as the
     * subset half, because a value's keys are allowed to be a partial view of its type.
     */
    private void collectObjectLiteral(TSNode object, byte[] src, String file, List<Shape> shapes) {
        List<Member> members = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < node_named(object); i++) {
            TSNode c = object.getNamedChild(i);
            TSNode name = switch (c.getType()) {
                case "pair", "method_definition" -> c.getChildByFieldName("key") != null
                        && !c.getChildByFieldName("key").isNull()
                    ? c.getChildByFieldName("key") : c.getChildByFieldName("name");
                case "shorthand_property_identifier" -> c;
                default -> null;
            };
            if (name == null || name.isNull()) continue;
            String type = name.getType();
            if (!type.equals("property_identifier") && !type.equals("string")
                    && !type.equals("shorthand_property_identifier")) continue;
            String raw = Shape.normalizeRaw(text(name, src));
            if (seen.add(Shape.normalize(raw))) members.add(new Member(raw, file, line(name)));
        }
        if (members.size() >= 3)
            shapes.add(new Shape(idForNearest(object, src, file), ShapeKind.LITERAL, file, line(object), members));
    }

    /**
     * A destructuring pattern's key set — {@code const { a, b, c } = opts}, or a destructured
     * parameter. This is the shape a consumer expects its source to have, so an option the source
     * declares but no destructure ever names is drift the type checker cannot see.
     */
    private void collectObjectPattern(TSNode pattern, byte[] src, String file, List<Shape> shapes) {
        List<Member> members = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < node_named(pattern); i++) {
            TSNode c = pattern.getNamedChild(i);
            TSNode name = switch (c.getType()) {
                case "shorthand_property_identifier_pattern" -> c;
                case "pair_pattern" -> c.getChildByFieldName("key");
                case "object_assignment_pattern" -> c.getChildByFieldName("left");
                default -> null;
            };
            if (name == null || name.isNull()) continue;
            if (name.getType().equals("object_pattern") || name.getType().equals("array_pattern")) continue;
            String raw = Shape.normalizeRaw(text(name, src));
            if (raw.isEmpty() || !seen.add(Shape.normalize(raw))) continue;
            members.add(new Member(raw, file, line(name)));
        }
        if (members.size() >= 3)
            shapes.add(new Shape(idForNearest(pattern, src, file), ShapeKind.LIST, file, line(pattern), members));
    }

    // ---- helpers shared with JavaAdapter: keep signatures identical there ----
    /**
     * tree-sitter reports byte offsets into the UTF-8 encoding of the source, not char indices,
     * so the source is carried as bytes: slicing a Java String by those offsets corrupts (or
     * throws on) every file containing a non-ASCII character.
     */
    static String text(TSNode n, byte[] src) {
        int start = n.getStartByte();
        int end = Math.min(n.getEndByte(), src.length);
        if (start < 0 || start >= end) return "";
        return new String(src, start, end - start, StandardCharsets.UTF_8);
    }
    static int line(TSNode n) { return n.getStartPoint().getRow() + 1; }
    static int node_named(TSNode n) { return n.getNamedChildCount(); }
    static UsageSite site(TSNode n, byte[] src, String file, String member) { return new UsageSite(file, line(n), member); }
    static String idFor(TSNode node, byte[] src, String file) {
        // Check the node itself before walking up: `collectInterface` passes the
        // `interface_declaration` node directly, so it must be checked, not just its ancestors.
        TSNode p = node;
        while (p != null && !p.isNull()) {
            if (p.getType().equals("variable_declarator") || p.getType().equals("type_alias_declaration")
                || p.getType().equals("interface_declaration") || p.getType().equals("enum_declaration")) {
                TSNode name = p.getChildByFieldName("name");
                if (name != null && !name.isNull()) return text(name, src);
            }
            p = p.getParent();
        }
        return file + ":" + line(node);
    }

    /**
     * Like {@link #idFor} but also accepts the key of an enclosing object-literal entry, so an
     * anonymous object/destructure nested inside a big literal is named after its own slot
     * ({@code _combinedContextOptions}) rather than the outermost {@code const}.
     */
    static String idForNearest(TSNode node, byte[] src, String file) {
        TSNode p = node.getParent();
        while (p != null && !p.isNull()) {
            String t = p.getType();
            if (t.equals("pair") || t.equals("public_field_definition")) {
                TSNode key = p.getChildByFieldName("key");
                if (key == null || key.isNull()) key = p.getChildByFieldName("name");
                if (key != null && !key.isNull()) return Shape.normalizeRaw(text(key, src));
            }
            if (t.equals("variable_declarator") || t.equals("type_alias_declaration")
                || t.equals("interface_declaration")) {
                TSNode name = p.getChildByFieldName("name");
                // `const { a, b } = x` names the declarator with the pattern itself; that is not an id.
                if (name != null && !name.isNull() && name.getType().endsWith("identifier"))
                    return text(name, src);
            }
            p = p.getParent();
        }
        return file + ":" + line(node);
    }
}
