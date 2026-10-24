#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os, re, sys

if sys.stdout.encoding != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8')

PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))
CHANGELOG_PATH = os.path.join(PROJECT_DIR, "CHANGELOG.md")

def get_latest_version():
    if not os.path.exists(CHANGELOG_PATH):
        return None, None
    with open(CHANGELOG_PATH, "r", encoding="utf-8") as f:
        content = f.read()
    match = re.search(r'##\s*\[?v?(\d+\.\d+\.\d+)\]?', content)
    if not match: return None, None
    version_name = match.group(1)
    parts = version_name.split('.')
    version_code = str(int(parts[0]) * 10000 + int(parts[1]) * 100 + int(parts[2]))
    return version_name, version_code

def update_file(rel_path, pattern, replacement):
    full = os.path.join(PROJECT_DIR, rel_path)
    if not os.path.exists(full): return
    with open(full, "r", encoding="utf-8") as f: content = f.read()
    new_content = re.sub(pattern, replacement, content)
    if new_content != content:
        with open(full, "w", encoding="utf-8", newline="\n") as f: f.write(new_content)
        print(f"[OK] Synced: {rel_path}")

if __name__ == "__main__":
    print("Checking CHANGELOG.md...")
    version_name, version_code = get_latest_version()
    if not version_name: sys.exit(1)
    print(f"Found version: v{version_name} (Code: {version_code})")

    # 1. Gradle 版本号
    gradle_files = ["app/build.gradle", "client/app/build.gradle", "tv/app/build.gradle", "watch/app/build.gradle"]
    for gf in gradle_files:
        update_file(gf, r'versionCode\s+\d+', f'versionCode {version_code}')
        update_file(gf, r'versionName\s+["\'][^"\']+["\']', f'versionName "{version_name}"')

    # 2. 服务端 OTA 接口
    update_file("app/src/main/java/com/androidnas/NasHttpServer.java", r'\\"version\\":\\"[^"]+\\"', f'\\"version\\":\\"{version_name}\\"')

    # 3. 前端 index.html (OTA 检查与关于页面)
    update_file("app/src/main/assets/index.html", r'当前版本: v\d+\.\d+\.\d+', f'当前版本: v{version_name}')
    update_file("app/src/main/assets/index.html", r'版本: v\d+\.\d+\.\d+ 旗舰版', f'版本: v{version_name} 旗舰版')
    update_file("app/src/main/assets/index.html", r'data\.version === "\d+\.\d+\.\d+"', f'data.version === "{version_name}"')

    # 4. 桌面端
    update_file("desktop/nas_client.py", r'self\.root\.title\("Android NAS Desktop[^"]*"\)', f'self.root.title("Android NAS Desktop v{version_name}")')

    # 5. CLI 端 (如果没有版本号则跳过)
    # 6. 苹果端 NetworkManager.swift
    update_file("apple/NASClient/NetworkManager.swift", r'let appVersion = "[^"]+"', f'let appVersion = "{version_name}"')
    
    # 7. 鸿蒙端 ApiClient.ets
    update_file("harmony/entry/src/main/ets/utils/ApiClient.ets", r'static appVersion = "[^"]+"', f'static appVersion = "{version_name}"')

    print("Version sync completed successfully.")
