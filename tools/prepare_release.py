#!/usr/bin/env python3
"""生成待上传的发布产物（publish/）。

只根据真实构建产物生成数据：APK 元数据由调用方（publish.sh）从 aapt 读取后传入，
发布日志必须由 --note / --notes-file 显式提供，不编造历史或测试结果。
"""
import argparse
import hashlib
import json
import shutil
import sys
from datetime import date
from pathlib import Path

# Android 版本号用于展示；实际兼容性判断以 minSdk 为准。
MIN_ANDROID = {
    26: "8.0", 27: "8.1", 28: "9", 29: "10", 30: "11",
    31: "12.0", 32: "12.1", 33: "13", 34: "14", 35: "15", 36: "16",
}


def main():
    ap = argparse.ArgumentParser(description="生成 publish/ 目录（latest.json、releases.json、APK）")
    ap.add_argument("--apk", required=True, help="已签名的正式 APK 路径")
    ap.add_argument("--application-id", required=True)
    ap.add_argument("--version-code", required=True, type=int)
    ap.add_argument("--version-name", required=True)
    ap.add_argument("--min-sdk", required=True, type=int)
    ap.add_argument("--note", action="append", default=[], help="更新日志条目，可重复")
    ap.add_argument("--notes-file", help="从文件读取更新日志，每行一条")
    ap.add_argument("--date", default=date.today().isoformat())
    ap.add_argument("--out", default="publish")
    a = ap.parse_args()

    apk = Path(a.apk)
    if not apk.is_file():
        sys.exit(f"错误：APK 不存在：{apk}")

    notes = list(a.note)
    if a.notes_file:
        notes += [line.strip() for line in Path(a.notes_file).read_text(encoding="utf-8").splitlines() if line.strip()]
    if not notes:
        sys.exit("错误：缺少发布日志。用 --note 或 --notes-file 提供，脚本不会自动生成更新内容。")

    out = Path(a.out)
    rel_dir = out / "releases"
    rel_dir.mkdir(parents=True, exist_ok=True)

    name = f"tesla-ble-{a.version_name}-{a.version_code}.apk"
    dst = rel_dir / name
    if dst.exists():
        sys.exit(f"错误：拒绝覆盖已发布的文件 {dst}。请提升 versionCode 后重新发布。")

    releases_path = out / "releases.json"
    history = []
    if releases_path.exists():
        try:
            history = json.loads(releases_path.read_text(encoding="utf-8")).get("releases", [])
        except Exception as e:  # noqa: BLE001
            sys.exit(f"错误：releases.json 解析失败：{e}")
    if any(item.get("versionCode") == a.version_code for item in history):
        sys.exit(f"错误：versionCode {a.version_code} 已存在于 releases.json。")

    shutil.copy2(apk, dst)
    data = dst.read_bytes()
    record = {
        "available": True,
        "applicationId": a.application_id,
        "versionCode": a.version_code,
        "versionName": a.version_name,
        "apkUrl": f"releases/{name}",
        "sha256": hashlib.sha256(data).hexdigest(),
        "sizeBytes": len(data),
        "publishedAt": a.date,
        "minAndroid": MIN_ANDROID.get(a.min_sdk, str(a.min_sdk)),
        "minSdk": a.min_sdk,
        "notes": notes,
        "releaseNotes": "\n".join(notes),
    }

    (out / "latest.json").write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
    (out / "releases.json").write_text(
        json.dumps({"releases": [record] + history}, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"已生成：{out / 'latest.json'}")
    print(f"已生成：{out / 'releases.json'}")
    print(f"已复制：{dst}")
    print(f"sha256={record['sha256']}")
    print(f"sizeBytes={record['sizeBytes']}")


if __name__ == "__main__":
    main()
