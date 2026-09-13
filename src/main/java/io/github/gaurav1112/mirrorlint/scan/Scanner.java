package io.github.gaurav1112.mirrorlint.scan;

import io.github.gaurav1112.mirrorlint.config.Config;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Scanner {
    private static final Pattern SUPPRESSION_PATTERN = Pattern.compile("mirrorlint:ignore (\\S+)");

    private final List<LanguageAdapter> adapters;
    private final PairMiner miner;
    private final Verifier verifier;
    private final List<PathMatcher> excludeMatchers;
    private final List<Config.DeclaredPair> declaredPairs;

    public Scanner(List<LanguageAdapter> adapters, PairMiner miner, Verifier verifier, List<String> excludeGlobs) {
        this(adapters, miner, verifier, excludeGlobs, List.of());
    }

    public Scanner(List<LanguageAdapter> adapters, PairMiner miner, Verifier verifier, List<String> excludeGlobs,
            List<Config.DeclaredPair> declaredPairs) {
        this.adapters = adapters;
        this.miner = miner;
        this.verifier = verifier;
        this.declaredPairs = declaredPairs;
        this.excludeMatchers = excludeGlobs.stream()
            .flatMap(g -> g.startsWith("**/")
                ? java.util.stream.Stream.of(g, g.substring(3))
                : java.util.stream.Stream.of(g))
            .map(g -> FileSystems.getDefault().getPathMatcher("glob:" + g))
            .toList();
    }

    /** Convenience constructor: builds the miner/verifier/excludes/declaredPairs straight from a loaded Config. */
    public Scanner(List<LanguageAdapter> adapters, Config config) {
        this(adapters,
            new PairMiner(config.minJaccard(), config.minShared(), config.minContainment(),
                config.minSubsetJaccard()),
            new Verifier(config.minScore()),
            config.excludes(),
            config.declaredPairs());
    }

    public ScanResult scan(Path root) {
        List<Shape> shapes = new ArrayList<>();
        List<UsageSite> usages = new ArrayList<>();
        Set<Suppression> suppressions = new HashSet<>();
        int filesScanned = 0;

        boolean singleFile = Files.isRegularFile(root);
        List<Path> files;
        try {
            files = singleFile ? List.of(root) : walkDirectory(root);
        } catch (IOException e) {
            throw new UncheckedScanException(e);
        }

        for (Path path : files) {
            // A single-file root has nothing to relativize against (root.relativize(root) is
            // "", which no adapter's handles() matches) — dispatch on the file's own name instead.
            String relative = singleFile
                    ? root.getFileName().toString()
                    : root.relativize(path).toString().replace('\\', '/');
            if (isExcluded(relative)) continue;

            LanguageAdapter adapter = adapterFor(relative);
            if (adapter == null) continue;

            try {
                String source = Files.readString(path);
                FileFacts facts = adapter.extract(relative, source);
                shapes.addAll(facts.shapes());
                usages.addAll(facts.usages());
                collectSuppressions(relative, source, suppressions);
                filesScanned++;
            } catch (IOException | RuntimeException e) {
                System.err.println("mirrorlint: skipping unreadable/unparseable file " + relative + ": " + e.getMessage());
            }
        }

        List<Pair> pairs = new ArrayList<>(miner.mine(shapes));
        addDeclaredPairs(shapes, pairs);

        List<Finding> findings = new ArrayList<>();
        Differ differ = new Differ();
        for (Pair pair : pairs) {
            Differ.DiffResult diff = differ.diff(pair);
            findings.addAll(verifier.verify(pair, diff, usages));
        }

        findings.removeIf(f -> suppressions.contains(
            new Suppression(f.pair().mirror().file(), Shape.normalize(f.member().name()))));

        findings.sort(Comparator
            .comparing((Finding f) -> f.pair().truth().file())
            .thenComparingInt(f -> f.pair().truth().line())
            .thenComparing(Finding::severity)
            .thenComparing(f -> f.member().name()));

        return new ScanResult(findings, filesScanned);
    }

    private List<Path> walkDirectory(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).toList();
        }
    }

    private void collectSuppressions(String relativeFile, String source, Set<Suppression> suppressions) {
        for (String line : source.split("\n", -1)) {
            Matcher matcher = SUPPRESSION_PATTERN.matcher(line);
            if (matcher.find()) {
                suppressions.add(new Suppression(relativeFile, Shape.normalize(matcher.group(1))));
            }
        }
    }

    private void addDeclaredPairs(List<Shape> shapes, List<Pair> pairs) {
        for (Config.DeclaredPair declared : declaredPairs) {
            Shape truthShape = findShape(shapes, declared.truthFile(), declared.truthId());
            Shape mirrorShape = findShape(shapes, declared.mirrorFile(), declared.mirrorId());
            if (truthShape == null || mirrorShape == null) continue;

            boolean alreadyMined = pairs.stream()
                .anyMatch(p -> p.truth().equals(truthShape) && p.mirror().equals(mirrorShape));
            if (alreadyMined) continue;

            pairs.add(new Pair(truthShape, mirrorShape, jaccard(truthShape, mirrorShape)));
        }
    }

    private Shape findShape(List<Shape> shapes, String file, String id) {
        for (Shape shape : shapes) {
            if (shape.file().equals(file) && shape.id().equals(id)) return shape;
        }
        return null;
    }

    private double jaccard(Shape a, Shape b) {
        Set<String> an = a.memberNames(), bn = b.memberNames();
        Set<String> inter = new HashSet<>(an); inter.retainAll(bn);
        Set<String> union = new HashSet<>(an); union.addAll(bn);
        return union.isEmpty() ? 0.0 : (double) inter.size() / union.size();
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

    private record Suppression(String file, String member) {}

    private static final class UncheckedScanException extends RuntimeException {
        UncheckedScanException(IOException cause) {
            super(cause);
        }
    }
}
