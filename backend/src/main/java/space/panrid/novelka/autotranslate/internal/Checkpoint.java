package space.panrid.novelka.autotranslate.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a chapter step already has, saved after every model answer: a restart continues
 * from here and never pays for the same part twice. Keys of the maps are segment numbers.
 */
class Checkpoint {

    record Line(String id, String text) {
    }

    public Long sourceId;
    public Integer chars;
    public String title;
    public List<Integer> analyzed = new ArrayList<>();
    public Map<String, List<Line>> draft = new HashMap<>();
    public Map<String, String> summaries = new HashMap<>();
    public Map<String, List<Line>> revised = new HashMap<>();
    /** Chapter summary for the next chapter's context. */
    public String summary;
    public Long revisionId;
}
