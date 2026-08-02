/**
 * Pont vers la coquille Capacitor, lu À L'EXÉCUTION et jamais importé.
 *
 * Le même bundle est servi dans un navigateur ordinaire (todo.mindlog.today/app),
 * où `window.Capacitor` n'existe pas : une dépendance de build sur `@capacitor/*`
 * embarquerait du code natif mort dans le build web. Le plugin `@capacitor/app`
 * est donc installé côté shell Android uniquement, et exposé sur ce global.
 */
interface CapacitorBridge {
  isNativePlatform?: () => boolean;
  Plugins?: {
    App?: {
      addListener: (event: 'appUrlOpen', cb: (data: { url: string }) => void) => void;
    };
  };
}

const bridge = (): CapacitorBridge | undefined =>
  (window as unknown as { Capacitor?: CapacitorBridge }).Capacitor;

/** Vrai dans l'APK Android, faux dans un navigateur. */
export const isNativeShell = (): boolean => bridge()?.isNativePlatform?.() === true;

/**
 * Ouverture de l'app par son schéma custom (`today.mindlog.todo[.testing]://…`).
 * C'est par là que revient l'autorisation mindlog id sur mobile : le navigateur
 * système porte le consentement, l'app ne reçoit que l'URL de retour.
 * No-op hors coquille native, ou si le plugin n'est pas embarqué.
 */
export function onDeepLink(handler: (url: string) => void): void {
  bridge()?.Plugins?.App?.addListener('appUrlOpen', ({ url }) => handler(url));
}
