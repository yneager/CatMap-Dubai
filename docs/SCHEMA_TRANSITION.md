# Client and worker schema transition

These local changes require a coordinated client/rules/index/worker rollout.
Nothing in this document authorizes production reads, migrations, claims, settings,
billing changes, deployments or publication. Rehearse only with synthetic data in
demo-* loopback emulators. Do not ship rules independently of the matching clients.

1. Run the Firestore and Storage rules suites, including largest legal batches and
   new `extras.mjs` cases. Run worker protocol/maintenance and synthetic recovery
   integration, web browser flows, native account deletion, Arabic and offline checks.
   Rules access-call and 1,000-expression budgets require emulator proof.
2. Review `tools/migrate/approved-extras.py`. It accepts a **synthetic** path-to-fields
   JSON fixture and emits a dry-run plan; it has no network or apply mode. It prepares
   canonical username claims, missing cat visibility defaults, private area centres, capacity/unread totals and
   per-message private photo copies. Conflicts and missing/foreign photos are blockers.
   A production migration is separate work needing explicit authorization and a
   version/precondition protected executor. This planner must not ingest live exports.
3. Verify aliases, usernames and caps before switching clients. Existing list entries
   above 500 need an owner policy; do not silently discard data. Create `userLimits`
   from actual favorites, blocked entries and each chat's unread map. Keep complete
   unread totals separate from the newest 50-chat UI window.
4. Move legacy private chat photos to unique `c_` IDs per chat/message and update both
   full/thumbnail metadata plus message reference together. Annotate public references
   as community images. Unknown legacy images remain owner-only. Copies previously
   downloaded by old clients cannot be remotely withdrawn. New private transfers use
   authenticated REST with no-store, bypassing Firestore SDK persistence; image cache
   namespaces are replaced on upgrade. Public/offline map caches remain public data.
5. Move legacy public area centres into the owner's private areaFollow document and
   strip public lat/lng/radius. Resolve conflicts instead of overwriting a private
   preference. Older clients that write the old public shape must upgrade first.
6. Stage matching client releases, indexes, rules and worker code together with an
   owner-reviewed migration/rollback plan. Rollback must preserve private-media ACLs
   and deletion tombstones. Reverting to permissive rules is not a safe rollback.

Deletion jobs hide parents before cleanup and block late children. Post manifests
retain media IDs after source deletion. Hidden authored-update deletions can leave
an `updateRepairs` job for the trusted maintenance worker. Auth deletion waits for
privacy-critical client cleanup; optional public counter repair can finish later.
UID-only account tombstones deliberately survive to prevent profile recreation.
Do not remove pending jobs or tombstones as a generic cleanup operation.

Notification receipts survive inbox dismissal. Message/update/medical alerts are
one per source event; request/decision/status alerts are one per source plus state;
match announcements/info/reunion alerts are one per match action kind. Repeated
notes without a state change coalesce. A second message uses a new event ID.
Receipts provide idempotency, not a universal atomic outbox: a client crash between
the primary action and notification can still miss an alert. Lost-area fanout has
its own durable paged job and resumes independently.

Maintenance is off unless `CATMAP_MAINTENANCE=1` and the worker account has both
`catmapAiWorker` and the separately granted `catmapMaintenance` claim. This is a
broader trust boundary: maintenance reads private chat references/media to enforce
retention, repairs counts, deletes manifest-backed posts and sends area alerts.
No claim was granted here. New uploads have a seven-day lease; linking/renewal
and claimed deletion coordinate through that lease. Retention is eventual while
the owner's worker is running. A failed or disconnected worker leaves retry jobs.
Video cleanup has an emulator-only Storage adapter; no production adapter, Blaze
activation or video rollout is included. Nonempty video cleanup must fail closed
until the owner separately supplies and tests its authorized runtime configuration.

Owner configuration checklist (inspect settings only after separate authorization):

- Confirm Spark, videos/push behavior and aggregate quota headroom.
- Review current staff/demo roles and disable unwanted demo access; do not infer
  today's roles or branch protection from an old handoff.
- Review repository protections, required CI checks, secrets handling and public
  API-key restrictions against actual services and app origins/package/signatures.
- Review App Check monitoring. Test the supported sideloaded APKs and web clients
  before any enforcement decision; do not blindly enforce Play-only assumptions.
- Verify Storage's Firestore rules lookup permissions and verified/unverified/
  suspended read/upload/delete cases in the disposable emulator.
- Review service-specific billing safeguards in `docs/cost/BILLING_SAFETY.md`.
- Keep a dated owner signoff and test evidence. A checklist is not proof that any
  production setting is enabled, nor permission to change it.

References: [Firestore REST authentication](https://firebase.google.com/docs/firestore/use-rest-api),
[rules access budgets](https://firebase.google.com/docs/firestore/security/rules-conditions),
[DirectML session serialization](https://onnxruntime.ai/docs/execution-providers/DirectML-ExecutionProvider.html).
