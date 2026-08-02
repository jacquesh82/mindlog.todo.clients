# mindlog · todo — client Android natif (Kotlin)

Implémentation native, à distinguer de la coquille `../android` :

| | `../android` | `android-native` (ici) |
|---|---|---|
| Nature | coquille Capacitor embarquant `../web` | implémentation à part entière |
| Vues | celles du client web | Jetpack Compose |
| Appels réseau | ceux du client web | Kotlin / Retrofit |
| Évolution fonctionnelle | héritée du web par synchronisation | à implémenter ici |

La contrepartie est permanente : chaque évolution fonctionnelle doit être portée
ici en plus du client web, et un écart entre les deux ne provoque aucune erreur
— il produit deux applications différentes sous le même nom. La section
« Ce qui n'est pas implémenté » ci-dessous existe pour rendre cet écart
traçable ; elle est à tenir à jour.

## Démarrer

```sh
./scripts/gradle.sh assembleDebug            # qualif (défaut)
MINDLOG_ENV=local ./scripts/gradle.sh installDebug
MINDLOG_ENV=prod  ./scripts/gradle.sh assembleRelease
```

`scripts/gradle.sh` force un JDK 21+ — AGP refuse le JDK 17 souvent installé par
défaut — et impose un `-PmindlogEnv` explicite, pour qu'aucun build ne parte
silencieusement sur la valeur par défaut.

| `MINDLOG_ENV` | API | applicationId |
|---|---|---|
| `prod` | `https://todo.mindlog.today/app/` | `today.mindlog.todo.native` |
| `qualif` | `https://todo.gra01.mindlog.today/app/` | `today.mindlog.todo.native.testing` |
| `local` | `http://10.0.2.2:8080/` | `today.mindlog.todo.native.testing` |

Les quatre variantes (coquille et natif, prod et testing) cohabitent sur un même
appareil — c'est ce qui permet de les comparer côte à côte.

`-PmindlogEnv` ne fixe qu'un **défaut** : `ServerStore` laisse changer l'URL à
l'exécution, ce qui permet de pointer un build debug sur la qualif sans
recompiler.

> ⚠️ Les URL de base doivent finir par `/`. La prod et la qualif sont servies
> sous le sous-chemin `/app`, et Retrofit supprime le dernier segment d'une base
> qui n'en a pas — `…/app` devient `…/`, ce qui donne un 404 en prod alors que
> le local, servi à la racine, continue de marcher. `ApiUrlTest` verrouille ce
> comportement.

## Contrat d'API

Le service expose sa spécification OpenAPI ; elle fait foi. Les DTO Kotlin en
sont **générés**, jamais retapés — `Task` porte vingt champs, et une copie
maintenue à la main dérive sans que rien n'échoue.

```sh
./scripts/fetch-openapi.sh                   # depuis un serveur qui tourne
./scripts/fetch-openapi.sh --from-repo       # depuis le dépôt frère compilé
git diff openapi/                            # ce que l'API a changé
```

Ce qui est versionné est le **snapshot** `openapi/mindlog-todo.openapi.json`,
pas le Kotlin qu'il produit : un clone compile sans serveur à portée, et le
`git diff` du snapshot est la seule chose qui mérite une revue. La génération
est rejouée à chaque `assembleDebug` (voir `AndroidOpenApiConventionPlugin`).

Le snapshot est en OpenAPI **3.0.3** et non 3.1 : 3.1 écrit un champ nullable
`type: ["string","null"]`, que le générateur Kotlin lit comme un `String` non
nul.

## Organisation

```
build-logic/convention/   plugins de convention — Android, Compose, Hilt, features, OpenAPI
app/                      activité, navigation, thème appliqué
core/designsystem/        palette ambre, typographie, formes
core/datastore/           session chiffrée, serveur courant, BuildConfig d'environnement
core/network/             Retrofit, interception, renouvellement de jeton, flux SSE
core/data/                repositories — seule source de vérité des écrans
feature/auth/             connexion e-mail/mot de passe et mindlog id
feature/tasks/            liste, ajout, complétion
```

Un module n'existe que si deux modules le consomment ou si sa configuration de
build diffère. `core/model`, `core/database` et `core/ui` sont donc absents à ce
stade, délibérément (voir `settings.gradle.kts`).

Les versions viennent du client Kotlin archivé de `mindlog.talk`
(`mindlog.talk/_archive/android/`), élaguées et remises à jour — cette archive
est aussi la source de `ChangeEventStream` et de la structure du thème.

## Ce qui n'est pas implémenté

Le jalon 1 est une **tranche verticale** : connexion, liste des tâches ouvertes,
ajout, complétion, rafraîchissement temps réel. Tout le reste du produit web est
absent :

- **Hors-ligne.** Aucune base locale. Ce n'est pas un raccourci mais une
  conséquence de l'API : le flux SSE transporte des signaux d'invalidation sans
  charge utile, il n'existe ni curseur `changedSince` ni pierre tombale, donc
  chaque événement impose une relecture complète de toute façon. Une couche Room
  ajouterait un schéma, des migrations et une seconde source de vérité sans
  supprimer une seule requête. C'est l'**écriture** hors-ligne qui la
  justifierait — et elle demande d'abord une réponse serveur pour les
  suppressions.
- **Pièces jointes** : l'API est câblée, mais aucun écran ne les affiche — il
  faudrait une vue de détail de tâche, qui n'existe pas encore.
- **Export** : copié dans le presse-papier, faute de sélecteur de fichier.
- **Google** comme fournisseur d'identité (mindlog id et mot de passe seulement).
- **i18n** : les libellés sont en anglais, en dur. Le web tient deux
  dictionnaires plats dans `web/src/i18n.tsx` ; les reprendre suppose de les
  extraire (l'interpolation `{var}` n'est pas celle d'Android).
- **Widgets, notifications push, thème sombre suivi finement, tests d'UI.**

## Connexion mindlog id

Le bouton n'apparaît que si `GET /api/v1/version` annonce le fournisseur
configuré — l'afficher sans cela mènerait droit à un 503.

Le parcours passe par un **Chrome Custom Tab**, pas une WebView : les
fournisseurs d'identité refusent les WebViews, et le Custom Tab partage la
session du navigateur. Le retour arrive sur un schéma propre à la variante
(`today.mindlog.todo.native[.testing]://auth/callback`), avec les jetons dans le
**fragment** — jamais dans la query, qui finirait dans un journal serveur.

Côté serveur, l'intention « native » voyage dans le paramètre `state`, signé
(`signOAuthState`) : c'est le seul paramètre qui survit à l'aller-retour chez
l'IdP, dont la `redirect_uri` est fixe. L'adresse de retour vient de
`NATIVE_CALLBACK_URL`, une variable de configuration — jamais d'un paramètre de
requête, qui serait un open redirect distribuant des jetons.

> ⚠️ **Avant publication.** Un schéma custom est interceptable : une autre
> application peut l'enregistrer et recevoir les jetons. Acceptable tant que
> l'app n'est pas publiée. Avant le Play Store, basculer sur des App Links —
> `assetlinks.json` et `android:autoVerify` — sur un chemin **distinct** de
> `/app/auth/callback`, pour ne pas capturer le retour de la SPA quand les deux
> sont installées. Seule la valeur de `NATIVE_CALLBACK_URL` change.

## Signature

`keystore.properties` à la racine du module, chargé s'il existe. Absent, la
variante release se construit quand même, non signée — de sorte qu'un clone
frais et la CI fonctionnent sans secret. Ce fichier et les `*.jks` sont ignorés
par git.
