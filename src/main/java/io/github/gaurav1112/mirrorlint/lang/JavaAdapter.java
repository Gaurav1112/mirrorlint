package io.github.gaurav1112.mirrorlint.lang;

import io.github.gaurav1112.mirrorlint.core.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.treesitter.TSNode;

public class JavaAdapter implements LanguageAdapter {
    @Override public boolean handles(String filename) {
        return filename.endsWith(".java");
    }

    @Override public FileFacts extract(String filename, String source) {
        TSNode root = TreeSitters.java_().parseString(null, source).getRootNode();
        byte[] src = source.getBytes(StandardCharsets.UTF_8);
        List<Shape> shapes = new ArrayList<>();
        List<UsageSite> usages = new ArrayList<>();
        walk(root, src, filename, shapes, usages);
        return new FileFacts(shapes, usages);
    }

    private void walk(TSNode node, byte[] src, String file, List<Shape> shapes, List<UsageSite> usages) {
        String type = node.getType();
        switch (type) {
            case "array_initializer" -> collectStringArrayInitializer(node, src, file, shapes);
            case "method_invocation" -> collectListOfInvocation(node, src, file, shapes);
            case "enum_declaration" -> collectEnum(node, src, file, shapes);
            case "interface_declaration" -> collectInterface(node, src, file, shapes);
            case "field_access" -> {
                TSNode fieldNode = node.getChildByFieldName("field");
                if (fieldNode != null && !fieldNode.isNull())
                    usages.add(TypeScriptAdapter.site(fieldNode, src, file, TypeScriptAdapter.text(fieldNode, src)));
            }
            case "switch_label" -> collectSwitchLabelUsages(node, src, file, usages);
            default -> {}
        }
        for (int i = 0; i < node.getChildCount(); i++) walk(node.getChild(i), src, file, shapes, usages);
    }

    private void collectStringArrayInitializer(TSNode array, byte[] src, String file, List<Shape> shapes) {
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < TypeScriptAdapter.node_named(array); i++) {
            TSNode c = array.getNamedChild(i);
            if (c.getType().equals("string_literal"))
                members.add(new Member(Shape.normalizeRaw(TypeScriptAdapter.text(c, src)), file, TypeScriptAdapter.line(c)));
        }
        if (members.size() >= 3)
            shapes.add(new Shape(TypeScriptAdapter.idFor(array, src, file), ShapeKind.LIST, file, TypeScriptAdapter.line(array), members));
    }

    private void collectListOfInvocation(TSNode invocation, byte[] src, String file, List<Shape> shapes) {
        TSNode objectNode = invocation.getChildByFieldName("object");
        TSNode nameNode = invocation.getChildByFieldName("name");
        if (objectNode == null || objectNode.isNull() || nameNode == null || nameNode.isNull()) return;
        String object = TypeScriptAdapter.text(objectNode, src);
        String name = TypeScriptAdapter.text(nameNode, src);
        if (!(object.equals("List") || object.equals("Set")) || !name.equals("of")) return;
        TSNode argsNode = invocation.getChildByFieldName("arguments");
        if (argsNode == null || argsNode.isNull()) return;
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < TypeScriptAdapter.node_named(argsNode); i++) {
            TSNode c = argsNode.getNamedChild(i);
            if (c.getType().equals("string_literal"))
                members.add(new Member(Shape.normalizeRaw(TypeScriptAdapter.text(c, src)), file, TypeScriptAdapter.line(c)));
        }
        if (members.size() >= 3)
            shapes.add(new Shape(TypeScriptAdapter.idFor(invocation, src, file), ShapeKind.LIST, file, TypeScriptAdapter.line(invocation), members));
    }

    private void collectEnum(TSNode decl, byte[] src, String file, List<Shape> shapes) {
        TSNode body = decl.getChildByFieldName("body");
        if (body == null || body.isNull()) return;
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < TypeScriptAdapter.node_named(body); i++) {
            TSNode c = body.getNamedChild(i);
            if (c.getType().equals("enum_constant")) {
                TSNode name = c.getChildByFieldName("name");
                if (name != null && !name.isNull()) members.add(new Member(TypeScriptAdapter.text(name, src), file, TypeScriptAdapter.line(name)));
            }
        }
        if (members.size() >= 3)
            shapes.add(new Shape(TypeScriptAdapter.idFor(decl, src, file), ShapeKind.TRUTH, file, TypeScriptAdapter.line(decl), members));
    }

    private void collectInterface(TSNode decl, byte[] src, String file, List<Shape> shapes) {
        TSNode body = decl.getChildByFieldName("body");
        if (body == null || body.isNull()) return;
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < TypeScriptAdapter.node_named(body); i++) {
            TSNode c = body.getNamedChild(i);
            if (c.getType().equals("method_declaration")) {
                TSNode name = c.getChildByFieldName("name");
                if (name != null && !name.isNull()) members.add(new Member(TypeScriptAdapter.text(name, src), file, TypeScriptAdapter.line(name)));
            }
        }
        if (members.size() >= 3)
            shapes.add(new Shape(TypeScriptAdapter.idFor(decl, src, file), ShapeKind.TRUTH, file, TypeScriptAdapter.line(decl), members));
    }

    private void collectSwitchLabelUsages(TSNode label, byte[] src, String file, List<UsageSite> usages) {
        for (int i = 0; i < TypeScriptAdapter.node_named(label); i++) {
            TSNode c = label.getNamedChild(i);
            if (c.getType().equals("string_literal"))
                usages.add(TypeScriptAdapter.site(c, src, file, Shape.normalizeRaw(TypeScriptAdapter.text(c, src))));
        }
    }
}
