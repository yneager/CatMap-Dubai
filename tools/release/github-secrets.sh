#!/bin/bash
# Writes the values for the release workflow's repository secrets to release-keys/github-secrets.txt
# (git-ignored), reading them from keystore.properties. Paste each one into
# GitHub → Settings → Secrets and variables → Actions → New repository secret, then delete the file.
set -euo pipefail
cd "$(dirname "$0")/../.."
[ -f keystore.properties ] || { echo "keystore.properties not found"; exit 1; }
prop() { grep -E "^$1=" keystore.properties | head -1 | cut -d= -f2-; }
store="$(prop storeFile)"
[ -f "$store" ] || { echo "Keystore $store not found"; exit 1; }
out=release-keys/github-secrets.txt
mkdir -p release-keys
{
  echo "CATMAP_KEYSTORE_BASE64"; base64 -w0 "$store"; echo; echo
  echo "CATMAP_KEYSTORE_PASSWORD"; prop storePassword; echo
  echo "CATMAP_KEY_ALIAS"; prop keyAlias; echo
  echo "CATMAP_KEY_PASSWORD"; prop keyPassword
} > "$out"
echo "Wrote $out — add each value as a repository secret, then delete the file."
