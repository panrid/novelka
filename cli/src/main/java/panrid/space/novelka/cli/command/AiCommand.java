package panrid.space.novelka.cli.command;

import picocli.CommandLine.Option;

public abstract class AiCommand extends DatabaseCommand {
    @Option(names = "--model", defaultValue = "${env:NOVELKA_MODEL:-openai/gpt-4o-mini}")
    protected String model;

    @Option(
            names = "--budget-usd",
            description = "Conservative USD ceiling for this invocation; 0 = no ceiling",
            defaultValue = "0")
    protected double budget;

    protected final void validateBudget() {
        if (!Double.isFinite(budget) || budget < 0) {
            throw new IllegalArgumentException("Budget must be finite and nonnegative");
        }
    }
}
