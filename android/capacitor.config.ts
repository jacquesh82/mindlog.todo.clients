import type { CapacitorConfig } from '@capacitor/cli';

// Coquille Android du client web. Aucun code applicatif ici : `webDir` pointe
// directement sur le build Vite de `@mindlog/web`, que `scripts/sync.sh`
// (re)génère avec les bonnes variables avant chaque `cap sync`.
//
// La WebView sert le bundle depuis l'origine `https://localhost` (androidScheme
// par défaut), donc l'API est forcément CROSS-ORIGIN : la SPA doit être buildée
// avec un VITE_API_URL ABSOLU (cf. scripts/env/*.env) et VITE_BASE=/ puisque
// les assets sont à la racine de l'APK et non sous le sous-chemin /app du web.
// Côté serveur rien à faire : `app.use(cors())` accepte toutes les origines et
// l'auth passe par un refresh token en localStorage (aucun cookie SameSite).
const config: CapacitorConfig = {
  // Identité de PROD. L'applicationId réellement posé sur l'appareil est décidé
  // par android/app/build.gradle d'après -PmindlogEnv : hors prod il devient
  // `today.mindlog.todo.testing`, pour que la variante de test cohabite avec la
  // prod. Ces deux champs ne servent qu'au runtime Capacitor et aux plugins.
  appId: 'today.mindlog.todo',
  appName: 'mindlog todo',
  webDir: '../packages/web/dist',
  android: {
    // Le bundle est local : aucun contenu en clair ne doit être chargé.
    allowMixedContent: false,
  },
};

export default config;
