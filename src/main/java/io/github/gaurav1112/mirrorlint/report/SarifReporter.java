package io.github.gaurav1112.mirrorlint.report;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.gaurav1112.mirrorlint.core.Finding;
import io.github.gaurav1112.mirrorlint.core.Pair;
import io.github.gaurav1112.mirrorlint.core.Severity;
import io.github.gaurav1112.mirrorlint.scan.ScanResult;
import java.util.List;

/**
 * Renders a {@link ScanResult} as a minimal valid SARIF 2.1.0 document, one result per
 * {@link Severity#DEFAULT} finding. Built as a Gson {@link JsonObject} tree — never string
 * templating — so the output is always well-formed JSON.
 */
public class SarifReporter {
    private static final Gson GSON = new Gson();

    private final HumanReporter humanReporter = new HumanReporter();

    public String render(ScanResult result) {
        JsonArray rules = new JsonArray();
        JsonObject rule = new JsonObject();
        rule.addProperty("id", "mirror-drift");
        rules.add(rule);

        JsonObject driver = new JsonObject();
        driver.addProperty("name", "mirrorlint");
        driver.add("rules", rules);

        JsonObject tool = new JsonObject();
        tool.add("driver", driver);

        JsonArray results = new JsonArray();
        for (Finding finding : result.findings()) {
            if (finding.severity() == Severity.DEFAULT) {
                results.add(toResult(finding));
            }
        }

        JsonObject run = new JsonObject();
        run.add("tool", tool);
        run.add("results", results);

        JsonArray runs = new JsonArray();
        runs.add(run);

        JsonObject root = new JsonObject();
        root.addProperty("version", "2.1.0");
        root.addProperty("$schema", "https://json.schemastore.org/sarif-2.1.0.json");
        root.add("runs", runs);

        return GSON.toJson(root);
    }

    private JsonObject toResult(Finding finding) {
        Pair pair = finding.pair();

        JsonObject message = new JsonObject();
        message.addProperty("text", firstLine(finding));

        JsonObject artifactLocation = new JsonObject();
        artifactLocation.addProperty("uri", pair.mirror().file());

        JsonObject region = new JsonObject();
        region.addProperty("startLine", pair.mirror().line());

        JsonObject physicalLocation = new JsonObject();
        physicalLocation.add("artifactLocation", artifactLocation);
        physicalLocation.add("region", region);

        JsonObject location = new JsonObject();
        location.add("physicalLocation", physicalLocation);

        JsonArray locations = new JsonArray();
        locations.add(location);

        JsonObject result = new JsonObject();
        result.addProperty("ruleId", "mirror-drift");
        result.addProperty("level", "warning");
        result.add("message", message);
        result.add("locations", locations);
        return result;
    }

    /** The message text is the human report's first line for this single finding. */
    private String firstLine(Finding finding) {
        String rendered = humanReporter.render(new ScanResult(List.of(finding), 1), true);
        return rendered.lines().findFirst().orElse("");
    }
}
