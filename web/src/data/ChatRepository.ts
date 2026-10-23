import {
  doc,
  where,
  orderBy,
  limitToLast,
  setDoc,
  updateDoc,
  deleteDoc,
  writeBatch,
  increment,
  getDocFromServer,
  getDocsFromServer,
  orderBy as orderMessages,
  limit,
  runTransaction,
} from 'firebase/firestore';
import { ImageRepository } from './ImageRepository';
import { database } from '../firebase';
import { col, ref, read, listQuery, AppException } from './Firestore';
import {
  ChatDefaults,
  ChatFromDoc,
  ChatToDoc,
  MessageFromDoc,
  MessageDefaults,
  MessageToDoc,
  type Chat,
  type Message,
  type UserProfile,
  type Post,
} from './model';
import { UserRepository } from './UserRepository';
import { NotificationRepository as notifications } from './NotificationRepository';
export const ChatRepository = {
  chatId: (a: string, b: string) => [a, b].sort().join('_'),
  chatsFor: (uid: string) => listQuery('chats', where('members', 'array-contains', uid)),
  chatFlow: (id: string) => ref(`chats/${id}`),
  messages: (id: string) =>
    listQuery(`chats/${id}/messages`, orderBy('createdAt'), limitToLast(50)),
  open: async (me: UserProfile, otherUid: string) => {
    if (!otherUid || otherUid === me.uid) throw new AppException('err_message_self');
    const id = ChatRepository.chatId(me.uid, otherUid);
    if (!(await read(`chats/${id}`, ChatFromDoc))) {
      const other = await UserRepository.profile(otherUid);
      const members = [me.uid, otherUid].sort();
      await setDoc(
        ref(`chats/${id}`),
        ChatToDoc({
          ...ChatDefaults,
          members,
          names: { [me.uid]: me.username, [otherUid]: other?.username ?? '' },
          photos: { [me.uid]: me.photoId, [otherUid]: other?.photoId ?? '' },
          lastAt: Date.now(),
          unread: Object.fromEntries(members.map((uid) => [uid, 0])),
        }),
      );
    }
    return id;
  },
  send: async (
    chat: Chat,
    me: UserProfile,
    text: string,
    imageId = '',
    post: Post | null = null,
  ) => {
    const clean = text.trim().slice(0, 2000);
    if (!clean && !imageId && !post) return;
    const other = chat.members.find((uid) => uid !== me.uid) ?? '';
    const preview = clean ? clean.slice(0, 120) : imageId ? 'Photo' : 'Post: ' + (post?.name ?? '');
    const b = writeBatch(database());
    b.set(
      doc(col(`chats/${chat.id}/messages`)),
      MessageToDoc({
        ...MessageDefaults,
        senderId: me.uid,
        text: clean,
        imageId,
        postId: post?.id ?? '',
        postTitle: post?.name ?? '',
        postPhotoId: post?.photoIds[0] ?? '',
        createdAt: Date.now(),
      }),
    );
    b.update(ref(`chats/${chat.id}`), {
      lastText: preview,
      lastSender: me.uid,
      lastAt: Date.now(),
      [`unread.${other}`]: increment(1),
      [`names.${me.uid}`]: me.username,
      [`photos.${me.uid}`]: me.photoId,
    });
    await b.commit();
    await notifications.send(
      [other],
      'message',
      { user: me.username, text: preview },
      `chat/${chat.id}`,
      `msg_${chat.id}`,
    );
  },
  markRead: async (id: string, uid: string) => {
    await updateDoc(ref(`chats/${id}`), { [`unread.${uid}`]: 0 }).catch(() => {});
    await notifications.dismiss(uid, `msg_${id}`);
  },
  deleteMessage: async (id: string, m: Message, uid: string) => {
    const chatRef = ref(`chats/${id}`);
    const before = await getDocFromServer(chatRef).catch(() => null);
    const newest = await getDocsFromServer(
      listQuery(`chats/${id}/messages`, orderMessages('createdAt', 'desc'), limit(2)),
    )
      .then((snap) => snap.docs.map((d) => MessageFromDoc(d.id, d.data())))
      .catch(() => []);
    await deleteDoc(ref(`chats/${id}/messages/${m.id}`));
    if (m.imageId)
      await ImageRepository.delete(m.imageId).catch((error) =>
        console.info('Message image cleanup failed', error),
      );
    if (before?.data()?.lastSender !== uid || newest[0]?.id !== m.id) return;
    await runTransaction(database(), async (tx) => {
      const fresh = await tx.get(chatRef),
        data = fresh.data(),
        old = before.data();
      if (
        data?.lastAt === old?.lastAt &&
        data?.lastSender === uid &&
        data?.lastText === old?.lastText
      ) {
        const previous = newest[1];
        const text = !previous
          ? ''
          : previous.text
            ? previous.text.slice(0, 120)
            : previous.imageId
              ? 'Photo'
              : previous.matchId
                ? ('Possible match: ' + previous.postTitle).trim()
                : 'Post: ' + previous.postTitle;
        tx.update(chatRef, { lastText: text });
      }
    }).catch((error) => console.info('Message preview cleanup failed', error));
  },
  blocked: (uid: string) => listQuery(`users/${uid}/blocked`),
  block: (me: string, other: string) =>
    setDoc(ref(`users/${me}/blocked/${other}`), { at: Date.now() }),
  unblock: (me: string, other: string) => deleteDoc(ref(`users/${me}/blocked/${other}`)),
};
