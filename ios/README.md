# mindlog · todo — client iOS (Capacitor)

Coquille [Capacitor](https://capacitorjs.com) autour du client web `../web`.
**Aucun code applicatif ici** : l'app embarque le build Vite de la SPA. Faire
évoluer l'app iOS = faire évoluer le code web, puis rejouer une commande.

C'est le jumeau de `../android`. Les deux coquilles ont la même structure, les
mêmes scripts et les mêmes environnements ; si l'une change, l'autre doit
suivre.

```
ios/
├─ capacitor.config.ts     appId, appName, webDir → ../web/dist
├─ scripts/
│  ├─ sync.sh              build web + cap sync   ← LA commande de mise à jour
│  ├─ xcode.sh             wrapper xcodebuild, décide l'identité de la variante
│  └─ env/{qualif,prod,local}.env
└─ ios/                    projet Xcode généré par `cap add` (commité)
```

## Mettre à jour l'app depuis le code web

```bash
cd mindlog.todo.clients/ios
npm install
npm run sync              # qualif (défaut) → todo.gra01.mindlog.today
npm run sync:prod         # prod          → todo.mindlog.today
npm run build:debug       # sync + build non signé
```

`sync.sh` rebuild `../web` puis `cap sync ios`, qui recopie `dist/` dans
`ios/App/App/public`. C'est tout : il n'y a aucun code à maintenir en double.

## Obtenir un .ipa sans Mac

Le build demande macOS et Xcode. Il n'y en a pas besoin sur le poste : la CI
s'en charge, la signature se fait ici.

```bash
gh workflow run build-ios.yml -f environment=qualif
gh run download -n ipa-shell-qualif-unsigned -D ~/Downloads/mindlog-ipa
iloader                   # Apple ID, puis « install IPA »
```

Mêmes contraintes que pour le client natif : un Apple ID gratuit signe pour
**7 jours** et **3 applications** au maximum, et le mode développeur doit être
actif sur l'appareil. Voir `../ios-native/README.md`, section « Installer sur un
appareil », pour le détail — la chaîne est identique.

## Identité des variantes

Hors prod, l'identifiant reçoit le suffixe `.testing` : l'app de test s'installe
**à côté** de la prod au lieu de l'écraser. C'est `scripts/xcode.sh` qui le
décide, d'après `MINDLOG_ENV`, exactement comme `android/app/build.gradle` le
fait avec `-PmindlogEnv`.

| `MINDLOG_ENV` | identifiant | libellé | API |
|---|---|---|---|
| `prod` | `today.mindlog.todo` | mindlog.todo | `https://todo.mindlog.today/app` |
| `qualif` | `today.mindlog.todo.testing` | mindlog.todo (qualif) | `https://todo.gra01.mindlog.today/app` |
| `local` | `today.mindlog.todo.testing` | mindlog.todo (local) | `http://localhost:8080` |

> ⚠️ Le schéma d'URL du retour OAuth **suit l'identifiant**
> (`CFBundleURLTypes` dans `ios/App/App/Info.plist`). Sans lui, la connexion
> mindlog id part dans Safari et ne revient jamais — c'est le correctif déjà
> appliqué à la coquille Android sous le nom `custom_url_scheme`. Le serveur
> doit avoir `NATIVE_CALLBACK_URL` réglé sur ce schéma.

## Pourquoi le build web est différent de celui du site

| | Web (déployé) | iOS (embarqué) |
|---|---|---|
| `VITE_BASE` | `/app/` — la SPA vit sous un sous-chemin | `/` — les assets sont à la racine du bundle |
| `VITE_API_URL` | `/app` — relatif, même origine | **absolu** (`https://…/app`) — la WebView est sur `capacitor://localhost` |

Conséquence : après un `npm run sync`, `../web/dist` contient la **saveur
embarquée**, pas celle du déploiement web. Un marqueur `dist/.android-build` le
signale — le nom vient de la coquille Android, qui pose le même, et il est gardé
tel quel pour que le `.gitignore` racine n'ait pas deux entrées à connaître.
Avant de builder l'image Docker web en local, refaire un build normal
(`cd ../web && npm run build`).

Côté serveur **rien à configurer** : `app.use(cors())` accepte toutes les
origines et l'authentification repose sur un refresh token en `localStorage`
(aucun cookie, donc aucun problème de `SameSite` en cross-origin).

## Dev contre un serveur local

`scripts/env/local.env` cible `http://localhost:8080` — le simulateur iOS
partage la pile réseau de l'hôte, contrairement à l'émulateur Android qui a
besoin de `10.0.2.2`. Sur un iPhone physique, mettre l'IP LAN du poste.

App Transport Security refusant le trafic en clair, il faut ajouter
temporairement à `ios/App/App/Info.plist` :

```xml
<key>NSAppTransportSecurity</key>
<dict><key>NSAllowsLocalNetworking</key><true/></dict>
```

**à ne jamais laisser dans un build de release.** Même précaution que le
`usesCleartextTraffic` de la coquille Android.

## Pré-requis

- Node ≥ 22 pour `sync.sh`.
- macOS, Xcode et CocoaPods **uniquement pour construire**. `cap add ios` et
  `cap sync ios` fonctionnent depuis Linux : ils sautent proprement
  `pod install`, ce qui suffit à régénérer le projet et à recopier la SPA.

## Signature

Pas de certificat : `build:debug` produit une app **non signée**, et c'est
délibéré — voir plus haut. Pour publier sur l'App Store il faudra un compte
développeur payant, un profil de provisionnement et un `exportOptions.plist` ;
rien de tout cela n'est en place, pas plus que le keystore côté Android.
