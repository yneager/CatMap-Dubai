"""Durable Spark maintenance. Requires an explicitly provisioned maintenance worker claim.

No Firebase client is initialized here. The owner starts the existing PC worker; no paid service is enabled.
"""
import time


class Maintenance:
    def __init__(self, db, worker_uid, events, storage_delete=None):
        self.db, self.uid, self.events, self.storage_delete = db, worker_uid, events, storage_delete
        self.next_attempt = 0
        self.job_cursors = {}
        self.media_cursors = {}
        self.retries = {}
        self.metrics = {"pages": 0, "deliveries": 0, "deletions": 0, "failures": 0}

    def work(self, budget_s=1.0):
        if time.monotonic() < self.next_attempt:
            return
        # Seven empty queries per idle sweep: five-minute idle checks avoid a polling read floor.
        self.next_attempt = time.monotonic() + 300
        end = time.monotonic() + budget_s
        try:
            from google.cloud.firestore_v1.base_query import FieldFilter
            for collection, handler in (("postDeletions", self.delete_post), ("lostAlertJobs", self.lost_alerts)):
                jobs = self.db.collection(collection).where(filter=FieldFilter("status", "==", "pending")).order_by("__name__").limit(5)
                cursor = self.job_cursors.get(collection)
                if cursor: jobs = jobs.start_after({"__name__": self.db.document(f"{collection}/{cursor}")})
                page = list(jobs.stream())
                if page: self.next_attempt = min(self.next_attempt, time.monotonic() + 15)
                if not page: self.job_cursors.pop(collection, None)
                for job in page:
                    if time.monotonic() >= end: return
                    self.job_cursors[collection] = job.id
                    if time.monotonic() < self.retries.get(job.reference.path, 0): continue
                    try:
                        if handler(job, end): job.reference.update({"status": "done"})
                    except Exception as error:
                        self.metrics["failures"] += 1
                        if len(self.retries) >= 256: self.retries.clear()
                        self.retries[job.reference.path] = time.monotonic() + 30
                        self.events.put(("log", f"A maintenance item will retry: {type(error).__name__}"))
            self.repair_updates(end)
            self.reap_media(end)
        except Exception as error:
            self.metrics["failures"] += 1
            self.next_attempt = time.monotonic() + 30
            self.events.put(("log", f"Maintenance will retry: {type(error).__name__}"))

    def repair_updates(self, end):
        from google.cloud import firestore
        # Every uncounted author deletion leaves a durable repair source. A concurrent normal
        # create/delete changes the parent version; a second repair job catches uncounted deletes.
        query = self.db.collection("updateRepairs").order_by("__name__").limit(5)
        cursor = self.job_cursors.get("updateRepairs")
        if cursor: query = query.start_after({"__name__": self.db.document(f"updateRepairs/{cursor}")})
        page = list(query.stream())
        if page: self.next_attempt = min(self.next_attempt, time.monotonic() + 15)
        if not page: self.job_cursors.pop("updateRepairs", None)
        for job in page:
            if time.monotonic() >= end: return
            self.job_cursors["updateRepairs"] = job.id
            data = job.to_dict() or {}
            parent = self.db.document(f"posts/{data['postId']}")
            before = parent.get()
            if not before.exists: job.reference.delete(); continue
            count = parent.collection("updates").count().get()[0][0].value
            @firestore.transactional
            def repair(tx):
                latest = parent.get(transaction=tx)
                if latest.update_time != before.update_time: return
                tx.update(parent, {"updatesCount": count, "repairSourceId": job.id})
                tx.delete(job.reference)
            repair(self.db.transaction())

    def remove_image(self, image_id):
        if not image_id: return
        # Shared galleries keep their media. D1 ownership is unchanged.
        from google.cloud.firestore_v1.base_query import FieldFilter
        for collection in ("cats", "posts", "sightings", "rescues"):
            if next(iter(self.db.collection(collection).where(filter=FieldFilter("photoIds", "array_contains", image_id)).limit(1).stream()), None): return
        for collection in ("users", "sitters"):
            if next(iter(self.db.collection(collection).where(filter=FieldFilter("photoId", "==", image_id)).limit(1).stream()), None): return
        for child in self.db.collection_group("updates").where(filter=FieldFilter("photoId", "==", image_id)).limit(2).stream():
            if child.reference.path != getattr(self, "deleting_child", None): return
        self.db.document(f"images/{image_id}").delete()
        self.db.document(f"images/{image_id}_t").delete()

    def media_referenced(self, job, data):
        from google.cloud.firestore_v1.base_query import FieldFilter
        if data.get("path"):
            return next(iter(self.db.collection("posts").where(filter=FieldFilter("videoPath", "==", data["path"])).limit(1).stream()), None) is not None
        if data.get("chatId"):
            return next(iter(self.db.collection(f"chats/{data['chatId']}/messages").where(filter=FieldFilter("imageId", "==", job.id)).limit(1).stream()), None) is not None
        queries = [self.db.collection(c).where(filter=FieldFilter("photoIds", "array_contains", job.id)) for c in ("posts", "cats", "sightings", "rescues")]
        queries += [self.db.collection(c).where(filter=FieldFilter("photoId", "==", job.id)) for c in ("users", "sitters")]
        queries.append(self.db.collection_group("updates").where(filter=FieldFilter("photoId", "==", job.id)))
        return any(next(iter(q.limit(1).stream()), None) is not None for q in queries)

    def reap_media(self, end):
        from google.cloud.firestore_v1.base_query import FieldFilter
        now = int(time.time() * 1000)
        for collection in ("mediaUploads", "videoUploads"):
            query = self.db.collection(collection).where(filter=FieldFilter("status", "in", ["staged", "linked"])).where(filter=FieldFilter("expiresAt", "<", now)).order_by("expiresAt").limit(10)
            cursor = self.media_cursors.get(collection)
            if cursor: query = query.start_after(cursor)
            page = list(query.stream())
            if page: self.next_attempt = min(self.next_attempt, time.monotonic() + 15)
            if not page: self.media_cursors.pop(collection, None)
            for job in page:
                if time.monotonic() >= end: return
                self.media_cursors[collection] = job
                if time.monotonic() < self.retries.get(job.reference.path, 0): continue
                try: self.claim_media(job, job.to_dict() or {}, now)
                except Exception as error: self.media_failure(job, error)
            key = collection + '/deleting'
            query = self.db.collection(collection).where(filter=FieldFilter("status", "==", "deleting")).order_by("__name__").limit(10)
            if key in self.media_cursors: query = query.start_after(self.media_cursors[key])
            page = list(query.stream())
            if page: self.next_attempt = min(self.next_attempt, time.monotonic() + 15)
            if not page: self.media_cursors.pop(key, None)
            for job in page:
                if time.monotonic() >= end: return
                self.media_cursors[key] = job
                self.try_finish_media(job, job.to_dict() or {})

    def claim_media(self, job, data, now):
        from google.cloud import firestore
        linked = self.media_referenced(job, data)
        @firestore.transactional
        def claim(tx):
            latest = job.reference.get(transaction=tx).to_dict() or {}
            if latest.get("status") not in ("staged", "linked") or latest.get("expiresAt", now) >= now: return False
            tx.update(job.reference, {"status": "linked" if linked else "deleting", "expiresAt": now + 7 * 86400000})
            return True
        if claim(self.db.transaction()) and not linked: self.try_finish_media(job, data)

    def media_failure(self, job, error):
        self.metrics["failures"] += 1
        if len(self.retries) >= 256: self.retries.clear()
        self.retries[job.reference.path] = time.monotonic() + 30
        self.events.put(("log", f"Media cleanup will retry: {type(error).__name__}"))

    def try_finish_media(self, job, data):
        if time.monotonic() < self.retries.get(job.reference.path, 0): return
        try: self.finish_media(job, data)
        except Exception as error:
            self.media_failure(job, error)

    def finish_media(self, job, data):
        if data.get("path"):
            if self.storage_delete is None: raise RuntimeError("Storage cleanup adapter is not configured")
            self.storage_delete(data["path"])
        else:
            self.db.document(f"images/{job.id}").delete()
            self.db.document(f"images/{job.id}_t").delete()
        job.reference.update({"status": "deleted"})

    def delete_post(self, job, end):
        data = job.to_dict() or {}
        post = self.db.document(f"posts/{job.id}").get()
        if post.exists:
            # The immutable manifest was checked against the parent by rules.
            if (post.to_dict() or {}).get("ownerId") != data.get("ownerId"):
                raise ValueError("Cleanup owner mismatch")
            post.reference.delete()
        children = list(self.db.collection(f"posts/{job.id}/updates").limit(25).stream())
        for child in children:
            if time.monotonic() >= end: return False
            self.deleting_child = child.reference.path
            try: self.remove_image((child.to_dict() or {}).get("photoId", ""))
            finally: self.deleting_child = None
            child.reference.delete()
        if children: return False
        from google.cloud.firestore_v1.base_query import FieldFilter
        for collection, field in (("adoptionRequests", "postId"), ("matches", "lostPostId"), ("matches", "sightingPostId")):
            docs = list(self.db.collection(collection).where(filter=FieldFilter(field, "==", job.id)).limit(25).stream())
            for doc in docs:
                if time.monotonic() >= end: return False
                doc.reference.delete()
            if docs: return False
        self.db.document(f"postEmbeddings/{job.id}").delete()
        for image_id in data.get("photoIds", []):
            if time.monotonic() >= end: return False
            self.remove_image(image_id)
        if data.get("videoPath"):
            if self.storage_delete is None:
                raise RuntimeError("Video cleanup needs the separately configured Storage adapter")
            self.storage_delete(data["videoPath"])
        self.metrics["deletions"] += 1
        return True

    def lost_alerts(self, job, end):
        from google.cloud.firestore_v1.field_path import FieldPath
        from google.cloud.firestore_v1.base_query import FieldFilter
        from google.api_core.exceptions import AlreadyExists, Conflict
        post = self.db.document(f"posts/{job.id}").get()
        p = post.to_dict() or {}
        if not post.exists or p.get("hidden") or p.get("category") != "lost" or p.get("status") != "open": return True
        if p.get("ownerId") != (job.to_dict() or {}).get("ownerId"): raise ValueError("Alert owner mismatch")
        query = self.db.collection("areaFollows").where(filter=FieldFilter("neighborhoods", "array_contains", p.get("neighborhood"))).order_by(FieldPath.document_id()).limit(50)
        cursor = (job.to_dict() or {}).get("cursor", "")
        if cursor: query = query.start_after({FieldPath.document_id(): self.db.document(f"areaFollows/{cursor}")})
        page = list(query.stream())
        self.metrics["pages"] += 1
        for follower in page:
            if time.monotonic() >= end: return False
            uid = follower.id
            blocked = self.db.document(f"users/{uid}/blocked/{p['ownerId']}").get()
            eligible = self.db.document(f"users/{uid}").get().exists and not self.db.document(f"accountDeletions/{uid}").get().exists
            if uid != p["ownerId"] and not blocked.exists and eligible:
                try:
                    created = int(time.time() * 1000)
                    batch = self.db.batch()
                    batch.create(self.db.document(f"users/{uid}/notifications/lost_{job.id}"), {
                        "type": "lost_nearby", "params": {"name": p.get("name", ""), "type": p.get("animalType", ""),
                            "category": "lost", "area": p.get("neighborhood", "")},
                        "route": f"post/{job.id}", "sourceId": job.id, "fromUid": self.uid, "read": False,
                        "createdAt": created})
                    batch.create(self.db.document(f"users/{uid}/notificationReceipts/lost_{job.id}"), {
                        "fromUid": self.uid, "sourceId": job.id, "createdAt": created})
                    batch.commit()
                    self.metrics["deliveries"] += 1
                except (AlreadyExists, Conflict): pass
            # Advance only after successful delivery/intentional skip. A crash replays an idempotent create.
            job.reference.update({"cursor": uid})
        return len(page) < 50
