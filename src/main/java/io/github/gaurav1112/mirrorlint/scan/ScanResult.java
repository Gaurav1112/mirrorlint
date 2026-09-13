package io.github.gaurav1112.mirrorlint.scan;

import io.github.gaurav1112.mirrorlint.core.Finding;
import java.util.List;

public record ScanResult(List<Finding> findings, int filesScanned) {}
