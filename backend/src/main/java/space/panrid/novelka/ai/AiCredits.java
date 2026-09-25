package space.panrid.novelka.ai;

import java.math.BigDecimal;

/** OpenRouter credits in dollars. */
public record AiCredits(BigDecimal total, BigDecimal used) {

    public BigDecimal left() {
        return total.subtract(used);
    }
}
