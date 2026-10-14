import { mkdir, copyFile, readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const repository = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const destination = path.join(repository, 'web/e2e/.emulator-rules');
await mkdir(destination, { recursive: true });
for (const name of ['firestore.rules', 'storage.rules', 'firestore.indexes.json']) {
  const source = path.join(repository, name),
    target = path.join(destination, name);
  await copyFile(source, target);
  if (!(await readFile(source)).equals(await readFile(target)))
    throw new Error('Rule copy mismatch');
}
console.log(
  'Isolated emulator fixtures copied byte-for-byte from the unchanged root rules/indexes.',
);
