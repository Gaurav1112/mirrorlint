package io.github.gaurav1112.mirrorlint.lang;

import io.github.gaurav1112.mirrorlint.core.Shape;
import io.github.gaurav1112.mirrorlint.core.UsageSite;
import java.util.List;

public record FileFacts(List<Shape> shapes, List<UsageSite> usages) {}
