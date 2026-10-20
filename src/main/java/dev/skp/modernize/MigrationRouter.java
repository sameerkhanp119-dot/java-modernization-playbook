package dev.skp.modernize;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicLong;

/** Read-only strangler seam. Do not mirror writes using this router. */
public final class MigrationRouter {
    public enum Mode { LEGACY, SHADOW, CANARY, MODERN }
    public record Config(Mode mode, int canaryPercent) {
        public Config {
            if (mode == null || canaryPercent < 0 || canaryPercent > 100) throw new IllegalArgumentException("Invalid routing config");
        }
    }
    public record Outcome(Quote.Result result, String servedBy, boolean compared, boolean mismatch, boolean fallback) {}
    public record Metrics(long requests, long comparisons, long mismatches, long modernFailures, long fallbacks) {}
    private final Quote.Pricer legacy;
    private final Quote.Pricer modern;
    private volatile Config config;
    private final AtomicLong requests=new AtomicLong(), comparisons=new AtomicLong(), mismatches=new AtomicLong(), failures=new AtomicLong(), fallbacks=new AtomicLong();
    public MigrationRouter(Quote.Pricer legacy, Quote.Pricer modern, Config config) {
        this.legacy=java.util.Objects.requireNonNull(legacy); this.modern=java.util.Objects.requireNonNull(modern);
        this.config=java.util.Objects.requireNonNull(config);
    }
    /** A single immutable config swap makes rollback immediate for subsequent calls. */
    public void configure(Config replacement) { config=java.util.Objects.requireNonNull(replacement); }
    public Outcome price(String routingKey, Quote quote) {
        if (routingKey == null || routingKey.isBlank()) throw new IllegalArgumentException("Stable routing key required");
        java.util.Objects.requireNonNull(quote);
        Config snapshot=config;
        requests.incrementAndGet();
        if (snapshot.mode()==Mode.LEGACY) return new Outcome(legacy.price(quote),"legacy",false,false,false);
        if (snapshot.mode()==Mode.SHADOW) {
            var baseline=legacy.price(quote);
            try {
                var candidate=modern.price(quote); comparisons.incrementAndGet();
                boolean mismatch=!baseline.equals(candidate);if(mismatch)mismatches.incrementAndGet();
                return new Outcome(baseline,"legacy",true,mismatch,false);
            } catch (RuntimeException failure) {
                failures.incrementAndGet();return new Outcome(baseline,"legacy",false,false,false);
            }
        }
        boolean useModern=snapshot.mode()==Mode.MODERN || bucket(routingKey)<snapshot.canaryPercent();
        if(!useModern)return new Outcome(legacy.price(quote),"legacy",false,false,false);
        try { return new Outcome(modern.price(quote),"modern",false,false,false); }
        catch (RuntimeException failure) {
            // Safe only because pricing is read-only. The legacy failure, if any, propagates.
            failures.incrementAndGet(); fallbacks.incrementAndGet();
            return new Outcome(legacy.price(quote),"legacy",false,false,true);
        }
    }
    public Metrics metrics(){return new Metrics(requests.get(),comparisons.get(),mismatches.get(),failures.get(),fallbacks.get());}
    static int bucket(String key) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            long unsigned=((long)(digest[0]&255)<<24)|((long)(digest[1]&255)<<16)|((long)(digest[2]&255)<<8)|(digest[3]&255L);
            return (int)(unsigned%100);
        }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
