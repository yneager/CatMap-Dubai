import en from './en.json';
import ar from './ar.json';
type Table = Record<string, string | Record<string, string>>;
let language: 'en' | 'ar' = 'en';
export function setLanguage(value: string) {
  language = value.toLowerCase().split('-')[0] === 'ar' ? 'ar' : 'en';
  if (typeof document !== 'undefined') {
    document.documentElement.lang = language;
    document.documentElement.dir = language === 'ar' ? 'rtl' : 'ltr';
  }
}
export function locale() {
  return language;
}
export function format(template: string, args: unknown[]) {
  let next = 0;
  return template.replace(
    /%(?:(\d+)\$)?([sd%])/g,
    (_all, index: string | undefined, kind: string) => {
      if (kind === '%') return '%';
      const arg = args[index ? Number(index) - 1 : next++];
      return kind === 'd'
        ? new Intl.NumberFormat(language, { useGrouping: false, maximumFractionDigits: 0 }).format(
            Number(arg ?? 0),
          )
        : String(arg ?? '');
    },
  );
}
export function t(key: string, ...args: unknown[]): string {
  const value = (language === 'ar' ? ar : (en as Table))[key as keyof typeof en];
  return format(typeof value === 'string' ? value : key, args);
}
export function plural(key: string, count: number, ...args: unknown[]) {
  const table = (language === 'ar' ? ar : en) as Table;
  const forms = table[key];
  if (typeof forms !== 'object') return t(key, count, ...args);
  const category = new Intl.PluralRules(language).select(count);
  return format(forms[category] ?? forms.other ?? key, [count, ...args]);
}
export function number(value: number) {
  return new Intl.NumberFormat(language).format(value);
}
export function dateTime(value: number) {
  return value <= 0
    ? ''
    : new Intl.DateTimeFormat(language, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
        timeZone: 'Asia/Dubai',
      }).format(value);
}
export function distanceText(km: number) {
  return km < 0.05
    ? t('distance_here')
    : km < 1
      ? t('distance_m', Math.round(km * 100) * 10)
      : t(
          'distance_km',
          new Intl.NumberFormat(language, {
            minimumFractionDigits: km < 10 ? 1 : 0,
            maximumFractionDigits: km < 10 ? 1 : 0,
          }).format(km),
        );
}

export function relativeTime(millis: number, now = Date.now()) {
  if (millis <= 0) return '';
  const diff = Math.max(60000, now - millis),
    format = new Intl.RelativeTimeFormat(language, { numeric: 'always' });
  return diff < 3600000
    ? format.format(-Math.floor(diff / 60000), 'minute')
    : diff < 86400000
      ? format.format(-Math.floor(diff / 3600000), 'hour')
      : diff < 604800000
        ? format.format(-Math.floor(diff / 86400000), 'day')
        : new Intl.DateTimeFormat(language, {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            timeZone: 'Asia/Dubai',
          }).format(millis);
}
