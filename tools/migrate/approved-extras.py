"""Offline planner for the approved schema transition. Never connects to Firebase.

Input: JSON object mapping document paths to plain JSON fields, from SYNTHETIC fixtures only.
Output: desired document replacements/deletions plus unresolved blockers. No credentials,
network, deployment or production export support. Review the plan before emulator rehearsal.
"""
import argparse
import copy
import hashlib
import json
import re
from collections import defaultdict
from pathlib import Path


def plan(documents):
    desired = copy.deepcopy(documents)
    blockers, notes = [], []
    def put(path, value): desired[path] = value
    public = set()
    for path, data in documents.items():
        parts = path.split('/')
        if len(parts) == 2 and parts[0] == 'cats':
            if not isinstance(data.get('hidden',False),bool) or not isinstance(data.get('mergedInto',''),str):
                blockers.append(path+': invalid visibility metadata')
            else:
                put(path,{**data,'hidden':data.get('hidden',False),'mergedInto':data.get('mergedInto','')})
        if len(parts) == 2 and parts[0] in ('posts', 'cats', 'sightings', 'rescues'):
            public.update(data.get('photoIds', []))
        if (len(parts) == 2 and parts[0] in ('users', 'sitters')) or (len(parts) == 4 and parts[0] == 'posts' and parts[2] == 'updates'):
            if data.get('photoId'): public.add(data['photoId'])
    private = defaultdict(list)
    for path, data in documents.items():
        parts = path.split('/')
        if len(parts) == 4 and parts[0] == 'chats' and parts[2] == 'messages' and data.get('imageId'):
            private[data['imageId']].append((path, parts[1], parts[3], data))
    for image_id, messages in private.items():
        for path, chat, message, data in messages:
            existing = documents.get('images/' + image_id, {})
            if image_id.startswith('c_') and existing.get('chatId') == chat and existing.get('messageId') == message and existing.get('rootId') == image_id:
                continue
            chat_data = documents.get('chats/' + chat, {})
            if data.get('senderId') not in chat_data.get('members', []):
                blockers.append(path + ': sender is not a chat member'); continue
            new_id = 'c_' + hashlib.sha256((chat + '/' + message + '/' + image_id).encode()).hexdigest()[:32]
            valid = True
            for suffix in ('', '_t'):
                image = documents.get('images/' + image_id + suffix)
                if image is None or image.get('ownerId') != data['senderId']:
                    blockers.append(path + ': missing image or ownership mismatch ' + suffix); valid = False
            if not valid: continue
            copies={'images/'+new_id+suffix:{**documents['images/'+image_id+suffix], 'audience':'chat','chatId':chat,'rootId':new_id,'messageId':message} for suffix in ('','_t')}
            if any(key in documents and documents[key]!=value for key,value in copies.items()) or ('mediaUploads/'+new_id in documents and documents['mediaUploads/'+new_id].get('ownerId')!=data['senderId']):
                blockers.append(path + ': destination photo or lease conflicts'); continue
            for suffix in ('', '_t'):
                put('images/' + new_id + suffix, {**documents['images/' + image_id + suffix], 'audience': 'chat', 'chatId': chat, 'rootId': new_id, 'messageId': message})
            if 'mediaUploads/' + new_id not in desired:
                put('mediaUploads/' + new_id, {'ownerId':data['senderId'],'chatId':chat,'audience':'chat','status':'linked','expiresAt':int(documents['images/'+image_id].get('createdAt',0))+7*86400000})
            put(path, {**data, 'imageId': new_id})
        if image_id in public:
            notes.append(image_id + ': previously shared publicly; copied private references, retained public original. Prior downloads cannot be withdrawn.')
        elif not image_id.startswith('c_') and not any(b.startswith(path + ':') for path, *_ in messages for b in blockers):
            desired.pop('images/' + image_id, None); desired.pop('images/' + image_id + '_t', None)
    for image_id in public:
        if image_id.startswith('c_'): blockers.append(image_id + ': private ID appears in public content'); continue
        for suffix in ('', '_t'):
            key='images/' + image_id + suffix
            if key in desired: put(key, {**desired[key], 'audience': 'community', 'chatId': '', 'rootId': image_id, 'messageId': ''})
    names = defaultdict(list)
    for path, data in documents.items():
        parts=path.split('/')
        if len(parts)==2 and parts[0]=='users':
            uid=parts[1]; name=data.get('username','').lower()
            if not re.fullmatch(r'[a-z0-9_.]{3,20}',name): blockers.append(path+': username requires owner-reviewed rename'); continue
            names[name].append(uid)
            claim=documents.get('usernames/'+name,{})
            if claim.get('uid',uid)!=uid: blockers.append(path+': username claim belongs to another UID')
            else: put('usernames/'+name,{**claim,'uid':uid})
            counts={bucket:sum(p.startswith(f'users/{uid}/{bucket}/') and len(p.split('/'))==4 for p in documents) for bucket in ('favorites','blocked')}
            if any(v>500 for v in counts.values()): blockers.append(path+': existing private list exceeds capacity; owner must choose entries')
            counts['unreadCount']=sum(int(c.get('unread',{}).get(uid,0)) for p,c in documents.items() if p.startswith('chats/') and len(p.split('/'))==2 and uid in c.get('members',[]))
            put('userLimits/'+uid,counts)
        if len(parts)==2 and parts[0]=='areaFollows':
            neighborhoods=data.get('neighborhoods',[])
            if not isinstance(neighborhoods,list) or len(neighborhoods)>200 or any(not isinstance(n,str) or re.fullmatch(r'[a-z0-9_-]{1,64}',n) is None for n in neighborhoods) or len(','.join(neighborhoods))>4000:
                blockers.append(path+': invalid neighborhood list'); continue
            private_path=f'users/{parts[1]}/private/areaFollow'
            if any(k in data for k in ('lat','lng','radiusKm')):
                centre={k:data[k] for k in ('lat','lng','radiusKm') if k in data}
                if len(centre)!=3 or not (-90<=centre['lat']<=90 and -180<=centre['lng']<=180 and 0<centre['radiusKm']<=100): blockers.append(path+': invalid legacy centre'); continue
                centre['updatedAt']=data.get('updatedAt',0)
                if private_path in documents and documents[private_path]!=centre: blockers.append(path+': private centre conflict'); continue
                put(private_path,centre)
                put(path,{k:v for k,v in data.items() if k not in ('lat','lng','radiusKm')})
    for name,uids in names.items():
        if len(uids)>1: blockers.append(name+': duplicate canonical usernames')
    replacements={p:v for p,v in desired.items() if documents.get(p)!=v}
    return {'schemaVersion':1,'syntheticOnly':True,'replacements':replacements,'deletions':sorted(set(documents)-set(desired)), 'blockers':sorted(set(blockers)), 'notes':notes}


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--synthetic-input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    result=plan(json.loads(args.synthetic_input.read_text(encoding='utf-8')))
    args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'replacements':len(result['replacements']),'deletions':len(result['deletions']),'blockers':len(result['blockers']),'applied':False}))
