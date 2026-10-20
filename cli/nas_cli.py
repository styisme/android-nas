#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import sys
import os
import argparse
import json
import requests
import cmd
import shlex
import getpass

CONFIG_FILE = os.path.expanduser("~/.nas_cli_config.json")

def format_size(size):
    if size < 1024: return f"{size} B"
    if size < 1024 * 1024: return f"{size / 1024:.1f} KB"
    return f"{size / 1024 / 1024:.1f} MB"

# ================= 配置向导 =================
def run_setup():
    print("\033[96m" + "="*40)
    print("      Android NAS CLI 初始化向导")
    print("="*40 + "\033[0m")
    
    url = input("1. 请输入 NAS 地址 (如 192.168.1.100:8080): ").strip()
    if not url: url = "http://127.0.0.1:8080"
    if not url.startswith("http"): url = "http://" + url
    
    user = input("2. 请输入账号 (直接回车默认 admin): ").strip() or "admin"
    pwd = getpass.getpass("3. 请输入密码 (直接回车默认 admin): ").strip() or "admin"
    
    print("4. 请选择服务类型:")
    print("   [1] 原生 NAS 接口 (默认)")
    print("   [2] WebDAV (暂仅供记录)")
    print("   [3] FTP (暂仅供记录)")
    proto = input("   请输入序号 (1/2/3): ").strip()
    service_type = {"1": "native", "2": "webdav", "3": "ftp"}.get(proto, "native")
    
    config = {
        "url": url,
        "user": user,
        "password": pwd,
        "type": service_type
    }
    
    with open(CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(config, f)
        
    print(f"\n\033[92m[+] 配置保存成功！\033[0m")
    return config

def load_config():
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return None

# ================= 基础网络请求 =================
class NasAPI:
    def __init__(self, config):
        self.base_url = config["url"]
        self.auth = (config["user"], config["password"])
        self.session = requests.Session()
        self.session.auth = self.auth
        self.session.timeout = 10

    def req(self, method, endpoint, **kwargs):
        url = f"{self.base_url}{endpoint}"
        try:
            r = self.session.request(method, url, **kwargs)
            if r.status_code == 401:
                print("\n\033[91m[-] 认证失败，请重新运行 --setup\033[0m")
                sys.exit(1)
            r.raise_for_status()
            return r
        except Exception as e:
            print(f"\n\033[91m[-] 请求失败: {e}\033[0m")
            return None

    def list_dir(self, path):
        r = self.req("GET", "/api/list", params={"path": path})
        if r: return r.json()
        return None

    def download(self, remote, local):
        r = self.req("GET", "/api/download", params={"path": remote}, stream=True)
        if not r: return
        total = int(r.headers.get('content-length', 0))
        with open(local, "wb") as f:
            downloaded = 0
            for chunk in r.iter_content(chunk_size=8192):
                f.write(chunk)
                downloaded += len(chunk)
                if total > 0:
                    percent = int(downloaded * 100 / total)
                    sys.stdout.write(f"\r\033[92m下载进度: {percent}% ({format_size(downloaded)}/{format_size(total)})\033[0m")
                    sys.stdout.flush()
        print(f"\n\033[92m[+] 保存成功: {local}\033[0m")

    def upload(self, local, remote_dir):
        filename = os.path.basename(local)
        with open(local, 'rb') as f:
            files = {'file': (filename, f)}
            data = {'path': remote_dir}
            r = self.req("POST", "/api/upload", files=files, data=data)
            if r: print(f"\033[92m[+] 上传成功: {filename}\033[0m")

    def mkdir(self, path, name):
        r = self.req("POST", "/api/mkdir", data={"path": path, "name": name})
        if r: print("\033[92m[+] 目录已创建\033[0m")

    def delete(self, path):
        r = self.req("POST", "/api/delete", data={"path": path})
        if r: print("\033[92m[+] 删除成功\033[0m")

# ================= 交互式 Shell =================
class NasShell(cmd.Cmd):
    intro = "\033[94m=== Android NAS Interactive Shell ===\n输入 ? 查看帮助，输入 exit 退出。\033[0m"
    prompt = "\033[92mnas> \033[0m"

    def __init__(self, api):
        super().__init__()
        self.api = api
        self.cwd = "/"

    def update_prompt(self):
        self.prompt = f"\033[92m{self.cwd}> \033[0m"

    def emptyline(self): pass

    def do_pwd(self, arg):
        """显示当前目录"""
        print(self.cwd)

    def do_cd(self, arg):
        """切换目录 (例如: cd /Movies 或 cd ..)"""
        if not arg or arg == ".": return
        if arg == "..":
            if self.cwd == "/": return
            parts = self.cwd.strip("/").split("/")
            parts.pop()
            self.cwd = "/" + "/".join(parts) if parts else "/"
        elif arg == "/":
            self.cwd = "/"
        else:
            path = arg if arg.startswith("/") else f"{self.cwd.rstrip('/')}/{arg}"
            data = self.api.list_dir(path)
            if data: self.cwd = data.get("path", path)
            else: print("\033[91m[-] 目录不存在\033[0m")
        self.update_prompt()

    def do_ls(self, arg):
        """列出当前目录内容"""
        path = arg if arg else self.cwd
        data = self.api.list_dir(path)
        if data:
            files = data.get("files", [])
            if not files:
                print(" (空目录)"); return
            print(f"\033[94m{'类型':<6} {'大小':<10} 名称\033[0m")
            print("-" * 40)
            for f in files:
                t = "\033[34m[DIR] \033[0m" if f["dir"] else "[FILE]"
                s = "-" if f["dir"] else format_size(f["size"])
                print(f"{t:<15} {s:<10} {f['name']}")

    def do_cat(self, arg):
        """查看文本文件内容"""
        if not arg: return
        path = arg if arg.startswith("/") else f"{self.cwd.rstrip('/')}/{arg}"
        r = self.api.req("GET", "/api/preview", params={"path": path})
        if r: print(r.text)

    def do_get(self, arg):
        """下载文件 (例如: get /Movies/a.mp4 本地路径)"""
        args = shlex.split(arg)
        if not args: return
        remote = args[0] if args[0].startswith("/") else f"{self.cwd.rstrip('/')}/{args[0]}"
        local = args[1] if len(args) > 1 else os.path.basename(remote)
        self.api.download(remote, local)

    def do_put(self, arg):
        """上传文件 (例如: put ./本地文件.mp4)"""
        if not arg or not os.path.exists(arg): return
        self.api.upload(arg, self.cwd)

    def do_mkdir(self, arg):
        """创建文件夹 (例如: mkdir NewFolder)"""
        if not arg: return
        self.api.mkdir(self.cwd, arg)

    def do_rm(self, arg):
        """删除文件或文件夹"""
        if not arg: return
        path = arg if arg.startswith("/") else f"{self.cwd.rstrip('/')}/{arg}"
        self.api.delete(path)

    def do_exit(self, arg):
        """退出 Shell"""
        print("Bye!")
        return True
    
    def do_quit(self, arg):
        """退出 Shell"""
        return self.do_exit(arg)

# ================= 主程序 =================
def main():
    parser = argparse.ArgumentParser(description="Android NAS CLI Client")
    parser.add_argument("--setup", action="store_true", help="重新运行配置向导")
    parser.add_argument("command", nargs="?", help="可用命令: shell, ls, cat, get, put, mkdir, rm")
    parser.add_argument("args", nargs=argparse.REMAINDER, help="命令参数")

    args = parser.parse_args()

    config = load_config()

    # 如果指定了 --setup，或者没有配置文件
    if args.setup or not config:
        config = run_setup()
        if not args.command and not args.setup:
            # 如果是第一次运行没带命令，进入 shell
            args.command = "shell"
        elif args.setup and not args.command:
            # 如果是主动 --setup，配置完就退出
            sys.exit(0)

    api = NasAPI(config)

    if args.command == "shell" or not args.command:
        shell = NasShell(api)
        shell.cmdloop()
    elif args.command == "ls":
        path = args.args[0] if args.args else "/"
        data = api.list_dir(path)
        if data:
            for f in data.get("files", []):
                t = "[DIR] " if f["dir"] else "[FILE]"
                s = "-" if f["dir"] else format_size(f["size"])
                print(f"{t:<6} {s:<10} {f['name']}")
    elif args.command == "cat":
        if not args.args: return
        r = api.req("GET", "/api/preview", params={"path": args.args[0]})
        if r: print(r.text)
    elif args.command == "get":
        if not args.args: return
        remote = args.args[0]
        local = args.args[1] if len(args.args) > 1 else os.path.basename(remote)
        api.download(remote, local)
    elif args.command == "put":
        if not args.args: return
        remote_dir = args.args[1] if len(args.args) > 1 else "/"
        api.upload(args.args[0], remote_dir)
    elif args.command == "mkdir":
        if len(args.args) < 2: return
        api.mkdir(args.args[0], args.args[1])
    elif args.command == "rm":
        if not args.args: return
        api.delete(args.args[0])
    else:
        print(f"未知命令: {args.command}")

if __name__ == "__main__":
    main()
