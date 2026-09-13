package io.github.gaurav1112.mirrorlint.core;

import java.util.List;

public record Finding(
    Pair pair, Member member, Severity severity, boolean omission, List<UsageSite> evidence,
    List<UsageSite> otherSites) {}
