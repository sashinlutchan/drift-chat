# frontend — multi-stage build (spec sections 22 and 24): Yarn build, then static files on a small non-root nginx.
# Built from the repository root:  docker build -f frontend/drift-chat/Dockerfile -t drift/frontend .

FROM node:24-alpine AS build
WORKDIR /app
# Dependencies first so they stay cached until package.json or yarn.lock change
COPY frontend/drift-chat/package.json frontend/drift-chat/yarn.lock ./
RUN yarn install --frozen-lockfile
COPY frontend/drift-chat/ ./
RUN yarn build

FROM nginxinc/nginx-unprivileged:1.29-alpine AS runtime
COPY frontend/drift-chat/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
