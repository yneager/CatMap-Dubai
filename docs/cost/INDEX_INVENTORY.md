# Query and index inventory

`firestore.indexes.json` is the local manifest. Nothing has been deployed. Preserve
all fields used by equality, array membership, ordering or range filters.

- Maps: visible posts + geoCells membership + updatedAt descending; visible/unmerged cats geoCells
  membership + updatedAt descending. Old global changed-since map listeners are gone.
- Feed/search: existing category/visibility/createdAt and searchTerms membership
  queries remain. Search terms and geoCells must remain indexed.
- Match detail/cleanup: members membership plus lostPostId or sightingPostId,
  with score descending for the paged suggestion windows.
- Histories: chats members/lastAt; adoption owner or requester/createdAt; bookings
  owner or sitter/createdAt; reviews sitter/createdAt; checkins station/at;
  maintenance station/createdAt. DocumentSnapshot cursors resolve timestamp ties.
- Worker: visible posts by createdAt or updatedAt; matches by createdAt or updatedAt;
  postEmbeddings model/createdAt; answered aiJobs status/doneAt; pending cleanup jobs
  by status/document ID; lease status/expiry; areaFollows neighborhoods/document ID.
- Retention reference checks: photoIds in public source collections, photoId in
  profiles/sitters/updates, imageId inside a chat and videoPath in posts stay indexed.
  Collection-group updates additionally support byUid author export/cleanup and
  photoId retention checks. The two single-field group indexes are explicit.

Unqueried payload fields have single-field exemptions: image bytes; vectors and
detector metadata; AI input/results/error payloads; notification parameters; post
deletion photo/video manifests; text bodies/previews/notes; unqueried appearance
features/fingerprints. Exemptions preserve document reads; they remove unused
index entries. Composite indexes are explicit and can still index an exempt field.

Synthetic size example: six float32 vectors of 512 dimensions contain 12,288 raw
bytes before Firestore document framing. The current worker stores them as six
byte-array values, not 3,072 numeric scalar fields. An exact index/storage saving
cannot be inferred from raw payload bytes. Index entries include document paths,
field names/values, framing and truncation; measure a disposable database before
quoting storage savings. The benchmark reports manifest counts and fixture bytes.

Source: [Firestore storage and index sizes](https://firebase.google.com/docs/firestore/storage-size),
[single-field exemptions](https://firebase.google.com/docs/firestore/query-data/index-overview#single-field_index_exemptions).
