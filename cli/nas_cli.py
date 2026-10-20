#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import sys
import os
import argparse
import urllib.parse
import requests
import cmd
import shlex

CONFIG_FILE = os.path.expanduser("~/.nas_cli_config")

def format_size(size):
    if size < 1024: return f"{size} B"
    if size < 1024 * 1024: return f"{size / 1024:.1f} KB"
    return f"{size / 1024 / 1024:.1f} MB"

# ================= 基础网络请求 =================
class NasAPI:
    def __init__(self, base_url, user="admin", password="admin"):
        self.base_url = base_url
        self.auth = (user, password) if user else None
        self.session = requests.Session()
        self.session.auth = self.auth
        self.session.timeout = 10

    def req(self, method, endpoint, **kwargs):
        url = f"{self.base_url}{endpoint}"
        try:
            r = self.session.request(method, url, **kwargs)
            if r.status_code == 401:
                print("\n\033[91m[-] 认证失败，请检查账号密码\033[0m")
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

    def emptyline(self):
        pass

    def do_pwd(self, arg):
        """显示当前目录"""
        print(self.cwd)

    def do_cd(self, arg):
        """切换目录 (例如: cd /Movies 或 cd ..)"""
        if not arg or arg == ".":
            return
        if arg == "..":
            if self.cwd == "/": return
            parts = self.cwd.strip("/").split("/")
            parts.pop()
            self.cwd = "/" + "/".join(parts) if parts else "/"
        elif arg == "/":
            self.cwd = "/"
        else:
            # 兼容相对路径和绝对路径
            path = arg if arg.startswith("/") else f"{self.cwd.rstrip('/')}/{arg}"
            # 验证目录是否存在
            data = self.api.list_dir(path)
            if data:
                self.cwd = data.get("path", path)
            else:
                print("\033[91m[-] 目录不存在\033[0m")
        self.update_prompt()

    def do_ls(self, arg):
        """列出当前目录内容"""
        path = arg if arg else self.cwd
        data = self.api.list_dir(path)
        if data:
            files = data.get("files", [])
            if not files:
                print(" (空目录)")
                return
            print(f"\033[94m{'类型':<6} {'大小':<10} 名称\033[0m")
            print("-" * 40)
            for f in files:
                t = "\033[34m[DIR] \033[0m" if f["dir"] else "[FILE]"
                s = "-" if f["dir"] else format_size(f["size"])
                print(f"{t:<15} {s:<10} {f['name']}")

    def do_cat(self, arg):
        """查看文本文件内容 (例如: cat /config.txt)"""
        if not arg:
            print("[-] 请输入文件路径"); return
        path = arg if arg.startswith("/") else f"{self.cwd.rstrip('/')}/{arg}"
        r = self.api.req("GET", "/api/preview", params={"path": path})
        if r: print(r.text)

    def do_get(self, arg):
        """下载文件 (例如: get /Movies/a.mp4 本地路径)"""
        args = shlex.split(arg)
        if not args: print("[-] 请提供远程路径"); return
        remote = args[0] if args[0].startswith("/") else f"{self.cwd.rstrip('/')}/{args[0]}"
        local = args[1] if len(args) > 1 else os.path.basename(remote)
        self.api.download(remote, local)

    def do_put(self, arg):
        """上传文件 (例如: put ./本地文件.mp4)"""
        if not arg: print("[-] 请提供本地路径"); return
        if not os.path.exists(arg):
            print("[-] 本地文件不存在"); return
        self.api.upload(arg, self.cwd)

    def do_mkdir(self, arg):
        """创建文件夹 (例如: mkdir NewFolder)"""
        if not arg: print("[-] 请输入文件夹名称"); return
        self.api.mkdir(self.cwd, arg)

    def do_rm(self, arg):
        """删除文件或文件夹 (例如: rm /Movies/a.mp4)"""
        if not arg: print("[-] 请输入路径"); return
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
def get_base_url(args):
    if args.url: url = args.url
    else:
        if os.path.exists(CONFIG_FILE):
            with open(CONFIG_FILE, "r") as f: url = f.read().strip()
        else:
            print("[-] 未提供 URL。请使用 --url，或先运行 --save-url")
            sys.exit(1)
    if not url.startswith("http"): url = "http://" + url
    return url.rstrip("/")

def main():
    parser = argparse.ArgumentParser(description="Android NAS CLI Client")
    parser.add_argument("--url", help="NAS 地址 (如 http://192.168.1.100:8080)")
    parser.add_argument("--user", default="admin", help="账号 (默认 admin)")
    parser.add_argument("--password", default="admin", help="密码 (默认 admin)")
    parser.add_argument("--save-url", action="store_true", help="保存 URL 到配置文件")

    subparsers = parser.add_subparsers(dest="command", help="可用命令")

    subparsers.add_parser("shell", help="进入交互式文件管理终端")

    p_ls = subparsers.add_parser("ls", help="列出目录")
    p_ls.add_argument("path", nargs="?", default="/")

    p_cat = subparsers.add_parser("cat", help="查看文本文件")
    p_cat.add_argument("path")

    p_dl = subparsers.add_parser("download", help="下载文件")
    p_dl.add_argument("remote"); p_dl.add_argument("local", nargs="?")

    p_up = subparsers.add_parser("upload", help="上传文件")
    p_up.add_argument("local"); p_up.add_argument("remote_dir", nargs="?", default="/")

    p_mkdir = subparsers.add_parser("mkdir", help="新建文件夹")
    p_mkdir.add_argument("path"); p_mkdir.add_argument("name")

    p_rm = subparsers.add_parser("rm", help="删除")
    p_rm.add_argument("path")

    args = parser.parse_args()

    if not args.command and not args.save_url:
        parser.print_help(); sys.exit(0)

    base_url = get_base_url(args)

    if args.save_url:
        with open(CONFIG_FILE, "w") as f: f.write(base_url)
        print(f"\033[92m[+] 地址已保存: {base_url}\033[0m")
        if not args.command: sys.exit(0)

    api = NasAPI(base_url, args.user, args.password)

    if args.command == "shell":
        shell = NasShell(api)
        shell.cmdloop()
    elif args.command == "ls":
        data = api.list_dir(args.path or "/")
        if data:
            for f in data.get("files", []):
                t = "[DIR] " if f["dir"] else "[FILE]"
                s = "-" if f["dir"] else format_size(f["size"])
                print(f"{t:<6} {s:<10} {f['name']}")
    elif args.command == "cat":
        r = api.req("GET", "/api/preview", params={"path": args.path})
        if r: print(r.text)
    elif args.command == "download":
        api.download(args.remote, args.local or os.path.basename(args.remote))
    elif args.command == "upload":
        api.upload(args.local, args.remote_dir)
    elif args.command == "mkdir":
        api.mkdir(args.path, args.name)
    elif args.command == "rm":
        api.delete(args.path)

if __name__ == "__main__":
    main()
