package io.github.gaurav1112.mirrorlint.lang;

import org.junit.jupiter.api.Test;
import org.treesitter.*;
import static org.assertj.core.api.Assertions.assertThat;

class TreeSitterSpikeTest {

    @Test
    void parsesTypeScriptArrayLiteral() {
        TSParser parser = new TSParser();
        parser.setLanguage(new TreeSitterTypescript());
        TSTree tree = parser.parseString(null, "const KEYS = ['alpha', 'beta'] as const;");
        TSNode root = tree.getRootNode();
        assertThat(root.hasError()).isFalse();
        assertThat(root.toString()).contains("array");
    }

    @Test
    void parsesJavaStringArray() {
        TSParser parser = new TSParser();
        parser.setLanguage(new TreeSitterJava());
        TSTree tree = parser.parseString(null,
            "class C { static final String[] KEYS = {\"alpha\", \"beta\"}; }");
        assertThat(tree.getRootNode().hasError()).isFalse();
    }
}
