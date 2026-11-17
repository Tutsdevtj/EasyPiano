#!/usr/bin/env bash
set -euo pipefail
# keytool asks for passwords interactively; secrets never appear in the command line.
piano_store="${1:-$PWD/easypiano-release.jks}"
piano_alias="${2:-easypiano}"
if [[ -e "$piano_store" ]]; then echo "A chave já existe: $piano_store" >&2; exit 1; fi
keytool -genkeypair -v -keystore "$piano_store" -alias "$piano_alias" -keyalg RSA -keysize 3072 -validity 10000
printf '\nChave criada. Guarde uma cópia segura; atualizações precisam da mesma chave.\n'
