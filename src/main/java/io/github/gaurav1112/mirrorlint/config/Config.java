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
        double minContainment,
        double minSubsetJaccard,
        List<String> excludes,
        List<DeclaredPair> declaredPairs) {

    public record DeclaredPair(String truthFile, String truthId, String mirrorFile, String mirrorId) {}

    public static final double DEFAULT_MIN_JACCARD = 0.6;
    public static final int DEFAULT_MIN_SHARED = 4;
    public static final double DEFAULT_MIN_SCORE = 0.5;
    /** See {@code PairMiner} — the subset-mirror rule, which jaccard alone cannot express. */
    public static final double DEFAULT_MIN_CONTAINMENT = 0.8;
    /** The lower jaccard floor a subset mirror must still clear. See {@code PairMiner}. */
    public static final double DEFAULT_MIN_SUBSET_JACCARD = 0.3;
    public static final List<String> DEFAULT_EXCLUDES = List.of(
            "**/test/**", "**/tests/**", "**/__tests__/**", "**/node_modules/**",
            "**/target/**", "**/build/**", "**/dist/**", "**/*.spec.*", "**/*.test.*");

    /**
     * Loads config from {@code tomlPath}, or returns all defaults when it is {@code null} (no
     * {@code --config} flag given at all). A non-null path that doesn't exist is a user error —
     * it throws {@link IllegalArgumentException} rather than silently falling back to defaults,
     * so a typo'd {@code --config} path fails loudly instead of scanning with the wrong
     * thresholds and reporting success.
     *
     * @throws IllegalArgumentException if {@code tomlPath} is non-null but doesn't exist, the
     *     TOML is syntactically invalid, or a value has the wrong type (e.g. {@code min_jaccard}
     *     given as an integer instead of a float)
     */
    public static Config load(Path tomlPath) {
        if (tomlPath == null) {
            return defaults();
        }
        if (!Files.exists(tomlPath)) {
            throw new IllegalArgumentException("config file not found: " + tomlPath);
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

        try {
            double minJaccard =
                    result.contains("min_jaccard") ? result.getDouble("min_jaccard") : DEFAULT_MIN_JACCARD;
            int minShared =
                    result.contains("min_shared") ? result.getLong("min_shared").intValue() : DEFAULT_MIN_SHARED;
            double minScore = result.contains("min_score") ? result.getDouble("min_score") : DEFAULT_MIN_SCORE;
            double minContainment = result.contains("min_containment")
                    ? result.getDouble("min_containment") : DEFAULT_MIN_CONTAINMENT;
            double minSubsetJaccard = result.contains("min_subset_jaccard")
                    ? result.getDouble("min_subset_jaccard") : DEFAULT_MIN_SUBSET_JACCARD;

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

            return new Config(minJaccard, minShared, minScore, minContainment, minSubsetJaccard,
                    List.copyOf(excludes), List.copyOf(declaredPairs));
        } catch (RuntimeException e) {
            // Catches org.tomlj.TomlInvalidTypeException (e.g. `min_jaccard = 1`, an integer
            // where a float is required) and any other value-extraction failure, so every
            // malformed-config path — syntax or type — surfaces as an IllegalArgumentException
            // instead of an internal exception type escaping the load() contract.
            throw new IllegalArgumentException("invalid value in " + tomlPath + ": " + e.getMessage(), e);
        }
    }

    private static Config defaults() {
        return new Config(DEFAULT_MIN_JACCARD, DEFAULT_MIN_SHARED, DEFAULT_MIN_SCORE,
                DEFAULT_MIN_CONTAINMENT, DEFAULT_MIN_SUBSET_JACCARD, DEFAULT_EXCLUDES, List.of());
    }
}
