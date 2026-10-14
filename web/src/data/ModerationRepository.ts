import {
  doc,
  where,
  orderBy,
  limit,
  writeBatch,
  setDoc,
  updateDoc,
  serverTimestamp,
} from 'firebase/firestore';
import { database } from '../firebase';
import { col, ref, listQuery } from './Firestore';
import {
  ContentReportDefaults,
  ContentReportToDoc,
  ModActionDefaults,
  ModActionToDoc,
  VolunteerApplicationDefaults,
  VolunteerApplicationToDoc,
  type UserProfile,
  type ContentReport,
  type VolunteerApplication,
} from './model';
import { NotificationRepository as notifications } from './NotificationRepository';
export const ModerationRepository = {
  reportContent: async (
    targetType: string,
    targetId: string,
    targetLabel: string,
    reason: string,
    details: string,
    me: UserProfile,
  ) => {
    const b = writeBatch(database());
    b.set(
      doc(col('contentReports')),
      ContentReportToDoc({
        ...ContentReportDefaults,
        targetType,
        targetId,
        targetLabel: targetLabel.slice(0, 80),
        reason,
        details: details.trim().slice(0, 500),
        reporterId: me.uid,
        reporterName: me.username,
        createdAt: Date.now(),
      }),
    );
    b.update(ref(`users/${me.uid}`), { lastPostAt: serverTimestamp() });
    await b.commit();
  },
  openReports: () => listQuery('contentReports', where('status', '==', 'open')),
  closeReport: async (r: ContentReport, status: string, resolution: string, me: UserProfile) => {
    await updateDoc(ref(`contentReports/${r.id}`), { status, resolution, resolvedBy: me.uid });
    await ModerationRepository.log(
      me,
      `report_${status}`,
      r.targetType,
      r.targetId,
      r.targetLabel,
      resolution,
    );
    await notifications.send([r.reporterId], 'moderation', {
      target: 'report',
      label: r.targetLabel,
      decision: status,
    });
  },
  log: async (
    me: UserProfile,
    action: string,
    targetType: string,
    targetId: string,
    targetLabel: string,
    note: string,
  ) => {
    await setDoc(
      doc(col('modActions')),
      ModActionToDoc({
        ...ModActionDefaults,
        moderatorId: me.uid,
        moderatorName: me.username,
        action,
        targetType,
        targetId,
        targetLabel,
        note,
        at: Date.now(),
      }),
    ).catch(() => {});
  },
  actions: () => listQuery('modActions', orderBy('at', 'desc'), limit(300)),
  applicationFlow: (uid: string) => ref(`volunteerApplications/${uid}`),
  pendingApplications: () => listQuery('volunteerApplications', where('status', '==', 'pending')),
  applyAsVolunteer: async (
    me: UserProfile,
    motivation: string,
    experience: string,
    area: string,
  ) => {
    const b = writeBatch(database());
    b.set(
      ref(`volunteerApplications/${me.uid}`),
      VolunteerApplicationToDoc({
        ...VolunteerApplicationDefaults,
        userId: me.uid,
        username: me.username,
        motivation: motivation.trim(),
        experience: experience.trim(),
        area,
        createdAt: Date.now(),
      }),
    );
    b.update(ref(`users/${me.uid}`), { volunteerStatus: 'applied' });
    await b.commit();
  },
  decideApplication: async (a: VolunteerApplication, approve: boolean, admin: UserProfile) => {
    const b = writeBatch(database());
    b.update(ref(`volunteerApplications/${a.id}`), {
      status: approve ? 'approved' : 'rejected',
      reviewedBy: admin.uid,
    });
    b.update(ref(`users/${a.userId}`), {
      volunteerStatus: approve ? 'approved' : 'rejected',
      ...(approve ? { role: 'volunteer' } : {}),
    });
    await b.commit();
    await ModerationRepository.log(
      admin,
      approve ? 'volunteer_approved' : 'volunteer_rejected',
      'user',
      a.userId,
      a.username,
      '',
    );
    await notifications.send(
      [a.userId],
      'volunteer_decision',
      { decision: approve ? 'approved' : 'rejected' },
      'profile',
    );
  },
  changeRole: async (user: UserProfile, role: string, admin: UserProfile) => {
    await updateDoc(ref(`users/${user.uid}`), {
      role,
      volunteerStatus: role === 'user' ? 'none' : 'approved',
    });
    await ModerationRepository.log(admin, `role_${role}`, 'user', user.uid, user.username, '');
    await notifications.send(
      [user.uid],
      'moderation',
      { target: 'role', label: role, decision: 'changed' },
      'profile',
    );
  },
};
