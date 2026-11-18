#!/usr/bin/env bash
#
# Generates the RSA key pair the identity service signs access tokens with, and writes a matching
# public key next to it. Run once per environment, then keep the private key out of Git (the
# repository's .gitignore already excludes *.pem and *.key).
#
#   scripts/generate-jwt-keys.sh              # writes ./secrets/jwt-private.pem and jwt-public.pem
#   scripts/generate-jwt-keys.sh /etc/choco/secrets
#
# The public key is not used by the application at runtime — every other service fetches the JWKS
# from the identity service over HTTP. It is written here so an operator can inspect or distribute it.
#
# Rotating this key invalidates every outstanding access token. That is intended: it is the emergency
# lever if a private key is believed to be exposed. Plan the rotation for a quiet window, because
# every signed-in customer and administrator has to sign in again.

set -euo pipefail

OUT_DIR="${1:-secrets}"
mkdir -p "$OUT_DIR"
chmod 700 "$OUT_DIR"

PRIVATE_KEY="$OUT_DIR/jwt-private.pem"
PUBLIC_KEY="$OUT_DIR/jwt-public.pem"

if [[ -e "$PRIVATE_KEY" ]]; then
  echo "Refusing to overwrite the existing key at $PRIVATE_KEY." >&2
  echo "Move it aside first if you really intend to rotate it." >&2
  exit 1
fi

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$PRIVATE_KEY" 2>/dev/null
openssl rsa -in "$PRIVATE_KEY" -pubout -out "$PUBLIC_KEY" 2>/dev/null

# The identity service reads a single PEM that contains both the private key and the matching public
# key: JwkConfiguration extracts the "PRIVATE KEY" and "PUBLIC KEY" blocks from the file it is
# pointed at, because the Nimbus RSAKey it builds needs both halves. Appending the public key to the
# private-key file is therefore required, not cosmetic — a file with only the private key fails at
# startup with "PEM does not contain a PUBLIC KEY block".
cat "$PUBLIC_KEY" >> "$PRIVATE_KEY"

# 600 is the tightest mode that still works when the file is bind-mounted into a container running
# as a non-root user.
chmod 600 "$PRIVATE_KEY"
chmod 644 "$PUBLIC_KEY"

echo "Wrote $PRIVATE_KEY (mode 600) and $PUBLIC_KEY (mode 644)."
echo "The identity service loads the private key from CHOCO_JWK_LOCATION, which the compose file"
echo "points at /run/secrets/jwt-private-key."
