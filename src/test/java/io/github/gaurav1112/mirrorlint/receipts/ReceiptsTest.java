package io.github.gaurav1112.mirrorlint.receipts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.gaurav1112.mirrorlint.Main;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

/**
 * The acceptance gate: mirrorlint is run against real repositories pinned at SHAs where a real,
 * independently confirmed drift bug was present, and must rediscover it.
 *
 * <p>Two receipts, both from bugs reported upstream:
 * <ul>
 *   <li>vitest at the parent of the merge commit of #11102 — {@code maxWorkers} missing from the
 *       per-project CLI override allowlist.
 *   <li>playwright at main — {@code screen} missing from the test runner's context-option fixtures
 *       (issue #42679, unfixed).
 * </ul>
 *
 * <p>A receipt may also declare {@code expect_pair_defaults}, in which case rediscovery alone
 * isn't enough: the pair that found the bug must emit exactly that many DEFAULT findings in
 * total. vitest declares 1. Recovering the bug while also naming the forty members the allowlist
 * omits on purpose is not a detection, it's a coin flip with good PR.
 *
 * <p>The negative controls are the other half of the gate. Three vitest options that are consumed
 * only at root level must NOT be reported as drift in the very same scan: a tool that flags
 * everything rediscovers everything, which proves nothing.
 *
 * <p>Excluded from the default build ({@code @Tag("receipts")}); run with
 * {@code mvn test -Preceipts -Dtest=ReceiptsTest}. Needs network on the first run.
 */
@Tag("receipts")
class ReceiptsTest {

    private static final Path RECEIPTS = Path.of("receipts");
    private static final Map<String, JsonArray> SCAN_CACHE = new HashMap<>();

    private static JsonObject manifest;
    private static String fetchFailure;

    @BeforeAll
    static void fetchCorpus() throws Exception {
        manifest = JsonParser.parseString(Files.readString(RECEIPTS.resolve("manifest.json")))
                .getAsJsonObject();

        Process process = new ProcessBuilder("bash", "fetch.sh")
                .directory(RECEIPTS.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(15, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            fetchFailure = "receipts/fetch.sh timed out";
        } else if (process.exitValue() != 0) {
            fetchFailure = "receipts/fetch.sh failed (exit " + process.exitValue() + "):\n" + output;
        }
        System.out.print(output);
    }

    @TestFactory
    List<DynamicTest> rediscoversKnownDriftAtPinnedShas() {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonElement element : manifest.getAsJsonArray("receipts")) {
            JsonObject receipt = element.getAsJsonObject();
            String name = receipt.get("repo").getAsString() + " → " + receipt.get("expect_member").getAsString();
            tests.add(DynamicTest.dynamicTest(name, () -> assertRediscovered(receipt)));
        }
        return tests;
    }

