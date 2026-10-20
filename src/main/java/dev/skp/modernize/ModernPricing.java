package dev.skp.modernize;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Extracted policy preserves baseline rules, including rounding and shipping boundary. */
public final class ModernPricing implements Quote.Pricer {
    private static final BigDecimal MEMBER_THRESHOLD = new BigDecimal("100.00");
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("150.00");
    private static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.075");
    private static final BigDecimal DISCOUNT_CAP = new BigDecimal("30.00");
    public Quote.Result price(Quote q) {
        var discount = memberDiscount(q);
        var net = q.subtotal().subtract(discount);
        var shipping = shipping(net, q.region());
        return new Quote.Result(discount, shipping, net.add(shipping).setScale(2, RoundingMode.HALF_UP));
    }
    private BigDecimal memberDiscount(Quote q) {
        return q.member() && q.subtotal().compareTo(MEMBER_THRESHOLD) >= 0
            ? q.subtotal().multiply(DISCOUNT_RATE).setScale(2, RoundingMode.HALF_UP).min(DISCOUNT_CAP)
            : new BigDecimal("0.00");
    }
    private BigDecimal shipping(BigDecimal net, String region) {
        return net.compareTo(FREE_SHIPPING_THRESHOLD) >= 0 ? new BigDecimal("0.00")
            : new BigDecimal(region.equals("US") ? "7.95" : "14.95");
    }
}
