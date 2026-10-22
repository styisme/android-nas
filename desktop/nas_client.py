#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os
import sys
import json
import urllib.parse
import tkinter as tk
from tkinter import ttk, filedialog, messagebox, simpledialog
import requests
from threading import Thread
import queue
import webbrowser

CONFIG_FILE = os.path.expanduser("~/.android-nas-config.json")

class DesktopNasClient:
    def __init__(self, root):
        self.root = root
        self.root.title("Android NAS Desktop")
        self.root.geometry("900x600")
        
        self.bg_color = "#F3EDF7"
        self.fg_color = "#1D1B20"
        self.primary = "#6750A4"
        self.surface = "#FFFFFF"
        
        self.root.configure(bg=self.bg_color)
        self.base_url = ""
        self.auth = None
        self.current_path = "/"
        self.config = self.load_config()
        self.ui_queue = queue.Queue()
        
        self.setup_styles()
        self.setup_ui()
        self.poll_ui_queue()
        self.show_connect_dialog()

    def load_config(self):
        if os.path.exists(CONFIG_FILE):
            try:
                with open(CONFIG_FILE, "r") as f: return json.load(f)
            except Exception: pass
        return {"ip": "192.168.1.100", "port": "8080", "user": "admin", "pass": "admin"}

    def save_config(self):
        try:
            with open(CONFIG_FILE, "w") as f: json.dump(self.config, f)
        except Exception: pass

    def ui(self, fn, *args):
        self.ui_queue.put((fn, args))

    def poll_ui_queue(self):
        try:
            while True:
                fn, args = self.ui_queue.get_nowait()
                try: fn(*args)
                except Exception: pass
        except queue.Empty: pass
        self.root.after(50, self.poll_ui_queue)

    def setup_styles(self):
        style = ttk.Style()
        style.theme_use("clam")
        style.configure(".", bg=self.bg_color, fg=self.fg_color, font=("Microsoft YaHei UI", 10))
        style.configure("TFrame", bg=self.bg_color)
        style.configure("Top.TFrame", bg=self.surface)
        style.configure("TLabel", bg=self.bg_color, fg=self.fg_color)
        style.configure("Top.TLabel", bg=self.surface, fg=self.primary, font=("Microsoft YaHei UI", 14, "bold"))
        style.configure("TButton", bg=self.surface, fg=self.primary, relief="flat", padding=6)
        style.map("TButton", background=[("active", "#EADDFF")])
        style.configure("Treeview", bg=self.surface, fg=self.fg_color, fieldbackground=self.surface, borderwidth=0, rowheight=32)
        style.configure("Treeview.Heading", bg=self.bg_color, fg=self.fg_color, relief="flat")

    def setup_ui(self):
        top = ttk.Frame(self.root, style="Top.TFrame", padding=10)
        top.pack(fill=tk.X)
        
        ttk.Button(top, text="⬆ 上一级", command=self.go_up).pack(side=tk.LEFT, padx=(0, 5))
        ttk.Button(top, text="🔄 刷新", command=self.load_files).pack(side=tk.LEFT, padx=(0, 15))
        
        self.path_var = tk.StringVar(value="/")
        ttk.Label(top, textvariable=self.path_var, style="Top.TLabel").pack(side=tk.LEFT, padx=(0, 10))
        
        # 全局搜索框
        self.search_var = tk.StringVar()
        search_entry = ttk.Entry(top, textvariable=self.search_var, width=20)
        search_entry.pack(side=tk.LEFT, padx=5)
        search_entry.bind("<Return>", lambda e: self.do_search())
        ttk.Button(top, text="🔍 搜索", command=self.do_search).pack(side=tk.LEFT, padx=2)
        ttk.Button(top, text="🔄 重置", command=self.load_files).pack(side=tk.LEFT, padx=2)

        # 状态监控显示
        self.status_var = tk.StringVar(value="未连接")
        tk.Label(top, textvariable=self.status_var, bg=self.surface, fg="#666").pack(side=tk.RIGHT, padx=10)
        
        ttk.Button(top, text="⚙ 连接设置", command=self.show_connect_dialog).pack(side=tk.RIGHT, padx=5)
        ttk.Button(top, text="🦅 控制台", command=self.open_web_console).pack(side=tk.RIGHT, padx=5)
        ttk.Button(top, text="📋 剪贴板", command=self.show_clipboard).pack(side=tk.RIGHT, padx=5)
        
        list_frame = ttk.Frame(self.root, padding=10)
        list_frame.pack(fill=tk.BOTH, expand=True)
        
        self.tree = ttk.Treeview(list_frame, columns=("name", "size", "type"), show="headings", selectmode="browse")
        self.tree.heading("name", text="文件名"); self.tree.heading("size", text="大小"); self.tree.heading("type", text="类型")
        self.tree.column("name", width=450); self.tree.column("size", width=100, anchor=tk.E); self.tree.column("type", width=100, anchor=tk.CENTER)
        self.tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        
        scrollbar = ttk.Scrollbar(list_frame, orient=tk.VERTICAL, command=self.tree.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.tree.configure(yscrollcommand=scrollbar.set)
        self.tree.bind("<Double-1>", self.on_double_click)
        
        bottom = ttk.Frame(self.root, padding=10)
        bottom.pack(fill=tk.X)
        
        ttk.Button(bottom, text="📤 上传", command=self.upload_files).pack(side=tk.LEFT, padx=5)
        ttk.Button(bottom, text="📥 下载", command=self.download_file).pack(side=tk.LEFT, padx=5)
        ttk.Button(bottom, text="✏️ 修改文本", command=self.edit_file).pack(side=tk.LEFT, padx=5)
        ttk.Button(bottom, text="🔗 分享外链", command=self.share_link).pack(side=tk.LEFT, padx=5)
        ttk.Button(bottom, text="📁 新建文件夹", command=self.mkdir).pack(side=tk.LEFT, padx=5)
        ttk.Button(bottom, text="📦 解压", command=self.unzip_file).pack(side=tk.LEFT, padx=5)
        ttk.Button(bottom, text="❌ 删除", command=self.delete_file).pack(side=tk.LEFT, padx=5)
        
        self.progress = ttk.Progressbar(self.root, orient=tk.HORIZONTAL, mode='determinate')
        self.progress.pack(fill=tk.X, padx=10, pady=(0, 10))

    def show_connect_dialog(self):
        dialog = tk.Toplevel(self.root)
        dialog.title("连接到 NAS")
        dialog.geometry("300x280")
        dialog.configure(bg=self.bg_color)
        dialog.transient(self.root)
        dialog.grab_set()
        
        ttk.Label(dialog, text="IP 地址:").pack(pady=(15,0))
        ip_var = tk.StringVar(value=self.config.get("ip"))
        ttk.Entry(dialog, textvariable=ip_var, width=25).pack()
        
        ttk.Label(dialog, text="端口 (默认 8080):").pack(pady=(10,0))
        port_var = tk.StringVar(value=self.config.get("port"))
        ttk.Entry(dialog, textvariable=port_var, width=25).pack()
        
        ttk.Label(dialog, text="账号 (默认 admin):").pack(pady=(10,0))
        user_var = tk.StringVar(value=self.config.get("user"))
        ttk.Entry(dialog, textvariable=user_var, width=25).pack()
        
        ttk.Label(dialog, text="密码 (默认 admin):").pack(pady=(10,0))
        pass_var = tk.StringVar(value=self.config.get("pass"))
        ttk.Entry(dialog, textvariable=pass_var, show="*", width=25).pack()
        
        def connect():
            self.config = {"ip": ip_var.get().strip(), "port": port_var.get().strip(), "user": user_var.get().strip(), "pass": pass_var.get().strip()}
            self.save_config()
            self.base_url = f"http://{self.config['ip']}:{self.config['port']}"
            self.auth = (self.config['user'], self.config['pass'])
            self.current_path = "/"
            dialog.destroy()
            self.load_files()
            self.check_status()
            
        ttk.Button(dialog, text="连接", command=connect).pack(pady=20)

    def check_status(self):
        def _check():
            try:
                res = requests.get(f"{self.base_url}/api/status", auth=self.auth, timeout=3)
                if res.status_code == 200:
                    data = res.json()
                    status_txt = "就绪"
                    if data.get("lowPower"): status_txt += " | 极寒"
                    if data.get("globalMode"): status_txt += " | 全盘"
                    self.ui(lambda: self.status_var.set(status_txt))
            except Exception:
                self.ui(lambda: self.status_var.set("离线"))
        Thread(target=_check).start()

    def load_files(self):
        if not self.base_url: return
        self.tree.delete(*self.tree.get_children())
        self.path_var.set(self.current_path)
        try:
            res = requests.get(f"{self.base_url}/api/list", params={"path": self.current_path}, auth=self.auth, timeout=5)
            if res.status_code == 401: messagebox.showerror("错误", "账号或密码错误"); return
            res.raise_for_status()
            data = res.json()
            for f in data.get("files", []):
                size_str = self.format_size(f["size"]) if not f["dir"] else "-"
                ext = f["name"].split(".")[-1].upper() + " 文件" if not f["dir"] and "." in f["name"] else ("文件夹" if f["dir"] else "文件")
                icon = "📁 " if f["dir"] else "📄 "
                self.tree.insert("", tk.END, values=(icon + f["name"], size_str, ext))
        except Exception as e:
            messagebox.showerror("连接失败", f"无法连接到 NAS\n{str(e)}")

    def on_double_click(self, event):
        selected = self.tree.selection()
        if not selected: return
        item = self.tree.item(selected[0])
        name = item['values'][0].replace("📁 ", "").replace("📄 ", "")
        
        if item['values'][2] == "文件夹":
            self.current_path = f"{self.current_path.rstrip('/')}/{name}" if self.current_path != "/" else f"/{name}"
            self.load_files()
        else:
            self.download_file()

    def go_up(self):
        if self.current_path == "/": return
        parts = self.current_path.strip("/").split("/")
        parts.pop()
        self.current_path = "/" + "/".join(parts) if parts else "/"
        self.load_files()

    def get_selected_file(self):
        selected = self.tree.selection()
        if not selected: return None, None
        item = self.tree.item(selected[0])
        name = item['values'][0].replace("📁 ", "").replace("📄 ", "")
        path = f"{self.current_path.rstrip('/')}/{name}" if self.current_path != "/" else f"/{name}"
        return name, path

    def share_link(self):
        name, path = self.get_selected_file()
        if not name: return
        try:
            res = requests.post(f"{self.base_url}/api/share", data={"path": path}, auth=self.auth, timeout=5)
            res.raise_for_status()
            token = res.text.strip()
            link = f"{self.base_url}/share/{token}"
            self.root.clipboard_clear()
            self.root.clipboard_append(link)
            messagebox.showinfo("成功", f"免密链接已复制到剪贴板:\n{link}")
        except Exception as e:
            messagebox.showerror("失败", str(e))

    # 其他方法 (mkdir, delete, unzip, download, upload) 保持原样

    def do_search(self):
        q = self.search_var.get().strip()
        if not q:
            self.load_files()
            return
        self.tree.delete(*self.tree.get_children())
        self.path_var.set(f"搜索: {q}")
        def _search():
            try:
                res = requests.get(f"{self.base_url}/api/search", params={"q": q}, auth=self.auth, timeout=5)
                res.raise_for_status()
                data = res.json()
                for f in data:
                    size_str = self.format_size(f["size"]) if not f["dir"] else "-"
                    ext = f["name"].split(".")[-1].upper() + " 文件" if not f["dir"] and "." in f["name"] else ("文件夹" if f["dir"] else "文件")
                    icon = "📁 " if f["dir"] else "📄 "
                    self.ui(self.tree.insert, "", tk.END, values=(icon + f["name"], size_str, ext))
            except Exception as e:
                self.ui(messagebox.showerror, "搜索失败", str(e))
        Thread(target=_search).start()

    def show_clipboard(self):
        win = tk.Toplevel(self.root)
        win.title("局域网剪贴板")
        win.geometry("400x300")
        win.configure(bg=self.bg_color)
        win.transient(self.root)
        win.grab_set()

        text_area = tk.Text(win, wrap=tk.WORD, font=("Microsoft YaHei", 11))
        text_area.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        
        def load_clip():
            try:
                res = requests.get(f"{self.base_url}/api/clipboard", auth=self.auth, timeout=5)
                text_area.delete("1.0", tk.END)
                text_area.insert(tk.END, res.text)
            except Exception: pass
            
        def save_clip():
            txt = text_area.get("1.0", tk.END).strip()
            try:
                requests.post(f"{self.base_url}/api/clipboard", data={"content": txt}, auth=self.auth, timeout=5)
                messagebox.showinfo("成功", "已同步至服务端剪贴板")
                win.destroy()
            except Exception as e: messagebox.showerror("失败", str(e))

        btn_frame = ttk.Frame(win)
        btn_frame.pack(fill=tk.X, padx=10, pady=(0, 10))
        ttk.Button(btn_frame, text="重新加载", command=load_clip).pack(side=tk.LEFT)
        ttk.Button(btn_frame, text="保存同步", command=save_clip).pack(side=tk.RIGHT)
        
        load_clip()

    def open_web_console(self):
        if self.base_url:
            webbrowser.open(self.base_url)

    def edit_file(self):
        name, path = self.get_selected_file()
        if not name: return
        if self.tree.item(self.tree.selection()[0])['values'][2] == "文件夹": return
        try:
            res = requests.get(f"{self.base_url}/api/preview", params={"path": path}, auth=self.auth, timeout=10)
            res.raise_for_status()
            content = res.text
        except Exception as e:
            messagebox.showerror("读取失败", "文件过大或不是文本文件\n" + str(e))
            return
        edit_win = tk.Toplevel(self.root)
        edit_win.title(f"编辑: {name}")
        edit_win.geometry("700x500")
        text_area = tk.Text(edit_win, wrap=tk.NONE, font=("Consolas", 11))
        text_area.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        text_area.insert(tk.END, content)
        def save():
            new_content = text_area.get("1.0", tk.END)
            try:
                res = requests.post(f"{self.base_url}/api/save", data={"path": path, "content": new_content}, auth=self.auth, timeout=10)
                res.raise_for_status()
                messagebox.showinfo("成功", "保存成功")
                edit_win.destroy()
                self.load_files()
            except Exception as e:
                messagebox.showerror("保存失败", str(e))
        ttk.Button(edit_win, text="保存文件", command=save).pack(pady=5)

    def mkdir(self):
        name = simpledialog.askstring("新建文件夹", "名称:")
        if not name: return
        requests.post(f"{self.base_url}/api/mkdir", data={"path": self.current_path, "name": name}, auth=self.auth)
        self.load_files()

    def delete_file(self):
        name, path = self.get_selected_file()
        if not name or not messagebox.askyesno("确认", f"删除 {name} ?"): return
        requests.post(f"{self.base_url}/api/delete", data={"path": path}, auth=self.auth)
        self.load_files()

    def unzip_file(self):
        name, path = self.get_selected_file()
        if not name or not name.endswith(".zip"): return
        requests.post(f"{self.base_url}/api/unzip", data={"path": path}, auth=self.auth)
        self.load_files()

    def download_file(self):
        name, path = self.get_selected_file()
        if not name or self.tree.item(self.tree.selection()[0])['values'][2] == "文件夹": return
        save_path = filedialog.asksaveasfilename(initialfile=name)
        if not save_path: return
        def _dl():
            res = requests.get(f"{self.base_url}/api/download", params={"path": path}, auth=self.auth, stream=True)
            with open(save_path, "wb") as f:
                for chunk in res.iter_content(8192): f.write(chunk)
            self.ui(messagebox.showinfo, "成功", "下载完成")
        Thread(target=_dl).start()

    def upload_files(self):
        paths = filedialog.askopenfilenames(title="选择文件")
        if not paths: return
        def _ul():
            try:
                files = [('file', (os.path.basename(p), open(p, 'rb'))) for p in paths]
                total_size = sum(os.path.getsize(p) for p in paths)
                self.ui(lambda: setattr(self.progress, 'maximum', total_size))
                self.ui(lambda: self.progress.configure(value=0))
                
                res = requests.post(f"{self.base_url}/api/upload", files=files, data={"path": self.current_path}, auth=self.auth, timeout=120)
                res.raise_for_status()
                
                for _, file_tuple in files: file_tuple[1].close()
                
                self.ui(lambda: self.progress.configure(value=total_size))
                self.ui(lambda: self.progress.configure(value=0))
                self.ui(self.load_files)
                self.ui(messagebox.showinfo, "成功", f"上传了 {len(paths)} 个文件")
            except Exception as e:
                self.ui(messagebox.showerror, "上传失败", str(e))
        Thread(target=_ul).start()

    def format_size(self, size):
        if size < 1024: return f"{size} B"
        if size < 1024 * 1024: return f"{size / 1024:.1f} KB"
        return f"{size / 1024 / 1024:.1f} MB"

if __name__ == "__main__":
    root = tk.Tk()
    app = DesktopNasClient(root)
    root.mainloop()
