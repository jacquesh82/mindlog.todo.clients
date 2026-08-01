# Migration des clients todo vers `mindlog.todo.clients/`

Relevé des couplages à traiter. **Rien n'a été déplacé** : cette liste est le
travail à faire, pas un compte rendu.

## Ce qui se déplace

| Depuis | Vers |
|---|---|
| `mindlog.todo/packages/web/` | `mindlog.todo.clients/web/` |
| `mindlog.todo/android/` | `mindlog.todo.clients/android/` |

## Ce que ça casse (relevé exhaustif au 2026-08-01)

**Workspace npm** — `packages/web` est le paquet `@mindlog/web`, déclaré dans
les `workspaces` de `mindlog.todo/package.json` (l. 11) et figé dans
`package-lock.json`. Sortir du dossier le sort du workspace : les scripts
racine `build` (l. 17) et `dev:web` (l. 21), qui font `-w @mindlog/web`,
tombent. Deux options : un workspace npm à la racine du monorepo (mais la
racine n'est pas un dépôt git — cf. `BACKLOG-vitrines.md` § Points ouverts), ou
un `package.json` autonome pour `mindlog.todo.clients` et des scripts de build
propres.

**Contexte de build Docker** — `mindlog.todo/docker-compose.yml:62` et
`.github/workflows/deploy.yml:70` référencent `packages/web/Dockerfile` ; ce
Dockerfile est bâti depuis la racine de `mindlog.todo`. Un client hors du dépôt
impose de remonter le contexte de build d'un cran, comme le fait déjà
`edge/Dockerfile` (`docker build -f edge/Dockerfile ..`).

**Dockerfile du serveur** — `packages/server/Dockerfile:8` copie
`packages/web/package.json` (pour l'installation des workspaces). À retirer si
le web quitte le dépôt.

**CI de déploiement** — `.github/workflows/deploy.yml` l. 101/119-120 embarque
`packages/web/nginx.prod.conf` dans le transfert vers l'hôte.

**Capacitor** — `android/capacitor.config.ts:20` et le fichier généré
`android/android/app/src/main/assets/capacitor.config.json:4` pointent sur
`../packages/web/dist` ; `android/scripts/sync.sh` l. 32 lance
`npm run build -w @mindlog/web` et l. 38 écrit `packages/web/dist/.android-build`.
Les deux `capacitor.config` doivent rester cohérents (le `.json` est régénéré
par `cap sync`, mais il est commité).

**Documentation** — `mindlog.todo/README.md:20`, `ROADMAP.md:18`,
`android/README.md` (l. 9 et 40).

## Ordre suggéré

1. Décider du modèle de paquet (workspace racine ou paquet autonome) — c'est la
   décision structurante, tout le reste en découle.
2. Déplacer `packages/web` → `mindlog.todo.clients/web`, corriger workspaces et
   scripts, vérifier `npm run build` puis la SPA en dev.
3. Déplacer `android/`, corriger les deux `capacitor.config` et `sync.sh`,
   vérifier par un `npm run sync` + `installRelease -PmindlogEnv=qualif`
   (⚠ jamais un build debug sur le S24 : il forcerait une désinstallation qui
   efface les données).
4. Corriger le contexte de build Docker et la CI, redéployer, vérifier en ligne.
5. Doc et `docs/architecture/clients.md` (tableau d'état).
