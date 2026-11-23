#!/usr/bin/env bash
# Baut Stockfish 17.1 als Android-Executable und legt es als libstockfish.so
# in app/src/main/jniLibs/<abi>/ ab. Die Engine wird zur Laufzeit aus
# nativeLibraryDir als UCI-Prozess gestartet.
#
# Die NNUE-Netze werden **nicht** eingebettet (`NNUE_EMBEDDING_OFF`): Sie kommen
# als Download-Pack aus dem Store, die Binary schrumpft damit von ~76 MB auf gut 1 MB.
# Ohne geladenes Netz beendet sich Stockfish beim ersten `isready`, deshalb müssen
# beide Netze (EvalFile und EvalFileSmall) im Pack liegen.
#
# Voraussetzungen: curl, tar, Android-NWK (r21+) unter $ANDROID_NDK oder im SDK.
set -euo pipefail

SF_VERSION="sf_17.1"
NDK="${ANDROID_NDK:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}/ndk/28.2.13676358}"
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WORK_DIR="$PROJECT_DIR/tools/stockfish-build"
SRC_DIR="$WORK_DIR/Stockfish-$SF_VERSION/src"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"

if [ ! -d "$SRC_DIR" ]; then
    mkdir -p "$WORK_DIR"
    echo "Lade Stockfish $SF_VERSION ..."
    curl -sSL -o "$WORK_DIR/stockfish.tar.gz" \
        "https://github.com/official-stockfish/Stockfish/archive/refs/tags/$SF_VERSION.tar.gz"
    tar xzf "$WORK_DIR/stockfish.tar.gz" -C "$WORK_DIR"
fi

# Netze nicht einbetten: incbin in nnue/network.cpp abschalten.
if ! grep -q '^#define NNUE_EMBEDDING_OFF' "$SRC_DIR/nnue/network.cpp"; then
    sed -i '0,/#define INCBIN_SILENCE_BITCODE_WARNING/s//\/\/ Netze werden nachgeladen, nicht eingebettet.\n#define NNUE_EMBEDDING_OFF\n#define INCBIN_SILENCE_BITCODE_WARNING/' \
        "$SRC_DIR/nnue/network.cpp"
fi

export PATH="$TOOLCHAIN:$PATH"

build_abi() {
    local arch="$1"
    local abi="$2"
    echo "Baue Stockfish für $abi (ARCH=$arch) ..."
    (cd "$SRC_DIR" && make clean >/dev/null 2>&1 || true)
    (cd "$SRC_DIR" && make -j"$(nproc)" build ARCH="$arch" COMP=ndk OS=Android)

    local out_dir="$PROJECT_DIR/app/src/main/jniLibs/$abi"
    mkdir -p "$out_dir"
    "$TOOLCHAIN/llvm-strip" --strip-all -o "$out_dir/libstockfish.so" "$SRC_DIR/stockfish"
    echo "-> $out_dir/libstockfish.so"
}

build_abi armv8 arm64-v8a
build_abi x86-64 x86_64

echo "Fertig. ABI-Filter/Verpackung prüfen: app/build.gradle.kts (useLegacyPackaging)."
