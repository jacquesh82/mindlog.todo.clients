import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../api/client';
import { useI18n } from '../i18n';
import { useAuth } from './AuthContext';
import type { VersionInfo } from '../types';

/**
 * Which sign-in paths this deployment has configured. Until GET /api/v1/version
 * answers we show none of the optional ones: flashing a button that then vanishes
 * is worse than it appearing a moment late. If the call fails we keep them hidden
 * rather than offering routes that may not work.
 */
type Providers = NonNullable<VersionInfo['authProviders']>;
const NO_PROVIDERS: Providers = { mindlogId: false, google: false, passwordReset: false };

type Mode = 'login' | 'register' | 'forgot';

export function LoginPage() {
  const { login, register, mindlogIdNeedsEmail, completeMindlogId, cancelMindlogId } = useAuth();
  const { t } = useI18n();
  const [mode, setMode] = useState<Mode>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [providers, setProviders] = useState<Providers>(NO_PROVIDERS);

  useEffect(() => {
    void api
      .version()
      .then((v) => setProviders(v.authProviders ?? NO_PROVIDERS))
      .catch(() => setProviders(NO_PROVIDERS));
  }, []);

  function switchMode(next: Mode) {
    setMode(next);
    setError(null);
    setInfo(null);
  }

  async function submitMindlogIdEmail(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await completeMindlogId(email);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed');
    } finally {
      setBusy(false);
    }
  }

  // mindlog id signed the user in but the account has no email — ask for one to
  // finish creating the todo account.
  if (mindlogIdNeedsEmail) {
    return (
      <div className="auth-card">
        <h1>mindlog.todo</h1>
        <p className="muted">{t('login.mindlogIdEmailTitle')}</p>
        <form onSubmit={submitMindlogIdEmail}>
          <p className="muted" style={{ marginTop: 0 }}>{t('login.mindlogIdEmailHint')}</p>
          <input
            type="email"
            placeholder="Email"
            value={email}
            required
            autoFocus
            onChange={(e) => setEmail(e.target.value)}
          />
          {error && <div className="error">{error}</div>}
          <button type="submit" disabled={busy}>
            {t('login.mindlogIdEmailSubmit')}
          </button>
          <p className="muted switch">
            <button
              type="button"
              className="link"
              onClick={() => {
                cancelMindlogId();
                setError(null);
                setEmail('');
              }}
            >
              {t('login.backToLogin')}
            </button>
          </p>
        </form>
      </div>
    );
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setInfo(null);
    setBusy(true);
    try {
      if (mode === 'login') await login(email, password);
      else if (mode === 'register') await register(email, password, displayName || undefined);
      else {
        await api.forgotPassword(email);
        setInfo(t('login.resetSent'));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed');
    } finally {
      setBusy(false);
    }
  }

  const title =
    mode === 'login'
      ? t('login.signInTitle')
      : mode === 'register'
        ? t('login.registerTitle')
        : t('login.forgotTitle');

  // Formulaire e-mail/mot de passe et bouton mindlog id : mêmes éléments à la
  // connexion et à l'inscription, mais pas la même mise en page (à l'inscription
  // ils deviennent deux blocs annoncés). On les monte une fois, on les place deux.
  const credentialsForm = (
    <form onSubmit={submit}>
      {mode === 'register' && (
        <input
          placeholder={t('login.displayName')}
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
        />
      )}
      <input
        type="email"
        placeholder="Email"
        value={email}
        required
        onChange={(e) => setEmail(e.target.value)}
      />
      <input
        type="password"
        placeholder="Password"
        value={password}
        required
        minLength={8}
        onChange={(e) => setPassword(e.target.value)}
      />
      {error && <div className="error">{error}</div>}
      <button type="submit" disabled={busy}>
        {mode === 'login' ? t('login.signIn') : t('login.register')}
      </button>
    </form>
  );

  const mindlogIdButton = providers.mindlogId && (
    <a className="google-btn mindlogid-btn" href={api.mindlogIdUrl(mode === 'register')}>
      <img
        src={`${import.meta.env.BASE_URL}milo.svg`}
        alt=""
        aria-hidden="true"
        className="provider-icon"
      />
      <span className="mindlogid-label">
        {mode === 'register' ? t('login.mindlogIdRegisterBtn') : t('login.mindlogIdBtn')}
      </span>
    </a>
  );

  return (
    <div className="auth-card">
      <h1>mindlog.todo</h1>
      <p className="muted">{title}</p>

      {mode === 'forgot' ? (
        <form onSubmit={submit}>
          <p className="muted" style={{ marginTop: 0 }}>{t('login.forgotHint')}</p>
          <input
            type="email"
            placeholder="Email"
            value={email}
            required
            onChange={(e) => setEmail(e.target.value)}
          />
          {error && <div className="error">{error}</div>}
          {info && <div className="info">{info}</div>}
          <button type="submit" disabled={busy}>
            {t('login.sendReset')}
          </button>
          <p className="muted switch">
            <button type="button" className="link" onClick={() => switchMode('login')}>
              {t('login.backToLogin')}
            </button>
          </p>
        </form>
      ) : (
        <>
          {mode === 'register' ? (
            // Créer un compte, c'est choisir ENTRE deux comptes de nature
            // différente : un compte local à mindlog.todo, ou une identité
            // mindlog valable pour toutes les apps. Empilés sans titres, le
            // second passait pour une variante de connexion du premier — d'où
            // les deux blocs annoncés, séparés par un « ou ».
            <>
              <section className="auth-block">
                <h2>{t('login.localTitle')}</h2>
                <p className="muted hint">{t('login.localHint')}</p>
                {credentialsForm}
              </section>

              {providers.mindlogId && (
                <>
                  <div className="auth-or">{t('login.or')}</div>
                  <section className="auth-block">
                    <h2>{t('login.mindlogIdTitle')}</h2>
                    <p className="muted hint">{t('login.mindlogIdHint')}</p>
                    {mindlogIdButton}
                  </section>
                </>
              )}
            </>
          ) : (
            <>
              {credentialsForm}

              {providers.passwordReset && (
                <button type="button" className="link forgot-link" onClick={() => switchMode('forgot')}>
                  {t('login.forgot')}
                </button>
              )}

              {mindlogIdButton}
            </>
          )}

          {providers.google && (
            <a className="google-btn" href={api.googleUrl()}>
              Sign in with Google
            </a>
          )}

          <p className="muted switch">
            {mode === 'login' ? t('login.noAccount') : t('login.haveAccount')}{' '}
            <button
              type="button"
              className="link"
              onClick={() => switchMode(mode === 'login' ? 'register' : 'login')}
            >
              {mode === 'login' ? t('login.register') : t('login.signIn')}
            </button>
          </p>
        </>
      )}
    </div>
  );
}
