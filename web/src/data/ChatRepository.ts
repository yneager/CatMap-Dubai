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
} from 'firebase/firestore';
import { database } from '../firebase';
import { col, ref, read, listQuery, AppException } from './Firestore';
import {
  ChatDefaults,
  ChatFromDoc,
  ChatToDoc,
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
    listQuery(`chats/${id}/messages`, orderBy('createdAt'), limitToLast(300)),
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
  deleteMessage: (id: string, m: Message) => deleteDoc(ref(`chats/${id}/messages/${m.id}`)),
  blocked: (uid: string) => listQuery(`users/${uid}/blocked`),
  block: (me: string, other: string) =>
    setDoc(ref(`users/${me}/blocked/${other}`), { at: Date.now() }),
  unblock: (me: string, other: string) => deleteDoc(ref(`users/${me}/blocked/${other}`)),
};
