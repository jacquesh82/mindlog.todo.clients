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
  ios: {
    // La SPA gère elle-même ses marges de sécurité (`env(safe-area-inset-*)`,
    // cf. le correctif « barres système sur mobile » du client web). Laisser
    // UIKit ajouter les siennes par-dessus décalerait tout deux fois.
    contentInset: 'never',
  },
};

export default config;
