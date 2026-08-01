# Image du client web todo. Le contexte de build est ce dossier : le client n'a
# aucune dépendance en dehors de son propre `package.json`.
#
# `@mindlog/core` n'est pas nécessaire ici — le client ne lui emprunte que des
# types, effacés à la compilation. Seul `npm run typecheck` le résout, depuis le
# dépôt du service (cf. tsconfig.json).
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
