# Sortie des clients de `mindlog.todo` — compte rendu

Migration faite le 2026-08-01. Ce document remplace le relevé de couplages qui
servait à la préparer ; il dit ce qui a changé et ce qui reste ouvert.

## Ce qui a bougé

| Depuis | Vers |
|---|---|
| `mindlog.todo/packages/web/` | `web/` |
| `mindlog.todo/android/` | `android/` |

Déplacé avec l'historique (`git subtree split`) : 69 commits côté web, l'ajout
de la coquille côté android. Les deux greffes ont été faites en `merge -s ours`
+ `read-tree --prefix`, donc `git log --follow` continue de remonter.

## Ce qu'il a fallu défaire

**Workspace npm** — `web/` était `@mindlog/web`, workspace de `mindlog.todo`. Il
devient `@mindlog/todo-web`, autonome, avec son propre `package-lock.json`. Les
scripts racine `build` et `dev:web` du dépôt serveur ont été nettoyés.

**`@mindlog/core`** — retiré des dépendances. Le client ne lui empruntait que
des types ; le lien passe désormais par un `paths` du `tsconfig.json` visant les
**déclarations compilées** du dépôt frère. Viser la source faisait typechecker
tout le serveur au passage (mjml sans types, paramètres implicites). Conséquence
assumée : `npm run typecheck` exige `mindlog.todo` cloné à côté et construit.

**`tsconfig.base.json`** — aplati dans `web/tsconfig.json`. Il vivait au-dessus,
donc hors du contexte de build Docker.

**Dockerfile** — était conscient du workspace (`npm install -w @mindlog/web
--include-workspace-root`, copie des `package.json` frères). Devient
`npm ci && npm run build`, contexte = `web/` seul.

**Capacitor** — `webDir` passe de `../packages/web/dist` à `../web/dist`, et
`scripts/sync.sh` build `../web` au lieu de `-w @mindlog/web`. Le
`capacitor.config.json` généré dans les assets est gitignoré : rien à corriger
là, `cap sync` le régénère.

**Configs nginx de déploiement** — `nginx.prod.conf` et `nginx.dev.conf` sont
**restées dans `mindlog.todo`**. Elles sont montées par ses composes et
expédiées sur str01 par sa CI : elles décrivent comment la stack tourne, pas
comment le client se bâtit. Seul `nginx.conf`, baké dans l'image, est ici.

**CI** — `mindlog.todo` ne construit plus que l'image API. L'image web est
publiée par `.github/workflows/build-web.yml` de ce dépôt, sous son propre
paquet GHCR (`ghcr.io/jacquesh82/mindlog.todo.clients/web`) : republier sous
`mindlog.todo/web` aurait demandé d'autoriser à la main ce dépôt sur un paquet
rattaché à un autre. Le compose de prod référence l'image via **`WEB_TAG`**,
distinct d'`IMAGE_TAG` qui ne tague plus que l'API.

## Vérifié

- `cd web && npm ci && npm run build` — bundle produit, hors de tout workspace.
- `npm run typecheck` — OK contre les déclarations de `core`.
- `docker build` du client seul, `VITE_BASE=/app/` — les assets sortent bien en
  `/app/assets/…`.
- `cd ../mindlog.todo && npm install && npm run build` — core + server compilent
  sans le workspace web.

## Reste ouvert

- **Le rollout n'est pas automatisé de bout en bout.** La CI d'ici publie
  l'image mais ne touche pas à str01 (les secrets SSH sont sur `mindlog.todo`).
  Mettre à jour le client en prod : `WEB_TAG=<tag> docker compose -f
  docker-compose.prod.yml up -d web`. À câbler si le va-et-vient devient pénible.
- **`ios/` reste vide.**
- **Aucune CI Android** — il n'y en avait pas non plus avant la scission.
