FROM caddy:2.11-alpine

ARG APP_VERSION=""
ENV APP_VERSION=${APP_VERSION}

COPY ./Caddyfile /etc/caddy/Caddyfile
COPY ./webclient/dist /usr/share/caddy

EXPOSE 8080
