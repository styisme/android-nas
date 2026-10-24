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
import subprocess
import tempfile

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
    config = {"url": url, "user": user, "password": pwd}
    with open(CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(config, f)
    print(f"\n\033[92m[+] 配置保存成功！\033[0m")
    return config

def load_config():
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f: return json.load(f)
        except Exception: pass
    return None

# ================= 网络请求 =================
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
        return r.json() if r else None

    def download(self, remote, local):
        r = self.req("GET", "/api/download", params={"path": remote}, stream=True)
        if not r: return False
        total = int(r.headers.get('content-length', 0))
        with open(local, "wb") as f:
            downloaded = 0
            for chunk in r.iter_content(chunk_size=8192):
                f.write(chunk)
                downloaded += len(chunk)
                if total > 0:
                    percent = int(downloaded * 100 / total)
                    sys.stdout.write(f"\r\033[92m下载: {percent}% ({format_size(downloaded)})\033[0m")
                    sys.stdout.flush()
        print(f"\n\033[92m[+] 保存成功: {local}\033[0m")
        return True

    def upload(self, local, remote_dir):
        import hashlib
        filename = os.path.basename(local)
        fsize = os.path.getsize(local)
        
        # 计算 MD5
        md5 = hashlib.md5()
        with open(local, 'rb') as f:
            for chunk in iter(lambda: f.read(8192), b""):
                md5.update(chunk)
        fhash = md5.hexdigest()
        
        # 秒传
        r = self.req("POST", f"/api/upload/check?hash={fhash}", data={})
        if r and "SECONDS_UPLOAD" in r.text:
            print(f"\033[92m[+] 秒传成功: {filename}\033[0m")
            return

        # 分片上传
        chunk_size = 2 * 1024 * 1024
        total_chunks = (fsize + chunk_size - 1) // chunk_size
        print(f"开始分片上传: {filename} ({fsize/1024/1024:.2f} MB)")
        
        with open(local, 'rb') as f:
            for i in range(total_chunks):
                chunk_data = f.read(chunk_size)
                url = f"/api/upload/chunk?hash={fhash}&index={i}&total={total_chunks}&name={filename}&path={remote_dir}"
                self.req("POST", url, data=chunk_data)
                pct = int((i + 1) / total_chunks * 100)
                sys.stdout.write(f"\r\033[92m上传进度: {pct}%\033[0m")
                sys.stdout.flush()
        print(f"\n\033[92m[+] 上传成功: {filename}\033[0m")

# ================= 交互式 Shell =================
class NasShell(cmd.Cmd):
    intro = "\033[94m=== Android NAS Pro Shell ===\n输入 ? 查看帮助，支持 Tab 补全。\033[0m"
    prompt = "\033[92mnas> \033[0m"

    def __init__(self, api):
        super().__init__()
        self.api = api
        self.cwd = "/"
        try:
            r = self.api.req("GET", "/api/status")
            if r and r.json().get("isDefaultPwd"):
                print("\033[91m⚠️ 警告: 当前 NAS 正在使用默认密码，请立即修改！\033[0m")
        except Exception:
            pass

    def update_prompt(self):
        self.prompt = f"\033[92m{self.cwd}> \033[0m"

    def emptyline(self): pass

    def do_pwd(self, arg):
        """显示当前目录"""
        print(self.cwd)

    # ---- Tab 自动补全核心逻辑 ----
    def complete_cd(self, text, line, begidx, endidx):
        return self._complete_path(text)

    def complete_cat(self, text, line, begidx, endidx):
        return self._complete_path(text)

    def complete_get(self, text, line, begidx, endidx):
        return self._complete_path(text)

    def complete_edit(self, text, line, begidx, endidx):
        return self._complete_path(text)

    def complete_unzip(self, text, line, begidx, endidx):
        return self._complete_path(text)

    def complete_share(self, text, line, begidx, endidx):
        return self._complete_path(text)

    def complete_rm(self, text, line, begidx, endidx):
        return self._complete_path(text)

    def _complete_path(self, text):
        """向服务端请求当前目录文件来实现补全"""
        dirname, _, basename = text.rpartition('/')
        if dirname:
            search_dir = dirname if dirname.startswith('/') else f"{self.cwd.rstrip('/')}/{dirname}"
        else:
            search_dir = self.cwd

        data = self.api.list_dir(search_dir)
        if not data: return []

        files = data.get("files", [])
        matches = []
        for f in files:
            name = f["name"]
            if f["dir"]: name += "/"
            if name.startswith(basename):
                matches.append(name)
        return matches

    # ---- 目录与列表 ----
    def do_cd(self, arg):
        """切换目录 (支持 Tab 补全)"""
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
            if not files: print(" (空目录)"); return
            print(f"\033[94m{'类型':<6} {'大小':<10} 名称\033[0m")
            print("-" * 40)
            for f in files:
                t = "\033[34m[DIR] \033[0m" if f["dir"] else "[FILE]"
                s = "-" if f["dir"] else format_size(f["size"])
                print(f"{t:<15} {s:<10} {f['name']}")

    # ---- 文件操作 ----
    def do_find(self, arg):
        """全局搜索文件 (例如: find mp4)"""
        if not arg: return
        print(f"正在搜索: {arg} ...")
        r = self.api.req("GET", "/api/search", params={"q": arg})
        if r:
            files = r.json()
            if not files: print(" (未找到相关文件)"); return
            for f in files:
                t = "\033[34m[DIR] \033[0m" if f["dir"] else "[FILE]"
                print(f"{t:<15} {format_size(f['size']):<10} {f['path']}")

    def do_edit(self, arg):
        """在线编辑文本 (调用系统默认编辑器)"""
        if not arg: return
        path = arg if arg.startswith("/") else f"{self.cwd.rstrip('/')}/{arg}"
        r = self.api.req("GET", "/api/preview", params={"path": path})
        if not r: return
        
        with tempfile.NamedTemporaryFile(mode='w', delete=False, encoding='utf-8') as f:
            f.write(r.text)
            tmp_path = f.name

        editor = os.environ.get('EDITOR', 'vim' if sys.platform != 'win32' else 'notepad')
        try:
            subprocess.call([editor, tmp_path])
        except Exception as e:
            print(f"[-] 无法打开编辑器 {editor}: {e}")
            return

        with open(tmp_path, 'r', encoding='utf-8') as f:
            new_content = f.read()
        os.unlink(tmp_path)

        data = {"path": path, "content": new_content}
        res = self.api.req("POST", "/api/save", data=data)
        if res: print("\033[92m[+] 文件已保存回 NAS\033[0m")

    def do_unzip(self, arg):
        """解压 ZIP 文件"""
        if not arg: return
        path = arg if arg.startswith("/") else f"{self.cwd.rstrip('/')}/{arg}"
        print(f"正在解压 {path} ...")
        res = self.api.req("POST", "/api/unzip", data={"path": path})
        if res: print("\033[92m[+] 解压成功\033[0m")

    def do_share(self, arg):
        """生成分享链接 (用法: share 文件名 [提取码])"""
        args = shlex.split(arg)
        if not args: return
        path = args[0] if args[0].startswith("/") else f"{self.cwd.rstrip('/')}/{args[0]}"
        data = {"path": path}
        if len(args) > 1:
            data["pass"] = args[1]
            
        res = self.api.req("POST", "/api/share", data=data)
        if res:
            token = res.text
            link = f"{self.api.base_url}/share/{token}"
            print(f"\033[92m[+] 分享链接: \033[4m{link}\033[0m")
            if len(args) > 1:
                print(f"\033[93m[+] 提取码: {args[1]}\033[0m")

    def do_cat(self, arg):
        """查看文本文件内容"""
        if not arg: return
        path = arg if arg.startswith("/") else f"{self.cwd.rstrip('/')}/{arg}"
        r = self.api.req("GET", "/api/preview", params={"path": path})
        if r: print(r.text)

    def do_get(self, arg):
        """下载文件 (例如: get /Movies/a.mp4)"""
        args = shlex.split(arg)
        if not args: return
        remote = args[0] if args[0].startswith("/") else f"{self.cwd.rstrip('/')}/{args[0]}"
        local = args[1] if len(args) > 1 else os.path.basename(remote)
        self.api.download(remote, local)

    def do_put(self, arg):
        """上传文件 (例如: put ./local.mp4)"""
        if not arg or not os.path.exists(arg): return
        self.api.upload(arg, self.cwd)

    def do_mkdir(self, arg):
        """创建文件夹"""
        if not arg: return
        r = self.api.req("POST", "/api/mkdir", data={"path": self.cwd, "name": arg})
        if r: print("\033[92m[+] 目录已创建\033[0m")

    def do_rm(self, arg):
        """删除文件或文件夹"""
        if not arg: return
        path = arg if arg.startswith("/") else f"{self.cwd.rstrip('/')}/{arg}"
        r = self.api.req("POST", "/api/delete", data={"path": path})
        if r: print("\033[92m[+] 删除成功\033[0m")

    def do_status(self, arg):
        """查看服务端状态"""
        r = self.api.req("GET", "/api/status")
        if r: print(json.dumps(r.json(), indent=4, ensure_ascii=False))


    def do_clip(self, arg):
        """局域网剪贴板同步 (用法: clip 查看 / clip 写入内容)"""
        if not arg:
            r = self.api.req("GET", "/api/clipboard")
            if r: print(f"\033[93m当前剪贴板:\033[0m\n{r.text}")
        else:
            r = self.api.req("POST", "/api/clipboard", data={"content": arg})
            if r: print("\033[92m[+] 剪贴板已同步到服务端\033[0m")

    def do_exec(self, arg):
        """在 NAS 服务端执行 Root / Shell 命令 (若服务端开启了 Root 全盘模式)"""
        if not arg:
            print("[-] 请输入要执行的命令")
            return
        print(f"\033[90m[服务端执行]: {arg}\033[0m")
        r = self.api.req("POST", "/api/shell", data={"cmd": arg})
        if r:
            out = r.text
            if out.strip():
                print(out)
            else:
                print("\033[90m(命令执行完毕，无输出)\033[0m")

    def do_exit(self, arg):
        """退出 Shell"""
        print("Bye!")
        return True
    
    def do_quit(self, arg):
        return self.do_exit(arg)

# ================= 主程序 =================
def main():
    parser = argparse.ArgumentParser(description="Android NAS CLI Pro")
    parser.add_argument("--setup", action="store_true", help="重新运行配置向导")
    parser.add_argument("command", nargs="?", help="命令: shell, ls, cat, get, put...")
    parser.add_argument("args", nargs=argparse.REMAINDER)
    args = parser.parse_args()

    config = load_config()
    if args.setup or not config:
        config = run_setup()
        if not args.command and not args.setup: args.command = "shell"
        elif args.setup and not args.command: sys.exit(0)

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

if __name__ == "__main__":
    main()
