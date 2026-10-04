#!/usr/bin/env bash
# Writes .env with freshly generated secrets. Refuses to overwrite an existing file.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -e .env ]; then echo ".env already exists; refusing to overwrite it" >&2; exit 1; fi
umask 077
cat > .env <<ENV
DB_PASSWORD=$(openssl rand -hex 16)
JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
FIELD_ENCRYPTION_KEY=$(openssl rand -base64 32 | tr -d '\n')
ENV
echo "Wrote .env with generated secrets (not committed: it is in .gitignore)"
