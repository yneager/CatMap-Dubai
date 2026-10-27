import { t } from '../../i18n';

/** The privacy policy and terms of use: static pages in web/public, also linked from Android (util/LegalLinks.kt). */
export function LegalLinks() {
  return (
    <p className="small">
      <a href="/terms.html" target="_blank" rel="noopener">
        {t('terms_of_use')}
      </a>
      {' · '}
      <a href="/privacy.html" target="_blank" rel="noopener">
        {t('privacy_policy')}
      </a>
    </p>
  );
}
