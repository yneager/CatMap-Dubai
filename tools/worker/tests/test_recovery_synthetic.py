"""Explicit emulator integration using synthetic vectors; no model downloads or credentials."""
import os
import queue
import sys
import time
from pathlib import Path
from types import SimpleNamespace
import numpy as np
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from emulator_safety import require_emulator

def main():
    project=os.environ.get('GCLOUD_PROJECT','demo-catmap-recovery')
    host=os.environ.get('FIRESTORE_EMULATOR_HOST','127.0.0.1:8180')
    require_emulator(project,host)
    os.environ['FIRESTORE_EMULATOR_HOST']=host
    from google.auth.credentials import AnonymousCredentials
    from google.cloud import firestore
    sys.modules['recognition']=SimpleNamespace(EMBEDDING_MODEL='synthetic/v1',from_blob=lambda b:np.frombuffer(b,dtype='<f4').copy())
    import recovery
    db=firestore.Client(project=project,credentials=AnonymousCredentials())
    now=int(time.time()*1000)
    posts={}
    for pid,owner,category in [('synthetic_L','synthetic_owner','lost'),('synthetic_S','synthetic_seer','spotted')]:
        posts[pid]={'id':pid,'ownerId':owner,'ownerName':owner,'animalType':'dog','category':category,'status':'open','hidden':False,'name':'Synthetic',
            'photoIds':[pid+'_photo'],'lat':25.2,'lng':55.27,'approximate':False,'neighborhood':'deira','createdAt':now,'updatedAt':now,'lastSeenAt':now,'color':'brown','features':'red collar'}
        db.document('posts/'+pid).set({k:v for k,v in posts[pid].items() if k!='id'})
        vector=np.zeros(512,dtype='<f4');vector[0]=1
        db.document('postEmbeddings/'+pid).set({'model':'synthetic/v1','signature':recovery.signature(posts[pid]),'vectors':[vector.tobytes()],'createdAt':firestore.SERVER_TIMESTAMP})
    worker=recovery.Recovery(db,None,queue.Queue(),'synthetic_worker');worker.posts=posts;worker.load_embeddings();worker.handle('synthetic_L')
    mid='synthetic_L__synthetic_S'
    match=db.document('matches/'+mid).get()
    assert match.exists and match.to_dict()['tier']=='strong'
    assert db.document('users/synthetic_owner/notificationReceipts/pm_'+mid).get().exists
    worker2=recovery.Recovery(db,None,queue.Queue(),'synthetic_worker');worker2.posts=posts;worker2.load_embeddings();worker2.handle('synthetic_L')
    assert len(list(db.collection('users/synthetic_owner/notifications').stream()))==1
    db.document('posts/synthetic_L').delete();worker2.sweep_orphans()
    assert not db.document('postEmbeddings/synthetic_L').get().exists
    assert db.document('postEmbeddings/synthetic_S').get().exists
    print('Synthetic recovery emulator: 5 checks passed')

if __name__=='__main__':main()
