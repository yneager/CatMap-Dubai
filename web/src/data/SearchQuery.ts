import { SearchTerms } from './SearchTerms';
import { enums } from './model/Enums';
import { t } from '../i18n';
export function parseSearch(text: string) {
  const types = new Set<string>(),
    categories = new Set<string>();
  const words = SearchTerms.words(text).filter((w) => {
    const ts = enums.AnimalType.filter(
      (v) => v.key.startsWith(w) || t(v.label).toLowerCase().startsWith(w),
    );
    const cs = enums.PostCategory.filter(
      (v) => v.key.startsWith(w) || t(v.label).toLowerCase().startsWith(w),
    );
    ts.forEach((v) => types.add(v.key));
    cs.forEach((v) => categories.add(v.key));
    return !ts.length && !cs.length;
  });
  return {
    words,
    types: [...types],
    categories: [...categories],
    word: words.slice().sort((a, b) => b.length - a.length)[0] ?? '',
  };
}
