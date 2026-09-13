package io.github.gaurav1112.mirrorlint.lang;

import io.github.gaurav1112.mirrorlint.core.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < node_named(body); i++) {
            TSNode c = body.getNamedChild(i);
            if (c.getType().equals("property_signature")) {
                TSNode name = c.getChildByFieldName("name");
                if (name != null && !name.isNull()) members.add(new Member(text(name, src), file, line(name)));
            }
        }
        if (members.size() >= 3)
            shapes.add(new Shape(idFor(decl, src, file), ShapeKind.TRUTH, file, line(decl), members));
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
        TSNode p = node.getParent();
        while (p != null && !p.isNull()) {
            if (p.getType().equals("variable_declarator") || p.getType().equals("type_alias_declaration")
                || p.getType().equals("interface_declaration")) {
                TSNode name = p.getChildByFieldName("name");
                if (name != null && !name.isNull()) return text(name, src);
            }
            p = p.getParent();
        }
        return file + ":" + line(node);
    }
}
