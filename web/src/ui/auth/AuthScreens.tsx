import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { AuthRepository } from '../../data/AuthRepository';
import { AppTopBar, Field, ErrorText } from '../components/Components';
import { useApp, useAction } from '../../state';
import { t } from '../../i18n';
import { safeRoute } from '../../util/links';
import { AppException } from '../../data/Firestore';
export default function AuthScreens() {
  const location = useLocation(),
    path = location.pathname,
    target =
      typeof location.state?.returnTo === 'string' &&
      /^\/(post|chat|cat|sighting|rescue|station|sitter|user)\//.test(location.state.returnTo)
        ? safeRoute(location.state.returnTo)
        : '/map',
    navigate = useNavigate(),
    { user, verified } = useApp(),
    action = useAction();
  const [email, setEmail] = useState(''),
    [password, setPassword] = useState(''),
    [confirmation, setConfirmation] = useState(''),
    [username, setUsername] = useState(''),
    [displayName, setDisplayName] = useState(''),
    [show, setShow] = useState(false),
    [sent, setSent] = useState(false),
    [cooldown, setCooldown] = useState(0);
  const route = path.slice(1);
  useEffect(() => {
    if (user && verified && !action.busy && !['register', 'forgot'].includes(route))
      navigate(target, { replace: true });
    if (user && !verified && route === 'login')
      navigate('/verify', { replace: true, state: { returnTo: target } });
  }, [user, verified, action.busy, route, navigate, target]);
  useEffect(() => {
    if (route !== 'verify') return;
    const interval = setInterval(() => void AuthRepository.reloadVerified().catch(() => {}), 5000);
    return () => {
      clearInterval(interval);
    };
  }, [route, navigate, target]);
  useEffect(() => {
    if (!cooldown) return;
    const timeout = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(timeout);
  }, [cooldown]);
  const submit = async () => {
    if (route === 'register') {
      if (await action.run(() => AuthRepository.register(email, password, username, displayName)))
        navigate('/verify', { replace: true, state: { returnTo: target } });
    } else if (route === 'forgot') {
      if (await action.run(() => AuthRepository.sendPasswordReset(email))) setSent(true);
    } else await action.run(() => AuthRepository.login(email, password));
  };
  if (route === 'verify')
    return (
      <>
        <AppTopBar title={t('verify_email_title')} back={false} />
        <div className="auth-column" role="main">
          <div className="auth-logo">
            <img src="/icon-192.png" alt="" />
          </div>
          <h2>{t('verify_email_title')}</h2>
          <p>{t('verify_email_body', user?.email ?? '')}</p>
          <ErrorText text={action.error} />
          <button
            className="primary"
            disabled={action.busy}
            onClick={() =>
              void action.run(async () => {
                if (!(await AuthRepository.reloadVerified()))
                  throw new AppException('not_verified_yet');
              })
            }
          >
            {t('i_verified')}
          </button>
          <button
            className="outlined"
            disabled={action.busy || cooldown > 0}
            onClick={() =>
              void action.run(async () => {
                await AuthRepository.resendVerification();
                setCooldown(60);
              }, t('verification_resent'))
            }
          >
            {cooldown ? t('resend_in', cooldown) : t('resend_email')}
          </button>
          <button className="text-button" onClick={() => void AuthRepository.logout()}>
            {t('use_other_account')}
          </button>
        </div>
      </>
    );
  return (
    <>
      {route !== 'login' && (
        <AppTopBar title={t(route === 'register' ? 'create_account' : 'reset_password')} />
      )}
      <form
        className="auth-column"
        role="main"
        onSubmit={(e) => {
          e.preventDefault();
          void submit();
        }}
      >
        {route === 'login' ? (
          <>
            <div className="auth-logo">
              <img src="/icon-192.png" alt="" />
            </div>
            <h1>{t('app_name')}</h1>
            <p className="center muted">{t('tagline')}</p>
          </>
        ) : route === 'forgot' ? (
          <p>{t('reset_password_hint')}</p>
        ) : (
          <>
            <Field
              label={t('display_name')}
              value={displayName}
              onChange={setDisplayName}
              limit={60}
            />
            <Field
              label={t('username')}
              value={username}
              onChange={setUsername}
              limit={20}
              hint={t('username_rules')}
              required
              pattern="[A-Za-z0-9_.]{3,20}"
              autoComplete="username"
            />
          </>
        )}
        {sent ? (
          <>
            <p className="card">{t('reset_sent', email)}</p>
            <Link className="primary" to="/login">
              {t('back_to_login')}
            </Link>
          </>
        ) : (
          <>
            <Field
              label={t(route === 'login' ? 'email_or_username' : 'email')}
              value={email}
              onChange={setEmail}
              type={route === 'login' ? 'text' : 'email'}
              required
              autoComplete={route === 'login' ? 'username' : 'email'}
            />
            {route !== 'forgot' && (
              <>
                <Field
                  label={t('password')}
                  value={password}
                  onChange={setPassword}
                  type={show ? 'text' : 'password'}
                  autoComplete={route === 'register' ? 'new-password' : 'current-password'}
                  required
                  minLength={route === 'register' ? 8 : 1}
                  hint={route === 'register' ? t('password_min') : undefined}
                />
                <button
                  className="text-button password-toggle"
                  type="button"
                  onClick={() => setShow((v) => !v)}
                >
                  {t(show ? 'hide_password' : 'show_password')}
                </button>
              </>
            )}
            {route === 'register' && (
              <>
                <Field
                  label={t('confirm_password')}
                  value={confirmation}
                  onChange={setConfirmation}
                  type={show ? 'text' : 'password'}
                  required
                />
                <p className="muted small">{t('register_privacy_note')}</p>
                {confirmation && confirmation !== password && (
                  <ErrorText text={t('passwords_dont_match')} />
                )}
              </>
            )}
            <ErrorText text={action.error} />
            <button
              className="primary"
              disabled={action.busy || (route === 'register' && confirmation !== password)}
            >
              {action.busy
                ? t('web_loading')
                : t(
                    route === 'register'
                      ? 'create_account'
                      : route === 'forgot'
                        ? 'send_reset_link'
                        : 'login',
                  )}
            </button>
          </>
        )}
        {route === 'login' && (
          <>
            <Link className="text-button" to="/forgot">
              {t('forgot_password')}
            </Link>
            <Link className="outlined" to="/register">
              {t('create_account')}
            </Link>
            <Link className="text-button" to="/help-directory">
              {t('help_directory')}
            </Link>
          </>
        )}
      </form>
    </>
  );
}
