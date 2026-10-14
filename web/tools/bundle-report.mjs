import { readFile, readdir, writeFile } from 'node:fs/promises';
import { gzipSync } from 'node:zlib';
import ts from 'typescript';

const files = await readdir('dist/assets');
const sizes = await Promise.all(
  files.map(async (name) => {
    const data = await readFile('dist/assets/' + name);
    return { name, bytes: data.length, gzipBytes: gzipSync(data).length };
  }),
);
const html = await readFile('dist/index.html', 'utf8');
const initial = new Set(
  [...html.matchAll(/(?:src|href)="\/assets\/([^"]+\.(?:js|css))"/g)].map((match) => match[1]),
);
const pending = [...initial];
while (pending.length) {
  const name = pending.pop();
  if (!name.endsWith('.js')) continue;
  const source = ts.createSourceFile(
    name,
    await readFile('dist/assets/' + name, 'utf8'),
    ts.ScriptTarget.Latest,
    true,
  );
  for (const statement of source.statements) {
    if (
      (ts.isImportDeclaration(statement) || ts.isExportDeclaration(statement)) &&
      statement.moduleSpecifier &&
      ts.isStringLiteral(statement.moduleSpecifier)
    ) {
      const target = statement.moduleSpecifier.text.replace(/^\.\//, '');
      if (files.includes(target) && !initial.has(target)) {
        initial.add(target);
        pending.push(target);
      }
    }
  }
}
const report = {
  initialAssets: sizes.filter((item) => initial.has(item.name)),
  initialGzipBytes: sizes
    .filter((item) => initial.has(item.name))
    .reduce((sum, item) => sum + item.gzipBytes, 0),
  lazyMap: sizes.filter((item) => /CommunityMap|maplibre|mapbox-gl-rtl/.test(item.name)),
  lazyAdmin: sizes.filter((item) => /Admin/.test(item.name)),
  assets: sizes,
};
await writeFile('docs/bundle-size.json', JSON.stringify(report, null, 2));
console.log(
  JSON.stringify({
    initialGzipKiB: report.initialGzipBytes / 1024,
    map: report.lazyMap,
    admin: report.lazyAdmin,
  }),
);
