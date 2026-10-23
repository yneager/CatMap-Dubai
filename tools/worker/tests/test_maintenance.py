"""Deterministic durable-job tests; no client, model, credentials or network."""
import sys
import time
import queue
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from maintenance import Maintenance

class Ref:
    def __init__(self, db, path): self.db,self.path=db,path
    @property
    def id(self): return self.path.split('/')[-1]
    def get(self, **_): return Snap(self.db,self.path)
    def update(self, fields): self.db.docs[self.path].update(fields)
    def delete(self): self.db.docs.pop(self.path,None)

class Snap:
    def __init__(self, db, path): self.reference=Ref(db,path);self.id=self.reference.id;self.exists=path in db.docs
    def to_dict(self): return dict(self.reference.db.docs.get(self.reference.path,{}))

class Query:
    def __init__(self,db,path,filters=(),cursor='',maximum=999,group=False): self.db,self.path,self.filters,self.cursor,self.maximum,self.group=db,path,filters,cursor,maximum,group
    def where(self,filter): return Query(self.db,self.path,(*self.filters,filter),self.cursor,self.maximum,self.group)
    def order_by(self,*_,**__): return self
    def limit(self,n): return Query(self.db,self.path,self.filters,self.cursor,n,self.group)
    def start_after(self,cursor): return Query(self.db,self.path,self.filters,(cursor.id if isinstance(cursor,Snap) else next(iter(cursor.values())).id),self.maximum,self.group)
    def stream(self):
        result=[]
        for path,data in sorted(self.db.docs.items()):
            parts=path.split('/')
            if (self.group and parts[-2]!=self.path) or (not self.group and '/'.join(parts[:-1])!=self.path):continue
            if parts[-1]<=self.cursor:continue
            def matches(f):
                value=data.get(f.field_path)
                return value==f.value if f.op_string=='==' else f.value in (value or []) if f.op_string=='array_contains' else value in f.value if f.op_string=='in' else value is not None and value<f.value
            if all(matches(f) for f in self.filters): result.append(Snap(self.db,path))
        return iter(result[:self.maximum])

class Batch:
    def __init__(self,db):self.db=db;self.writes=[]
    def create(self,ref,data):self.writes.append((ref.path,data))
    def commit(self):
        from google.api_core.exceptions import AlreadyExists
        if self.db.fail_at==self.db.commits: self.db.fail_at=None;raise OSError('injected outage')
        if any(p in self.db.docs for p,_ in self.writes):raise AlreadyExists('duplicate')
        self.db.docs.update(self.writes);self.db.commits+=1

class DB:
    def __init__(self,docs):self.docs=docs;self.commits=0;self.fail_at=None
    def document(self,path):return Ref(self,path)
    def collection(self,path):return Query(self,path)
    def collection_group(self,path):return Query(self,path,group=True)
    def batch(self):return Batch(self)
    def transaction(self):return SimpleNamespace(update=lambda ref,fields:ref.update(fields))

