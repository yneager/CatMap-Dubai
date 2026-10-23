# Billing readiness - 23 October 2026

The authorized target remains Spark with videos and paid services disabled.
This local target is not evidence of the current production plan: no billing
console or production configuration was inspected or changed.

Spark quota exhaustion can cause an outage. Monitor aggregate Firestore reads,
writes, deletes, stored bytes and downloads before growth. The current operation
ledger is in `COST_MODEL.md`; historical dollar/DAU forecasts are retired.

Any future Blaze decision needs separate owner approval and a service inventory.
Budget alerts provide notification, not a guaranteed spending stop. Google now
offers service-scoped spend caps for eligible services including Cloud Functions,
App Hosting/Cloud Run and AI services. The documented eligible list currently
does not include Firestore or Cloud Storage. Caps have enforcement delays and
do not stop all fixed-resource charges. A cap for one service does not cap the
project. Recheck eligible services and behavior at decision time.

Do not automatically disable billing as a supposedly harmless fallback. It can
interrupt unrelated services and recovery is not guaranteed. Prepare an owner
runbook identifying permitted shutdowns, aggregate thresholds, notification
recipients, service quotas and a tested recovery path. No budget, notification,
billing, quota, App Check or project setting was changed here.

Sources: [Firebase spend caps](https://firebase.google.com/docs/projects/billing/spend-caps),
[Cloud Billing scope and limitations](https://docs.cloud.google.com/billing/docs/how-to/budgets-spend-caps),
[budget notifications](https://docs.cloud.google.com/billing/docs/how-to/budgets).
