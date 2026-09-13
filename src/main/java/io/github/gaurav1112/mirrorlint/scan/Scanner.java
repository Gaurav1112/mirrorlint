package io.github.gaurav1112.mirrorlint.scan;

import io.github.gaurav1112.mirrorlint.core.Differ;
import io.github.gaurav1112.mirrorlint.core.Finding;
import io.github.gaurav1112.mirrorlint.core.Pair;
import io.github.gaurav1112.mirrorlint.core.PairMiner;
import io.github.gaurav1112.mirrorlint.core.Shape;
import io.github.gaurav1112.mirrorlint.core.UsageSite;
import io.github.gaurav1112.mirrorlint.core.Verifier;
import io.github.gaurav1112.mirrorlint.lang.FileFacts;
import io.github.gaurav1112.mirrorlint.lang.LanguageAdapter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Scanner {
    private final List<LanguageAdapter> adapters;
    private final PairMiner miner;
    private final Verifier verifier;
    private final List<PathMatcher> excludeMatchers;

    public Scanner(List<LanguageAdapter> adapters, PairMiner miner, Verifier verifier, List<String> excludeGlobs) {
        this.adapters = adapters;
        this.miner = miner;
        this.verifier = verifier;
        this.excludeMatchers = excludeGlobs.stream()
            .flatMap(g -> g.startsWith("**/")
                ? java.util.stream.Stream.of(g, g.substring(3))
                : java.util.stream.Stream.of(g))
            .map(g -> FileSystems.getDefault().getPathMatcher("glob:" + g))
            .toList();
    }

    public ScanResult scan(Path root) {
        List<Shape> shapes = new ArrayList<>();
        List<UsageSite> usages = new ArrayList<>();
        int filesScanned = 0;

        try (var paths = Files.walk(root)) {
            List<Path> files = paths.filter(Files::isRegularFile).toList();
            for (Path path : files) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (isExcluded(relative)) continue;

                LanguageAdapter adapter = adapterFor(relative);
                if (adapter == null) continue;

                try {
                    String source = Files.readString(path);
                    FileFacts facts = adapter.extract(relative, source);
                    shapes.addAll(facts.shapes());
                    usages.addAll(facts.usages());
                    filesScanned++;
                } catch (IOException | RuntimeException e) {
                    System.err.println("mirrorlint: skipping unreadable/unparseable file " + relative + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new UncheckedScanException(e);
        }

        List<Finding> findings = new ArrayList<>();
        Differ differ = new Differ();
        for (Pair pair : miner.mine(shapes)) {
            Differ.DiffResult diff = differ.diff(pair);
            findings.addAll(verifier.verify(pair, diff, usages));
        }

        findings.sort(Comparator
            .comparing((Finding f) -> f.pair().truth().file())
            .thenComparingInt(f -> f.pair().truth().line())
            .thenComparing(Finding::severity)
            .thenComparing(f -> f.member().name()));

        return new ScanResult(findings, filesScanned);
    }

    private LanguageAdapter adapterFor(String relativePath) {
        for (LanguageAdapter adapter : adapters) {
            if (adapter.handles(relativePath)) return adapter;
        }
        return null;
    }

    private boolean isExcluded(String relativePath) {
        Path relPath = Path.of(relativePath);
        for (PathMatcher matcher : excludeMatchers) {
            if (matcher.matches(relPath)) return true;
        }
        return false;
    }

    private static final class UncheckedScanException extends RuntimeException {
        UncheckedScanException(IOException cause) {
            super(cause);
        }
    }
}
