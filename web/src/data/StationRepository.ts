import {
  doc,
  where,
  setDoc,
  deleteDoc,
  writeBatch,
  updateDoc,
  increment,
} from 'firebase/firestore';
import { database } from '../firebase';
import { col, ref, listQuery } from './Firestore';
import {
  FeedingStationToDoc,
  FeedingStationDefaults,
  StationCheckinDefaults,
  StationCheckinToDoc,
  MaintenanceReportDefaults,
  MaintenanceReportToDoc,
  type FeedingStation,
  type MaintenanceReport,
  type UserProfile,
} from './model';
import { Dubai } from './Neighborhoods';
import { UserRepository as users } from './UserRepository';
import { ModerationRepository as moderation } from './ModerationRepository';
import { NotificationRepository as notifications } from './NotificationRepository';
export const StationRepository = {
  stations: () => listQuery('stations'),
  station: (id: string) => ref(`stations/${id}`),
  save: async (station: FeedingStation, admin: UserProfile) => {
    const target = station.id ? ref(`stations/${station.id}`) : doc(col('stations'));
    await setDoc(
      target,
      FeedingStationToDoc({
        ...station,
        neighborhood: Dubai.nearest(station.lat, station.lng).key,
        createdAt: station.createdAt || Date.now(),
        updatedAt: Date.now(),
      }),
    );
    await moderation.log(
      admin,
      station.id ? 'station_updated' : 'station_created',
      'station',
      target.id,
      station.name,
      '',
    );
    return target.id;
  },
  delete: async (s: FeedingStation, me: UserProfile) => {
    await deleteDoc(ref(`stations/${s.id}`));
    await moderation.log(me, 'station_deleted', 'station', s.id, s.name, '');
  },
  seedSamples: async (me: UserProfile) => {
    const samples: [string, number, number][] = [
      ['Sample – Al Barsha Pond Park', 25.1027, 55.2003],
      ['Sample – Deira Souq Corner', 25.2706, 55.299],
      ['Sample – Karama Park', 25.241, 55.301],
      ['Sample – JLT Cluster D', 25.07, 55.1415],
      ['Sample – Mirdif Community Garden', 25.217, 55.418],
      ['Sample – Al Quoz Pond Park', 25.144, 55.238],
    ];
    const b = writeBatch(database());
    for (const [name, lat, lng] of samples)
      b.set(
        doc(col('stations')),
        FeedingStationToDoc({
          ...FeedingStationDefaults,
          name,
          lat,
          lng,
          description: 'Example station created by the admin seeding tool. Edit or delete it.',
          operatorName: 'Ehsan volunteers',
          schedule: 'Daily, after Maghrib',
          foodType: 'Dry food',
          neighborhood: Dubai.nearest(lat, lng).key,
          sample: true,
          createdAt: Date.now(),
          updatedAt: Date.now(),
        }),
      );
    await b.commit();
    await moderation.log(me, 'stations_seeded', 'station', '', String(samples.length), '');
  },
  checkins: (id: string) => listQuery('checkins', where('stationId', '==', id)),
  maintenanceFor: (id: string) => listQuery('maintenance', where('stationId', '==', id)),
  openMaintenance: () => listQuery('maintenance', where('status', 'in', ['open', 'in_progress'])),
  checkIn: async (
    s: FeedingStation,
    catsSeen: number,
    catIds: string[],
    food: string,
    water: string,
    note: string,
    me: UserProfile,
  ) => {
    const at = Date.now(),
      hour = Number(
        new Intl.DateTimeFormat('en', {
          hour: 'numeric',
          hourCycle: 'h23',
          timeZone: 'Asia/Dubai',
        }).format(at),
      );
    const b = writeBatch(database());
    b.set(
      doc(col('checkins')),
      StationCheckinToDoc({
        ...StationCheckinDefaults,
        stationId: s.id,
        stationName: s.name,
        userId: me.uid,
        username: me.username,
        catsSeen: Math.round(catsSeen),
        catIds,
        foodLevel: food,
        waterLevel: water,
        note: note.trim(),
        at,
      }),
    );
    b.update(ref(`stations/${s.id}`), {
      checkinCount: increment(1),
      lastCheckinAt: at,
      lastCheckinBy: me.username,
    });
    users.addStats(
      b,
      me.uid,
      { checkins: 1, iftarCheckins: hour >= 18 && hour <= 19 ? 1 : 0 },
      5,
      s.neighborhood,
    );
    await b.commit();
  },
  reportMaintenance: async (
    s: FeedingStation,
    issue: string,
    description: string,
    me: UserProfile,
  ) => {
    const b = writeBatch(database());
    b.set(
      doc(col('maintenance')),
      MaintenanceReportToDoc({
        ...MaintenanceReportDefaults,
        stationId: s.id,
        stationName: s.name,
        userId: me.uid,
        username: me.username,
        issue,
        description: description.trim(),
        createdAt: Date.now(),
        updatedAt: Date.now(),
      }),
    );
    users.addStats(b, me.uid, { maintenanceReports: 1 }, 5, undefined, true);
    await b.commit();
  },
  updateMaintenance: async (
    r: MaintenanceReport,
    status: string,
    note: string,
    admin: UserProfile,
  ) => {
    await updateDoc(ref(`maintenance/${r.id}`), {
      status,
      resolutionNote: note.trim(),
      resolvedBy: admin.username,
      updatedAt: Date.now(),
    });
    await moderation.log(
      admin,
      `maintenance_${status}`,
      'station',
      r.stationId,
      r.stationName,
      note,
    );
    await notifications.send(
      [r.userId].filter((uid) => uid !== admin.uid),
      'maintenance_update',
      { station: r.stationName, status },
      `station/${r.stationId}`,
    );
  },
};
