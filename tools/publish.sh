#!/usr/bin/env bash
# 一条命令发布：构建正式 APK → 校验签名 → 生成 publish/ 待上传目录。
#
# 用法:
#   tools/publish.sh --note "修复小屏布局" --note "新增检查更新"
#   tools/publish.sh --notes-file publish-notes.txt
#
# 前置条件: 仓库根目录存在 keystore.properties（不入库）:
#   storeFile=/绝对路径/tesla-release.jks
#   storePassword=...
#   keyAlias=...
#   keyPassword=...
set -euo pipefail
cd "$(dirname "$0")/.."

# 正式签名来源（二选一）：
#   A. keystore.properties（仓库根目录，不入库）
#   B. macOS 钥匙串条目 tesla-ble-release-keystore-password / -key-password
KS=keystore.properties
if [ -f "$KS" ]; then
  for key in storeFile storePassword keyAlias keyPassword; do
    grep -qE "^${key}=.+" "$KS" || { echo "错误：keystore.properties 缺少 ${key}" >&2; exit 2; }
  done
elif security find-generic-password -a "$USER" -s tesla-ble-release-keystore-password >/dev/null 2>&1 \
  && security find-generic-password -a "$USER" -s tesla-ble-release-key-password >/dev/null 2>&1; then
  echo "使用 macOS 钥匙串中的正式签名密码"
else
  echo "错误：缺少正式签名配置（keystore.properties 或钥匙串条目）。" >&2
  echo "本项目不会用 debug 签名代替正式签名。" >&2
  exit 2
fi

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
  export JAVA_HOME
fi
[ -n "${JAVA_HOME:-}" ] || { echo "错误：未找到 JDK 17，请设置 JAVA_HOME" >&2; exit 2; }
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"

echo "==> 构建正式包（含协议测试）"
./gradlew :protocol:test :app:assembleRelease

APK=app/build/outputs/apk/release/app-release.apk
[ -f "$APK" ] || { echo "错误：未生成 $APK" >&2; exit 1; }

APKSIGNER="$(ls "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)"
AAPT="$(ls "$SDK"/build-tools/*/aapt 2>/dev/null | sort -V | tail -1 || true)"
[ -n "$APKSIGNER" ] || { echo "错误：未找到 apksigner（请安装 build-tools）" >&2; exit 1; }
[ -n "$AAPT" ] || { echo "错误：未找到 aapt（请安装 build-tools）" >&2; exit 1; }

echo "==> 校验签名"
CERT="$("$APKSIGNER" verify --print-certs "$APK" 2>&1)" || { echo "错误：APK 未签名，拒绝发布" >&2; exit 1; }
if echo "$CERT" | grep -qi "Android Debug"; then
  echo "错误：检测到 debug 签名，拒绝发布" >&2
  exit 1
fi

echo "==> 读取 APK 元数据"
BADGING="$("$AAPT" dump badging "$APK")"
PKG="$(echo "$BADGING" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
VCODE="$(echo "$BADGING" | sed -n "s/^package:.*versionCode='\([^']*\)'.*/\1/p")"
VNAME="$(echo "$BADGING" | sed -n "s/^package:.*versionName='\([^']*\)'.*/\1/p")"
MINSDK="$(echo "$BADGING" | sed -n "s/^sdkVersion:'\([^']*\)'.*/\1/p")"
[ -n "$PKG" ] && [ -n "$VCODE" ] && [ -n "$VNAME" ] && [ -n "$MINSDK" ] \
  || { echo "错误：无法从 APK 读取包名/版本/最低系统版本" >&2; exit 1; }

echo "==> 生成发布产物 (package=$PKG versionCode=$VCODE versionName=$VNAME minSdk=$MINSDK)"
python3 tools/prepare_release.py \
  --apk "$APK" \
  --application-id "$PKG" \
  --version-code "$VCODE" \
  --version-name "$VNAME" \
  --min-sdk "$MINSDK" \
  "$@"

echo "==> 完成。上传 publish/ 内的 latest.json、releases.json 和 releases/ 下的 APK（不要覆盖 index.html/app.js）。"
