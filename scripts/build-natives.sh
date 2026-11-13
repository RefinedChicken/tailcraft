#!/usr/bin/env bash
# Cross-compiles tailcraft-helper for every supported platform and copies the
# binaries plus checksums.json into the mod's resources.
#
# Uses `go` from PATH when it is the pinned version (as in CI via setup-go);
# otherwise downloads and verifies the official toolchain into the user cache.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/helper/bin"
RES="$ROOT/mod/src/main/resources/assets/tailcraft/native"
GO_VERSION=go1.27.4
mkdir -p "$OUT" "$RES"

export CGO_ENABLED=0
export GOTOOLCHAIN=local

sha256() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

GO=go
if ! command -v go >/dev/null 2>&1 || [[ $(go env GOVERSION) != "$GO_VERSION" ]]; then
  host_os=$(uname -s | tr '[:upper:]' '[:lower:]')
  host_arch=$(uname -m)
  case "$host_arch" in
    x86_64|amd64) host_arch=amd64 ;;
    arm64|aarch64) host_arch=arm64 ;;
    *) echo "unsupported build host architecture: $host_arch" >&2; exit 1 ;;
  esac
  [[ "$host_os" == darwin || "$host_os" == linux ]] || { echo "unsupported build host OS: $host_os" >&2; exit 1; }
  toolchain="${XDG_CACHE_HOME:-$HOME/.cache}/tailcraft/go/$GO_VERSION"
  if [[ ! -x "$toolchain/bin/go" ]]; then
    file="$GO_VERSION.$host_os-$host_arch.tar.gz"
    want=$(curl --retry 3 -fsSL 'https://go.dev/dl/?mode=json&include=all' |
      jq -r --arg f "$file" '.[].files[] | select(.filename == $f) | .sha256')
    [[ -n "$want" ]] || { echo "no published checksum for $file" >&2; exit 1; }
    archive=$(mktemp)
    curl --retry 3 -fsSL "https://go.dev/dl/$file" -o "$archive"
    [[ $(sha256 "$archive") == "$want" ]] || { echo "checksum mismatch for $file" >&2; rm -f "$archive"; exit 1; }
    rm -rf "$toolchain" && mkdir -p "$toolchain"
    tar -xzf "$archive" -C "$toolchain" --strip-components=1
    rm -f "$archive"
  fi
  GO="$toolchain/bin/go"
fi
[[ $("$GO" env GOVERSION) == "$GO_VERSION" ]] || { echo "Go version mismatch: want $GO_VERSION" >&2; exit 1; }

for target in windows/amd64 windows/arm64 darwin/amd64 darwin/arm64 linux/amd64 linux/arm64; do
  os=${target%/*}
  arch=${target#*/}
  dir="$os-$arch"
  name=tailcraft-helper
  [[ "$os" == windows ]] && name=tailcraft-helper.exe
  mkdir -p "$OUT/$dir" "$RES/$dir"
  (cd "$ROOT/helper" && GOOS="$os" GOARCH="$arch" "$GO" build -buildvcs=false -tags=ts_omit_ssh -trimpath -ldflags='-s -w' -o "$OUT/$dir/$name" ./cmd/tailcraft-helper)
  cp "$OUT/$dir/$name" "$RES/$dir/$name"
done

# checksums.json maps "<os>-<arch>/<binary>" to its SHA-256.
{
  echo "{"
  first=1
  for p in $(cd "$RES" && ls -d */tailcraft-helper* | sort); do
    [[ $first == 1 ]] || echo ","
    printf '  "%s": "%s"' "$p" "$(sha256 "$RES/$p")"
    first=0
  done
  echo
  echo "}"
} > "$RES/checksums.json"
