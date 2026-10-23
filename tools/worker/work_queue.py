"""Bounded ID queues. Overflow requests bounded refill from authoritative listener state."""
from collections import OrderedDict
import queue, threading
import time
class WorkQueue:
    def __init__(self, capacity=4096):
        self.capacity, self.items, self.lock = capacity, OrderedDict(), threading.Lock()
        self.overflow = False
    def put(self, item):
        key = item[0] if isinstance(item, tuple) else item
        with self.lock:
            self.items[key] = item
            if len(self.items) > self.capacity:
                self.items.popitem(last=False)
                self.overflow = True
    def get_nowait(self):
        with self.lock:
            if not self.items: raise queue.Empty()
            return self.items.popitem(last=False)[1]
    def empty(self):
        with self.lock: return not self.items
    def qsize(self):
        with self.lock: return len(self.items)


class LatestJobQueue:
    """At most one pending snapshot per UID; older retries cannot replace a newer request."""
    def __init__(self, capacity=256):
        self.capacity, self.items, self.condition = capacity, OrderedDict(), threading.Condition()

    @staticmethod
    def version(doc):
        stamp = getattr(doc, 'update_time', None)
        return stamp.timestamp() if hasattr(stamp, 'timestamp') else float(stamp or 0)

    def put_nowait(self, doc):
        with self.condition:
            old = self.items.get(doc.id)
            if old is not None and self.version(old) >= self.version(doc): return
            if old is None and len(self.items) >= self.capacity: raise queue.Full()
            self.items[doc.id] = doc
            self.condition.notify()

    def get(self, timeout=5):
        end = time.monotonic() + timeout
        with self.condition:
            while not self.items:
                remaining = end - time.monotonic()
                if remaining <= 0: raise queue.Empty()
                self.condition.wait(remaining)
            return self.items.popitem(last=False)[1]

    def empty(self):
        with self.condition: return not self.items

    def qsize(self):
        with self.condition: return len(self.items)
