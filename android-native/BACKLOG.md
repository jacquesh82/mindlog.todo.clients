# Parité API du client natif — plan de charge

Objectif : amener `android-native` de la tranche verticale du jalon 1 à la
**parité avec la surface consommée par le client web** (~61 endpoints, 18
domaines). Ce fichier est le pilote d'une boucle autonome : il est lu au début
de chaque itération et mis à jour à la fin.

## Règles de travail

1. **Une tranche verticale à la fois**, dans l'ordre ci-dessous. Une tranche
   n'est finie que si elle va du contrat à l'écran.
2. **Les DTO sont générés, jamais retapés.** Si un domaine manque au contrat, il
   se documente d'abord dans `mindlog.todo/packages/server/src/openapi.ts`, puis
   `./scripts/fetch-openapi.sh --from-repo`, puis génération.
3. **Fini = compile + tests verts.** `bash scripts/gradle.sh :app:assembleDebug`
   pour le natif, `npx vitest run` dans `mindlog.todo/packages/server` pour le
   contrat. Un domaine dont les tests ne passent pas n'est pas coché.
4. **Un commit par tranche terminée**, dans chaque dépôt touché — jamais de
   `push`. Le commit ne prend que les fichiers de la tranche : l'arbre porte
   aussi du travail antérieur non commité, et tout ramasser d'un `git add -A`
   produirait des commits illisibles. Message en français, préfixe conventionnel
   (`feat(android-native): …`), comme l'historique du dépôt. Si la branche
   courante est la branche par défaut, en créer une d'abord.
5. **Le S24 est le juge de recette** quand il est joignable
   (`adb connect 192.168.1.152:40895`) et déverrouillé. S'il est verrouillé, ne
   pas insister : noter la vérification comme non faite.
6. Tenir à jour la section « Ce qui n'est pas implémenté » du `README.md` — c'est
   elle qui rend l'écart traçable.
7. Ne jamais supprimer de données utilisateur sur gra01 ni sur le téléphone.

## Tranches, dans l'ordre

### T1 — Navigation (projets, sections, étiquettes, filtres)

- [x] Contrat : `filterSchema` dans core + 6 routes filtres documentées (46 endpoints, test OK)
- [x] DTO régénérés (`Filter`, `FilterCreateRequest`, `FilterUpdateRequest`)
- [x] `TodoApi` : CRUD projets / sections / étiquettes / filtres (32 endpoints côté client)
- [x] Dépôt : `NavigationRepository` — projets, étiquettes, filtres (+ sections à la demande)
- [x] Écran : tiroir de navigation — projets (avec compteurs), étiquettes, filtres, vues Aujourd'hui / Boîte de réception
- [x] Filtrage de `TasksScreen` par la sélection du tiroir
- [~] Recette S24 — APK installé, vérification visuelle NON faite (téléphone verrouillé)

### T2 — Notes et carnets

- [x] Contrat : `notes` documenté — **15 routes** (et non 10 : le web n'en appelle pas la moitié), 61 endpoints publiés, test vert
- [x] DTO générés + `NotesApi` (15 routes) + `NotesRepository`
- [ ] Écrans : liste des carnets, liste des pages, éditeur
- [ ] Recette S24

### T3 — Recherche et IA

- [ ] Contrat : documenter `ai` (10 routes) — recherche sémantique, ask, réglages, quotas
- [ ] DTO + `AiApi` + `AiRepository`
- [ ] Écrans : recherche, « Ask AI », réglages IA (BYOK)
- [ ] Recette S24

### T4 — Calendrier, karma, tableau de bord

- [ ] Contrat : documenter `calendar` (4), `karma` (1), `dashboard` (1)
- [ ] DTO + API + dépôts
- [ ] Écrans : sources de calendrier, événements, karma, tableau de bord
- [ ] Recette S24

### T5 — Reste de la surface

- [ ] Contrat : `attachments`, `storage`, `export`, `oauth`
- [ ] Clés d'API et réglages de compte (le contrat les couvre déjà)
- [ ] Pagination des tâches (aujourd'hui `limit=200`, au-delà des tâches manquent en silence)
- [ ] Recette S24

## Journal

- 2026-08-02 — T1 : contrat filtres + couche réseau navigation faits ; dépôts et
  écran restants. Le client natif est passé de 14 à 32 endpoints.
- 2026-08-02 — T1 : `NavigationRepository` livré, `:app:assembleDebug` vert.
  **Écart assumé** au plan : un seul dépôt au lieu de trois. Ces trois listes ne
  sont jamais lues séparément (le tiroir les affiche ensemble et n'importe lequel
  de leurs événements le périme) ; trois dépôts auraient triplé l'abonnement au
  flux, la machine à états et le chemin de rechargement pour un seul écran — le
  client web aboutit au même regroupement dans `reloadSidebar`. Les sections
  restent hors de l'état : elles appartiennent à un projet et se lisent à la
  demande. Reste de T1 : l'écran de tiroir et le filtrage de `TasksScreen`.
- 2026-08-02 — T1 terminée côté code : `TaskView` (sélection nommée), sélection
  servie PAR LE SERVEUR (`projectId`, `labelId`, `dueBefore`, `GET /filters/{id}/tasks`)
  et non par filtrage local — `limit=200` ne garantit pas d'avoir toute la liste.
  Compteurs calculés depuis une seule lecture des tâches ouvertes, comme
  `reloadSidebar` côté web. `:app:assembleDebug` vert, APK installé sur le S24 ;
  **recette visuelle non faite**, le téléphone s'est reverrouillé.
- 2026-08-02 — T2 (1/4) : contrat des notes publié côté `mindlog.todo` et commité
  (`f756b10`). 15 routes et non 10 : le client web ignore la duplication de page,
  le résumé de carnet, l'extraction de tâches et la mise au propre de croquis.
  `Notebook`/`NotePage`/`NotePageHit` convertis en schémas Zod, le résumé servant
  de forme de référence que la page complète étend. Les corps de requête qui
  vivaient dans le fichier de routes sont remontés dans le domaine — invisibles
  du document sinon. Prochaine étape : DTO générés + `NotesApi` + dépôt.
- 2026-08-02 — T2 (2/4) : DTO régénérés, `NotesApi` et `NotesRepository` livrés,
  `:app:assembleDebug` vert. Le client natif passe à 47 endpoints. Deux écarts
  volontaires avec les autres dépôts, justifiés dans la classe : pas d'abonnement
  au flux de changements (le serveur n'émet aucun événement pour les notes —
  `ChangeEvent.Entity` ne connaît que task/project/section/label/filter), et le
  contenu des pages n'est jamais mis en cache (plusieurs Mo par page possibles).
  Contrat corrigé au passage : `DrawShape` est nommé, sinon le générateur
  produisait `DrawCleanupRequestShapesInner`. Reste : les écrans.
