# syntax=docker/dockerfile:1

# ---- build stage: install dependencies and build the Angular app ----
# Angular 22 needs Node 22.22.3+, 24.15+ or 26+. Debian-based image (not alpine) so native build tools find their binaries.
FROM node:24-slim AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- runtime stage: nginx serves the static app and forwards /api to the backend ----
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Angular's application builder writes the browser bundle to dist/<project>/browser
COPY --from=build /app/dist/claim-management-ui/browser /usr/share/nginx/html
EXPOSE 80