    @TestFactory
    List<DynamicTest> keepsRootOnlyOptionsQuiet() {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonElement element : manifest.getAsJsonArray("negative_controls")) {
            JsonObject control = element.getAsJsonObject();
            String member = control.get("member_never_default").getAsString();
            String name = control.get("repo").getAsString() + " ∌ " + member;
            tests.add(DynamicTest.dynamicTest(name, () -> assertQuiet(control, member)));
        }
        return tests;
    }

    private void assertRediscovered(JsonObject receipt) throws Exception {
        JsonArray findings = scanFor(receipt);
        String member = receipt.get("expect_member").getAsString();
        String mirrorContains = receipt.get("expect_mirror_contains").getAsString();

        List<JsonObject> matches = findings.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(f -> "DEFAULT".equals(f.get("severity").getAsString()))
                .filter(f -> memberName(f).equalsIgnoreCase(member))
                .filter(f -> mirrorFile(f).contains(mirrorContains))
                .toList();

        System.out.printf("receipts: %s @ %s — %d findings, %d DEFAULT, %d matching '%s' in a mirror under '%s'%n",
                receipt.get("repo").getAsString(), shortSha(receipt), findings.size(),
                defaultCount(findings), matches.size(), member, mirrorContains);
        matches.forEach(f -> System.out.println("          " + describe(f)));

        assertThat(matches)
                .describedAs("DEFAULT finding for '%s' whose mirror file contains '%s' in %s@%s",
                        member, mirrorContains, receipt.get("repo").getAsString(), shortSha(receipt))
                .isNotEmpty();

        if (receipt.has("expect_pair_defaults")) {
            assertPairIsPrecise(receipt, findings, matches.get(0), member);
        }
    }

    /**
     * The precision half of the receipt: rediscovering the bug is worth nothing if the same pair
     * also names every other member it omits. Counts the DEFAULT findings the <em>winning pair</em>
     * emits — the pair is identified from the matched finding itself, so no extra manifest
     * bookkeeping can drift out of sync — and demands the declared number, all naming the member.
     */
    private void assertPairIsPrecise(JsonObject receipt, JsonArray findings, JsonObject match, String member) {
        int expected = receipt.get("expect_pair_defaults").getAsInt();
        String pair = pairKey(match);

        List<JsonObject> fromPair = findings.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(f -> "DEFAULT".equals(f.get("severity").getAsString()))
                .filter(f -> pairKey(f).equals(pair))
                .toList();

        System.out.printf("receipts: winning pair %s — %d DEFAULT (expected %d)%n",
                pair, fromPair.size(), expected);
        fromPair.forEach(f -> System.out.println("          " + describe(f)));

        assertThat(fromPair)
                .describedAs("the winning pair %s in %s@%s must emit exactly %d DEFAULT finding(s); got %s",
                        pair, receipt.get("repo").getAsString(), shortSha(receipt), expected,
                        fromPair.stream().map(ReceiptsTest::memberName).toList())
                .hasSize(expected);
        assertThat(fromPair).extracting(ReceiptsTest::memberName).containsOnly(member.toLowerCase(Locale.ROOT));
    }

    private static String pairKey(JsonObject finding) {
        JsonObject pair = finding.getAsJsonObject("pair");
        return "%s[%s:%d] → %s[%s:%d]".formatted(
                pair.getAsJsonObject("truth").get("id").getAsString(),
                pair.getAsJsonObject("truth").get("file").getAsString(),
                pair.getAsJsonObject("truth").get("line").getAsInt(),
                pair.getAsJsonObject("mirror").get("id").getAsString(),
                pair.getAsJsonObject("mirror").get("file").getAsString(),
                pair.getAsJsonObject("mirror").get("line").getAsInt());
    }

    private void assertQuiet(JsonObject control, String member) throws Exception {
        JsonArray findings = scanFor(control);

        List<JsonObject> offenders = findings.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(f -> "DEFAULT".equals(f.get("severity").getAsString()))
                .filter(f -> memberName(f).equalsIgnoreCase(member))
                .toList();

        assertThat(offenders)
                .describedAs("'%s' is consumed only at root level, so no DEFAULT finding may name it; got: %s",
                        member, offenders.stream().map(ReceiptsTest::describe).toList())
                .isEmpty();
    }

    /** One scan per (repo, sha, subpath); the negative controls read the receipt's own scan. */
    private JsonArray scanFor(JsonObject entry) throws Exception {
        Path root = cacheDir(entry).resolve(entry.get("subpath").getAsString());
        int minShared = entry.has("min_shared") ? entry.get("min_shared").getAsInt() : minSharedOfReceipt(entry);
        String key = root + "|" + minShared;

        JsonArray cached = SCAN_CACHE.get(key);
        if (cached != null) return cached;

        assumeTrue(fetchFailure == null, () -> "receipts corpus unavailable (network?): " + fetchFailure);
        assumeTrue(Files.isDirectory(root), () -> "receipts corpus not checked out at " + root);

        Path config = Files.createTempFile("mirrorlint-receipt", ".toml");
        Files.writeString(config, "min_shared = " + minShared + "\n");

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        long started = System.nanoTime();
        try (PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            Main.run(new String[] {"scan", root.toString(), "--format", "json", "--all",
                    "--config", config.toString()}, out);
        }
        Files.deleteIfExists(config);
        long millis = (System.nanoTime() - started) / 1_000_000;

        JsonArray findings = JsonParser.parseString(buffer.toString(StandardCharsets.UTF_8)).getAsJsonArray();
        System.out.printf("receipts: scanned %s (min_shared=%d) in %d ms%n", root, minShared, millis);
        SCAN_CACHE.put(key, findings);
        return findings;
    }

    /** A control names no thresholds of its own; it must be judged by the receipt's scan. */
    private int minSharedOfReceipt(JsonObject control) {
        for (JsonElement element : manifest.getAsJsonArray("receipts")) {
            JsonObject receipt = element.getAsJsonObject();
            if (receipt.get("repo").getAsString().equals(control.get("repo").getAsString())
                    && receipt.get("sha").getAsString().equals(control.get("sha").getAsString())
                    && receipt.get("subpath").getAsString().equals(control.get("subpath").getAsString())) {
                return receipt.get("min_shared").getAsInt();
            }
        }
        throw new IllegalStateException("negative control has no matching receipt scan: " + control);
    }

    private static Path cacheDir(JsonObject entry) {
        String repo = entry.get("repo").getAsString().replace("/", "__");
        return RECEIPTS.resolve("cache").resolve(repo + "-" + shortSha(entry));
    }

    private static String shortSha(JsonObject entry) {
        return entry.get("sha").getAsString().substring(0, 8);
    }

    private static long defaultCount(JsonArray findings) {
        return findings.asList().stream()
                .filter(f -> "DEFAULT".equals(f.getAsJsonObject().get("severity").getAsString()))
                .count();
    }

    private static String memberName(JsonObject finding) {
        return finding.getAsJsonObject("member").get("name").getAsString().toLowerCase(Locale.ROOT);
    }

    private static String mirrorFile(JsonObject finding) {
        return finding.getAsJsonObject("pair").getAsJsonObject("mirror").get("file").getAsString();
    }

    private static String describe(JsonObject finding) {
        JsonObject pair = finding.getAsJsonObject("pair");
        JsonObject truth = pair.getAsJsonObject("truth");
        JsonObject mirror = pair.getAsJsonObject("mirror");
        return "%s: %s [%s:%d] → %s [%s:%d]".formatted(
                finding.getAsJsonObject("member").get("name").getAsString(),
                truth.get("id").getAsString(), truth.get("file").getAsString(), truth.get("line").getAsInt(),
                mirror.get("id").getAsString(), mirror.get("file").getAsString(), mirror.get("line").getAsInt());
    }
}
