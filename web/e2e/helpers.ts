import { randomBytes } from 'node:crypto';
import path from 'node:path';
import { expect, type Page } from '@playwright/test';
export const project = process.env.CATMAP_EMULATOR_PROJECT || 'demo-catmap';
if (!['demo-catmap', 'demo-catmap-web-test'].includes(project))
  throw new Error('Test project must be a demo project');
export const authBase = 'http://127.0.0.1:' + (process.env.CATMAP_AUTH_PORT || 9099);
const firestorePort = Number(process.env.CATMAP_FIRESTORE_PORT || 8080);
if (![8080, 8180].includes(firestorePort)) throw new Error('Unsupported local test port');
export const firestoreBase = `http://127.0.0.1:${firestorePort}/v1/projects/${project}/databases/(default)/documents`;
export const photoPath = path.resolve('..', 'tools', 'testdata', 'orange_tabby_1.jpg');
export const headers = { Authorization: 'Bearer owner', 'Content-Type': 'application/json' };
export interface Account {
  email: string;
  password: string;
  uid: string;
  username: string;
}
async function api(url: string, body?: unknown, method = body ? 'POST' : 'GET') {
  if (!/^http:\/\/127\.0\.0\.1:(8080|8180|9099|9098)\//.test(url))
    throw new Error('Emulator-only helper');
  const r = await fetch(url, { method, headers, body: body ? JSON.stringify(body) : undefined });
  if (!r.ok) throw new Error(`Emulator ${method} failed: ${r.status}`);
  return r.json();
}
export async function account(admin = false): Promise<Account> {
  const suffix = randomBytes(5).toString('hex'),
    username = 'web_' + suffix,
    email = username + '@web-test.invalid',
    password = randomBytes(24).toString('base64url');
  const result = await api(
    `${authBase}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key`,
    { email, password, returnSecureToken: true },
  );
  await api(`${authBase}/identitytoolkit.googleapis.com/v1/accounts:update?key=demo-key`, {
    localId: result.localId,
    emailVerified: true,
    ...(admin ? { customAttributes: JSON.stringify({ catmapAdmin: true }) } : {}),
  });
  return { email, password, uid: result.localId, username };
}
export function value(input: unknown): Record<string, unknown> {
  if (input === null) return { nullValue: null };
  if (typeof input === 'string') return { stringValue: input };
  if (typeof input === 'boolean') return { booleanValue: input };
  if (typeof input === 'number')
    return Number.isInteger(input) ? { integerValue: String(input) } : { doubleValue: input };
  if (Array.isArray(input)) return { arrayValue: { values: input.map(value) } };
  if (input && typeof input === 'object')
    return { mapValue: { fields: fields(input as Record<string, unknown>) } };
  throw new Error('Unsupported test value');
}
export function fields(input: Record<string, unknown>) {
  return Object.fromEntries(Object.entries(input).map(([k, v]) => [k, value(v)]));
}
export async function write(path: string, input: Record<string, unknown>, mask?: string[]) {
  // This emulator version ignores the dotted updateMask query string. Keep raw
  // REST fields (including timestamps) when updating disposable test fixtures.
  let existing: Record<string, unknown> = {};
  if (mask) {
    try {
      existing = (await get(path)).fields ?? {};
    } catch {
      /* New fixture. */
    }
  }
  return api(firestoreBase + '/' + path, { fields: { ...existing, ...fields(input) } }, 'PATCH');
}
export async function get(path: string) {
  return api(firestoreBase + '/' + path);
}
export function decode(v: Record<string, unknown>): unknown {
  if ('integerValue' in v) return Number(v.integerValue);
  if ('doubleValue' in v) return v.doubleValue;
  if ('stringValue' in v) return v.stringValue;
  if ('booleanValue' in v) return v.booleanValue;
  if ('nullValue' in v) return null;
  if ('arrayValue' in v)
    return ((v.arrayValue as { values?: Record<string, unknown>[] }).values ?? []).map(decode);
  if ('mapValue' in v)
    return Object.fromEntries(
      Object.entries(
        (v.mapValue as { fields?: Record<string, Record<string, unknown>> }).fields ?? {},
      ).map(([k, v]) => [k, decode(v)]),
    );
  return v;
}
export async function document(path: string) {
  const d = await get(path);
  return Object.fromEntries(
    Object.entries(d.fields ?? {}).map(([k, v]) => [k, decode(v as Record<string, unknown>)]),
  );
}
export async function collection(path: string) {
  const parts = path.split('/'),
    collectionId = parts.pop()!,
    parent = parts.length ? firestoreBase + '/' + parts.join('/') : firestoreBase;
  const result = await api(parent + ':runQuery', {
    structuredQuery: { from: [{ collectionId }], limit: 1000 },
  });
  return result.flatMap(
    (entry: { document?: { name: string; fields: Record<string, Record<string, unknown>> } }) =>
      entry.document ? [entry.document] : [],
  ) as { name: string; fields: Record<string, Record<string, unknown>> }[];
}
export async function verify(email: string) {
  const d = await api(`${authBase}/emulator/v1/projects/${project}/oobCodes`);
  const code = d.oobCodes
    ?.filter(
      (c: { email: string; requestType: string }) =>
        c.email === email && c.requestType === 'VERIFY_EMAIL',
    )
    .at(-1)?.oobCode;
  if (!code) throw new Error('Verification code missing');
  const result = await fetch(
    `${authBase}/identitytoolkit.googleapis.com/v1/accounts:update?key=demo-key`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ oobCode: code }),
    },
  );
  if (!result.ok) throw new Error('Verification failed ' + result.status);
}
export async function login(page: Page, a: Account) {
  await page.bringToFront();
  await page.goto('/login');
  await page.getByLabel('Email or username', { exact: true }).fill(a.email);
  await page.getByLabel('Password', { exact: true }).fill(a.password);
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  await expect(page).toHaveURL(/\/map$/);
  await expect(page.getByRole('link', { name: 'Explore', exact: true })).toBeVisible();
  await expect
    .poll(async () =>
      page.evaluate(async () => {
        const { authentication } = await import('/src/firebase.ts' /* @vite-ignore */),
          { UserRepository } = await import('/src/data/UserRepository.ts' /* @vite-ignore */);
        const user = authentication().currentUser;
        if (!user) return false;
        const profile = await UserRepository.profile(user.uid);
        return !!profile?.createdAt && !!profile?.username;
      }),
    )
    .toBe(true);
  a.username = await page.evaluate(async () => {
    const { authentication } = await import('/src/firebase.ts' /* @vite-ignore */),
      { UserRepository } = await import('/src/data/UserRepository.ts' /* @vite-ignore */);
    return (await UserRepository.profile(authentication().currentUser!.uid))!.username as string;
  });
}

