package dev.skp.modernize;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Deliberately monolithic baseline. Preserve its behavior before extracting rules. */
public final class LegacyPricing implements Quote.Pricer {
    public Quote.Result price(Quote q) {
        BigDecimal discount = BigDecimal.ZERO.setScale(2);
        if (q.member() && q.subtotal().compareTo(new BigDecimal("100.00")) >= 0) {
            discount = q.subtotal().multiply(new BigDecimal("0.075")).setScale(2, RoundingMode.HALF_UP);
            if (discount.compareTo(new BigDecimal("30.00")) > 0) discount = new BigDecimal("30.00");
        }
        BigDecimal afterDiscount = q.subtotal().subtract(discount);
        // Existing compatibility rule: free shipping checks the DISCOUNTED subtotal.
        BigDecimal shipping = afterDiscount.compareTo(new BigDecimal("150.00")) >= 0
            ? new BigDecimal("0.00") : "US".equals(q.region()) ? new BigDecimal("7.95") : new BigDecimal("14.95");
        return new Quote.Result(discount, shipping, afterDiscount.add(shipping).setScale(2, RoundingMode.HALF_UP));
    }
}
