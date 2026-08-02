# mindlog · todo — client iOS natif (Swift)

Implémentation native, à distinguer de la coquille `../ios` :

| | `../ios` | `ios-native` (ici) |
|---|---|---|
| Nature | coquille Capacitor embarquant `../web` | implémentation à part entière |
| Vues | celles du client web | SwiftUI |
| Appels réseau | ceux du client web | Swift / URLSession |
| Évolution fonctionnelle | héritée du web par synchronisation | à implémenter ici |

C'est le **portage du client `../android-native`**, fichier pour fichier : même
découpage en modules, mêmes dépôts, mêmes décisions. Les commentaires qui
expliquent un choix non évident sont repris des deux côtés, et quand le choix
diffère, le fichier dit pourquoi.

La contrepartie est permanente : chaque évolution fonctionnelle doit être portée
ici en plus du client web **et** du client Android, et un écart ne provoque
aucune erreur — il produit trois applications différentes sous le même nom. La
section « Ce qui n'est pas implémenté » et la section « Écarts assumés »
existent pour rendre cet écart traçable ; elles sont à tenir à jour.

## Démarrer

macOS et Xcode 16 (Swift 6) sont requis. Le projet Xcode n'est **pas** versionné
— il est généré depuis `project.yml` :

```sh
brew install xcodegen
xcodegen generate            # produit MindlogTodo.xcodeproj
open MindlogTodo.xcodeproj
```

En ligne de commande :

```sh
./scripts/xcode.sh build                      # qualif (défaut)
MINDLOG_ENV=local ./scripts/xcode.sh build
MINDLOG_ENV=prod  ./scripts/xcode.sh archive
swift test                                    # tests du paquet, sans simulateur
```

| `MINDLOG_ENV` | configuration | API | bundle id |
|---|---|---|---|
| `prod` | `Release` | `https://todo.mindlog.today/app/` | `today.mindlog.todo.native` |
| `qualif` | `Debug-qualif` | `https://todo.gra01.mindlog.today/app/` | `today.mindlog.todo.native.testing` |
| `local` | `Debug-local` | `http://localhost:8080/` | `today.mindlog.todo.native.testing` |

`local` vise `localhost` et non `10.0.2.2` : cette adresse est l'alias de la
machine hôte vu de l'émulateur Android ; le simulateur iOS partage directement
la pile réseau de l'hôte.

L'environnement ne fixe qu'un **défaut** : `ServerStore` laisse changer l'URL à
l'exécution, ce qui permet de pointer un build debug sur la qualif sans
recompiler.

> ⚠️ Les URL de base doivent finir par `/`. La prod et la qualif sont servies
> sous le sous-chemin `/app`, et tout ce qui *résout* un chemin relatif contre la
> base au lieu d'y *ajouter* des segments perd ce préfixe — l'échec n'apparaît
> alors qu'à la promotion, le local étant servi à la racine. `APIURLTests`
> verrouille les URL produites, comme `ApiUrlTest` du côté Android.

Le cleartext vers `localhost` demande une exception App Transport Security ;
elle est dans `App/Info-Debug.plist`, utilisé par les seules configurations
debug. La release utilise `App/Info.plist` et donc le défaut de la plateforme,
HTTPS uniquement. Même arrangement que le `networkSecurityConfig` cantonné à
`app/src/debug/` côté Android.

## Contrat d'API

Le service expose sa spécification OpenAPI ; elle fait foi. Les DTO Swift en
sont **générés**, jamais retapés — `TodoTask` porte vingt champs, et une copie
maintenue à la main dérive sans que rien n'échoue.

```sh
./scripts/fetch-openapi.sh                   # depuis un serveur qui tourne
./scripts/fetch-openapi.sh --from-repo       # depuis le dépôt frère compilé
./scripts/generate-models.sh                 # Swift ← snapshot
git diff openapi/ Sources/CoreNetwork/Generated/
```

Deux différences avec le client Android, toutes deux assumées :

- **Le Swift généré est versionné**, pas seulement le snapshot. Gradle a déjà
  une JVM sous la main et régénère à chaque `assembleDebug` ; Xcode n'en a pas,
  et exiger un JDK de quiconque ouvre le projet coûte plus cher que de relire un
  diff généré. La revue se fait sur `git diff`, snapshot **et** sortie.
