import { t } from '../../i18n';
const labels: Record<string, string> = {
  arabian_mau: 'breed_arabian_mau',
  domestic_shorthair: 'breed_domestic_shorthair',
  colorpoint: 'breed_colorpoint',
  tabby: 'breed_tabby_trait',
  tuxedo: 'breed_tuxedo_trait',
  gray: 'breed_gray_trait',
  orange_male: 'breed_orange_male',
  tortie_female: 'breed_tortie_female',
  white_deafness: 'breed_white_deafness',
};
export function BreedTraits({ hints }: { hints: string[] }) {
  return hints.length ? (
    <section>
      <h3>{t('ai_breed_traits')}</h3>
      {hints.map((key, i) => (
        <p key={i}>{t(labels[key] ?? 'breed_domestic_shorthair')}</p>
      ))}
      <p className="small muted">{t('ai_disclaimer')}</p>
    </section>
  ) : null;
}
