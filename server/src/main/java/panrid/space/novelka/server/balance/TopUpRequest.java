package panrid.space.novelka.server.balance;

import java.math.BigDecimal;

/** A positive amount adds money; a negative one corrects a mistaken top-up. */
public record TopUpRequest(BigDecimal amountUsd, String note) {
}
