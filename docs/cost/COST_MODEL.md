# Current operation budgets - 23 October 2026

The earlier Phase 9 dollar/DAU forecast predates source counters, receipts,
viewport listeners and maintenance jobs. It is retired. No production usage,
region, billing account or prices were inspected for this update.

Run `python tools/cost/cost_model.py` for a parameterized document-operation
scenario. It has no dollar output or fixed worker-read allowance. For example,
500 enabled installations making 96 empty polls give an assumed 48,000 query-read
floor/day. Disabled polling exits before Firebase access; Doze, permission, quiet
hours and preferences change the actual run count.

Map listeners follow at most six visible cells, with two queries per cell. Each
collection's first screen budget is approximately 300 documents. Subsequent
deliveries depend on changes inside those windows. Reconnects can repeat initial
reads. Web retains overlapping listener plans; Android restarts its bounded plan
after a changed viewport settles. SDK callback counts cannot prove billing savings.

Histories initially request 50 documents. A 1,000-row station history therefore
returns at most 50 on its first page: a 95% reduction in that synthetic scenario.
Explicit older-page requests add reads. Chat badge totals use one `userLimits`
document, independently of the bounded chat page.

Integrity adds writes: a message plus alert now uses five documents (message,
preview, unread total, notification, receipt); a photo uses three (full, thumbnail,
lease); fanout writes notification, receipt and cursor. Private photo viewing
checks authorization on every transfer and has no durable JPEG cache. These
costs support stricter access, idempotency and retryability.

Worker startup includes recent posts, matches, up to 2,048 warm vectors and their
projected parent checks. Vectors stream in pages of 100. Orphan retention rotates
through 25 projected vectors plus parents every 15 minutes (at most 4,800 source
reads/day on nonempty pages, before rule-dependent reads). Optional maintenance
checks seven empty queries per idle sweep, at most once every five minutes:
an assumed 2,016-read daily floor when enabled and idle. Active jobs run at most
once per 15 seconds. Maintenance adds source,
block and reference checks plus retries. Supply measured counts to the ledger.

`tools/cost/extras_benchmark.py` compares the actual spatial candidate method to
an exhaustive synthetic scan, verifies equal results, measures CPU latency,
Python heap/RSS and current build exclusions. These are local fixture results,
not model accuracy, device latency, index storage bytes or billed operations.
`tools/cost/baseline.mjs` is a historical Phase 1 replay, not the current model.

Local native/web map counters expose queries, document/change deliveries, active
listeners and cache writes. Worker maintenance counts pages, deliveries, deletions
and failures; recovery counts orphan reads/deletes. Counters contain no UID, photo,
token, coordinates or text. Emulator cold/warm/offline actions, rule-dependent
reads, index-entry reads, reconnects, timestamp ties and interruption remain
required before rollout. SDK/server caches can coalesce accesses.

References: [Firestore pricing semantics](https://firebase.google.com/docs/firestore/pricing),
[index best practices](https://firebase.google.com/docs/firestore/best-practices).
