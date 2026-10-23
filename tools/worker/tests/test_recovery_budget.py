"""Exercise actual recovery scheduling/backfill methods without models or a Firebase client."""
import ast
import queue
import sys
import threading
import time
import types
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from work_queue import WorkQueue
tree=ast.parse((Path(__file__).resolve().parent.parent/'recovery.py').read_text(encoding='utf-8'))
cls=next(n for n in tree.body if isinstance(n,ast.ClassDef) and n.name=='Recovery')
methods=[n for n in cls.body if isinstance(n,ast.FunctionDef) and n.name in ('check','refill_overflow','refill_sources','fill_geo_cells')]
namespace={'time':time,'queue':queue};exec(compile(ast.Module(body=methods,type_ignores=[]),'recovery.py','exec'),namespace)

def fixture():
    obj=types.SimpleNamespace(lock=threading.Lock(),todo=WorkQueue(2),geo_todo=WorkQueue(2),removed=WorkQueue(2),
        posts={k:{} for k in 'abcde'},geo_queued={},geo_versions={},todo_refill=None,geo_refill=None,
        retry_at={},geo_fixed=0,geo_failed=False,events=queue.Queue(),healthy=lambda:True,listening_since=time.time())
    obj.refill_overflow=types.MethodType(namespace['refill_overflow'],obj)
    obj.restart=lambda:(_ for _ in ()).throw(AssertionError('Overflow must not restart a healthy listener'))
    obj.load_embeddings=lambda:(_ for _ in ()).throw(AssertionError('Overflow must not redownload embeddings'))
    return obj

class BudgetTests(unittest.TestCase):
    def test_match_overflow_refills_all_ids_without_database_resync(self):
        obj=fixture()
        for key in obj.posts:obj.todo.put(key)
        namespace['check'](obj);seen=set()
        for _ in range(10):
            while not obj.todo.empty():seen.add(obj.todo.get_nowait())
            namespace['refill_sources'](obj);self.assertLessEqual(obj.todo.qsize(),2)
        self.assertEqual(seen,set(obj.posts));self.assertIsNone(obj.todo_refill)
    def test_geo_overflow_uses_current_pending_values_with_bounded_queue(self):
        obj=fixture();obj.geo_queued={k:{'geoCells':[k]} for k in 'abcde'}
        for key,value in obj.geo_queued.items():obj.geo_todo.put((key,value))
        namespace['check'](obj);seen=set()
        for _ in range(10):
            while not obj.geo_todo.empty():seen.add(obj.geo_todo.get_nowait()[0])
            namespace['refill_sources'](obj);self.assertLessEqual(obj.geo_todo.qsize(),2)
        self.assertEqual(seen,set(obj.geo_queued));self.assertIsNone(obj.geo_refill)
    def backfill(self,current,replace=False):
        obj=fixture();fill={'geoCells':['old']};obj.geo_queued={'p':fill};obj.geo_versions={'p':1};obj.geo_todo.put(('p',fill))
        state={'writes':0,'fields':{'geoCells':['newer']}}
        def update(fields,option):
            if replace:obj.geo_queued['p']={'geoCells':['latest']};obj.geo_versions['p']=2
            if option!=current:raise OSError('source version changed')
            state['fields']=fields;state['writes']+=1
        obj.db=types.SimpleNamespace(document=lambda _:types.SimpleNamespace(update=update),write_option=lambda **kw:kw['last_update_time'])
        namespace['fill_geo_cells'](obj);return obj,state
    def test_unchanged_backfill_succeeds_and_releases_pending_state(self):
        obj,state=self.backfill(1);self.assertEqual(state['writes'],1);self.assertFalse(obj.geo_queued);self.assertFalse(obj.geo_versions)
    def test_backfill_cannot_overwrite_a_newer_post_revision(self):
        obj,state=self.backfill(2);self.assertEqual(state['writes'],0);self.assertEqual(state['fields'],{'geoCells':['newer']});self.assertEqual(obj.geo_todo.qsize(),1)
    def test_failed_old_backfill_requeues_the_latest_payload(self):
        obj,state=self.backfill(2,True);self.assertEqual(obj.geo_todo.get_nowait()[1],{'geoCells':['latest']})

if __name__=='__main__':unittest.main()
