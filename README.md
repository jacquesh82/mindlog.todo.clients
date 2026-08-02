# mindlog · todo — clients

Les clients de [mindlog · todo](https://github.com/jacquesh82/mindlog.todo), un
gestionnaire de tâches avec API REST et serveur MCP. Un dossier par plateforme.

```
web/             SPA React + Vite — client REST de l'API todo
android/         coquille Capacitor embarquant web/
ios/             coquille Capacitor embarquant web/
android-native/  implémentation Kotlin / Compose
ios-native/      implémentation Swift / SwiftUI
```

`web/` porte l'interface et la logique ; `android/` et `ios/` n'embarquent aucun
code applicatif — leur `webDir` pointe sur `../web/dist`, et `npm run sync`
reconstruit la SPA puis lance `cap sync`. Faire évoluer l'application mobile
revient donc à faire évoluer le client web. Les deux coquilles sont jumelles :
même structure, mêmes scripts, mêmes environnements.

Les dossiers `-native` portent des implémentations complètes, qui redessinent les
vues et réécrivent les appels réseau. `ios-native/` est le portage de
`android-native/` : même découpage en modules, mêmes dépôts, mêmes décisions, et
un README qui liste les écarts assumés. Leurs README détaillent ce que cela
implique — une évolution fonctionnelle doit désormais être portée trois fois, et
rien ne signale un oubli.

## Démarrage

```sh
cd web && npm install && npm run dev     # Vite sur :5173
```

L'API doit tourner à part : `cd ../mindlog.todo && docker compose up -d api`.

```sh
cd android && npm install
npm run sync            # environnement de qualification (défaut)
npm run build:release   # APK de production
```

Les clients natifs se construisent depuis leur propre dossier, avec la même
variable d'environnement de part et d'autre :

```sh
cd android-native && ./scripts/gradle.sh assembleDebug     # qualif par défaut
cd ios-native && xcodegen generate && ./scripts/xcode.sh build
```

## Types partagés avec le serveur

`web/src/types.ts` réexporte une quarantaine de types depuis `@mindlog/core`, le
paquet de domaine du service. Ce sont des `import type`, effacés à la
compilation : ni le bundle ni l'image Docker n'en dépendent, seul
`npm run typecheck` les résout.

La résolution passe par un `paths` du `tsconfig.json` visant les déclarations
compilées du dépôt du service. Le typecheck suppose donc `mindlog.todo` cloné à
côté de ce dépôt et construit :

```sh
cd ../mindlog.todo && npm install && npm run build -w @mindlog/core
```

Redéclarer ces types ici les ferait diverger de ceux du serveur sans qu'aucune
erreur ne le signale.

## Image Docker

L'image du client web est construite par la CI de ce dépôt et publiée sur
`ghcr.io/jacquesh82/mindlog.todo.clients/web`. Elle se construit aussi en local,
le contexte étant `web/` seul :

```sh
cd web && docker build --build-arg VITE_API_URL=/app --build-arg VITE_BASE=/app/ -t todo-web .
```

`VITE_BASE` préfixe les URL des assets : la SPA est servie sous le sous-chemin
`/app` en production. Le déploiement de la pile (compose, reverse proxy) est
piloté depuis le dépôt du service, qui référence l'image via la variable
`WEB_TAG`.

## Licence

AGPL-3.0-or-later — voir [LICENSE](LICENSE).
