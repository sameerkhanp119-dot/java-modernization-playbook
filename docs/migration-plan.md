# Migration plan and rollback boundary

This is a synthetic pricing migration, not an account of an employer system. The legacy adapter is intentionally compact so its behavior is inspectable.

## Compatibility first

The behavior contract includes decimal rounding, a capped member discount, and a less obvious shipping rule: free shipping uses the subtotal **after** discount. A member's $150 basket therefore still incurs shipping. Characterization tests preserve this behavior rather than silently changing a business rule during structural work. Any change to it should be a separate, versioned product decision.

## Release stages

| Stage | Response authority | Evidence required before promotion |
|---|---|---|
| LEGACY | Legacy only | Golden cases agreed; invalid-input boundary defined |
| SHADOW | Legacy; modern evaluated for comparison | No unexplained mismatches across representative inputs; errors inspected |
| CANARY | Stable SHA-256 key bucket below configured percentage | Compare error rate, business outcomes and latency for each cohort |
| MODERN | Modern; legacy fallback on runtime failure | Stable behavior over a defined observation period; tested rollback |
| Retirement | Not implemented | Remove fallback only after data ownership, consumers and recovery no longer depend on legacy |

The code snapshots one immutable routing configuration per request. A config change affects subsequent requests; it cannot cancel in-flight calls. Each routing key stays in the same bucket, and increasing the percentage grows the candidate cohort. Percentage describes bucket assignment, not guaranteed exact traffic share.

## Failure and observability

Shadow discrepancies never replace the legacy response. Candidate failures are counted separately from mismatches. Canary/modern exceptions trigger a legacy fallback. A fallback failure propagates. These fallbacks are valid here because pricing is read-only. Do not copy this mechanism into payment or order mutations without idempotency, transaction boundaries and reconciliation.

The demo runs both adapters in one JVM. It has no remote timeout, circuit breaker, distributed tracing or latency isolation. A shadow adapter that blocks can still delay the response; deploy remote shadow evaluation asynchronously with bounded work and explicit deadlines. Counters are process-local and their individual atomic reads are not a transactional snapshot. Persist/export metrics and configuration in a real service.

## Data migration: expand, migrate, contract

The runnable example deliberately has no database. A real pricing schema migration would require an additive field or table, readers tolerant of old/new records, an idempotent checkpointed backfill, consistency checks and a documented ownership switch. Keep the original columns and writer available during the rollback window. Avoid destructive schema contraction until old application versions and rollback are retired. A feature flag cannot recover dropped data.

## Rollback drill

1. Start in SHADOW and inspect comparison/error counters.
2. Introduce the deliberately incorrect adapter used in the mismatch test. Verify the legacy result still serves.
3. Route 100% of read-only quotes to modern and inject a candidate exception. Verify fallback and its counter.
4. Set LEGACY. Verify the modern adapter is no longer invoked for new requests.
5. Record the code/config versions, reason, observed impact and recovery. No production metrics are supplied by this repository.
