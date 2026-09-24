package panrid.space.novelka.server.correction;

import panrid.space.novelka.server.repository.CorrectionRepository;

import java.util.List;
import java.util.Map;

/** Loads the corrections to review, locked, inside the review transaction. */
@FunctionalInterface
public interface CorrectionSelection {
    List<Map<String, Object>> rows(CorrectionRepository repository) throws Exception;
}
