# mindlog.todo — client Android (Capacitor)

Coquille [Capacitor](https://capacitorjs.com) autour du client web `@mindlog/web`.
**Aucun code applicatif ici** : l'app embarque le build Vite de la SPA. Faire
évoluer l'app Android = faire évoluer le code web, puis rejouer une commande.

```
android/
├─ capacitor.config.ts     appId, appName, webDir → ../packages/web/dist
├─ scripts/
│  ├─ sync.sh              build web + cap sync   ← LA commande de mise à jour
│  ├─ gradle.sh            wrapper Gradle avec JDK 21 forcé
│  └─ env/{qualif,prod,local}.env
└─ android/                projet Gradle généré par `cap add` (commité)
```

## Mettre à jour l'app depuis le code web

```bash
cd mindlog.todo/android
npm run sync              # qualif (défaut) → todo.gra01.mindlog.today
npm run sync:prod         # prod          → todo.mindlog.today
npm run build:debug       # sync + APK debug
npm run build:release     # sync + APK release (env=prod)
```

`sync.sh` rebuild `@mindlog/web` puis `cap sync android`, qui recopie `dist/`
dans `android/app/src/main/assets/public`. C'est tout : il n'y a aucun code à
maintenir en double.

APK produit dans `android/app/build/outputs/apk/{debug,release}/`.

## Pourquoi le build web est différent de celui du site

| | Web (déployé) | Android (embarqué) |
|---|---|---|
| `VITE_BASE` | `/app/` — la SPA vit sous un sous-chemin | `/` — les assets sont à la racine de l'APK |
| `VITE_API_URL` | `/app` — relatif, même origine | **absolu** (`https://…/app`) — la WebView est sur `https://localhost` |

Conséquence : après un `npm run sync`, `packages/web/dist` contient la **saveur
Android**, pas celle du déploiement web. Un marqueur `dist/.android-build` le
signale. Avant de builder l'image Docker web en local, refaire un build normal
(`npm run build -w @mindlog/web`) — la CI, elle, part toujours d'un checkout
propre et n'est donc pas concernée.

Côté serveur **rien à configurer** : `app.use(cors())` accepte toutes les
origines et l'authentification repose sur un refresh token en `localStorage`
(aucun cookie, donc aucun problème de `SameSite` en cross-origin).

## Pré-requis

- Node ≥ 22, Android SDK (`ANDROID_HOME`), **JDK ≥ 21** (Capacitor 7 / AGP 8).
  `scripts/gradle.sh` sélectionne le JDK 21 tout seul même si le JDK par défaut
  du poste est le 17 — ne pas invoquer `./gradlew` directement.

## Dev contre un serveur local

`scripts/env/local.env` cible `http://10.0.2.2:8080` (l'hôte vu de l'émulateur).
Android bloquant le trafic en clair, il faut ajouter temporairement
`android:usesCleartextTraffic="true"` au `<application>` de
`android/app/src/main/AndroidManifest.xml` — **à ne jamais laisser dans un
build de release**.

## Signature release

Pas encore de keystore : `build:release` produit un APK **non signé**. Pour
publier, déposer un keystore et le référencer dans `android/app/build.gradle`
(cf. `mindlog.talk/android/keystore/` pour la convention déjà en place).