class MaintenanceTests(unittest.TestCase):
    def fixture(self,count=350):
        docs={'posts/lost':{'ownerId':'owner','name':'Demo','animalType':'cat','neighborhood':'deira','category':'lost','status':'open','hidden':False},'lostAlertJobs/lost':{'ownerId':'owner','cursor':'','status':'pending'}}
        docs.update({f'areaFollows/f{i:04}':{'neighborhoods':['deira']} for i in range(count)})
        docs.update({f'users/f{i:04}':{'username':f'f{i:04}'} for i in range(count)})
        return DB(docs)
    def test_more_than_300_followers_survive_restart_and_replay(self):
        db=self.fixture();job=Snap(db,'lostAlertJobs/lost')
        worker=Maintenance(db,'worker',queue.Queue());self.assertFalse(worker.lost_alerts(job,time.monotonic()+10))
        worker=Maintenance(db,'worker',queue.Queue())
        while not worker.lost_alerts(Snap(db,job.reference.path),time.monotonic()+10):pass
        self.assertEqual(sum('/notifications/' in p for p in db.docs),350)
        db.docs[job.reference.path]['cursor']=''
        while not worker.lost_alerts(Snap(db,job.reference.path),time.monotonic()+10):pass
        self.assertEqual(sum('/notifications/' in p for p in db.docs),350)
    def test_failure_does_not_advance_past_undelivered_follower(self):
        db=self.fixture(12);db.fail_at=5;w=Maintenance(db,'worker',queue.Queue())
        with self.assertRaises(OSError):w.lost_alerts(Snap(db,'lostAlertJobs/lost'),time.monotonic()+10)
        self.assertEqual(db.docs['lostAlertJobs/lost']['cursor'],'f0004')
        self.assertTrue(w.lost_alerts(Snap(db,'lostAlertJobs/lost'),time.monotonic()+10))
        self.assertEqual(db.commits,12)
    def test_hidden_source_sends_nothing(self):
        db=self.fixture();db.docs['posts/lost']['hidden']=True
        self.assertTrue(Maintenance(db,'worker',queue.Queue()).lost_alerts(Snap(db,'lostAlertJobs/lost'),time.monotonic()+10));self.assertEqual(db.commits,0)
    def test_deleted_deleting_and_blocked_followers_do_not_stall_fanout(self):
        db=self.fixture(5);del db.docs['users/f0000'];db.docs['accountDeletions/f0001']={};db.docs['users/f0002/blocked/owner']={}
        self.assertTrue(Maintenance(db,'worker',queue.Queue()).lost_alerts(Snap(db,'lostAlertJobs/lost'),time.monotonic()+10))
        self.assertEqual(db.commits,2);self.assertEqual(db.docs['lostAlertJobs/lost']['cursor'],'f0004')
    def test_shared_cat_and_other_authors_update_keep_image(self):
        for source,data in [('cats/shared',{'photoIds':['pic']}),('posts/p/updates/u',{'photoId':'pic'}),('users/owner',{'photoId':'pic'})]:
            db=DB({'images/pic':{},'images/pic_t':{},source:data});Maintenance(db,'worker',queue.Queue()).remove_image('pic');self.assertIn('images/pic',db.docs)
    def test_current_deleted_child_does_not_keep_its_own_orphan(self):
        db=DB({'images/pic':{},'images/pic_t':{},'posts/p/updates/u':{'photoId':'pic'}});w=Maintenance(db,'worker',queue.Queue());w.deleting_child='posts/p/updates/u';w.remove_image('pic');self.assertNotIn('images/pic',db.docs)
    def test_media_delete_failure_keeps_retryable_state(self):
        db=DB({'videoUploads/v':{'path':'videos/u/v','status':'deleting'}});w=Maintenance(db,'worker',queue.Queue());w.try_finish_media(Snap(db,'videoUploads/v'),db.docs['videoUploads/v']);self.assertEqual(db.docs['videoUploads/v']['status'],'deleting');self.assertEqual(w.metrics['failures'],1)
    def test_one_failed_media_reference_check_does_not_block_others(self):
        db=DB({'mediaUploads/a':{'status':'staged','expiresAt':0},'mediaUploads/b':{'status':'staged','expiresAt':0},'images/b':{},'images/b_t':{}})
        w=Maintenance(db,'worker',queue.Queue())
        def referenced(job,data):
            if job.id=='a':raise OSError('one bad reference')
            return False
        w.media_referenced=referenced
        with patch('google.cloud.firestore.transactional',lambda f:f):w.reap_media(time.monotonic()+10)
        self.assertEqual(db.docs['mediaUploads/b']['status'],'deleted');self.assertEqual(db.docs['mediaUploads/a']['status'],'staged')
    def test_renewed_lease_wins_before_claim(self):
        db=DB({'mediaUploads/p':{'ownerId':'u','status':'staged','expiresAt':0}});w=Maintenance(db,'worker',queue.Queue())
        def renew(job,data):db.docs[job.reference.path]['expiresAt']=int(time.time()*1000)+100000;return False
        w.media_referenced=renew
        with patch('google.cloud.firestore.transactional',lambda f:f): w.reap_media(time.monotonic()+10)
        self.assertEqual(db.docs['mediaUploads/p']['status'],'staged')

if __name__=='__main__':unittest.main()
