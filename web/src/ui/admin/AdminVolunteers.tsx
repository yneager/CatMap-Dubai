import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useApp, useList, useAction } from '../../state';
import { AdminRepository } from '../../data/AdminRepository';
import { ModerationRepository } from '../../data/ModerationRepository';
import { UserRepository } from '../../data/UserRepository';
import {
  UserProfileFromDoc,
  VolunteerApplicationFromDoc,
  type UserProfile,
} from '../../data/model';
import { t, dateTime } from '../../i18n';
import {
  AppTopBar,
  Avatar,
  Field,
  Dialog,
  Loading,
  EmptyState,
  ErrorText,
} from '../components/Components';
export default function AdminVolunteers() {
  const { profile } = useApp(),
    action = useAction(),
    [tab, setTab] = useState('applications'),
    [search, setSearch] = useState(''),
    [manage, setManage] = useState<UserProfile | null>(null);
  const applications = useList(
      ModerationRepository.pendingApplications,
      VolunteerApplicationFromDoc,
      'volunteer-applications',
    ),
    users = useList(AdminRepository.users, UserProfileFromDoc, 'volunteer-users');
  const shown = users?.filter(
    (u) =>
      (tab === 'volunteers' ? u.role === 'volunteer' : ['moderator', 'admin'].includes(u.role)) &&
      [u.username, u.displayName].join(' ').toLowerCase().includes(search.toLowerCase()),
  );
  return (
    <>
      <AppTopBar title={t('volunteers')} />
      <div className="screen">
        <div className="segments">
          {['applications', 'volunteers', 'staff'].map((value) => (
            <button
              key={value}
              className={tab === value ? 'selected' : ''}
              onClick={() => setTab(value)}
            >
              {t(value)}
            </button>
          ))}
        </div>
        {tab !== 'applications' && (
          <Field label={t('search')} value={search} onChange={setSearch} />
        )}
        <ErrorText text={action.error} />
        {tab === 'applications' ? (
          applications === undefined ? (
            <Loading />
          ) : applications.length ? (
            applications.map((a) => (
              <article className="card" key={a.id}>
                <Link to={'/user/' + a.userId}>
                  <h3>@{a.username}</h3>
                </Link>
                <p>{a.area}</p>
                <p dir="auto">{a.motivation}</p>
                <p dir="auto">{a.experience}</p>
                <small>{dateTime(a.createdAt)}</small>
                <div className="row">
                  {[true, false].map((approve) => (
                    <button
                      className="outlined"
                      key={String(approve)}
                      disabled={action.busy}
                      onClick={() =>
                        void action.run(
                          () => ModerationRepository.decideApplication(a, approve, profile!),
                          t('updated'),
                        )
                      }
                    >
                      {t(approve ? 'approve' : 'reject')}
                    </button>
                  ))}
                </div>
              </article>
            ))
          ) : (
            <EmptyState text={t('queue_empty')} />
          )
        ) : shown === undefined ? (
          <Loading />
        ) : (
          <>
            {shown.map((u) => (
              <article className="card row" key={u.uid}>
                <Avatar name={u.username} id={u.photoId} />
                <Link to={'/user/' + u.uid}>
                  @{u.username}
                  <small>{t('role_' + u.role)}</small>
                </Link>
                {u.uid !== profile?.uid && (
                  <button className="text-button" onClick={() => setManage(u)}>
                    {t('change_role')}
                  </button>
                )}
              </article>
            ))}
            {!shown.length && <EmptyState text={t('no_volunteers')} />}{' '}
            {tab === 'staff' && (
              <button
                className="outlined"
                disabled={!search.trim() || action.busy}
                onClick={() =>
                  void action.run(async () => {
                    const user = await UserRepository.findByUsername(search);
                    if (!user) throw new Error('User not found');
                    setManage(user);
                  })
                }
              >
                {t('find_user')}
              </button>
            )}
          </>
        )}
      </div>
      {manage && (
        <Dialog title={t('change_role')} onClose={() => setManage(null)}>
          {['user', 'volunteer', 'moderator', 'admin']
            .filter((role) => role !== manage.role)
            .map((role) => (
              <button
                className="link-row"
                key={role}
                disabled={action.busy || manage.uid === profile?.uid}
                onClick={() =>
                  void action.run(async () => {
                    await ModerationRepository.changeRole(manage, role, profile!);
                    setManage(null);
                  }, t('updated'))
                }
              >
                {t('make_x', t('role_' + role))}
              </button>
            ))}
        </Dialog>
      )}
    </>
  );
}
