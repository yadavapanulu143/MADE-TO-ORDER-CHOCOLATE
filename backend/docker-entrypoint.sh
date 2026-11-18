#!/bin/sh
#
# Entrypoint for the service images. Starts as root only long enough to stage any mounted secrets
# into a location the unprivileged application user can read, then drops to that user and execs the
# JVM. If nothing needs staging it execs the JVM directly.
#
# Why this exists: Docker Compose file secrets are bind mounts, so the file keeps the host's
# ownership and mode. A private key with mode 0600 owned by the deploying host user is not readable
# by the container's non-root user, and the identity service would log "not readable" and fall back
# to an ephemeral signing key. Copying it to a 0400 file owned by the app user inside the container
# keeps it unreadable to anyone else while making it usable by the service.
#
# The JVM is exec'd (not run as a child), so it becomes PID 1's child under tini and receives signals.

set -eu

APP_USER=choco
# The service reads its key from CHOCO_JWK_LOCATION. Everything else is read from the environment.
KEY_SRC="${CHOCO_JWK_LOCATION:-}"
KEY_DST=/run/secrets-staged/jwt-private.pem

if [ "$(id -u)" = "0" ]; then
  if [ -n "$KEY_SRC" ] && [ -f "$KEY_SRC" ] && [ ! -r "$KEY_SRC" -o "$(stat -c '%u' "$KEY_SRC" 2>/dev/null || echo 0)" != "$(id -u $APP_USER)" ]; then
    mkdir -p "$(dirname "$KEY_DST")"
    cp "$KEY_SRC" "$KEY_DST"
    chown "$APP_USER:$APP_USER" "$KEY_DST"
    chmod 400 "$KEY_DST"
    echo "entrypoint: staged the signing key at $KEY_DST (readable by $APP_USER only)"
    exec setpriv --reuid "$APP_USER" --regid "$APP_USER" --init-groups \
      env "CHOCO_JWK_LOCATION=$KEY_DST" "$@"
  fi
  exec setpriv --reuid "$APP_USER" --regid "$APP_USER" --init-groups "$@"
fi

exec "$@"