- **Les modèles sont préfixés `Todo`.** `Task`, `Label` et `Section` sont des
  noms du contrat *et* de Swift (`_Concurrency.Task`, `SwiftUI.Label`,
  `SwiftUI.Section`). Un modèle nommé `Task` masque celui de la concurrence dans
  tout le paquet. Le préfixe ne renomme que le type Swift : les `CodingKeys`, et
  donc le format sur le fil, sont intacts.

Seuls les **modèles** sont générés. Le client que produirait openapi-generator
apporte sa propre pile URLSession, sa gestion d'authentification et ses types
d'erreur, dont aucun ne survit au renouvellement de jeton ni au flux SSE de ce
module. `AuthAPI` et `TodoAPI` restent écrits à la main, et c'est une liste de
chemins.

Le snapshot est en OpenAPI **3.0.3** et non 3.1 : 3.1 écrit un champ nullable
`type: ["string","null"]`, que le générateur lit comme un type non optionnel.

## Organisation

```
project.yml                cible application, configurations, schémas (XcodeGen)
Package.swift              les modules, un par module Gradle du client Android
App/                       point d'entrée, garde d'authentification, racine de composition
Sources/CoreDesignSystem/  palette ambre, typographie, formes
Sources/CoreDatastore/     trousseau, serveur courant, environnement de build
Sources/CoreNetwork/       URL, client HTTP, renouvellement de jeton, flux SSE, DTO générés
Sources/CoreData/          dépôts — seule source de vérité des écrans
Sources/FeatureAuth/       connexion e-mail/mot de passe et mindlog id
Sources/FeatureTasks/      liste, ajout, complétion, colonne de navigation
Tests/CoreNetworkTests/    résolution d'URL, découpage des trames SSE
Tests/CoreDataTests/       analyse du retour OAuth
```

Les tests portent sur les trois fonctions pures qui, en échouant, ne produisent
aucun message : une URL qui perd `/app`, une trame SSE mal recollée, un jeton
dont le `+` devient une espace. `swift test` les exécute sans simulateur.

La correspondance avec le client Android est directe :

| Android | ici |
|---|---|
| `BaseUrlInterceptor` | `APIURL` |
| `AuthInterceptor` + `OkHttpClient` | `HTTPClient` |
| `TokenAuthenticator` (Mutex) | `TokenRefresher` (acteur) |
| `ChangeEventStream` (okhttp-sse) | `ChangeEventStream` (URLSession.bytes) |
| `KeystoreCipher` + DataStore | `Keychain` |
| `BuildConfig` / `-PmindlogEnv` | `MindlogEnvironment` / `MINDLOG_ENV` |
| Hilt | `AppContainer` |
| `StateFlow` | `@Observable` (+ `signedInChanges()`) |
| `debounce(300)` | `changeSignals(from:matching:debounce:)` |
| `ModalNavigationDrawer` | `NavigationSplitView` |
| Chrome Custom Tab | `ASWebAuthenticationSession` |

Un module n'existe que si deux modules le consomment ou si sa configuration de
build diffère — même règle que `settings.gradle.kts`. `CoreModel`,
`CoreDatabase` et `CoreUI` sont donc absents à ce stade, délibérément.

## Écarts assumés avec le client Android

Ce ne sont pas des oublis. Chacun est commenté à l'endroit où il se produit.

- **Navigation.** Le tiroir modal n'existe pas sur iOS et son imitation se
  remarque. La colonne latérale d'un `NavigationSplitView` porte le même
  contenu : liste puis détail sur iPhone, deux colonnes sur iPad. C'est le seul
  écart d'**interaction**.
- **Typographie.** Android fige des tailles en `sp` ; ici les styles sont
  relatifs aux styles système, pour que le réglage de taille de texte de l'iOS
  soit suivi. Les proportions sont les mêmes.
- **Chiffrement de session.** `KeystoreCipher` fait soixante lignes d'AES-GCM
  parce que DataStore est un fichier ordinaire. Le trousseau *est* le magasin
  chiffré ; le portage est un accesseur. Le choix de ne pas exiger l'appareil
  déverrouillé est repris tel quel (`kSecAttrAccessibleAfterFirstUnlock`) — le
  jeton doit rester lisible écran verrouillé, c'est exactement quand le flux
  d'événements se reconnecte.
