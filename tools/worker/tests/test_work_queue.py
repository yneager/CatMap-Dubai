import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from work_queue import WorkQueue, LatestJobQueue


class QueueTests(unittest.TestCase):
    def test_coalesces_latest_payload_and_bounds_memory(self):
        q = WorkQueue(2)
        q.put(('a', 1)); q.put(('a', 2)); q.put(('b', 1))
        self.assertEqual(q.qsize(), 2)
        self.assertEqual(q.get_nowait(), ('a', 2))
        q.put('c'); q.put('d')
        self.assertEqual(q.qsize(), 2)
        self.assertTrue(q.overflow)

    def test_empty_queue_uses_standard_signal(self):
        import queue
        with self.assertRaises(queue.Empty): WorkQueue().get_nowait()

    def test_one_busy_user_uses_one_slot_and_cannot_overwrite_a_newer_job(self):
        from types import SimpleNamespace
        q = LatestJobQueue(2)
        for revision in range(100): q.put_nowait(SimpleNamespace(id='alice', update_time=revision))
        q.put_nowait(SimpleNamespace(id='bob', update_time=1))
        q.put_nowait(SimpleNamespace(id='alice', update_time=0))
        self.assertEqual(q.qsize(), 2)
        self.assertEqual(q.get().update_time, 99)
        self.assertEqual(q.get().id, 'bob')

    def test_queue_saturation_is_explicit_and_requests_a_refill(self):
        import queue
        from types import SimpleNamespace
        q = LatestJobQueue(1)
        q.put_nowait(SimpleNamespace(id='alice', update_time=1))
        with self.assertRaises(queue.Full): q.put_nowait(SimpleNamespace(id='bob', update_time=1))


if __name__ == '__main__': unittest.main()
