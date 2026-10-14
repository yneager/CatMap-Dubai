import { t } from '../../i18n';
import { enums } from '../../data/model/Enums';
import type { Post } from '../../data/model';
import { PostStatusBadge, enumLabel } from './Components';
import { postHeadline, postTitle } from './postText';
export { postHeadline, postTitle } from './postText';
export function PostDetailHeader({ post }: { post: Post }) {
  const c = enums.PostCategory.find((c) => c.key === post.category);
  return (
    <div className="detail-header">
      <div className="row wrap">
        <span className="category-pill" style={{ background: c?.color }}>
          {enumLabel('PostCategory', post.category)}
        </span>
        {post.reward > 0 && <span className="reward-pill">{t('reward_aed', post.reward)}</span>}
        <PostStatusBadge post={post} />
        {post.hidden && <span className="status-pill hidden">{t('hidden')}</span>}
      </div>
      <h2 dir="auto">{postTitle(post)}</h2>
      {post.name && <p className="detail-headline">{postHeadline(post)}</p>}
    </div>
  );
}
