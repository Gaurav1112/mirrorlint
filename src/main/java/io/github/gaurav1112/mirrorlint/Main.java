package io.github.gaurav1112.mirrorlint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.gaurav1112.mirrorlint.config.Config;
import io.github.gaurav1112.mirrorlint.core.Severity;
import io.github.gaurav1112.mirrorlint.lang.JavaAdapter;
import io.github.gaurav1112.mirrorlint.lang.LanguageAdapter;
import io.github.gaurav1112.mirrorlint.lang.TypeScriptAdapter;
import io.github.gaurav1112.mirrorlint.report.HumanReporter;
import io.github.gaurav1112.mirrorlint.report.SarifReporter;
import io.github.gaurav1112.mirrorlint.scan.ScanResult;
import io.github.gaurav1112.mirrorlint.scan.Scanner;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * CLI entry point: {@code mirrorlint scan <path> [--format human|sarif|json] [--all] [--config <file>]}.
 *
 * <p>{@link #run(String[], PrintStream)} is the testable core (no {@code System.exit} inside); {@link #main}
 * is a thin delegator that calls it and exits with its result.
 *
 * <p>Exit codes: {@code 0} = scan completed, no {@link Severity#DEFAULT}-severity findings; {@code 1} = scan
 * completed, at least one {@link Severity#DEFAULT} finding; {@code 2} = usage error, unreadable/nonexistent
 * scan path, or a malformed {@code --config} file.
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out));
    }

    public static int run(String[] args, PrintStream out) {
        CommandLine cli = new CommandLine(new RootCommand());
        cli.addSubcommand("scan", new ScanCommand(out));
        return cli.execute(args);
    }

    @Command(name = "mirrorlint", mixinStandardHelpOptions = true,
            description = "Detects drift between a truth definition and its mirrored copies.")
    static final class RootCommand implements Callable<Integer> {
        @Override
        public Integer call() {
            System.err.println("mirrorlint: missing required subcommand 'scan' (try `mirrorlint scan <path>`)");
            return 2;
        }
    }

    @Command(name = "scan", description = "Scan a path for truth/mirror drift.")
    static final class ScanCommand implements Callable<Integer> {
        private static final Set<String> VALID_FORMATS = Set.of("human", "sarif", "json");
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

        private final PrintStream out;

        @Spec
        CommandSpec spec;

        @Parameters(index = "0", description = "Path to scan (file or directory).")
        Path path;

        @Option(names = "--format", defaultValue = "human", description = "Output format: human|sarif|json.")
        String format;

        @Option(names = "--all", description = "Include INFO-severity findings (human format only).")
        boolean all;

        @Option(names = "--config", description = "Path to a mirrorlint TOML config file.")
        Path configPath;

        ScanCommand(PrintStream out) {
            this.out = out;
        }

        @Override
        public Integer call() {
            if (!VALID_FORMATS.contains(format)) {
                throw new ParameterException(spec.commandLine(),
                        "Invalid value for --format: '" + format + "' (must be one of human, sarif, json)");
            }
            if (!Files.exists(path)) {
                System.err.println("mirrorlint: scan path does not exist: " + path);
                return 2;
            }

            Config config;
            try {
                config = Config.load(configPath);
            } catch (IllegalArgumentException e) {
                System.err.println("mirrorlint: invalid config: " + e.getMessage());
                return 2;
            }

            ScanResult result;
            try {
                List<LanguageAdapter> adapters = List.of(new TypeScriptAdapter(), new JavaAdapter());
                result = new Scanner(adapters, config).scan(path);
            } catch (RuntimeException e) {
                System.err.println("mirrorlint: scan failed: " + e.getMessage());
                return 2;
            }

            out.print(render(result));

            boolean hasDrift = result.findings().stream().anyMatch(f -> f.severity() == Severity.DEFAULT);
            return hasDrift ? 1 : 0;
        }

        /** JSON format is a Gson serialization of {@code result.findings()} — every finding, unfiltered by --all. */
        private String render(ScanResult result) {
            return switch (format) {
                case "sarif" -> new SarifReporter().render(result) + "\n";
                case "json" -> GSON.toJson(result.findings()) + "\n";
                default -> new HumanReporter().render(result, all);
            };
        }
    }
}
