# Credential-free worker source

This is the reviewable source mirror of the owner's desktop worker. Runtime
credentials, Firebase configuration, models, private photos and local backups
are excluded. Do not copy those into this repository.

Portable checks: install `requirements-test.txt` in an isolated environment, then
`python tools/worker/tests/run.py`. They exercise the actual job protocol, bounded
queues, overflow refill without database resync, conditional cleanup/backfill,
maintenance restart/replay, 350-follower fanout, lease renewal, migration,
matching/geohash/search and Android fingerprint fixtures. They use synthetic inputs
and initialize no Firebase clients or models.

Explicit integration: with a demo-* loopback Firestore emulator running,
`python tools/worker/tests/test_recovery_synthetic.py`. It exercises actual Recovery
with synthetic vectors; no model download is needed. The separate real-model
`test_recovery_e2e.py` is opt-in and needs owner-provided, reviewed fixture images
and already-pinned models. It is not part of portable CI and is not evidence that
model accuracy or real GPU inference passed.

`model-manifest.json` pins public model/tokenizer revisions and file hashes. The
embedding identity includes the manifest and actual preprocessing/crop code. The
experimental small embedder has no reviewed cached pin and intentionally refuses
to run. Base/large/DETR/CLIP are pinned; hashes are verified before runtime use.
DirectML calls sharing a session are serialized. CPU background work has a separate
bounded executor; time budgets are checked between operations, not hard deadlines
on individual network or inference calls.

Maintenance remains opt-in. See `docs/SCHEMA_TRANSITION.md` for the additional role,
private-data trust scope, lease retention, deletion/fanout jobs and rollout gates.
Only the demo loopback Storage adapter is provided. No service is deployed or enabled.
