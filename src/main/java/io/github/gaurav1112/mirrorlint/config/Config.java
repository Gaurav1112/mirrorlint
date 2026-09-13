package io.github.gaurav1112.mirrorlint.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Project-level tuning: thresholds, extra exclude globs (added to the built-in defaults,
 * never replacing them), and manually declared truth/mirror pairs that bypass mining thresholds.
 */
public record Config(
        double minJaccard,
        int minShared,
        double minScore,
        List<String> excludes,
        List<DeclaredPair> declaredPairs) {

    public record DeclaredPair(String truthFile, String truthId, String mirrorFile, String mirrorId) {}

    public static final double DEFAULT_MIN_JACCARD = 0.6;
    public static final int DEFAULT_MIN_SHARED = 4;
    public static final double DEFAULT_MIN_SCORE = 0.5;
    public static final List<String> DEFAULT_EXCLUDES = List.of(
            "**/test/**", "**/tests/**", "**/__tests__/**", "**/node_modules/**",
            "**/target/**", "**/build/**", "**/dist/**", "**/*.spec.*", "**/*.test.*");

    /** Loads config from {@code tomlPath}, or returns all defaults when it is null or absent. */
    public static Config load(Path tomlPath) {
        if (tomlPath == null || !Files.exists(tomlPath)) {
            return defaults();
        }

        TomlParseResult result;
        try {
            result = Toml.parse(tomlPath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (result.hasErrors()) {
            throw new IllegalArgumentException("invalid TOML in " + tomlPath + ": " + result.errors());
        }

        double minJaccard = result.contains("min_jaccard") ? result.getDouble("min_jaccard") : DEFAULT_MIN_JACCARD;
        int minShared = result.contains("min_shared") ? result.getLong("min_shared").intValue() : DEFAULT_MIN_SHARED;
        double minScore = result.contains("min_score") ? result.getDouble("min_score") : DEFAULT_MIN_SCORE;

        List<String> excludes = new ArrayList<>(DEFAULT_EXCLUDES);
        TomlArray excludesArray = result.getArrayOrEmpty("excludes");
        for (int i = 0; i < excludesArray.size(); i++) {
            excludes.add(excludesArray.getString(i));
        }

        List<DeclaredPair> declaredPairs = new ArrayList<>();
        TomlArray pairsArray = result.getArrayOrEmpty("pairs");
        for (int i = 0; i < pairsArray.size(); i++) {
            TomlTable pairTable = pairsArray.getTable(i);
            declaredPairs.add(new DeclaredPair(
                    pairTable.getString("truth_file"),
                    pairTable.getString("truth_id"),
                    pairTable.getString("mirror_file"),
                    pairTable.getString("mirror_id")));
        }

        return new Config(minJaccard, minShared, minScore, List.copyOf(excludes), List.copyOf(declaredPairs));
    }

    private static Config defaults() {
        return new Config(DEFAULT_MIN_JACCARD, DEFAULT_MIN_SHARED, DEFAULT_MIN_SCORE, DEFAULT_EXCLUDES, List.of());
    }
}
