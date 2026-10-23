import { ReportButton } from '../components/ReportButton';
import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useApp, useList, useDoc, useAction } from '../../state';
import { ChatRepository } from '../../data/ChatRepository';
import { UserRepository } from '../../data/UserRepository';
import { ImageRepository } from '../../data/ImageRepository';
import { PostRepository } from '../../data/PostRepository';
import { useOlder } from '../../data/useOlder';
import { listQuery } from '../../data/Firestore';
import { orderBy } from 'firebase/firestore';
import { ChatFromDoc, MessageFromDoc } from '../../data/model';
import { processPhoto } from '../../util/images';
import { t, dateTime } from '../../i18n';
import {
  AppTopBar,
  Bell,
  Avatar,
  EmptyState,
  Loading,
  Field,
  Photo,
  ControlIcon,
  Dialog,
  ErrorText,
} from '../components/Components';
export default function MessagesScreens() {
  const location = useLocation(),
    { id = '', uid: otherUid = '' } = useParams(),
    navigate = useNavigate(),
    { profile, chats } = useApp(),
    action = useAction(),
    [text, setText] = useState(''),
    [newChat, setNewChat] = useState(false),
    [username, setUsername] = useState('');
  const endRef = useRef<HTMLDivElement>(null);
  const uid = profile?.uid ?? '',
    isChat = location.pathname.startsWith('/chat/');
  const chat = useDoc(
      () => (isChat ? ChatRepository.chatFlow(id) : null),
      ChatFromDoc,
      'chat-' + id + '-' + isChat,
    ),
    liveMessages = useList(
      () => (isChat ? ChatRepository.messages(id) : null),
      MessageFromDoc,
      'messages-' + id + '-' + isChat,
    ),
    blocked = useList(
      () => (uid ? ChatRepository.blocked(uid) : null),
      (id, data) => ({ id, ...data }),
      'blocked-' + uid,
    );
  const history = useOlder(
    () => listQuery(`chats/${id}/messages`, orderBy('createdAt', 'desc')),
    MessageFromDoc,
    'chat-' + id,
    50,
  );
  const messages = liveMessages
    ? [...new Map([...history.items, ...liveMessages].map((m) => [m.id, m])).values()].sort(
        (a, b) => a.createdAt - b.createdAt,
      )
    : undefined;
  const postId = new URLSearchParams(location.search).get('postId');
  useEffect(() => {
    if (!otherUid || !profile) return;
    let active = true;
    void ChatRepository.open(profile, otherUid)
      .then((chatId) => {
        if (active)
          navigate('/chat/' + chatId + (postId ? '?postId=' + postId : ''), { replace: true });
      })
      .catch(() => {
        if (active) navigate('/messages', { replace: true });
      });
    return () => {
      active = false;
    };
  }, [otherUid, profile, postId, navigate]);
  useEffect(() => {
    if (isChat && uid && chat) void ChatRepository.markRead(id, uid);
  }, [isChat, uid, id, chat, messages]);
  useEffect(() => {
    if (isChat && messages?.length)
      endRef.current?.scrollIntoView({
        behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
        block: 'end',
      });
  }, [isChat, messages?.length]);
  if (otherUid) return <Loading />;
  if (!isChat)
    return (
      <>
        <AppTopBar title={t('tab_messages')} back={false}>
          <Bell />
          <button
            className="icon-button"
            aria-label={t('message')}
            onClick={() => setNewChat(true)}
          >
            <ControlIcon name="plus" />
          </button>
        </AppTopBar>
        <div className="screen">
          {!chats ? (
            <Loading />
          ) : chats.length ? (
            chats
              .sort((a, b) => b.lastAt - a.lastAt)
              .map((c) => {
                const other = c.members.find((x) => x !== uid) ?? '';
                return (
                  <Link key={c.id} className="chat-row" to={'/chat/' + c.id}>
                    <Avatar id={c.photos[other]} name={c.names[other] ?? ''} />
                    <div>
                      <strong>@{c.names[other]}</strong>
                      <p dir="auto">{c.lastText}</p>
                      <small>{dateTime(c.lastAt)}</small>
                    </div>
                    {c.unread[uid] > 0 && <span className="badge static">{c.unread[uid]}</span>}
                  </Link>
                );
              })
          ) : (
            <EmptyState text={t('no_chats')} />
          )}
        </div>
        {newChat && (
          <Dialog title={t('message')} onClose={() => setNewChat(false)}>
            <Field label={t('username')} value={username} onChange={setUsername} />
            <ErrorText text={action.error} />
            <button
              className="primary"
              disabled={action.busy || !username.trim()}
              onClick={() =>
                void action.run(async () => {
                  const other = await UserRepository.findByUsername(username);
                  if (!other) throw new Error('User missing');
                  navigate('/chat/' + (await ChatRepository.open(profile!, other.uid)));
                  setNewChat(false);
                })
              }
            >
              {t('message')}
            </button>
          </Dialog>
        )}
      </>
    );
  if (chat === undefined) return <Loading />;
  if (!chat) return <EmptyState text={t('no_chats')} />;
  const other = chat.members.find((x) => x !== uid) ?? '',
    isBlocked = blocked?.some((x) => x.id === other);
  return (
    <>
      <AppTopBar title={'@' + (chat.names[other] ?? '')}>
        <ReportButton type="user" id={other} label={chat.names[other] ?? ''} />
        <button
          className="text-button"
          disabled={action.busy}
          onClick={() =>
            void action.run(() =>
              isBlocked ? ChatRepository.unblock(uid, other) : ChatRepository.block(uid, other),
            )
          }
        >
          {t(isBlocked ? 'unblock_user' : 'block_user')}
        </button>
        <Link className="text-button" to={'/user/' + other}>
          {t('view_profile')}
        </Link>
      </AppTopBar>
      <div className="chat-messages">
        {!history.done && (liveMessages?.length ?? 0) >= 50 && (
          <button
            className="text-button"
            disabled={history.busy}
            onClick={() => void history.load(`chats/${id}/messages/${liveMessages![0].id}`)}
          >
            {t('older_messages')}
          </button>
        )}
        {messages?.map((m) => (
          <div className={'message-bubble ' + (m.senderId === uid ? 'mine' : '')} key={m.id}>
            {m.text && <p dir="auto">{m.text}</p>}
            {m.imageId && (
              <Photo id={m.imageId} onClick={() => navigate('/photo/' + m.imageId)} />
            )}{' '}
            {m.matchId ? (
              <Link className="shared-post match-message" to={'/match/' + m.matchId}>
                <span className="match-pair">
                  <Photo id={m.postPhotoId} alt={t('match_lost_photo')} />
                  <Photo id={m.matchPhotoId} alt={t('match_sighting_photo')} />
                </span>
                <strong dir="auto">
                  {t('match_card_chat') + (m.postTitle ? ': ' + m.postTitle : '')}
                </strong>
                <small>{t('match_card_open')}</small>
              </Link>
            ) : (
              m.postId && (
                <Link className="shared-post" to={'/post/' + m.postId}>
                  <Photo id={m.postPhotoId} />
                  <strong dir="auto">{m.postTitle || t('post_new')}</strong>
                </Link>
              )
            )}
            <small>{dateTime(m.createdAt)}</small>
            {m.senderId === uid && (
              <button
                className="text-button small"
                onClick={() => void action.run(() => ChatRepository.deleteMessage(id, m, profile!.uid))}
              >
                {t('delete_message')}
              </button>
            )}
          </div>
        ))}
        <div ref={endRef} className="chat-end-space" />
      </div>
      {isBlocked ? (
        <p className="screen">{t('you_blocked_user')}</p>
      ) : (
        <form
          className="chat-composer"
          onSubmit={(e) => {
            e.preventDefault();
            void action.run(async () => {
              await ChatRepository.send(chat, profile!, text);
              setText('');
            });
          }}
        >
          {postId && (
            <button
              type="button"
              className="outlined"
              onClick={() =>
                void action.run(async () => {
                  const p = await PostRepository.post(postId);
                  if (p) await ChatRepository.send(chat, profile!, '', '', p);
                  navigate('/chat/' + id, { replace: true });
                })
              }
            >
              {t('share')}
            </button>
          )}
          <label className="icon-button file-button" aria-label={t('add_photo')}>
            <ControlIcon name="plus" />
            <input
              type="file"
              accept="image/*"
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file)
                  void action.run(async () => {
                    const imageId = await ImageRepository.upload(uid, await processPhoto(file));
                    await ChatRepository.send(chat, profile!, '', imageId);
                  });
              }}
            />
          </label>
          <textarea
            aria-label={t('type_message')}
            placeholder={t('type_message')}
            dir="auto"
            value={text}
            maxLength={2000}
            onChange={(e) => setText(e.target.value)}
            rows={1}
          />
          <button
            className="icon-button"
            aria-label={t('send')}
            disabled={action.busy || !text.trim()}
          >
            <ControlIcon name="send" />
          </button>
          <ErrorText text={action.error} />
        </form>
      )}
    </>
  );
}
