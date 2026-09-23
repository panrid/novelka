package panrid.space.novelka.server.settings;

import java.util.List;

public record SiteSettings(long revision, boolean registrationOpen, int segmentChars,
        double targetUsdPer5000, double maxBudgetUsd, List<StageSettings> stages, boolean adminSelfApproval) {
}
