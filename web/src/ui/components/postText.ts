import type { Post } from '../../data/model';
import { enums } from '../../data/model/Enums';
import { t, locale } from '../../i18n';
export function postHeadline(post: Post) {
  const animal = t(
      enums.AnimalType.find((a) => a.key === post.animalType)?.label ?? 'animal_other',
    ).toLocaleLowerCase(locale()),
    headline = t('headline_' + post.category, animal);
  return headline.slice(0, 1).toLocaleUpperCase(locale()) + headline.slice(1);
}
export function postTitle(post: Post) {
  return post.name.trim() ? post.name : postHeadline(post);
}
