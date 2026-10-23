"""Exercise the actual Worker.handle without importing its GUI, credentials or models."""
import ast
import queue
import sys
import time
import types
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parent.parent
tree = ast.parse((ROOT / 'server.py').read_text(encoding='utf-8'))
worker = next(n for n in tree.body if isinstance(n, ast.ClassDef) and n.name == 'Worker')
handle = next(n for n in worker.body if isinstance(n, ast.FunctionDef) and n.name == 'handle')
namespace = {'time': time, 'MODEL_NAME': 'test', 'CLIP_NAME': 'test', 'MAX_IMAGES': 16}
exec(compile(ast.Module(body=[handle], type_ignores=[]), str(ROOT / 'server.py'), 'exec'), namespace)


class Reference:
    def __init__(self):
        self.current = {'requestId': 'A', 'status': 'pending'}
        self.fail = False
        self.writes = 0

    def get(self, transaction=None):
        return types.SimpleNamespace(to_dict=lambda: dict(self.current),update_time=getattr(self,'version',None))

    def update(self, ref, value):
        if self.fail: raise OSError('injected outage')
        self.current.update(value)
        self.writes += 1


def make(ref, uid='alice'):
    doc = types.SimpleNamespace(id=uid, reference=ref,
        to_dict=lambda: {'uid': uid, 'requestId': 'A', 'status': 'pending', 'kind': 'compare', 'query': b'jpeg', 'images': [b'jpeg']})
    obj = types.SimpleNamespace(answered=set(), handled=0, model=types.SimpleNamespace(provider='test'),
        events=queue.Queue(), username=lambda _: 'test', compare=lambda *args: [], retry_jobs={}, retry_attempts={},
        fs=types.SimpleNamespace(client=types.SimpleNamespace(transaction=lambda: ref)))
    return obj, doc


class JobProtocol(unittest.TestCase):
    def setUp(self):
        firestore = types.ModuleType('google.cloud.firestore')
        firestore.SERVER_TIMESTAMP = 'timestamp'
        firestore.transactional = lambda fn: fn
        google, cloud = types.ModuleType('google'), types.ModuleType('google.cloud')
        google.cloud, cloud.firestore = cloud, firestore
        self.modules = patch.dict(sys.modules, {'google': google, 'google.cloud': cloud, 'google.cloud.firestore': firestore})
        self.modules.start()
        self.addCleanup(self.modules.stop)

    def test_stale_inference_cannot_answer_a_replacement(self):
        ref = Reference(); obj, doc = make(ref)
        def replace(*_): ref.current = {'requestId': 'B', 'status': 'pending'}; return []
        obj.compare = replace
        namespace['handle'](obj, doc)
        self.assertEqual(ref.current, {'requestId': 'B', 'status': 'pending'})
        self.assertEqual(ref.writes, 0)

    def test_failed_commit_is_retryable(self):
        ref = Reference(); obj, doc = make(ref); ref.fail = True
        namespace['handle'](obj, doc)
        self.assertNotIn(('alice', 'A', None), obj.answered)
        self.assertIn(('alice', 'A', None), obj.retry_jobs)
        ref.fail = False
        namespace['handle'](obj, doc)
        self.assertEqual(ref.writes, 1)
        self.assertEqual(ref.current['answeredRequestId'], 'A')

    def test_same_request_id_with_new_source_version_is_not_overwritten(self):
        ref=Reference();ref.version=1;obj,doc=make(ref);doc.update_time=1
        def replace(*_):ref.version=2;return []
        obj.compare=replace
        namespace['handle'](obj,doc)
        self.assertEqual(ref.writes,0);self.assertEqual(ref.current['status'],'pending')

    def test_dedupe_is_scoped_to_user_and_request(self):
        ref = Reference(); obj, doc = make(ref)
        obj.answered.add(('bob', 'A', None))
        namespace['handle'](obj, doc)
        namespace['handle'](obj, doc)
        self.assertEqual(ref.writes, 1)

    def test_failed_retries_back_off(self):
        ref = Reference(); obj, doc = make(ref); ref.fail = True
        namespace['handle'](obj, doc); first = obj.retry_jobs[('alice', 'A', None)][0]
        namespace['handle'](obj, doc); second = obj.retry_jobs[('alice', 'A', None)][0]
        self.assertGreater(second, first)

    def test_answer_memory_is_bounded(self):
        ref = Reference(); obj, doc = make(ref)
        obj.answered = {('user', str(n)) for n in range(2048)}
        namespace['handle'](obj, doc)
        self.assertLessEqual(len(obj.answered), 2048)


if __name__ == '__main__': unittest.main()
