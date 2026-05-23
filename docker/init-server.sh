#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/.env"
EXAMPLE_FILE="${SCRIPT_DIR}/.env.production.example"

public_url="${1:-}"
if [[ -z "${public_url}" ]]; then
  public_url="http://$(hostname -I 2>/dev/null | awk '{print $1}'):18080"
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker is not installed or not in PATH." >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "ERROR: docker compose v2 is required." >&2
  exit 1
fi

random_b64() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 48 | tr -d '\n'
  else
    date +%s%N | sha256sum | awk '{print $1}'
  fi
}

random_hex() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 32 | tr -d '\n'
  else
    date +%s%N | sha256sum | awk '{print $1}'
  fi
}

if [[ -f "${ENV_FILE}" ]]; then
  echo "docker/.env already exists; leaving it unchanged."
else
  cp "${EXAMPLE_FILE}" "${ENV_FILE}"
  db_password="$(random_b64)"
  db_root_password="$(random_b64)"
  jwt_secret="$(random_b64)"
  searxng_secret="$(random_hex)"

  sed -i.bak \
    -e "s#MATECLAW_PUBLIC_URL=.*#MATECLAW_PUBLIC_URL=${public_url}#" \
    -e "s#MATECLAW_CORS_ALLOWED_ORIGINS=.*#MATECLAW_CORS_ALLOWED_ORIGINS=${public_url}#" \
    -e "s#DB_PASSWORD=.*#DB_PASSWORD=${db_password}#" \
    -e "s#DB_ROOT_PASSWORD=.*#DB_ROOT_PASSWORD=${db_root_password}#" \
    -e "s#JWT_SECRET=.*#JWT_SECRET=${jwt_secret}#" \
    -e "s#SEARXNG_SECRET=.*#SEARXNG_SECRET=${searxng_secret}#" \
    "${ENV_FILE}"
  rm -f "${ENV_FILE}.bak"
  chmod 600 "${ENV_FILE}" || true
  echo "Created docker/.env for ${public_url}"
fi

echo
echo "Next commands:"
echo "  cd ${SCRIPT_DIR}"
echo "  docker compose --env-file .env -f docker-compose.server.yml up -d --build"
echo "  docker compose --env-file .env -f docker-compose.server.yml logs -f yliyunclaw-server"
