package panrid.space.novelka.core;

import static panrid.space.novelka.core.Domain.*;

import com.fasterxml.jackson.databind.JsonNode;

public interface AiClient {
  JsonNode generate(
      Work job, int segment, String stage, Glossary glossary, Object payload, double budget)
      throws Exception;
}
