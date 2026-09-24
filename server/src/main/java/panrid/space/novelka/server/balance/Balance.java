package panrid.space.novelka.server.balance;

import java.math.BigDecimal;

/**
 * The account's translation money in USD. {@code available} = toppedUp − reserved − spent.
 * {@code unlimited} is the site owner: their tasks use the site budget and are not charged.
 */
public record Balance(BigDecimal available, BigDecimal toppedUp, BigDecimal reserved, BigDecimal spent, boolean unlimited) {
}
