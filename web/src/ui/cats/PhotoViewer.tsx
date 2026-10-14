import { useRef, useState } from 'react';
import { useLocation, useParams } from 'react-router-dom';
import { useApp, useDoc, useAction } from '../../state';
import { CatRepository } from '../../data/CatRepository';
import { CatFromDoc } from '../../data/model';
import { t } from '../../i18n';
import { AppTopBar, Photo } from '../components/Components';
import { ReportButton } from '../components/ReportButton';
export default function PhotoViewer() {
  const { id = '' } = useParams(),
    catId = new URLSearchParams(useLocation().search).get('catId'),
    { profile } = useApp(),
    action = useAction(),
    cat = useDoc(
      () => (catId && /^[A-Za-z0-9_-]{1,128}$/.test(catId) ? CatRepository.catFlow(catId) : null),
      CatFromDoc,
      'photo-cat-' + catId,
    ),
    [zoom, setZoom] = useState(1),
    [pan, setPan] = useState({ x: 0, y: 0 });
  const pointers = useRef(new Map<number, { x: number; y: number }>()),
    distance = useRef(0),
    last = useRef<{ x: number; y: number } | null>(null);
  const mayCover =
    cat &&
    (cat.createdBy === profile?.uid || ['moderator', 'admin'].includes(profile?.role ?? '')) &&
    cat.photoIds.includes(id);
  return (
    <>
      <AppTopBar title={t('photo')}>
        {mayCover && (
          <button
            className="text-button"
            disabled={action.busy || cat.coverPhotoId === id}
            onClick={() =>
              void action.run(() => CatRepository.setCoverPhoto(cat.id, id), t('cover_updated'))
            }
          >
            {t('set_as_cover')}
          </button>
        )}
        <ReportButton type="photo" id={id} label={cat?.name ?? t('photo')} />
      </AppTopBar>
      <div
        className="photo-viewer zoom-viewer"
        onDoubleClick={() => {
          setZoom((z) => (z === 1 ? 2 : 1));
          setPan({ x: 0, y: 0 });
        }}
        onWheel={(e) => {
          const z = Math.max(1, Math.min(5, zoom - e.deltaY * 0.005));
          setZoom(z);
          if (z === 1) setPan({ x: 0, y: 0 });
        }}
        onPointerDown={(e) => {
          e.currentTarget.setPointerCapture(e.pointerId);
          pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY });
          last.current = { x: e.clientX, y: e.clientY };
          if (pointers.current.size === 2) {
            const p = [...pointers.current.values()];
            distance.current = Math.hypot(p[0].x - p[1].x, p[0].y - p[1].y);
          }
        }}
        onPointerMove={(e) => {
          if (!pointers.current.has(e.pointerId)) return;
          pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY });
          if (pointers.current.size === 2) {
            const p = [...pointers.current.values()],
              next = Math.hypot(p[0].x - p[1].x, p[0].y - p[1].y);
            if (distance.current > 0)
              setZoom((z) => Math.max(1, Math.min(5, (z * next) / distance.current)));
            distance.current = next;
          } else if (last.current && zoom > 1) {
            const dx = e.clientX - last.current.x,
              dy = e.clientY - last.current.y,
              width = e.currentTarget.clientWidth,
              height = e.currentTarget.clientHeight;
            setPan((p) => ({
              x: Math.max((-width * (zoom - 1)) / 2, Math.min((width * (zoom - 1)) / 2, p.x + dx)),
              y: Math.max(
                (-height * (zoom - 1)) / 2,
                Math.min((height * (zoom - 1)) / 2, p.y + dy),
              ),
            }));
          }
          last.current = { x: e.clientX, y: e.clientY };
        }}
        onPointerUp={(e) => {
          pointers.current.delete(e.pointerId);
          distance.current = 0;
          last.current = null;
          if (zoom <= 1) setPan({ x: 0, y: 0 });
        }}
        onPointerCancel={(e) => {
          pointers.current.delete(e.pointerId);
          last.current = null;
        }}
      >
        <div style={{ transform: `translate(${pan.x}px,${pan.y}px) scale(${zoom})` }}>
          <Photo id={id} thumb={false} alt={cat?.name ?? t('photo')} />
        </div>
      </div>
    </>
  );
}
