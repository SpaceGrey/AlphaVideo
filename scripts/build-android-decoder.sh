#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" ]]; then
  SDK="$(sed -n 's/^sdk.dir=//p' "$ROOT/local.properties" 2>/dev/null || true)"
fi
[[ -d "$SDK" ]] || { echo "Set ANDROID_HOME to the Android SDK" >&2; exit 1; }
NDK="$SDK/ndk/29.0.14206865"
[[ -d "$NDK" ]] || { echo "Install NDK: sdkmanager 'ndk;29.0.14206865'" >&2; exit 1; }
HOST="darwin-x86_64"
[[ "$(uname -s)" == Linux ]] && HOST="linux-x86_64"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST/bin"
VERSION="8.1.1"
WORK="$ROOT/native/build"
SOURCE="$WORK/ffmpeg-$VERSION"
mkdir -p "$WORK"
if [[ ! -d "$SOURCE" ]]; then
  ARCHIVE="$WORK/ffmpeg-$VERSION.tar.xz"
  curl --fail --location "https://ffmpeg.org/releases/ffmpeg-$VERSION.tar.xz" -o "$ARCHIVE"
  python3 - "$ARCHIVE" <<'PY'
import hashlib, sys
expected = 'b6863adde98898f42602017462871b5f6333e65aec803fdd7a6308639c52edf3'
with open(sys.argv[1], 'rb') as f:
    assert hashlib.file_digest(f, 'sha256').hexdigest() == expected, 'FFmpeg checksum mismatch'
PY
  tar -xJf "$ARCHIVE" -C "$WORK"
fi
JOBS="${ALPHAVIDEO_BUILD_JOBS:-8}"
for ABI in ${ALPHAVIDEO_ABIS:-arm64-v8a x86_64}; do
  case "$ABI" in
    arm64-v8a) ARCH=aarch64; TARGET=aarch64-linux-android; EXTRA=() ;;
    x86_64) ARCH=x86_64; TARGET=x86_64-linux-android; EXTRA=(--disable-x86asm) ;;
    *) echo "Unsupported ABI: $ABI" >&2; exit 1 ;;
  esac
  PREFIX="$WORK/$ABI/install"
  mkdir -p "$WORK/$ABI" "$PREFIX"
  if [[ ! -f "$PREFIX/lib/libavcodec.so" ]]; then
    (
      cd "$WORK/$ABI"
      "$SOURCE/configure" --prefix="$PREFIX" --target-os=android --arch="$ARCH" \
        --enable-cross-compile --cc="$TOOLCHAIN/${TARGET}29-clang" \
        --cxx="$TOOLCHAIN/${TARGET}29-clang++" --ar="$TOOLCHAIN/llvm-ar" \
        --ranlib="$TOOLCHAIN/llvm-ranlib" --strip="$TOOLCHAIN/llvm-strip" \
        --disable-everything --disable-autodetect --disable-programs --disable-doc \
        --enable-shared --disable-static --enable-pic --enable-pthreads \
        --enable-avcodec --enable-avformat --enable-avutil --enable-swscale \
        --disable-avdevice --disable-avfilter --disable-swresample \
        --enable-decoder=hevc --enable-parser=hevc --enable-demuxer=mov \
        --enable-protocol=file --extra-cflags='-O2 -fPIC' \
        --extra-ldflags='-Wl,-z,max-page-size=16384' ${EXTRA[@]+"${EXTRA[@]}"} > configure.log
      make -j"$JOBS" > compile.log 2>&1
      make install > install.log 2>&1
    )
  fi
  OUT="$ROOT/alphavideo/src/androidMain/jniLibs/$ABI"
  mkdir -p "$OUT"
  for LIB in avcodec avformat avutil swscale; do
    cp -L "$PREFIX/lib/lib$LIB.so" "$OUT/lib$LIB.so"
    "$TOOLCHAIN/llvm-strip" --strip-unneeded "$OUT/lib$LIB.so"
  done
  "$TOOLCHAIN/${TARGET}29-clang" -shared -O2 -fPIC \
    -I"$PREFIX/include" "$ROOT/alphavideo/src/androidMain/cpp/decoder.c" \
    -L"$PREFIX/lib" -lavformat -lavcodec -lavutil -lswscale -ljnigraphics -llog \
    -Wl,-z,max-page-size=16384 -Wl,-soname,libalphavideo.so -o "$OUT/libalphavideo.so"
  "$TOOLCHAIN/llvm-strip" --strip-unneeded "$OUT/libalphavideo.so"
  echo "Built HEVC Alpha decoder: $ABI"
done
