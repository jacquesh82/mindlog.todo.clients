# mindlog · todo — clients

Tous les clients de todo, un dossier par plateforme. Convention commune :
`docs/architecture/clients.md` du monorepo.

```
web/             SPA React + Vite — client REST pur de l'API todo
android/         coquille Capacitor autour de web/ (en service, qualif)
ios/             coquille Capacitor autour de web/ — à venir
android-native/  implémentation Kotlin à part entière — à venir
ios-native/      implémentation Swift à part entière — à venir
```

**`web/` est le produit ; `android/` n'est qu'un emballage.** La coquille ne
contient aucun code applicatif : sa `webDir` pointe sur `../web/dist` et
`npm run sync` rebuild la SPA puis lance `cap sync`. Faire évoluer l'app mobile
= faire évoluer le code web, puis rejouer une commande.

Les dossiers `-native` échappent à cette règle : ce sont de vraies
implémentations, avec leurs vues et leurs appels API, donc de la duplication
assumée. Leurs README disent ce qu'elle coûte avant d'y toucher.

## Démarrage

```sh
cd web && npm install && npm run dev     # Vite sur :5173
```

L'API doit tourner à part — `cd ../mindlog.todo && docker compose up -d api`.

```sh
cd android && npm install && npm run sync   # qualif ; puis build:debug / build:release
```

## Rapport à `mindlog.todo`

Le client était `mindlog.todo/packages/web`, un workspace npm du dépôt serveur.
Il en est sorti avec son historique (69 commits, `git subtree split`) et n'a
plus aucune dépendance de workspace : `npm ci && npm run build` suffit, et
l'image Docker se construit depuis `web/` seul.

Reste **un seul lien**, volontaire : `src/types.ts` importe ~35 types depuis
`@mindlog/core`. Ce sont des `import type`, effacés par esbuild — ni le bundle
ni l'image n'en ont besoin. Seul `npm run typecheck` les résout, via un `paths`
du `tsconfig.json` visant les déclarations compilées du dépôt frère :

```sh
cd ../mindlog.todo && npm run build -w @mindlog/core   # prérequis au typecheck
```

Les redéclarer ici serait pire : c'est exactement la divergence que le commit
« re-export shared API types from core » avait corrigée — `Task.completedAt`
existait côté serveur et restait invisible côté client, `Task.children?`
traînait sans usage.

## Déploiement

L'image web est construite et poussée par la CI de **ce** dépôt
(`.github/workflows/build-web.yml` → `ghcr.io/jacquesh82/mindlog.todo/web`).
`mindlog.todo` ne construit plus que l'API et pilote le compose de prod, où le
tag de l'image web est désormais une variable distincte (`WEB_TAG`) : les deux
dépôts ont des SHA différents, un tag commun n'avait plus de sens.
