import { postHeadline, postTitle } from './postText';
import { useEffect, useRef, useState, type ReactNode, type InputHTMLAttributes } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { t, relativeTime, plural } from '../../i18n';
import { CatMapIcons, catMapSvg, type IconName } from '../../icons/CatMapIcons';
import { ImageRepository } from '../../data/ImageRepository';
import { FavoritesRepository } from '../../data/FavoritesRepository';
import { enums } from '../../data/model/Enums';
import type { Post } from '../../data/model';
import { useApp, useAction } from '../../state';
import { Dubai } from '../../data/Neighborhoods';
export function Icon({
  name,
  size = 24,
  original = false,
}: {
  name: IconName;
  size?: number;
  original?: boolean;
}) {
  return (
    <span
      className="icon"
      style={{ width: size, height: size }}
      aria-hidden="true"
      dangerouslySetInnerHTML={{ __html: catMapSvg(name, original) }}
    />
  );
}
export function ControlIcon({
  name,
}: {
  name:
    | 'back'
    | 'close'
    | 'search'
    | 'more'
    | 'settings'
    | 'plus'
    | 'heart'
    | 'location'
    | 'check'
    | 'send';
}) {
  const paths = {
    back: 'M20 12H4m7-7-7 7 7 7',
    close: 'm6 6 12 12M6 18 18 6',
    search: 'm21 21-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
    more: 'M12 5h.01M12 12h.01M12 19h.01',
    settings: 'M9 3h6l1 4 4 1v6l-4 1-1 4H9l-1-4-4-1V8l4-1 1-4M15 11a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
    plus: 'M12 4v16M4 12h16',
    heart:
      'M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8',
    location: 'M12 2v4m0 12v4M2 12h4m12 0h4M18 12a6 6 0 1 1-12 0 6 6 0 0 1 12 0',
    check: 'm4 12 5 5L20 6',
    send: 'm3 3 18 9-18 9 3-9-3-9M6 12h15',
  };
  return (
    <svg
      className={'control-icon ' + (name === 'back' ? 'mirror' : '')}
      width="24"
      height="24"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name]} />
    </svg>
  );
}
export function AppTopBar({
  title,
  back = true,
  children,
}: {
  title: string;
  back?: boolean;
  children?: ReactNode;
}) {
  const navigate = useNavigate();
  return (
    <header className="topbar">
      {back && (
        <button
          className="icon-button"
          aria-label={t('back')}
          onClick={() => (history.length > 1 ? navigate(-1) : navigate('/map'))}
        >
          <ControlIcon name="back" />
        </button>
      )}
      <h1>{title}</h1>
      {children}
    </header>
  );
}
export function Bell() {
  const { inbox } = useApp();
  const n = inbox.filter((n) => !n.read).length;
  return (
    <Link
      className="icon-button bell"
      to="/notifications"
      aria-label={plural('unread_notifications', n)}
    >
      <Icon name={CatMapIcons.BELL} />
      {n > 0 && <span className="badge">{n > 99 ? '99+' : n}</span>}
    </Link>
  );
}
export function SectionHeader({ children }: { children: ReactNode }) {
  return <h2 className="section-header">{children}</h2>;
}
export function EmptyState({
  text,
  icon = CatMapIcons.PAW,
  children,
}: {
  text: string;
  icon?: IconName;
  children?: ReactNode;
}) {
  return (
    <div className="empty">
      <Icon name={icon} size={48} />
      <p>{text}</p>
      {children}
    </div>
  );
}
export function Loading({ rows = 3 }: { rows?: number }) {
  return (
    <div className="loading" aria-label={t('web_loading')} role="status">
      {Array.from({ length: rows }, (_, i) => (
        <div className="skeleton" key={i}>
          <div />
          <p />
          <p />
        </div>
      ))}
    </div>
  );
}
export function Field({
  label,
  value,
  onChange,
  multiline = false,
  limit,
  hint,
  ...props
}: {
  label: string;
  value: string | number;
  onChange: (value: string) => void;
  multiline?: boolean;
  limit?: number;
  hint?: string;
} & Omit<InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange'>) {
  return (
    <label className="field">
      <span>{label}</span>
      {multiline ? (
        <textarea
          aria-label={label}
          dir="auto"
          value={value}
          maxLength={limit}
          onChange={(e) => onChange(e.target.value)}
          rows={4}
          required={props.required}
        />
      ) : (
        <input
          aria-label={label}
          dir="auto"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          maxLength={limit}
          {...props}
        />
      )}
      <small>
        {hint}
        {limit && (
          <span className="counter">
            {String(value).length} / {limit}
          </span>
        )}
      </small>
    </label>
  );
}
export function Switch({
  label,
  checked,
  onChange,
  hint,
}: {
  label: string;
  checked: boolean;
  onChange: (value: boolean) => void;
  hint?: string;
}) {
  return (
    <label className="switch-row">
      <span>
        {label}
        {hint && <small>{hint}</small>}
      </span>
      <input
        type="checkbox"
        role="switch"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
    </label>
  );
}
export function Chips({
  options,
  value,
  onChange,
  multi = false,
}: {
  options: readonly { key: string; label: string; color?: string }[];
  value: string | string[];
  onChange: (value: string) => void;
  multi?: boolean;
}) {
  return (
    <div className="chips" role="group">
      {options.map((o) => (
        <button
          key={o.key}
          type="button"
          className={
            'chip ' + ((multi ? value.includes(o.key) : value === o.key) ? 'selected' : '')
          }
          aria-pressed={multi ? value.includes(o.key) : value === o.key}
          onClick={() => onChange(o.key)}
        >
          {t(o.label)}
        </button>
      ))}
    </div>
  );
}
export const enumLabel = (group: keyof typeof enums, value: string) =>
  t(enums[group].find((o) => o.key === value)?.label ?? 'unknown');
