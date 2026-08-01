# mindlog · todo — clients

Destination des clients de todo, un dossier par plateforme. Convention commune :
`docs/architecture/clients.md`.

```
web/        ⬜ vide — à migrer depuis mindlog.todo/packages/web  (React + Vite)
android/    ⬜ vide — à migrer depuis mindlog.todo/android       (Capacitor, en service)
ios/        ⬜ vide — à venir
```

> **Le code n'a pas encore bougé.** Les clients tournent toujours dans
> `mindlog.todo/`. Ce dossier est la cible, pas l'état actuel — voir
> `MIGRATION.md` pour ce que le déplacement casse et dans quel ordre le faire.

## Ce qui existe déjà, et pourquoi c'est un bon point de départ

`mindlog.todo` applique **déjà** la règle « un seul socle, des shells autour » :
`android/` ne contient aucun code applicatif, sa `webDir` pointe sur
`../packages/web/dist` et `npm run sync` rebuild la SPA puis lance `cap sync`.
La migration est donc un déménagement de fichiers et de chemins, pas une
refonte : il n'y a pas de logique dupliquée à réconcilier.

Le point délicat est ailleurs — `packages/web` est un **workspace npm** de
`mindlog.todo` (`@mindlog/web`), référencé par les scripts racine, le Dockerfile
du serveur et la CI de déploiement.
