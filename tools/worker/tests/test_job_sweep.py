"""The real cleanup function must not delete a newly reused UID job document."""
import ast
import queue
import unittest
from datetime import datetime,timedelta,timezone
from pathlib import Path
from types import SimpleNamespace
from google.api_core.exceptions import FailedPrecondition
tree=ast.parse((Path(__file__).resolve().parent.parent/'server.py').read_text(encoding='utf-8'))
worker=next(n for n in tree.body if isinstance(n,ast.ClassDef) and n.name=='Worker')
method=next(n for n in worker.body if isinstance(n,ast.FunctionDef) and n.name=='sweep_jobs')
namespace={};exec(compile(ast.Module(body=[method],type_ignores=[]),'server.py','exec'),namespace)

class Query:
    def __init__(self,doc):self.doc=doc
    def where(self,**_):return self
    def select(self,*_):return self
    def order_by(self,*_):return self
    def limit(self,*_):return self
    def stream(self):return iter([self.doc])

class SweepTests(unittest.TestCase):
    def run_sweep(self,version):
        result={'deletes':0,'version':version}
        def delete(option):
            if option!=result['version']:raise FailedPrecondition('replaced request')
            result['deletes']+=1
        doc=SimpleNamespace(update_time=1,reference=SimpleNamespace(delete=delete),to_dict=lambda:{'doneAt':datetime.now(timezone.utc)-timedelta(hours=1)})
        client=SimpleNamespace(collection=lambda _:Query(doc),write_option=lambda **kw:kw['last_update_time'])
        obj=SimpleNamespace(fs=SimpleNamespace(client=client),events=queue.Queue())
        namespace['sweep_jobs'](obj);return result
    def test_unchanged_answered_snapshot_can_be_deleted(self):self.assertEqual(self.run_sweep(1)['deletes'],1)
    def test_replacement_pending_request_is_not_deleted(self):self.assertEqual(self.run_sweep(2)['deletes'],0)

if __name__=='__main__':unittest.main()
