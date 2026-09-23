package panrid.space.novelka.server.models;

/** OpenRouter /models response with one usable model and one example of each rejection reason. */
public final class ModelFixtures {
    public static final String MODELS = """
            {"data":[
              {"id":"good/model","name":"Good","context_length":128000,
               "architecture":{"input_modalities":["text","image"],"output_modalities":["text"]},
               "pricing":{"prompt":"0.00000015","completion":"0.0000006"},
               "supported_parameters":["response_format","tools","temperature"]},
              {"id":"no/tools","name":"No tools","architecture":{"input_modalities":["text"],"output_modalities":["text"]},
               "pricing":{"prompt":"0.000001","completion":"0.000002"},"supported_parameters":["structured_outputs"]},
              {"id":"router/auto","name":"Variable","architecture":{"input_modalities":["text"],"output_modalities":["text"]},
               "pricing":{"prompt":"-1","completion":"-1"},"supported_parameters":["response_format","tools"]},
              {"id":"image/only","name":"Images","architecture":{"input_modalities":["text"],"output_modalities":["image"]},
               "pricing":{"prompt":"0","completion":"0"},"supported_parameters":["response_format","tools"]},
              {"name":"missing id"}
            ]}
            """;

    private ModelFixtures() {}
}
