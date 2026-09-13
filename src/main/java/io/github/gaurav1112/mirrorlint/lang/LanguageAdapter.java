package io.github.gaurav1112.mirrorlint.lang;

public interface LanguageAdapter {
    boolean handles(String filename);
    FileFacts extract(String filename, String source);
}
