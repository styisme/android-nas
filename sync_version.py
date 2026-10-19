#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os
import re
import sys

# 强制标准输出为 UTF-8，防止 Windows 环境 cp1252 编码报错
if sys.stdout.encoding != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8')

PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))
CHANGELOG_PATH = os.path.join(PROJECT_DIR, "CHANGELOG.md")

def get_latest_version():
    if not os.path.exists(CHANGELOG_PATH):
        print("[ERROR] Cannot find CHANGELOG.md")
        return None, None
    
    with open(CHANGELOG_PATH, "r", encoding="utf-8") as f:
        content = f.read()
    
    match = re.search(r'##\s*\[?v?(\d+\.\d+\.\d+)\]?', content)
    if not match:
        print("[ERROR] No version found in CHANGELOG.md")
        return None, None
        
    version_name = match.group(1)
    parts = version_name.split('.')
    version_code = str(int(parts[0]) * 10000 + int(parts[1]) * 100 + int(parts[2]))
    
    return version_name, version_code

def update_gradle(file_path, version_name, version_code):
    if not os.path.exists(file_path): 
        return
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()
    
    content = re.sub(r'versionCode\s+\d+', f'versionCode {version_code}', content)
    content = re.sub(r'versionName\s+"[^"]+"', f'versionName "{version_name}"', content)
    
    with open(file_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print(f"[OK] Synced: {os.path.relpath(file_path, PROJECT_DIR)} -> v{version_name} (Code: {version_code})")

def update_desktop():
    py_path = os.path.join(PROJECT_DIR, "desktop/nas_client.py")
    if os.path.exists(py_path):
        with open(py_path, "r", encoding="utf-8") as f:
            content = f.read()
        content = re.sub(r'self\.root\.title\(".*?"\)', f'self.root.title("Android NAS Desktop v{version_name}")', content)
        with open(py_path, "w", encoding="utf-8", newline="\n") as f:
            f.write(content)
        print(f"[OK] Synced desktop title")
    
    yml_path = os.path.join(PROJECT_DIR, ".github/workflows/build-desktop.yml")
    if os.path.exists(yml_path):
        with open(yml_path, "r", encoding="utf-8") as f:
            content = f.read()
        content = re.sub(r'asset_name:\s*nas-client-linux.*', f'asset_name: nas-client-linux-v{version_name}', content)
        content = re.sub(r'asset_name:\s*nas-client-windows.*', f'asset_name: nas-client-windows-v{version_name}.exe', content)
        with open(yml_path, "w", encoding="utf-8", newline="\n") as f:
            f.write(content)
        print(f"[OK] Synced Actions artifact names")

if __name__ == "__main__":
    print("Checking CHANGELOG.md...")
    version_name, version_code = get_latest_version()
    if version_name:
        print(f"Found version: v{version_name} (Code: {version_code})")
        update_gradle(os.path.join(PROJECT_DIR, "app/build.gradle"), version_name, version_code)
        update_gradle(os.path.join(PROJECT_DIR, "client/app/build.gradle"), version_name, version_code)
        update_desktop()
        print("Version sync completed successfully.")
