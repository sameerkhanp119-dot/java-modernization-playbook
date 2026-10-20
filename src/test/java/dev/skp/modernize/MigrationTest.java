package dev.skp.modernize;

import java.math.BigDecimal;
import java.util.Random;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.skp.modernize.MigrationRouter.*;

class MigrationTest {
    private Quote quote(String amount,boolean member,String region){return new Quote(new BigDecimal(amount),member,region);}
    private Quote.Result result(String discount,String shipping,String total){return new Quote.Result(new BigDecimal(discount),new BigDecimal(shipping),new BigDecimal(total));}
    @Test void characterizeLegacyBoundaryAndRounding() {
        var legacy=new LegacyPricing();
        assertEquals(result("0.00","7.95","107.94"),legacy.price(quote("99.99",true,"US")));
        assertEquals(result("7.50","7.95","100.45"),legacy.price(quote("100",true,"US")));
        assertEquals(result("11.25","7.95","146.70"),legacy.price(quote("150",true,"US")));
        assertEquals(result("0.00","0.00","150.00"),legacy.price(quote("150",false,"US")));
        assertEquals(result("30.00","0.00","970.00"),legacy.price(quote("1000",true,"EU")));
        assertEquals(result("7.52","14.95","107.70"),legacy.price(quote("100.27",true,"EU")));
    }
    @Test void invalidInputsCannotReachEitherAdapter(){
        assertThrows(IllegalArgumentException.class,()->quote("-1",false,"US"));
        assertThrows(IllegalArgumentException.class,()->quote("1.001",false,"US"));
        assertThrows(IllegalArgumentException.class,()->quote("10",false,"GB"));
    }
    @Test void tenThousandDeterministicRequestsPreserveCompatibility(){
        var random=new Random(42);var legacy=new LegacyPricing();var modern=new ModernPricing();
        for(int i=0;i<10000;i++){
            var q=new Quote(BigDecimal.valueOf(random.nextInt(200001),2),random.nextBoolean(),random.nextBoolean()?"US":"EU");
            assertEquals(legacy.price(q),modern.price(q),q.toString());
        }
    }
    @Test void shadowDetectsMismatchButKeepsLegacyResponse(){
        Quote.Pricer broken=q->result("0.00","0.00","0.00");
        var router=new MigrationRouter(new LegacyPricing(),broken,new Config(Mode.SHADOW,0));
        var q=quote("100",true,"US");var outcome=router.price("customer-42",q);
        assertEquals(new LegacyPricing().price(q),outcome.result());assertTrue(outcome.mismatch());
        assertEquals(1,router.metrics().mismatches());
    }
    @Test void shadowFailureDoesNotBreakLegacyResponse(){
        var router=new MigrationRouter(new LegacyPricing(),q->{throw new IllegalStateException("unavailable");},new Config(Mode.SHADOW,0));
        assertEquals("legacy",router.price("customer",quote("100",true,"US")).servedBy());
        assertEquals(1,router.metrics().modernFailures());assertEquals(0,router.metrics().fallbacks());
    }
    @Test void canaryRoutingIsStableAndRollbackIsImmediate(){
        var router=new MigrationRouter(new LegacyPricing(),new ModernPricing(),new Config(Mode.CANARY,25));
        var q=quote("100",true,"US");String first=router.price("stable-key",q).servedBy();
        for(int i=0;i<100;i++)assertEquals(first,router.price("stable-key",q).servedBy());
        router.configure(new Config(Mode.MODERN,100));assertEquals("modern",router.price("stable-key",q).servedBy());
        router.configure(new Config(Mode.LEGACY,0));assertEquals("legacy",router.price("stable-key",q).servedBy());
    }
    @Test void canaryZeroAndHundredAreExact(){
        var router=new MigrationRouter(new LegacyPricing(),new ModernPricing(),new Config(Mode.CANARY,0));var q=quote("1",false,"EU");
        for(int i=0;i<100;i++)assertEquals("legacy",router.price("key"+i,q).servedBy());
        router.configure(new Config(Mode.CANARY,100));
        for(int i=0;i<100;i++)assertEquals("modern",router.price("key"+i,q).servedBy());
    }
    @Test void modernFailureFallsBackForReadOnlyQuote(){
        var router=new MigrationRouter(new LegacyPricing(),q->{throw new IllegalStateException("down");},new Config(Mode.MODERN,100));
        assertTrue(router.price("key",quote("10",false,"US")).fallback());assertEquals(1,router.metrics().fallbacks());
    }
    @Test void parallelRequestsKeepMetricsAccurate() throws Exception {
        var router=new MigrationRouter(new LegacyPricing(),new ModernPricing(),new Config(Mode.SHADOW,0));
        var pool=Executors.newFixedThreadPool(4);
        try{var tasks=new java.util.ArrayList<java.util.concurrent.Callable<Outcome>>();
            for(int i=0;i<1000;i++){final int key=i;tasks.add(()->router.price("key"+key,quote("100",true,"US")));}
            for(var f:pool.invokeAll(tasks))assertFalse(f.get().mismatch());
        }finally{pool.shutdown();}
        assertEquals(1000,router.metrics().requests());assertEquals(1000,router.metrics().comparisons());
    }
}
