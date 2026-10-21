import { useState } from 'react';
import { useAsyncList } from '../../data/useAsyncList';
import { useLocation } from 'react-router-dom';
import { useApp, useList, useDoc, useAction } from '../../state';
import { PostFromDoc, AreaFollowFromDoc } from '../../data/model';
import { PostRepository } from '../../data/PostRepository';
import { t } from '../../i18n';
import { AppTopBar, PostCard, Loading, EmptyState, Switch } from '../components/Components';
import { LocationField } from '../components/LocationField';
export default function PostLists() {
  const path = useLocation().pathname,
    { profile, favoriteIds } = useApp(),
    uid = profile?.uid ?? '',
    action = useAction(),
    [closed, setClosed] = useState(false),
    [position, setPosition] = useState({ lat: 0, lng: 0 }),
    [pickedRadius, setRadius] = useState<number | null>(null);
  const own = useList(
    () => (uid && path === '/my-posts' ? PostRepository.postsBy(uid) : null),
    PostFromDoc,
    'my-posts-' + uid + '-' + path,
  );
  const saved = useAsyncList(
    () => (path === '/favorites' ? PostRepository.byIds(favoriteIds) : Promise.resolve([])),
    'favorites-' + uid + '-' + path + '-' + favoriteIds.join(','),
  );
  const centre = useDoc(
    () => (uid && path === '/area-alerts' ? PostRepository.areaCentre(uid) : null),
    AreaFollowFromDoc,
    'area-centre-' + uid + path,
  );
  const sharedArea = useDoc(
    () => (uid && path === '/area-alerts' ? PostRepository.areaFollow(uid) : null),
    AreaFollowFromDoc,
    'area-follow-' + uid + '-' + path,
  );
  const area =
    sharedArea && centre
      ? { ...sharedArea, lat: centre.lat, lng: centre.lng, radiusKm: centre.radiusKm }
      : sharedArea;
  const radius = pickedRadius ?? area?.radiusKm ?? 3;
  if (path === '/area-alerts')
    return (
      <>
        <AppTopBar title={t('area_alerts')} />
        <div className="screen">
          <p>{t('area_alerts_intro')}</p>
          <LocationField
            lat={position.lat || area?.lat || 0}
            lng={position.lng || area?.lng || 0}
            onChange={(lat, lng) => setPosition({ lat, lng })}
          />
          <label>
            {t('alert_radius_km', radius)}
            <input
              type="range"
              min="1"
              max="15"
              value={radius}
              onChange={(e) => setRadius(Number(e.target.value))}
            />
          </label>
          {area && <p>{t('alert_areas_n', area.neighborhoods.length)}</p>}
          <button
            className="primary"
            disabled={action.busy || !(position.lat || area?.lat)}
            onClick={() =>
              void action.run(
                () =>
                  PostRepository.followArea(
                    uid,
                    position.lat || area!.lat,
                    position.lng || area!.lng,
                    radius,
                  ),
                t('alerts_saved'),
              )
            }
          >
            {t('save')}
          </button>
          {area && (
            <button
              className="text-button"
              onClick={() => void action.run(() => PostRepository.unfollowArea(uid))}
            >
              {t('delete')}
            </button>
          )}
        </div>
      </>
    );
  const shown =
    path === '/favorites'
      ? saved
      : own
          ?.filter((p) => (p.status !== 'open') === closed)
          .sort((a, b) => b.createdAt - a.createdAt);
  return (
    <>
      <AppTopBar title={t(path === '/favorites' ? 'favorites' : 'my_posts')} />
      <div className="screen">
        {path === '/my-posts' && (
          <Switch label={t('tab_closed')} checked={closed} onChange={setClosed} />
        )}{' '}
        {!shown ? (
          <Loading />
        ) : shown.length ? (
          <div className={path === '/favorites' ? 'post-grid' : ''}>
            {shown.map((p) => (
              <PostCard post={p} key={p.id} grid={path === '/favorites'} />
            ))}
          </div>
        ) : (
          <EmptyState text={t(path === '/favorites' ? 'no_favorites' : 'no_posts_profile')} />
        )}
      </div>
    </>
  );
}
