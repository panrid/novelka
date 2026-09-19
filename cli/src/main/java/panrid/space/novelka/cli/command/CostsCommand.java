package panrid.space.novelka.cli.command;

import panrid.space.novelka.cli.support.Output;
import panrid.space.novelka.core.Store;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "costs",
    mixinStandardHelpOptions = true,
    description = "Stored estimates, actual spend, usage and unknown costs")
public final class CostsCommand extends DatabaseCommand {
  @Parameters(index = "0", arity = "0..1", paramLabel = "NOVEL")
  private String novel;

  @Option(names = "--details")
  private boolean details;

  @Override
  protected void execute(Store store) throws Exception {
    String where = novel == null ? "" : " WHERE j.novel_id=?";
    Object[] arguments = novel == null ? new Object[] {} : new Object[] {novelId(store, novel)};
    Output.json(
        store.rows(
            details
                ? "SELECT"
                    + " a.id,a.job_id,j.novel_id,j.chapter,a.stage,a.segment,a.model,a.provider,a.prompt_version,a.glossary_revision,a.estimated_usd,a.actual_usd,a.cost_source,a.input_tokens,a.output_tokens,a.cached_tokens,a.state,a.request_id,a.duration_ms,a.created_at"
                    + " FROM ai_calls a JOIN jobs j ON j.id=a.job_id"
                    + where
                    + " ORDER BY a.created_at"
                : "SELECT j.novel_id,j.chapter,a.stage,a.model,count(*)"
                    + " calls,sum(a.estimated_usd) estimated_usd,sum(a.actual_usd)"
                    + " known_actual_usd,count(*) FILTER(WHERE a.actual_usd IS NULL)"
                    + " unknown_cost_calls,sum(a.input_tokens)"
                    + " input_tokens,sum(a.output_tokens) output_tokens FROM ai_calls a JOIN"
                    + " jobs j ON j.id=a.job_id"
                    + where
                    + " GROUP BY j.novel_id,j.chapter,a.stage,a.model ORDER BY"
                    + " j.novel_id,j.chapter,a.stage",
            arguments));
  }
}
