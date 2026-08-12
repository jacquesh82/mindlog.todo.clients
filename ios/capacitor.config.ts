import type { CapacitorConfig } from '@capacitor/cli';

// Coquille iOS du client web. Aucun code applicatif ici : `webDir` pointe
// directement sur le build Vite du client `../web`, que `scripts/sync.sh`
// (re)génère avec les bonnes variables avant chaque `cap sync`.
//
// La WebView sert le bundle depuis l'origine `capacitor://localhost`, donc
// l'API est forcément CROSS-ORIGIN : la SPA doit être buildée avec un
// VITE_API_URL ABSOLU (cf. scripts/env/*.env) et VITE_BASE=/ puisque les assets
// sont à la racine du bundle et non sous le sous-chemin /app du web. Côté
// serveur rien à faire : `app.use(cors())` accepte toutes les origines et
// l'auth passe par un refresh token en localStorage (aucun cookie SameSite).
//
// Le schéma diffère d'Android (`https://localhost`) sans que cela change quoi
// que ce soit ici — dans les deux cas l'origine n'est pas celle de l'API.
const config: CapacitorConfig = {
  // Identité de PROD, comme la coquille Android. L'identifiant réellement posé
  // sur l'appareil est décidé au build par `scripts/xcode.sh` d'après
  // MINDLOG_ENV : hors prod il devient `today.mindlog.todo.testing`, pour que
  // la variante de test cohabite avec la prod. Ces deux champs ne servent qu'au
  // runtime Capacitor et aux plugins.
  appId: 'today.mindlog.todo',
  appName: 'mindlog todo',
  webDir: '../web/dist',
  // Volontairement AUCUN réglage `ios`. La coquille Android n'en a qu'un
  // (`allowMixedContent: false`), et tout ce qui existe ici sans exister là-bas
  // est un écart de comportement de plus entre deux apps censées être la même.
  //
  // Un `contentInset: 'never'` a figuré ici, déduit du fait que la SPA gère ses
  // propres `env(safe-area-inset-*)`. Déduit, pas mesuré : rien dans `web/` ne
  // le demandait. Il est retiré tant qu'une observation ne le justifie pas.
};

export default config;
