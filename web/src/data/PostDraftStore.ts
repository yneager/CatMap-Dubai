import * as idb from '../util/idb';
import type { Post } from './model';
export interface SavedDraft {
  post: Post;
  step: number;
  stepName?: string;
  photos: Blob[];
  at: number;
}
export const PostDraftStore = {
  save: (uid: string, draft: SavedDraft) => idb.put('draft_' + uid, draft),
  load: (uid: string) => idb.get<SavedDraft>('draft_' + uid),
  clear: (uid: string) => idb.remove('draft_' + uid),
};