/** Exercise React Router navigation as a user does, keeping the shared client alive. */
export async function navigate(page: Page, route: string) {
  await page.bringToFront();
  if (!page.url().startsWith('http://127.0.0.1:5173/')) {
    await page.goto(route);
    return;
  }
  await page.evaluate((route) => {
    history.pushState(null, '', route);
    window.dispatchEvent(new PopStateEvent('popstate'));
  }, route);
  if (!route.startsWith('/chat-with/')) {
    await expect(page.locator('.app')).toHaveAttribute('data-route', route);
    await expect(page.locator('.route-screen')).toHaveAttribute('data-screen', route.split('?')[0]);
  }
}

export async function promote(a: Account) {
  await write('users/' + a.uid, { role: 'admin' }, ['role']);
}
export async function waitRateLimit(uid: string) {
  await write('users/' + uid, { lastPostAt: null }, ['lastPostAt']);
}
export async function setupUsername(a: Account) {
  await write('usernames/' + a.username, { uid: a.uid, loginEmail: a.email });
}
export async function publishLost(page: Page, name: string) {
  await navigate(page, '/post-new');
  await page.getByRole('button', { name: 'Lost', exact: true }).click();
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await page.locator('input[type=file][multiple]').setInputFiles(photoPath);
  await expect(page.getByText('Photos: 1 / 6')).toBeVisible();
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await page.getByLabel('Name', { exact: true }).fill(name);
  await page
    .getByLabel('Description', { exact: true })
    .fill('Friendly orange cat with a blue collar.');
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await pickLocation(page);
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(page).toHaveURL(/\/post\/[A-Za-z0-9_-]+$/);
  return page.url().split('/').at(-1)!;
}
export async function pickLocation(page: Page) {
  await page.getByRole('button', { name: 'Choose location', exact: true }).click();
  await page.getByRole('button', { name: 'Use this location', exact: true }).click();
}
