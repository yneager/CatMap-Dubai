import {
  doc,
  where,
  orderBy,
  limitToLast,
  setDoc,
  writeBatch,
  increment,
  getDocsFromServer,
  orderBy as orderMessages,
  limit,
  runTransaction,
} from 'firebase/firestore';
import { ImageRepository } from './ImageRepository';
import { setPrivateSlot } from './PrivateSlots';
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
  chatsQuery: (uid: string) =>
    listQuery('chats', where('members', 'array-contains', uid), orderBy('lastAt', 'desc')),
  chatsFor: (uid: string) =>
    listQuery(
      'chats',
      where('members', 'array-contains', uid),
      orderBy('lastAt', 'desc'),
      limit(50),
    ),
  chatFlow: (id: string) => ref(`chats/${id}`),
  messages: (id: string) =>
    listQuery(`chats/${id}/messages`, orderBy('createdAt'), limitToLast(50)),
  open: async (me: UserProfile, otherUid: string) => {
    if (!otherUid || otherUid === me.uid) throw new AppException('err_message_self');
    const id = ChatRepository.chatId(me.uid, otherUid);
    if (!(await read(`chats/${id}`, ChatFromDoc))) {
      const other = await UserRepository.profile(otherUid);
      const members = [me.uid, otherUid].sort();
      try {
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
      } catch (e) {
        // Two opens at once (or the other person opening it too): the rules refuse the second create as an
        // overwrite, and the chat is there to use (ChatRepository.kt does the same).
        if (!(await read(`chats/${id}`, ChatFromDoc))) throw e;
      }
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
    await ImageRepository.retain([imageId]);
    const other = chat.members.find((uid) => uid !== me.uid) ?? '';
    const preview = clean ? clean.slice(0, 120) : imageId ? 'Photo' : 'Post: ' + (post?.name ?? '');
    const messageRef = doc(col(`chats/${chat.id}/messages`));
    const createdAt = Date.now();
    const b = writeBatch(database());
    b.set(
      messageRef,
      MessageToDoc({
        ...MessageDefaults,
        senderId: me.uid,
        text: clean,
        imageId,
        postId: post?.id ?? '',
        postTitle: post?.name ?? '',
        postPhotoId: post?.photoIds[0] ?? '',
        createdAt,
      }),
    );
    b.update(ref(`chats/${chat.id}`), {
      lastText: preview,
      lastSender: me.uid,
      lastMessageId: messageRef.id,
      lastAt: createdAt,
      [`unread.${other}`]: increment(1),
      [`names.${me.uid}`]: me.username,
      [`photos.${me.uid}`]: me.photoId,
    });
    if (imageId) {
      b.update(ref(`images/${imageId}`), { messageId: messageRef.id });
      b.update(ref(`images/${imageId}_t`), { messageId: messageRef.id });
    }
    b.update(ref(`userLimits/${other}`), {
      unreadCount: increment(1),
      unreadChatId: chat.id,
      unreadMessageId: messageRef.id,
    });
    await b.commit();
    await notifications.send(
      [other],
      'message',
      { user: me.username, text: preview },
      `chat/${chat.id}`,
      `msg_${chat.id}_${messageRef.id}`,
      messageRef.id,
    );
  },
  markRead: async (id: string, uid: string) => {
    await runTransaction(database(), async (tx) => {
      const chat = await tx.get(ref(`chats/${id}`));
      if (!chat.exists()) return;
      const count = Number(chat.data().unread?.[uid] ?? 0);
      if (!count) return;
      tx.update(chat.ref, { [`unread.${uid}`]: 0 });
      tx.update(ref(`userLimits/${uid}`), { unreadCount: increment(-count), unreadChatId: id });
    }).catch((error) => console.info('Read marker will retry', error));
    await notifications.dismiss(uid, `msg_${id}`);
    await notifications
      .readChat(uid, id)
      .catch((error) => console.info('Chat alerts will be marked read on retry', error));
  },
  deleteMessage: async (id: string, m: Message, uid: string) => {
    const chatRef = ref(`chats/${id}`);
    // Querying outside a web transaction is necessary, but every selected document is re-read
    // inside it. A concurrent deletion restarts the query; a concurrent send conflicts on chat.
    for (let attempt = 0; ; attempt++) {
      const newest = await getDocsFromServer(
        listQuery(`chats/${id}/messages`, orderMessages('createdAt', 'desc'), limit(2)),
      );
      try {
        await runTransaction(database(), async (tx) => {
          const fresh = await tx.get(chatRef);
          const target = await tx.get(ref(`chats/${id}/messages/${m.id}`));
          const data = fresh.data();
          if (target.exists() && target.data().senderId !== uid)
            throw new Error('Message owner mismatch');
          const isLast = data?.lastMessageId
            ? data.lastMessageId === m.id
            : newest.docs[0]?.id === m.id &&
              data?.lastSender === uid &&
              data?.lastAt === m.createdAt;
          let previous: Message | undefined;
          if (isLast) {
            const candidate = newest.docs.find((d) => d.id !== m.id);
            if (candidate) {
              const checked = await tx.get(candidate.ref);
              if (!checked.exists()) throw new Error('retry-message-predecessor');
              previous = MessageFromDoc(checked.id, checked.data());
            }
          }
          tx.delete(ref(`chats/${id}/messages/${m.id}`));
          if (isLast)
            tx.update(chatRef, {
              lastMessageId: previous?.id ?? '',
              lastDeletedMessageId: m.id,
              lastSender: previous?.senderId ?? '',
              lastAt: previous?.createdAt ?? 0,
              lastText: previous ? messagePreview(previous) : '',
            });
        });
        break;
      } catch (error) {
        if (
          !(error instanceof Error) ||
          error.message !== 'retry-message-predecessor' ||
          attempt >= 4
        )
          throw error;
      }
    }
    if (m.imageId)
      await ImageRepository.delete(m.imageId).catch((error) =>
        console.info('Message image cleanup failed', error),
      );
  },
  blocked: (uid: string) => listQuery(`users/${uid}/blocked`),
  block: async (me: string, other: string) => {
    await setPrivateSlot(me, 'blocked', other, { at: Date.now() });
    await ImageRepository.clearCache();
  },
  unblock: (me: string, other: string) => setPrivateSlot(me, 'blocked', other, null),
};

export const messagePreview = (m: Message): string =>
  m.text
    ? m.text.slice(0, 120)
    : m.imageId
      ? 'Photo'
      : m.matchId
        ? ('Possible match: ' + m.postTitle).trim()
        : 'Post: ' + m.postTitle;
