import { t } from '../i18n';
export function nameIdeas(coat: string) {
  const groups: Record<string, string> = {
    orange_white: 'orange',
    gray_white: 'gray',
    tortoiseshell: 'calico',
    colorpoint: 'cream',
  };
  const group =
    groups[coat] ??
    (['orange', 'black', 'white', 'gray', 'black_white', 'calico', 'cream', 'brown'].includes(coat)
      ? coat
      : 'unknown');
  return t('web_name_ideas_' + group).split('|');
}
