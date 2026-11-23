#!/usr/bin/env bash
# Baut die mitgelieferten Zusatz-Engines als Android-Binaries nach
# app/src/main/jniLibs/<abi>/lib<name>.so.
#
# Warum fest eingebaut: Android 10+ verbietet das Ausführen heruntergeladener
# Programme. Nachladen lassen sich nur Daten (NNUE-Netze über EnginePack.files).
#
# Voraussetzungen: git, curl, NDK r26+ unter $ANDROID_NDK oder im SDK.
set -euo pipefail

NDK="${ANDROID_NDK:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}/ndk/28.2.13676358}"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WORK_DIR="$PROJECT_DIR/tools/engines-build"
JNI_DIR="$PROJECT_DIR/app/src/main/jniLibs"
API=26

# Gepinnte Stände der gebauten Binaries (GPLv3-Quellen).
FAIRY_REF=5283883        # Fairy-Stockfish, klassische Bewertung (nnue=no)
ETHEREAL_REF=0e47e9b     # Ethereal 14
MONOLITH_REF=19b8a61     # Monolith v2.01 – v3 nutzt std::jthread, das Androids libc++ nicht anbietet

export PATH="$TOOLCHAIN:$PATH"
mkdir -p "$WORK_DIR"

clone() {
    local url="$1" dir="$2" ref="$3"
    if [ ! -d "$dir" ]; then
        git clone "$url" "$dir"
        git -C "$dir" checkout --detach "$ref"
    fi
}

build_fairy() {
    local dir="$WORK_DIR/fairy"
    clone https://github.com/fairy-stockfish/Fairy-Stockfish.git "$dir" "$FAIRY_REF"
    cd "$dir/src"
    make clean >/dev/null 2>&1 || true
    make -j"$(nproc)" build ARCH=armv8 COMP=ndk OS=Android nnue=no \
        CXX=aarch64-linux-android${API}-clang++
    llvm-strip --strip-all -o "$JNI_DIR/arm64-v8a/libfairyfish.so" stockfish
    make clean >/dev/null 2>&1 || true
    make -j"$(nproc)" build ARCH=x86-64 COMP=ndk OS=Android nnue=no \
        CXX=x86_64-linux-android${API}-clang++
    llvm-strip --strip-all -o "$JNI_DIR/x86_64/libfairyfish.so" stockfish
}

build_ethereal() {
    local dir="$WORK_DIR/ethereal"
    clone https://github.com/AndyGrant/Ethereal.git "$dir" "$ETHEREAL_REF"
    cd "$dir/src"
    aarch64-linux-android${API}-clang -O3 -std=gnu11 -DNDEBUG -DUSE_POPCNT \
        -o "$JNI_DIR/arm64-v8a/libethereal.so" *.c pyrrhic/tbprobe.c -lm
    x86_64-linux-android${API}-clang -O3 -std=gnu11 -DNDEBUG -DUSE_POPCNT -mpopcnt \
        -o "$JNI_DIR/x86_64/libethereal.so" *.c pyrrhic/tbprobe.c -lm
}

build_monolith() {
    local dir="$WORK_DIR/monolith"
    clone https://github.com/cimarronOST/Monolith.git "$dir" "$MONOLITH_REF"
    cd "$dir/Source"
    # Bewusst statisch gegen libc++ gelinkt: libc++_shared.so liegt nicht in der APK.
    local sources="attack.cpp bench.cpp bit.cpp board.cpp eval.cpp magic.cpp main.cpp \
        misc.cpp move.cpp movegen.cpp movepick.cpp movesort.cpp polyglot.cpp random.cpp \
        search.cpp syzygy.cpp texel.cpp thread.cpp time.cpp trans.cpp types.cpp uci.cpp zobrist.cpp"
    aarch64-linux-android${API}-clang++ -std=c++17 -O3 -DNDEBUG -pthread -static-libstdc++ \
        -o "$JNI_DIR/arm64-v8a/libmonolith.so" $sources -lm
    x86_64-linux-android${API}-clang++ -std=c++17 -O3 -DNDEBUG -pthread -static-libstdc++ \
        -o "$JNI_DIR/x86_64/libmonolith.so" $sources -lm
}

mkdir -p "$JNI_DIR/arm64-v8a" "$JNI_DIR/x86_64"
build_fairy
build_ethereal
build_monolith

echo "Fertig. Größen:"
ls -lh "$JNI_DIR"/*/lib*.so
echo "Danach ./gradlew assembleDebug."
