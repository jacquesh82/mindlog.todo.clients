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
   (`adb connect 192.168.1.152:40895`), déverrouillé ET LIBRE. S'il est
   verrouillé, ou si une autre application est au premier plan — Jacques s'en
   sert — ne pas insister : noter la vérification comme non faite. Prendre
   l'écran interromprait son travail, et une capture photographierait ce qu'il
   est en train de faire.
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
- [x] Écrans : carnets, pages, éditeur (module `feature/notes`) + entrée « Notes » dans le tiroir
- [~] Recette S24 — NON faite : téléphone déverrouillé mais en cours d'usage personnel

### T3 — Recherche et IA

- [x] Contrat : `ai` documenté — **12 routes** (prompts, usage, logs, réglages, modèles), 73 endpoints publiés, test vert
- [x] DTO générés + `AiApi` (12 routes) + `AiRepository` + recherche/ask de tâches dans `TodoApi`
- [x] Écrans : recherche (deux corpus), « Ask AI » avec sources, réglages IA (BYOK et mode hébergé)
- [~] Recette S24 — NON faite : téléphone verrouillé

### T4 — Calendrier, karma, tableau de bord

- [x] Contrat : `calendar` (7 routes), `karma`, `dashboard` — **82 endpoints publiés**, test vert
- [x] DTO générés + `CalendarApi` (9 routes, calendrier + karma + dashboard) + `CalendarRepository`
- [x] Écrans : calendrier (événements + abonnements + rattachement) et tableau de bord (karma inclus)
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
- 2026-08-02 — T2 (3/4) : module `feature/notes` — trois écrans et leur
  navigation typée. **Décision de format** : le contenu d'une page est un
  document `{ mode, boxes, markdown }` ; le natif n'édite QUE le markdown et
  reconduit `boxes` à l'identique. Sans ça, ouvrir puis sauver depuis le
  téléphone effacerait un canevas construit sur le web, sans erreur. Une page en
  mode `blocks` s'ouvre donc en LECTURE, avec la raison affichée à l'écran, et
  le mode n'est jamais basculé — le passer en `raw` ferait disparaître les
  boîtes de la vue web alors qu'elles existent encore. Sauvegarde explicite et
  non à la frappe : chaque PATCH renvoie la page entière. `:app:assembleDebug`
  vert. Reste de T2 : la recette S24.
- 2026-08-02 — Recette T2 abandonnée volontairement : le S24 était déverrouillé
  mais Jacques s'en servait (session personnelle au premier plan). Faire la
  recette imposait de voler l'écran et d'en capturer le contenu ; la capture
  prise par erreur a été supprimée. **Règle 5 à préciser : « déverrouillé » ne
  suffit pas, il faut aussi que l'appareil soit libre.**
- 2026-08-02 — T3 (1/4) : contrat `ai` publié (12 routes, 73 endpoints, 59
  schémas nommés), commité `5bfd8a5`. Huit interfaces converties en schémas.
  Le contrat énonce deux invariants qui n'étaient écrits nulle part : la clé
  d'API est en écriture seule, et `/ai/models` est un POST parce que la requête
  peut porter une clé.
- 2026-08-02 — T3 (2/4) : `AiApi`, `AiRepository`, et les deux routes de
  recherche/ask de tâches ajoutées à `TodoApi`. Client natif à **61 endpoints**.
  Contrat corrigé une seconde fois pour le nommage : `AiSettings` est désormais
  composé de parties nommées (`AiCredits`, `ChatProvider`, `ChatModel`) au lieu
  de produire des `AiSettingsProvidersInner`. Le dépôt interroge tâches ET notes
  en parallèle et tolère l'échec d'un seul corpus — la moitié des résultats vaut
  mieux que rien, à condition que l'écran le dise. Reste : les écrans.
- 2026-08-02 — T3 (3/4) : module `feature/ai` — recherche, ask, réglages.
  `SearchResults` porte désormais l'échec de chaque corpus : une liste vide
  faute de résultats et une liste vide faute de réponse se ressemblent à
  l'écran et ne veulent pas dire la même chose — l'écran affiche
  « unavailable » plutôt que « no match ». Les sources de la réponse sont
  rendues telles quelles : c'est ce qui permet de vérifier au lieu de croire.
  Le champ de clé d'API est masqué, jamais pré-rempli, et vidé de l'état dès
  l'envoi réussi. Mode hébergé : les réglages sont masqués plutôt qu'offerts
  sans effet. `:app:assembleDebug` vert. Reste de T3 : la recette.
- 2026-08-02 — T4 (1/3) : contrat calendrier/karma/tableau de bord (82 endpoints,
  commit `9349204`). `MindlogIdConnectionStatus` déplacé de `auth.service.ts`
  vers le domaine — le document ne lit que le domaine, l'état de rattachement
  lui était invisible. Blocs du tableau de bord nommés séparément et `karma`
  publié en $ref vers le même schéma que `/karma`. Vérifié : plus aucun schéma
  au nom dérivé d'un chemin dans tout le document.
- 2026-08-02 — Recette T3 non faite : téléphone verrouillé. **Trois recettes en
  attente** (T1, T2, T3). L'écart entre « compile » et « fonctionne » ne se
  réduit pas ; c'est le risque principal du plan de charge à ce stade.
- 2026-08-02 — T4 (2/3) : `CalendarApi` et `CalendarRepository`. Client natif à
  **70 endpoints**. **Ma vérification anti-`Inner` de l'itération précédente
  était fausse** : je cherchais des noms suspects dans les composants du
  document, or le générateur invente ces noms PARCE QUE les schémas sont inline
  — ils ne peuvent donc pas y figurer. Le bon contrôle parcourt les schémas à la
  recherche d'objets imbriqués sans `$ref`. Il a révélé quatre blocs du tableau
  de bord, plus `VersionInfo.authProviders` et `AskResult.noteSources` qui
  traînaient depuis l'origine. Tous nommés : **plus aucun objet inline** dans les
  74 schémas. Les événements sont demandés sur une fenêtre explicite (28 jours),
  et l'URL d'un abonnement iCal est convertie en `URI` dans le dépôt — une
  adresse mal formée échoue avant l'aller-retour.
- 2026-08-02 — T4 (3/3) : module `feature/calendar` — deux écrans, deux entrées
  de tiroir de plus. Le karma est lu DANS le tableau de bord au lieu d'un second
  appel : `/dashboard` le porte déjà. Une entrée « journée entière » n'affiche
  pas d'heure — montrer minuit ferait croire à un rendez-vous nocturne. Le
  rattachement mindlog id distingue « lié » de « droit agenda accordé » : sans
  le second, aucun événement n'en vient, et le dire évite de chercher la panne
  ailleurs. Tendance sur 14 jours en barres de texte : une dépendance de tracé
  pour quatorze valeurs serait payer cher une courbe. T4 close côté code.
