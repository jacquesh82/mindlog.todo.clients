# Image du client web todo. Contexte de build = ce dossier (`web/`), plus rien
# au-dessus : depuis la sortie du monorepo `mindlog.todo`, ce client n'a plus
# aucune dépendance de workspace.
#
# `@mindlog/core` n'est PAS nécessaire ici. Le client ne lui emprunte que des
# types (`import type`), effacés par esbuild — l'ancien Dockerfile ne copiait
# d'ailleurs que son `package.json`, jamais son code. Seul `npm run typecheck`
# le résout, depuis la source du dépôt frère (cf. tsconfig.json).
FROM node:24-bookworm AS build
WORKDIR /app
ARG VITE_API_URL
ARG VITE_BASE
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_BASE=$VITE_BASE

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# Runtime stage: nginx serving the static files.
FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