- **Retour OAuth.** `ASWebAuthenticationSession` rend l'URL de rappel
  directement à l'appelant, ce qu'un Custom Tab ne sait pas faire. Le schéma
  `today.mindlog.todo.native[.testing]://auth/callback` est quand même
  enregistré et traité par `onOpenURL`, pour le cas où l'utilisateur termine le
  parcours dans Safari.
- **`coerceInputValues`.** kotlinx.serialization remplace silencieusement une
  valeur nulle par le défaut du champ ; `Codable` échoue. Une dérive du contrat
  se voit donc ici et pas là-bas.

## Ce qui n'est pas implémenté

Comme le client Android, le jalon 1 est une **tranche verticale** : connexion,
liste des tâches ouvertes, ajout, complétion, rafraîchissement temps réel,
navigation entre projets, étiquettes et filtres. Tout le reste du produit web
est absent :

- **Hors-ligne.** Aucune base locale. Ce n'est pas un raccourci mais une
  conséquence de l'API : le flux SSE transporte des signaux d'invalidation sans
  charge utile, il n'existe ni curseur `changedSince` ni pierre tombale, donc
  chaque événement impose une relecture complète de toute façon. Une couche
  SwiftData ajouterait un schéma, des migrations et une seconde source de vérité
  sans supprimer une seule requête. C'est l'**écriture** hors-ligne qui la
  justifierait — et elle demande d'abord une réponse serveur pour les
  suppressions.
- **Pagination.** La liste demande `limit=200`, le maximum accepté. Au-delà, des
  tâches manquent silencieusement.
- **Création et modification** de projets, sections, étiquettes et filtres : les
  méthodes existent sur `NavigationRepository`, aucun écran ne les appelle.
- **Notes, recherche sémantique, calendrier, karma, pièces jointes, réglages.**
- **Google** comme fournisseur d'identité (mindlog id et mot de passe seulement).
- **i18n** : les libellés sont en anglais, en dur — même état que le client
  Android.
- **Widgets, notifications push, tests d'interface.**

## Connexion mindlog id

Le bouton n'apparaît que si `GET /api/v1/version` annonce le fournisseur
configuré — l'afficher sans cela mènerait droit à un 503.

Le parcours passe par un `ASWebAuthenticationSession`, pas un `WKWebView` : les
fournisseurs d'identité refusent les vues web embarquées, et cette session
partage les cookies de Safari, donc un utilisateur déjà connecté à mindlog id
n'est pas interrogé deux fois. Le retour arrive sur un schéma propre à la
variante (`today.mindlog.todo.native[.testing]://auth/callback`), avec les
jetons dans le **fragment** — jamais dans la query, qui finirait dans un journal
serveur.

Côté serveur, l'intention « native » voyage dans le paramètre `state`, signé :
c'est le seul paramètre qui survit à l'aller-retour chez l'IdP, dont la
`redirect_uri` est fixe. L'adresse de retour vient de `NATIVE_CALLBACK_URL`, une
variable de configuration — jamais d'un paramètre de requête, qui serait un open
redirect distribuant des jetons.

> ⚠️ **Avant publication.** Un schéma custom est interceptable : une autre
> application peut l'enregistrer et recevoir les jetons. Acceptable tant que
> l'app n'est pas publiée. Avant l'App Store, basculer sur des Universal Links —
> `apple-app-site-association` et le droit `associated-domains` — sur un chemin
> **distinct** de `/app/auth/callback`, pour ne pas capturer le retour de la SPA
> quand les deux sont installées. Seule la valeur de `NATIVE_CALLBACK_URL`
> change. C'est la même précaution que les App Links du côté Android.

## Signature

`CODE_SIGN_STYLE` est automatique et aucune équipe n'est fixée : un clone frais
construit pour le simulateur sans aucun secret, `scripts/xcode.sh` passant
`CODE_SIGNING_ALLOWED=NO`. Pour un appareil, renseigner `DEVELOPMENT_TEAM` dans
Xcode après `xcodegen generate` — ce réglage ne survit pas à une régénération,
il doit donc aller dans `project.yml` s'il devient permanent. Les profils et
certificats sont ignorés par git.
