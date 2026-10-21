import { doc, where, writeBatch, updateDoc, arrayUnion, orderBy, limit } from 'firebase/firestore';
import { database } from '../firebase';
import { col, ref, listQuery } from './Firestore';
import { RescueReportToDoc, type RescueReport, type UserProfile } from './model';
import { Dubai, LocationPrivacy } from './Neighborhoods';
import { UserRepository as users } from './UserRepository';
import { NotificationRepository as notifications } from './NotificationRepository';
import { ModerationRepository as moderation } from './ModerationRepository';
export const RescueRepository = {
  volunteerStatuses: ['approved', 'in_progress', 'resolved'],
  myReports: (uid: string) => listQuery('rescues', where('reporterId', '==', uid)),
  volunteerBoard: () =>
    listQuery('rescues', where('status', 'in', ['approved', 'in_progress', 'resolved'])),
  allReports: (status = '') =>
    listQuery(
      'rescues',
      ...(status ? [where('status', '==', status)] : []),
      orderBy('createdAt', 'desc'),
      limit(150),
    ),
  pending: () => listQuery('rescues', where('status', '==', 'submitted')),
  report: (id: string) => ref(`rescues/${id}`),
  submit: async (report: RescueReport, me: UserProfile) => {
    const target = doc(col('rescues')),
      created = Date.now(),
      hood = Dubai.nearest(report.lat, report.lng).key;
    const b = writeBatch(database());
    b.set(
      target,
      RescueReportToDoc({
        ...report,
        lat: LocationPrivacy.approximate(report.lat),
        lng: LocationPrivacy.approximate(report.lng),
        neighborhood: hood,
        reporterId: me.uid,
        reporterName: me.username,
        status: 'submitted',
        updates: [
          { status: 'submitted', note: '', byUid: me.uid, byName: me.username, at: created },
        ],
        createdAt: created,
        updatedAt: created,
      }),
    );
    users.addStats(b, me.uid, { rescueReports: 1 }, 20, hood, true);
    await b.commit();
    await notifications.send(
      (await users.uidsWithRoles('moderator', 'admin').catch(() => [])).filter(
        (uid) => uid !== me.uid,
      ),
      'rescue_moderation',
      { type: report.type, area: hood },
      `rescue/${target.id}`,
    );
    return target.id;
  },
  changeStatus: async (
    r: RescueReport,
    status: string,
    note: string,
    by: UserProfile,
    assignToMe = false,
  ) => {
    await updateDoc(ref(`rescues/${r.id}`), {
      status,
      updates: arrayUnion({
        status,
        note: note.trim(),
        byUid: by.uid,
        byName: by.username,
        at: Date.now(),
      }),
      updatedAt: Date.now(),
      ...(assignToMe ? { assignedTo: by.uid, assignedName: by.username } : {}),
    });
    if (r.status === 'submitted' && ['approved', 'rejected'].includes(status))
      await moderation.log(
        by,
        `rescue_${status}`,
        'rescue',
        r.id,
        Dubai.byKey(r.neighborhood).nameEn,
        note,
      );
    await notifications.send(
      [r.reporterId].filter((uid) => uid !== by.uid),
      'rescue_update',
      { status, area: r.neighborhood },
      `rescue/${r.id}`,
    );
    if (status === 'approved')
      await notifications.send(
        (await users.uidsWithRoles('volunteer', 'moderator', 'admin').catch(() => [])).filter(
          (uid) => uid !== by.uid,
        ),
        'rescue_volunteer',
        { type: r.type, area: r.neighborhood },
        `rescue/${r.id}`,
      );
  },
  addNote: async (r: RescueReport, note: string, by: UserProfile) => {
    await updateDoc(ref(`rescues/${r.id}`), {
      updates: arrayUnion({
        status: r.status,
        note: note.trim(),
        byUid: by.uid,
        byName: by.username,
        at: Date.now(),
      }),
      updatedAt: Date.now(),
    });
    await notifications.send(
      [r.reporterId].filter((uid) => uid !== by.uid),
      'rescue_update',
      { status: r.status, area: r.neighborhood },
      `rescue/${r.id}`,
    );
  },
};
