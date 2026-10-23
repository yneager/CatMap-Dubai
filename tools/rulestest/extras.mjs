// Approved schema contracts, exercised only by the local emulator suite.
import {assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {doc,setDoc,getDoc,getDocs,deleteDoc,updateDoc,writeBatch,collection,query,where,increment,serverTimestamp,Bytes} from 'firebase/firestore';

export async function runExtras({env,check,ctx,profile,postDoc,catDoc,sightingDoc,userStats}) {
  console.log('Approved extras: source contracts and recovery');
  const A=ctx('extra_a'),B=ctx('extra_b'),X=ctx('extra_x');
  const seed=async(path,value)=>env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),path),value));
  for(const uid of ['extra_a','extra_b','extra_x']) {
    await seed('users/'+uid,profile(uid));
    await seed('usernames/'+uid,{uid});
    await seed('userLimits/'+uid,{favorites:0,blocked:0,unreadCount:0});
  }
  const batch=async(db,fn)=>{const b=writeBatch(db);fn(b);await b.commit();};
  const photo=(owner,root,audience='community',chatId='')=>({ownerId:owner,rootId:root,audience,chatId,messageId:'',createdAt:Date.now(),data:Bytes.fromUint8Array(new Uint8Array([255,216,255,0]))});
  const chat='extra_a_extra_b';
  await seed('chats/'+chat,{members:['extra_a','extra_b'],names:{extra_a:'extra_a',extra_b:'extra_b'},photos:{extra_a:'',extra_b:''},unread:{extra_a:0,extra_b:0},lastMessageId:'',lastDeletedMessageId:'',lastText:'',lastSender:'',lastAt:0});
  await check('R07 forged chat names refused',assertFails(setDoc(doc(A,'chats','extra_a_extra_x'),{members:['extra_a','extra_x'],names:{extra_a:'admin',extra_x:'extra_x'},photos:{extra_a:'',extra_x:''},unread:{extra_a:0,extra_x:0},lastMessageId:'',lastText:'',lastSender:'',lastAt:0})));
  await check('R11 unleased private upload refused',assertFails(setDoc(doc(A,'images','c_extra'),photo('extra_a','c_extra','chat',chat))));
  await check('R01 staged private image with lease uploads',assertSucceeds(batch(A,b=>{
    b.set(doc(A,'images','c_extra'),photo('extra_a','c_extra','chat',chat));
    b.set(doc(A,'mediaUploads','c_extra'),{ownerId:'extra_a',chatId:chat,audience:'chat',status:'staged',expiresAt:Date.now()+7*86400000});
  })));
  await check('R01 staged image hidden from counterpart',assertFails(getDoc(doc(B,'images','c_extra'))));
  await check('R01 outsider cannot fetch private ID',assertFails(getDoc(doc(X,'images','c_extra'))));
  await check('R01 sender can recover staged photo',assertSucceeds(getDoc(doc(A,'images','c_extra'))));
  const at=Date.now(),message={senderId:'extra_a',text:'Hello',imageId:'c_extra',postId:'',postTitle:'',postPhotoId:'',createdAt:at};
  await check('R14 message without counter batch refused',assertFails(setDoc(doc(A,'chats',chat,'messages','event'),message)));
  await check('R01 R09 R14 atomic message preview image binding and total allowed',assertSucceeds(batch(A,b=>{
    b.set(doc(A,'chats',chat,'messages','event'),message);
    b.update(doc(A,'images','c_extra'),{messageId:'event'});
    b.update(doc(A,'chats',chat),{lastMessageId:'event',lastText:'Hello',lastSender:'extra_a',lastAt:at,'unread.extra_b':1});
    b.update(doc(A,'userLimits','extra_b'),{unreadCount:1,unreadChatId:chat,unreadMessageId:'event'});
  })));
  await check('R01 attached image visible to counterpart',assertSucceeds(getDoc(doc(B,'images','c_extra'))));
  await check('R14 standalone unread reset refused',assertFails(updateDoc(doc(B,'chats',chat),{'unread.extra_b':0})));
  await check('R14 unread reset and complete total change together',assertSucceeds(batch(B,b=>{b.update(doc(B,'chats',chat),{'unread.extra_b':0});b.update(doc(B,'userLimits','extra_b'),{unreadCount:0,unreadChatId:chat});})));
  await seed('users/extra_b/blocked/extra_a',{at:Date.now()});
  await check('R01 block revokes counterpart image access',assertFails(getDoc(doc(B,'images','c_extra'))));
  await env.withSecurityRulesDisabled(c=>deleteDoc(doc(c.firestore(),'users/extra_b/blocked/extra_a')));
  await check('R09 cannot overwrite preview without a source',assertFails(updateDoc(doc(B,'chats',chat),{lastText:'forged'})));
  await check('R09 empty preview repaired with own deletion',assertSucceeds(batch(A,b=>{b.delete(doc(A,'chats',chat,'messages','event'));b.update(doc(A,'chats',chat),{lastMessageId:'',lastDeletedMessageId:'event',lastText:'',lastSender:'',lastAt:0});})));
  await check('R01 deleted message revokes orphan image access',assertFails(getDoc(doc(B,'images','c_extra'))));
  await check('R03 unknown private document refused',assertFails(setDoc(doc(A,'users','extra_a','private','payload'),{blob:'x'})));
  await check('R03 private email cannot differ from token',assertFails(setDoc(doc(A,'users','extra_a','private','account'),{email:'someone@x.test',createdAt:Date.now()})));
  await check('R03 favorite requires capacity counter',assertFails(setDoc(doc(A,'users','extra_a','favorites','pet'),{postId:'pet',savedAt:Date.now()})));
  await check('R03 real favorite plus slot allowed',assertSucceeds(batch(A,b=>{b.set(doc(A,'users','extra_a','favorites','pet'),{postId:'pet',savedAt:Date.now()});b.update(doc(A,'userLimits','extra_a'),{favorites:1,favoritesSource:'pet'});})));
  await seed('userLimits/extra_a',{favorites:500,blocked:0,unreadCount:0});
  await check('R03 favorite quota cannot exceed 500',assertFails(batch(A,b=>{b.set(doc(A,'users','extra_a','favorites','overflow'),{postId:'overflow',savedAt:Date.now()});b.update(doc(A,'userLimits','extra_a'),{favorites:501,favoritesSource:'overflow'});})));
  await seed('posts/extra_post',postDoc('extra_a'));
  const update={postId:'extra_post',kind:'sighting',text:'Seen',photoId:'',lat:25,lng:55,neighborhood:'deira',byUid:'extra_b',byName:'extra_b',createdAt:Date.now()};
  const writeUpdate=override=>batch(B,b=>{b.set(doc(B,'posts','extra_post','updates','invalid'),{...update,...override});b.update(doc(B,'posts','extra_post'),{updatesCount:increment(1),counterSourceId:'invalid'});b.update(doc(B,'users','extra_b'),{lastPostAt:serverTimestamp()});});
  for(const [label,fields] of [['oversize text',{text:'x'.repeat(1001)}],['extra payload',{payload:'x'}],['false author',{byName:'admin'}],['unknown kind',{kind:'unknown'}],['invalid latitude',{lat:91}]])
    await check('R02 '+label+' refused',assertFails(writeUpdate(fields)));
  await check('R02 update requires atomic parent count',assertFails(setDoc(doc(B,'posts','extra_post','updates','u'),update)));
  await check('R02 R05 valid source and parent batch',assertSucceeds(batch(B,b=>{b.set(doc(B,'posts','extra_post','updates','u'),update);b.update(doc(B,'posts','extra_post'),{updatesCount:increment(1),counterSourceId:'u'});b.update(doc(B,'users','extra_b'),{lastPostAt:serverTimestamp()});})));
  await seed('posts/extra_post',{...postDoc('extra_a'),hidden:true,updatesCount:1});
  await check('R02 hidden child inaccessible to outsider',assertFails(getDoc(doc(X,'posts','extra_post','updates','u'))));
  await check('R02 own authored child remains exportable',assertSucceeds(getDoc(doc(B,'posts','extra_post','updates','u'))));
  await check('R02 hidden collection cannot be broadly queried',assertFails(getDocs(collection(X,'posts','extra_post','updates'))));
  await check('R06 hidden child deletion can leave a durable count repair',assertSucceeds(batch(B,b=>{b.delete(doc(B,'posts','extra_post','updates','u'));b.set(doc(B,'updateRepairs','10_extra_post_u'),{postId:'extra_post',updateId:'u',byUid:'extra_b'});})));
  await check('R05 count decrement cannot replay a deleted source',assertFails(updateDoc(doc(A,'posts','extra_post'),{updatesCount:0,counterSourceId:'u'})));
  await check('R02 orphan creation refused',assertFails(setDoc(doc(B,'posts','missing','updates','u'),{...update,postId:'missing'})));
  await check('R25 legacy public precise centre refused',assertFails(setDoc(doc(A,'areaFollows','extra_a'),{lat:25,lng:55,radiusKm:2,neighborhoods:['deira'],updatedAt:Date.now()})));
  await check('R25 neighborhood-only follow remains allowed',assertSucceeds(setDoc(doc(A,'areaFollows','extra_a'),{neighborhoods:['deira'],updatedAt:Date.now()})));
  await check('R07 unmatched username claim refused',assertFails(setDoc(doc(A,'usernames','unbound'),{uid:'extra_a'})));
  await seed('cats/extra_cat',{...catDoc('extra_a'),photoIds:['shared','mine'],coverPhotoId:'shared',contributors:['extra_a','extra_b']});
  await seed('images/mine',photo('extra_b','mine'));await seed('images/shared',photo('extra_a','shared'));
  await check('R06 cannot clear another persons cover without removing an owned photo',assertFails(updateDoc(doc(B,'cats','extra_cat'),{coverPhotoId:'',removedPhotoIds:[]})));
  await check('R06 cannot remove another persons shared photo',assertFails(updateDoc(doc(B,'cats','extra_cat'),{photoIds:['mine'],coverPhotoId:'mine',removedPhotoIds:['shared']})));
  await check('R06 narrow own photo and contributor detach allowed',assertSucceeds(updateDoc(doc(B,'cats','extra_cat'),{photoIds:['shared'],coverPhotoId:'shared',removedPhotoIds:['mine'],contributors:['extra_a']})));
  await check('R06 detachment cannot add membership',assertFails(updateDoc(doc(X,'cats','extra_cat'),{contributors:['extra_a','extra_x']})));
  await seed('users/extra_x',{...profile('extra_x'),suspended:true});
  await check('R06 suspended deletion starts with UID tombstone',assertSucceeds(setDoc(doc(X,'accountDeletions','extra_x'),{requestedAt:serverTimestamp()})));
  await check('R06 suspended own profile may be deleted after tombstone',assertSucceeds(deleteDoc(doc(X,'users','extra_x'))));
  await check('R06 tombstone prevents same UID profile recreation',assertFails(setDoc(doc(X,'users','extra_x'),profile('extra_x'))));
  // New message events remain distinct after dismissal. A receipt prevents replay of the same event.
  const n={type:'message',sourceId:'notify1',route:'chat/'+chat,fromUid:'extra_a',read:false,params:{user:'extra_a',text:'First'},createdAt:Date.now()};
  await seed('chats/'+chat+'/messages/notify1',{...message,text:'First',imageId:''});
  const notify=(id,value)=>batch(A,b=>{b.set(doc(A,'users','extra_b','notifications',id),value);b.set(doc(A,'users','extra_b','notificationReceipts',id),{fromUid:'extra_a',sourceId:value.sourceId,createdAt:value.createdAt});});
  await check('R04 truthful notification plus receipt allowed',assertSucceeds(notify('msg_'+chat+'_notify1',n)));
  await check('R04 misleading source text refused',assertFails(notify('msg_'+chat+'_notify1',{...n,params:{user:'extra_a',text:'fake'}})));
  await deleteDoc(doc(B,'users','extra_b','notifications','msg_'+chat+'_notify1'));
  await check('R04 dismissal does not permit event replay',assertFails(notify('msg_'+chat+'_notify1',n)));
  await seed('chats/'+chat+'/messages/notify2',{...message,text:'Second',imageId:''});
  await check('R04 second distinct message still alerts',assertSucceeds(notify('msg_'+chat+'_notify2',{...n,sourceId:'notify2',params:{user:'extra_a',text:'Second'}})));
  const Y=ctx('extra_y');await seed('users/extra_y',profile('extra_y'));await seed('usernames/extra_y',{uid:'extra_y'});
  const adopt={postId:'extra_adopt',postName:'Rex',postPhotoId:'',ownerId:'extra_a',requesterId:'extra_y',requesterName:'extra_y',message:'Demo',home:'Demo',status:'pending',ownerNote:'',createdAt:Date.now(),decidedAt:0};
  for(const [label,fields] of [['closed',{status:'resolved'}],['hidden',{hidden:true}],['non adoption',{category:'lost'}],['already adopted',{adoptionStatus:'adopted'}]]) {
    await seed('posts/extra_adopt',postDoc('extra_a',{category:'adoption',...fields}));
    await check('R05 '+label+' request refused',assertFails(setDoc(doc(Y,'adoptionRequests','extra_adopt_extra_y'),adopt)));
  }
  await seed('posts/extra_adopt',postDoc('extra_a',{category:'adoption'}));
  await check('R05 active adoption request allowed',assertSucceeds(setDoc(doc(Y,'adoptionRequests','extra_adopt_extra_y'),adopt)));
  await seed('sitters/extra_a',{name:'extra_a',active:true});
  const booking={ownerId:'extra_y',ownerName:'extra_y',sitterId:'extra_a',sitterName:'extra_a',petTypes:['cat'],petCount:1,startAt:Date.now(),endAt:Date.now()+86400000,notes:'',sitterNote:'',status:'requested',reviewed:false,createdAt:Date.now(),updatedAt:Date.now()};
  const book=override=>batch(Y,b=>{b.set(doc(Y,'bookings','extra_booking'),{...booking,...override});b.update(doc(Y,'users','extra_y'),{lastPostAt:serverTimestamp()});});
  for(const [label,fields] of [['backwards dates',{endAt:0}],['zero duration',{endAt:booking.startAt}],['negative date',{startAt:-1}],['bad count',{petCount:0}],['unknown pet',{petTypes:['dragon']}],['false sitter name',{sitterName:'admin'}]])
    await check('R05 '+label+' booking refused',assertFails(book(fields)));
  await check('R05 valid booking with rate stamp allowed',assertSucceeds(book({})));
  await check('R05 moderator cannot accept another persons booking',assertFails(updateDoc(doc(ctx('mod'),'bookings','extra_booking'),{status:'accepted',updatedAt:Date.now()})));
  await seed('posts/extra_reunion',postDoc('extra_a'));
  await check('R05 unrelated match cannot receive reunion credit',assertFails(updateDoc(doc(A,'posts','extra_reunion'),{status:'reunited',resolution:'catmap',resolvedMatchId:'other_match',resolvedAt:Date.now()})));
}
