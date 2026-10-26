#!/usr/bin/env bash
# 分辨率兼容性测试：列出 / 启动不同机型的模拟器
# 用法:
#   scripts/screen-matrix.sh list
#   scripts/screen-matrix.sh start small      # 854x480 小屏
#   scripts/screen-matrix.sh start pixel7     # 1080x2400
#   scripts/screen-matrix.sh start s23u       # 1440x3088
# 可选机型: small hd fhd xl pixel7 pixel4a nexus5 redmi s23u
# 附加参数会透传给 emulator（如 -no-window -gpu swiftshader_indirect）
set -euo pipefail
SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
EMU="$SDK/emulator/emulator"

case "${1:-list}" in
  list)
    "$EMU" -list-avds | grep '^tesla_' | sed 's/^tesla_/  /' | sort
    ;;
  start)
    name="${2:-fhd}"
    shift 2 || true
    exec "$EMU" -avd "tesla_$name" -no-snapshot -no-boot-anim "$@"
    ;;
  *)
    echo "用法: $0 list | start <small|hd|fhd|xl|pixel7|pixel4a|nexus5|redmi|s23u>" >&2
    exit 1
    ;;
esac
