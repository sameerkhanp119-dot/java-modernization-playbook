package dev.skp.modernize;

import java.math.BigDecimal;

public final class MigrationDemo {
    public static void main(String[] args) {
        var request=new Quote(new BigDecimal("150.00"),true,"US");
        var router=new MigrationRouter(new LegacyPricing(),new ModernPricing(),new MigrationRouter.Config(MigrationRouter.Mode.SHADOW,0));
        System.out.println("Synthetic read-only pricing migration; no customer data or external calls.");
        System.out.println("SHADOW: " + router.price("customer-demo-42",request));
        router.configure(new MigrationRouter.Config(MigrationRouter.Mode.CANARY,25));
        System.out.println("CANARY 25%: " + router.price("customer-demo-42",request));
        router.configure(new MigrationRouter.Config(MigrationRouter.Mode.MODERN,100));
        System.out.println("MODERN: " + router.price("customer-demo-42",request));
        router.configure(new MigrationRouter.Config(MigrationRouter.Mode.LEGACY,0));
        System.out.println("ROLLBACK: " + router.price("customer-demo-42",request));
        System.out.println("METRICS: " + router.metrics());
    }
}
