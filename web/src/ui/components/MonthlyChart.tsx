import type { Sighting } from '../../data/model';
import { locale, number, t } from '../../i18n';
export function MonthlyChart({ sightings }: { sightings: Sighting[] }) {
  const now = new Date(Date.now() + 4 * 3600000),
    months = Array.from({ length: 6 }, (_, index) => {
      const date = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - 5 + index, 1));
      return {
        date,
        count: sightings.filter((s) => {
          const seen = new Date(s.seenAt + 4 * 3600000);
          return (
            seen.getUTCFullYear() === date.getUTCFullYear() &&
            seen.getUTCMonth() === date.getUTCMonth()
          );
        }).length,
      };
    }),
    max = Math.max(1, ...months.map((m) => m.count));
  return (
    <figure>
      <figcaption>{t('activity_6_months')}</figcaption>
      <svg
        className="monthly-chart"
        viewBox="0 0 360 120"
        role="img"
        aria-label={t('stat_sightings')}
      >
        {months.map((m, i) => (
          <g key={m.date.toISOString()}>
            <rect
              x={i * 60 + 16}
              y="18"
              width="28"
              height="80"
              rx="6"
              fill="var(--surface-container-high)"
            />
            <rect
              x={i * 60 + 16}
              y={98 - (m.count / max) * 80}
              width="28"
              height={(m.count / max) * 80}
              rx="6"
              fill="var(--primary)"
            />
            <text x={i * 60 + 30} y="13" textAnchor="middle" fill="var(--on-surface)" fontSize="11">
              {number(m.count)}
            </text>
            <text
              x={i * 60 + 30}
              y="116"
              textAnchor="middle"
              fill="var(--on-surface-variant)"
              fontSize="10"
            >
              {new Intl.DateTimeFormat(locale(), { month: 'short', timeZone: 'UTC' }).format(
                m.date,
              )}
            </text>
          </g>
        ))}
      </svg>
    </figure>
  );
}
