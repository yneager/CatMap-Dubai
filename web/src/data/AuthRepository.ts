import {
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  sendEmailVerification,
  sendPasswordResetEmail,
  reload,
  getIdTokenResult,
  signOut,
  deleteUser,
  type User,
} from 'firebase/auth';
import { writeBatch, updateDoc } from 'firebase/firestore';
import { authentication, database } from '../firebase';
import { ref, read, AppException, AppErrors } from './Firestore';
import { UserRepository as users } from './UserRepository';
import { UserProfileDefaults, UserProfileToDoc } from './model';
import { NotificationRepository } from './NotificationRepository';
import { DemoAccounts, DemoVault } from './DemoAccounts';
import { wipeAndReload } from './LocalWipe';
async function createProfile(user: User, username: string, displayName: string, role = 'user') {
  const b = writeBatch(database());
  b.set(ref(`usernames/${username.toLowerCase()}`), { uid: user.uid });
  b.set(
    users.ref(user.uid),
    UserProfileToDoc({
      ...UserProfileDefaults,
      username,
      displayName: displayName.trim().slice(0, 60),
      role,
      createdAt: Date.now(),
    }),
  );
  b.set(ref(`users/${user.uid}/private/account`), {
    email: user.email ?? '',
    createdAt: Date.now(),
  });
  await b.commit();
  await NotificationRepository.send([user.uid], 'welcome', { user: username });
}
export const AuthRepository = {
  register: async (email: string, password: string, username: string, displayName: string) => {
    const name = username.trim();
    users.validateUsername(name);
    if (password.length < 8) throw new AppException('err_weak_password');
    if (!(await users.isUsernameFree(name))) throw new AppException('err_username_taken');
    const { user } = await createUserWithEmailAndPassword(authentication(), email.trim(), password);
    try {
      await createProfile(user, name, displayName.trim() || name);
    } catch (error) {
      await deleteUser(user).catch(() => {});
      throw error;
    }
    await sendEmailVerification(user).catch(() => {});
  },
  ensureProfile: async (user: User) => {
    if (await users.profile(user.uid)) return;
    const base =
      (user.email ?? 'cat')
        .split('@')[0]
        .replace(/[^A-Za-z0-9_]/g, '')
        .slice(0, 14) || 'catfriend';
    let candidate = base.padEnd(3, '0');
    while (!(await users.isUsernameFree(candidate)))
      candidate = base + Math.floor(1000 + Math.random() * 9000);
    await createProfile(user, candidate, candidate);
  },
  ensureDemoProfile: async (user: User, account: (typeof DemoAccounts.ALL)[number]) => {
    const admin = (await getIdTokenResult(user)).claims.catmapAdmin === true;
    const p = await users.profile(user.uid);
    if (!p) {
      let name = account.username;
      while (!(await users.isUsernameFree(name)))
        name = account.username.slice(0, 14) + Math.floor(1000 + Math.random() * 9000);
      await createProfile(user, name, account.displayName, admin ? 'admin' : 'user');
    } else if (admin && p.role !== 'admin') await updateDoc(users.ref(user.uid), { role: 'admin' });
  },
  login: async (input: string, password: string) => {
    const name = input.trim();
    const demo = DemoAccounts.find(name);
    let email = name;
    if (demo) email = `${demo.username}@${DemoAccounts.DOMAIN}`;
    else if (!name.includes('@')) {
      const key = name.replace(/^@/, '').toLowerCase();
      if (!/^[a-z0-9_.]{3,20}$/.test(key)) throw new AppException('err_wrong_credentials');
      const record = await read(`usernames/${key}`, (_id, d) => d);
      if (!record?.loginEmail) throw new AppException('err_username_login');
      email = String(record.loginEmail);
    }
    const { user } = await signInWithEmailAndPassword(authentication(), email, password);
    if (demo) {
      await AuthRepository.ensureDemoProfile(user, demo);
      await DemoVault.save(password).catch(() => {});
    } else await AuthRepository.ensureProfile(user);
  },
  reloadVerified: async () => {
    const user = authentication().currentUser;
    if (!user) return false;
    await reload(user);
    if (authentication().currentUser?.uid !== user.uid) return false;
    if (user.emailVerified) await user.getIdToken(true);
    return user.emailVerified;
  },
  resendVerification: () => {
    const user = authentication().currentUser;
    return user ? sendEmailVerification(user) : Promise.resolve();
  },
  sendPasswordReset: async (email: string) => {
    try { await sendPasswordResetEmail(authentication(), email.trim()); }
    catch (error) { if ((error as { code?: string }).code !== 'auth/user-not-found') throw error; }
  },
  logout: async () => {
    localStorage.setItem('catmap_pending_wipe', '1');
    try {
      await DemoVault.clear();
      await signOut(authentication());
      await wipeAndReload();
    } catch (error) {
      // The pending flag stays set: the reload finishes the wipe at start-up (firebase.ts) before anything else.
      AppErrors.report(error);
      location.replace('/login');
    }
  },
  switchDemoAccount: async (account: (typeof DemoAccounts.ALL)[number]) => {
    const password = await DemoVault.password();
    if (!password) throw new AppException('err_demo_sign_in_again');
    try {
      await AuthRepository.login(account.username, password);
    } catch (e) {
      const code = (e as { code?: string }).code;
      if (code === 'auth/invalid-credential' || code === 'auth/wrong-password') {
        await DemoVault.clear();
        throw new AppException('err_demo_sign_in_again');
      }
      if (code === 'auth/user-not-found') throw new AppException('err_demo_missing');
      throw e;
    }
  },
};