export function Photo({
  id,
  thumb = true,
  alt = '',
  className = '',
  onClick,
}: {
  id: string;
  thumb?: boolean;
  alt?: string;
  className?: string;
  onClick?: () => void;
}) {
  const container = useRef<HTMLDivElement>(null);
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    let alive = true;
    setUrl(null);
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          observer.disconnect();
          void ImageRepository.url(id, thumb).then((value) => {
            if (alive) setUrl(value);
          });
        }
      },
      { rootMargin: '300px' },
    );
    if (container.current) observer.observe(container.current);
    return () => {
      alive = false;
      observer.disconnect();
    };
  }, [id, thumb]);
  return (
    <div ref={container} className={'photo ' + className} onClick={onClick}>
      {url ? <img src={url} alt={alt} loading="lazy" /> : <Icon name={CatMapIcons.PAW} size={48} />}
    </div>
  );
}
export function Avatar({ id, name, size = 44 }: { id?: string; name: string; size?: number }) {
  return (
    <div className="avatar" style={{ width: size, height: size }}>
      {id ? <Photo id={id} alt={name} /> : name.trim().slice(0, 1).toUpperCase() || '?'}
    </div>
  );
}
export function StatusPill({
  group = 'PostStatus',
  value,
}: {
  group?: keyof typeof enums;
  value: string;
}) {
  const info = enums[group].find((o) => o.key === value);
  const color = info && 'color' in info ? String(info.color) : '#546E7A';
  return (
    <span
      className="status-pill"
      style={{ color, background: color + '20', borderColor: color + '99' }}
    >
      {enumLabel(group, value)}
    </span>
  );
}
export function PostStatusBadge({ post }: { post: Post }) {
  return post.category === 'adoption' || post.status !== 'open' ? (
    <StatusPill
      group={post.category === 'adoption' ? 'AdoptionStatus' : 'PostStatus'}
      value={post.category === 'adoption' ? post.adoptionStatus : post.status}
    />
  ) : null;
}
export function FavoriteButton({ post, onImage = false }: { post: Post; onImage?: boolean }) {
  const { user, favoriteIds } = useApp(),
    action = useAction(),
    saved = favoriteIds.includes(post.id);
  return (
    <button
      className={'favorite icon-button ' + (onImage ? 'on-image ' : '') + (saved ? 'saved' : '')}
      aria-label={t(saved ? 'remove_favorite' : 'add_favorite')}
      aria-pressed={saved}
      disabled={action.busy}
      onClick={(e) => {
        e.stopPropagation();
        e.preventDefault();
        if (user) void action.run(() => FavoritesRepository.set(user.uid, post.id, !saved));
      }}
    >
      <ControlIcon name="heart" />
    </button>
  );
}
export function PostCard({ post, grid = false }: { post: Post; grid?: boolean }) {
  const category = enums.PostCategory.find((c) => c.key === post.category);
  return (
    <article className={'post-card ' + (grid ? 'grid-card' : '')}>
      <Link to={'/post/' + post.id}>
        <div className="post-image">
          {post.photoIds[0] ? (
            <Photo id={post.photoIds[0]} alt={post.name} />
          ) : (
            <div
              className="photo"
              style={{
                background: `linear-gradient(135deg, ${category?.color}59, ${category?.color}1f)`,
              }}
            >
              <Icon name={CatMapIcons.animal(post.animalType)} size={48} />
            </div>
          )}
          {!grid && <div className="photo-scrim" />}
          {!grid && (
            <div className="post-pills">
              {!grid && (
                <span className="category-pill" style={{ background: category?.color }}>
                  {t(category?.label ?? 'cat_other')}
                </span>
              )}
              {post.reward > 0 && (
                <span className="reward-pill">{t('reward_aed', post.reward)}</span>
              )}
            </div>
          )}
          {grid ? (
            <div className="grid-status">
              <PostStatusBadge post={post} />
            </div>
          ) : (
            <div className="post-photo-title">
              <h3 dir="auto">{postTitle(post)}</h3>
              {post.name && <p>{postHeadline(post)}</p>}
            </div>
          )}
        </div>
        <div className="post-card-body">
          {grid && <h3 dir="auto">{postTitle(post)}</h3>}
          {grid ? (
            <>
              {(post.sex !== 'unknown' || post.ageGroup !== 'unknown') && (
                <p>
                  {[
                    post.sex !== 'unknown' ? enumLabel('CatSex', post.sex) : '',
                    post.ageGroup !== 'unknown' ? enumLabel('AgeGroup', post.ageGroup) : '',
                  ]
                    .filter(Boolean)
                    .join(' · ')}
                </p>
              )}
              <p className="small">{Dubai.nameOf(post.neighborhood)}</p>
            </>
          ) : (
            <>
              {(post.description || post.features) && (
                <p dir="auto" className="clamp2">
                  {post.description || post.features}
                </p>
              )}
              <div className="meta">
                <Icon name={CatMapIcons.PIN} size={14} />
                {Dubai.nameOf(post.neighborhood)}
                {(post.lastSeenAt || post.createdAt) > 0 && (
                  <>
                    <Icon name={CatMapIcons.CALENDAR} size={14} />
                    <time>{relativeTime(post.lastSeenAt || post.createdAt)}</time>
                  </>
                )}
              </div>
              {!grid && (
                <div className="row between">
                  <PostStatusBadge post={post} />
                  {post.updatesCount > 0 && <small>{t('updates_n', post.updatesCount)}</small>}
                </div>
              )}
            </>
          )}
        </div>
      </Link>
      <FavoriteButton post={post} onImage />
    </article>
  );
}
export function Sheet({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const element = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = element.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);
  return (
    <dialog
      ref={element}
      className="sheet"
      onCancel={onClose}
      onClick={(e) => {
        if (e.target === element.current) onClose();
      }}
    >
      <div className="sheet-body">
        <div className="sheet-handle" />
        <div className="row between">
          <h2>{title}</h2>
          <button className="icon-button" onClick={onClose} aria-label={t('close')}>
            <ControlIcon name="close" />
          </button>
        </div>
        {children}
      </div>
    </dialog>
  );
}
export function Dialog({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const element = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = element.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);
  return (
    <dialog ref={element} className="dialog" onCancel={onClose}>
      <h2>{title}</h2>
      {children}
      <button type="button" className="text-button" onClick={onClose}>
        {t('cancel')}
      </button>
    </dialog>
  );
}
export function ErrorText({ text }: { text: string }) {
  return text ? (
    <p className="error" role="alert">
      {text}
    </p>
  ) : null;
}
export function LinkRow({ to, label, icon }: { to: string; label: string; icon?: IconName }) {
  return (
    <Link to={to} className="link-row">
      {icon && <Icon name={icon} />}
      <span>{label}</span>
      <span className="chevron mirror">›</span>
    </Link>
  );
}
