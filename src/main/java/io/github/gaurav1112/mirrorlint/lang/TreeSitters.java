package io.github.gaurav1112.mirrorlint.lang;

import org.treesitter.*;

// Grammar class names pinned by Task 1 spike; update BOTH places if the spike changed them.
public final class TreeSitters {
    private TreeSitters() {}
    public static TSParser typescript() { TSParser p = new TSParser(); p.setLanguage(new TreeSitterTypescript()); return p; }
    public static TSParser java_() { TSParser p = new TSParser(); p.setLanguage(new TreeSitterJava()); return p; }
}
