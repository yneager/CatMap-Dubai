import type { AppNotification } from '../data/model';
import { t } from '../i18n';
import { Dubai } from '../data/Neighborhoods';
import { enums } from '../data/model/Enums';
export const NotificationDelivery = {
  fresh: (inbox: AppNotification[], delivered: string[] | undefined) =>
    delivered === undefined
      ? []
      : inbox
          .filter((n) => !n.read && n.id && !delivered.includes(n.id))
          .filter((n, i, list) => list.findIndex((x) => x.id === n.id) === i)
          .sort((a, b) => a.createdAt - b.createdAt),
  checkpoint: (inbox: AppNotification[], delivered: string[] | undefined) => {
    const visible = [...new Set(inbox.map((n) => n.id).filter(Boolean))];
    return [...(delivered ?? []).filter((id) => !visible.includes(id)), ...visible].slice(-500);
  },
};
const label = (group: keyof typeof enums, key: string) =>
  t(enums[group].find((v) => v.key === key)?.label ?? 'unknown');
export const NotificationText = {
  category: (type: string) =>
    (
      ({
        followed_cat: 'followed',
        stale_post: 'post_updates',
        rescue_update: 'rescue',
        rescue_volunteer: 'volunteer',
        rescue_moderation: 'volunteer',
        achievement: 'achievements',
        moderation: 'moderation',
        volunteer_decision: 'moderation',
        maintenance_update: 'moderation',
        message: 'messages',
        lost_nearby: 'lost_nearby',
        post_update: 'post_updates',
        possible_match: 'post_updates',
        match_found: 'post_updates',
        match_reporter: 'post_updates',
        match_info: 'post_updates',
        match_reunited: 'post_updates',
        adoption_request: 'adoption',
        adoption_decision: 'adoption',
        booking_request: 'sitting',
        booking_update: 'sitting',
        review: 'sitting',
      }) as Record<string, string>
    )[type],
  format: (n: AppNotification): [string, string] => {
    const p = n.params,
      area = Dubai.nameOf(p.area),
      animal = label('AnimalType', p.type);
    switch (n.type) {
      case 'stale_post':
        return [t('stale_post_title'), t('stale_post_body', p.name ?? '')];
      case 'followed_cat':
        return [
          t('n_followed_title', p.cat),
          p.area === 'medical'
            ? t('n_followed_medical_body', p.user, p.cat)
            : t('n_followed_body', p.user, p.cat, area),
        ];
      case 'rescue_update':
        return [
          t('n_rescue_update_title'),
          t('n_rescue_update_body', label('RescueStatus', p.status), area),
        ];
      case 'rescue_volunteer':
      case 'rescue_moderation':
        return [
          t(n.type === 'rescue_volunteer' ? 'n_rescue_volunteer_title' : 'n_rescue_mod_title'),
          t('n_rescue_new_body', label('RescueType', p.type), area),
        ];
      case 'achievement':
        return [t('n_achievement_title'), t('n_achievement_body', t('badge_' + p.badge))];
      case 'moderation':
        return [
          t('n_moderation_title'),
          p.target === 'role'
            ? t('n_role_changed', t('role_' + p.label))
            : t(
                'n_moderation_body',
                t('target_' + (p.target || 'content')),
                p.label,
                t('decision_' + p.decision),
              ),
        ];
      case 'volunteer_decision':
        return [
          t('n_volunteer_title'),
          t(p.decision === 'approved' ? 'n_volunteer_approved' : 'n_volunteer_rejected'),
        ];
      case 'maintenance_update':
        return [
          t('n_maintenance_title'),
          t('n_maintenance_body', p.station, label('MaintenanceStatus', p.status)),
        ];
      case 'welcome':
        return [t('n_welcome_title'), t('n_welcome_body')];
      case 'message':
        return [t('n_message_title', p.user), p.text ?? ''];
      case 'lost_nearby':
        return [t('n_lost_nearby_title', animal), t('n_lost_nearby_body', p.name || animal, area)];
      case 'possible_match':
        return [
          t('n_match_title'),
          t('n_match_body', label('PostCategory', p.category), animal, area),
        ];
      // Lost-pet matches: only the animal's name, type and neighbourhood names; never an exact place or a contact.
      case 'match_found':
        return [
          t('n_match_found_title', p.name || t('your_pet')),
          t('n_match_found_body', animal.toLowerCase(), area),
        ];
      case 'match_reporter':
        return [
          t('n_match_reporter_title', p.name || animal.toLowerCase()),
          t('n_match_reporter_body', animal.toLowerCase(), area, Dubai.nameOf(p.lostArea ?? '')),
        ];
      case 'match_info':
        return [t('n_match_info_title', p.name || animal), t('n_match_info_body', p.user ?? '')];
      case 'match_reunited':
        return [t('n_match_reunited_title', p.name || animal), t('n_match_reunited_body')];
      case 'post_update':
        return [
          t('n_post_update_title', p.name || animal),
          t(p.kind === 'sighting' ? 'n_post_sighting_body' : 'n_post_update_body', p.user),
        ];
      case 'adoption_request':
        return [t('n_adoption_request_title'), t('n_adoption_request_body', p.user, p.name)];
      case 'adoption_decision':
        return [
          t('n_adoption_decision_title'),
          t(p.decision === 'approved' ? 'n_adoption_approved' : 'n_adoption_declined', p.name),
        ];
      case 'booking_request':
        return [t('n_booking_request_title'), t('n_booking_request_body', p.user)];
      case 'booking_update':
        return [
          t('n_booking_update_title'),
          t('n_booking_update_body', p.user, label('BookingStatus', p.status)),
        ];
      case 'review':
        return [t('n_review_title'), t('n_review_body', p.user, p.rating)];
      default:
        return [t('app_name'), ''];
    }
  },
};
