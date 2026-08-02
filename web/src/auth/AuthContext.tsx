import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, setTokens } from '../api/client';
import type { User } from '../types';
import { onDeepLink } from '../native';

interface AuthState {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName?: string) => Promise<void>;
  logout: () => Promise<void>;
  /** Re-fetch the current user (e.g. after a profile/avatar update). */
  refreshUser: () => Promise<void>;
  /** True when a mindlog-id sign-in needs an email before the account can be created. */
  mindlogIdNeedsEmail: boolean;
  completeMindlogId: (email: string) => Promise<void>;
  cancelMindlogId: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [pendingToken, setPendingToken] = useState<string | null>(null);

  async function loadUser(): Promise<void> {
    try {
      setUser(await api.me());
    } catch {
      setUser(null);
    }
  }

  // OAuth redirect returns either tokens or, for mindlog id without an email, a
  // pending token in the URL fragment. Same payload on both platforms: the web
  // gets it in `location.hash`, the Android shell in the deep link that reopens
  // the app — hence one reader for the two.
  function consumeAuthFragment(fragment: string): 'tokens' | 'pending' | null {
    const p = new URLSearchParams(fragment.replace(/^#/, ''));
    const access = p.get('access_token');
    const refresh = p.get('refresh_token');
    if (access && refresh) {
      setTokens(access, refresh);
      return 'tokens';
    }
    const pending = p.get('mindlog_id_pending');
    if (pending) {
      setPendingToken(pending);
      return 'pending';
    }
    return null;
  }

  useEffect(() => {
    // Coquille native : le consentement se déroule dans le navigateur système,
    // l'app est rouverte par son schéma custom. Il n'y a donc AUCUNE navigation
    // web au retour et `location.hash` est vide au démarrage — sans cet écouteur
    // les jetons n'atteignent jamais l'app. No-op dans un navigateur.
    onDeepLink((url) => {
      const hash = url.indexOf('#');
      if (hash < 0) return;
      void (async () => {
        if (consumeAuthFragment(url.slice(hash + 1)) === 'tokens') await loadUser();
        setLoading(false);
      })();
    });

    void (async () => {
      const kind = consumeAuthFragment(window.location.hash);
      if (kind) {
        window.history.replaceState({}, '', window.location.pathname);
        if (kind === 'tokens') await loadUser();
        setLoading(false);
        return;
      }
      if (await api.restoreSession()) await loadUser();
      setLoading(false);
    })();
  }, []);

  const value: AuthState = {
    user,
    loading,
    login: async (email, password) => {
      await api.login(email, password);
      await loadUser();
    },
    register: async (email, password, displayName) => {
      await api.register(email, password, displayName);
      await loadUser();
    },
    logout: async () => {
      await api.logout();
      setUser(null);
    },
    refreshUser: loadUser,
    mindlogIdNeedsEmail: pendingToken !== null,
    completeMindlogId: async (email) => {
      if (!pendingToken) return;
      await api.completeMindlogId(pendingToken, email);
      setPendingToken(null);
      await loadUser();
    },
    cancelMindlogId: () => setPendingToken(null),
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
