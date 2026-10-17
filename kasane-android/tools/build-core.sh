#!/usr/bin/env bash
# Builds app/src/main/jniLibs/<abi>/liblumecore.so from lume/core.lm.
#
# Needs: the Lume compiler (branch clean/lume of android-apps, checked out at
# $LUME_HOME, default ../lume next to this repo's worktree), Python 3, and an
# Android NDK (ANDROID_NDK_HOME, ANDROID_NDK_ROOT or $ANDROID_HOME/ndk/*).
#
#   tools/build-core.sh            # arm64-v8a and x86_64
#   tools/build-core.sh --check    # also run lume/host_check.c on this machine
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
check="${1:-}"
lume_home="${LUME_HOME:-$here/../lume}"
[ -f "$lume_home/lume/__main__.py" ] || { echo "Lume not found at $lume_home (set LUME_HOME)"; exit 1; }
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

ndk="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [ -z "$ndk" ] && [ -n "${ANDROID_HOME:-}" ]; then ndk="$(ls -d "$ANDROID_HOME"/ndk/* 2>/dev/null | sort -V | tail -1)"; fi
[ -d "$ndk" ] || { echo "Android NDK not found"; exit 1; }
bin="$(ls -d "$ndk"/toolchains/llvm/prebuilt/*/bin | head -1)"

# LLVM IR for the core, without the C main() wrapper the CLI adds.
(cd "$lume_home" && python3 -m lume llvm "$here/lume/core.lm") > "$work/core.ll"
sed -i '/^define i32 @main() {/,/^}/d' "$work/core.ll"

for pair in "arm64-v8a aarch64-linux-android26" "x86_64 x86_64-linux-android26"; do
    set -- $pair
    abi="$1"; triple="$2"
    out="$here/app/src/main/jniLibs/$abi"
    mkdir -p "$out"
    "$bin/clang" --target="$triple" -O2 -fPIC -shared -Wno-override-module \
        -x ir "$work/core.ll" -x c "$lume_home/runtime/lume_rt.c" "$here/lume/lume_jni.c" \
        -lm -Wl,-z,max-page-size=16384 -o "$out/liblumecore.so"
    echo "built $out/liblumecore.so ($(wc -c < "$out/liblumecore.so") bytes)"
done

if [ "$check" = "--check" ]; then
    clang -O2 -Wno-override-module -x ir "$work/core.ll" -x c "$lume_home/runtime/lume_rt.c" \
        "$here/lume/host_check.c" -lm -o "$work/host_check"
    "$work/host_check"
fi
