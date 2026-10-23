import { describe, it, expect } from 'vitest';
import { emptyArea, withItems } from '../src/data/MapArea';
import { replaceMapWindow } from '../src/data/MapWindow';
import { PostDefaults, CatDefaults } from '../src/data/model';
describe('authoritative map display windows', () => {
  const post = { ...PostDefaults, id: 'gone', geoCells: ['thrr'], updatedAt: 1 };
  it('removes a hidden or deleted pin promptly on the next server snapshot', () => {
    const before = withItems(emptyArea(), [post], []);
    expect(replaceMapWindow(before, 'thrr', 'posts', []).posts).toEqual({});
  });
  it('evicts old cached pins even when the replacement query is limited', () => {
    const before = withItems(emptyArea(), [post], []);
    const newest = { ...post, id: 'new', updatedAt: 100 };
    expect(Object.keys(replaceMapWindow(before, 'thrr', 'posts', [newest]).posts)).toEqual(['new']);
  });
  it('preserves other cells and withdraws merged cats', () => {
    const elsewhere = { ...post, id: 'elsewhere', geoCells: ['thrs'] };
    const cat = { ...CatDefaults, id: 'cat', geoCells: ['thrr'] };
    const before = withItems(emptyArea(), [post, elsewhere], [cat]);
    const next = replaceMapWindow(replaceMapWindow(before, 'thrr', 'posts', []), 'thrr', 'cats', [
      { ...cat, mergedInto: 'other' },
    ]);
    expect(Object.keys(next.posts)).toEqual(['elsewhere']);
    expect(next.cats).toEqual({});
  });
});
