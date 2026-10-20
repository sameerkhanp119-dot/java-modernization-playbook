package dev.skp.modernize;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Canonical contract shared by legacy and replacement adapters. */
public record Quote(BigDecimal subtotal, boolean member, String region) {
    public Quote {
        if (subtotal == null || subtotal.signum() < 0 || subtotal.compareTo(new BigDecimal("1000000")) > 0)
            throw new IllegalArgumentException("Subtotal must be between 0 and 1000000");
        if (subtotal.stripTrailingZeros().scale() > 2) throw new IllegalArgumentException("At most two decimal places");
        if (!"US".equals(region) && !"EU".equals(region)) throw new IllegalArgumentException("Supported regions: US, EU");
        subtotal = subtotal.setScale(2, RoundingMode.UNNECESSARY);
    }
    public record Result(BigDecimal discount, BigDecimal shipping, BigDecimal total) {}
    public interface Pricer { Result price(Quote request); }
}
