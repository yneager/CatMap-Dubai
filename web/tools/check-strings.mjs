import { readFile, readdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import ts from 'typescript';

const root = fileURLToPath(new URL('../src/', import.meta.url));
const [en, ar] = await Promise.all(
  ['en', 'ar'].map(async (language) =>
    JSON.parse(await readFile(path.join(root, 'i18n', `${language}.json`), 'utf8')),
  ),
);
const differences = Object.keys(en)
  .filter((key) => !(key in ar))
  .concat(Object.keys(ar).filter((key) => !(key in en)));
const missing = [];
let files = 0;
async function scan(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) await scan(file);
    else if (/\.[cm]?[jt]sx?$/.test(entry.name)) {
      files += 1;
      const source = await readFile(file, 'utf8');
      const tree = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true);
      function literals(expression) {
        if (!expression) return [];
        if (ts.isStringLiteral(expression) || ts.isNoSubstitutionTemplateLiteral(expression))
          return [expression.text];
        if (ts.isParenthesizedExpression(expression)) return literals(expression.expression);
        if (ts.isConditionalExpression(expression))
          return [...literals(expression.whenTrue), ...literals(expression.whenFalse)];
        if (
          ts.isElementAccessExpression(expression) &&
          ts.isArrayLiteralExpression(expression.expression)
        )
          return expression.expression.elements.flatMap(literals);
        return [];
      }
      function visit(node) {
        if (
          ts.isCallExpression(node) &&
          ts.isIdentifier(node.expression) &&
          node.expression.text === 't'
        )
          for (const key of literals(node.arguments[0])) {
            if (!(key in en) || !(key in ar))
              missing.push(
                `${path.relative(root, file)}:${tree.getLineAndCharacterOfPosition(node.getStart()).line + 1}: ${key}`,
              );
          }
        if (
          ts.isPropertyAssignment(node) &&
          ts.isIdentifier(node.name) &&
          node.name.text === 'label' &&
          ts.isStringLiteral(node.initializer) &&
          /^[a-z][a-z0-9_]*$/.test(node.initializer.text)
        ) {
          const key = node.initializer.text;
          if (!(key in en) || !(key in ar))
            missing.push(
              `${path.relative(root, file)}:${tree.getLineAndCharacterOfPosition(node.getStart()).line + 1}: ${key}`,
            );
        }
        ts.forEachChild(node, visit);
      }
      visit(tree);
    }
  }
}
await scan(root);
if (differences.length || missing.length) {
  if (differences.length) console.error(`Language key mismatch: ${differences.join(', ')}`);
  if (missing.length) console.error(`Missing translations:\n${missing.join('\n')}`);
  process.exitCode = 1;
} else {
  console.log(
    `String keys checked: ${files} source files, ${Object.keys(en).length} keys per language.`,
  );
}
