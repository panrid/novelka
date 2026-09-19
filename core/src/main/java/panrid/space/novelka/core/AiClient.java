package panrid.space.novelka.core;

import com.fasterxml.jackson.databind.JsonNode;
import panrid.space.novelka.core.model.Glossary;
import panrid.space.novelka.core.model.Work;

public interface AiClient {
    JsonNode generate(
            Work job, int segment, String stage, Glossary glossary, Object payload, double budget)
            throws Exception;
}
