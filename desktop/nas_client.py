#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os
import sys
import json
import urllib.parse
import tkinter as tk
from tkinter import ttk, filedialog, messagebox, simpledialog
import requests

CONFIG_FILE = os.path.expanduser("~/.android-nas-config.json")

class DesktopNasClient:
    def __init__(self, root):
        self.root = root
        self.root.title("Android NAS 桌面客户端")
        self.root.geometry("700x500")
        
        self.base_url = ""
        self.auth = None
        self.current_path = "/"
        self.config = self.load_config()

        self.setup_ui()
        self.show_connect_dialog()

    def load_config(self):
        if os.path.exists(CONFIG_FILE):
            try:
                with open(CONFIG_FILE, "r") as f:
                    return json.load(f)
            except Exception:
                pass
        return {"ip": "192.168.1.100", "port": "8080", "user": "admin", "pass": "admin"}

    def save_config(self):
        try:
            with open(CONFIG_FILE, "w") as f:
                json.dump(self.config, f)
        except Exception:
            pass

    def setup_ui(self):
        self.top_frame = ttk.Frame(self.root, padding=5)
        self.top_frame.pack(fill=tk.X)
        
        self.btn_back = ttk.Button(self.top_frame, text="⬆ 上级", command=self.go_up)
        self.btn_back.pack(side=tk.LEFT, padx=2)
        
        self.btn_refresh = ttk.Button(self.top_frame, text="🔄 刷新", command=self.load_files)
        self.btn_refresh.pack(side=tk.LEFT, padx=2)
        
        self.path_var = tk.StringVar(value="/")
        self.lbl_path = ttk.Label(self.top_frame, textvariable=self.path_var, font=("Arial", 10, "bold"), foreground="#0055aa")
        self.lbl_path.pack(side=tk.LEFT, padx=10, fill=tk.X, expand=True)

        columns = ("name", "size", "type")
        self.tree = ttk.Treeview(self.root, columns=columns, show="headings", selectmode="browse")
        self.tree.heading("name", text="文件名")
        self.tree.heading("size", text="大小")
        self.tree.heading("type", text="类型")
        self.tree.column("name", width=400)
        self.tree.column("size", width=100, anchor=tk.E)
        self.tree.column("type", width=100, anchor=tk.CENTER)
        self.tree.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)
        self.tree.bind("<Double-1>", self.on_double_click)

        self.bottom_frame = ttk.Frame(self.root, padding=5)
        self.bottom_frame.pack(fill=tk.X)
        
        ttk.Button(self.bottom_frame, text="📥 下载", command=self.download_file).pack(side=tk.LEFT, padx=5)
        ttk.Button(self.bottom_frame, text="📤 上传", command=self.upload_file).pack(side=tk.LEFT, padx=5)
        ttk.Button(self.bottom_frame, text="📁 新建文件夹", command=self.mkdir).pack(side=tk.LEFT, padx=5)
        ttk.Button(self.bottom_frame, text="❌ 删除", command=self.delete_file).pack(side=tk.LEFT, padx=5)
        ttk.Button(self.bottom_frame, text="⚙ 重新连接", command=self.show_connect_dialog).pack(side=tk.RIGHT, padx=5)

    def show_connect_dialog(self):
        dialog = tk.Toplevel(self.root)
        dialog.title("连接到 NAS")
        dialog.geometry("300x250")
        dialog.transient(self.root)
        dialog.grab_set()

        ttk.Label(dialog, text="IP 地址:").pack(pady=(15,0))
        ip_var = tk.StringVar(value=self.config.get("ip"))
        ttk.Entry(dialog, textvariable=ip_var).pack()

        ttk.Label(dialog, text="端口 (默认8080):").pack(pady=(10,0))
        port_var = tk.StringVar(value=self.config.get("port"))
        ttk.Entry(dialog, textvariable=port_var).pack()

        ttk.Label(dialog, text="账号:").pack(pady=(10,0))
        user_var = tk.StringVar(value=self.config.get("user"))
        ttk.Entry(dialog, textvariable=user_var).pack()

        ttk.Label(dialog, text="密码:").pack(pady=(10,0))
        pass_var = tk.StringVar(value=self.config.get("pass"))
        ttk.Entry(dialog, textvariable=pass_var, show="*").pack()

        def connect():
            self.config = {
                "ip": ip_var.get().strip(),
                "port": port_var.get().strip(),
                "user": user_var.get().strip(),
                "pass": pass_var.get().strip()
            }
            self.save_config()
            self.base_url = f"http://{self.config['ip']}:{self.config['port']}"
            self.auth = (self.config['user'], self.config['pass'])
            self.current_path = "/"
            dialog.destroy()
            self.load_files()

        ttk.Button(dialog, text="连接", command=connect).pack(pady=15)

    def load_files(self):
        if not self.base_url: return
        self.tree.delete(*self.tree.get_children())
        self.path_var.set(self.current_path)

        try:
            url = f"{self.base_url}/api/list"
            res = requests.get(url, params={"path": self.current_path}, auth=self.auth, timeout=5)
            if res.status_code == 401:
                messagebox.showerror("错误", "账号或密码错误")
                return
            res.raise_for_status()
            
            data = res.json()
            for f in data.get("files", []):
                size_str = self.format_size(f["size"]) if not f["dir"] else "-"
                type_str = "文件夹" if f["dir"] else f.get("name").split(".")[-1].upper() + " 文件"
                self.tree.insert("", tk.END, values=(f["name"], size_str, type_str))

        except Exception as e:
            messagebox.showerror("连接失败", f"无法连接到 NAS\n{str(e)}")

    def on_double_click(self, event):
        selected = self.tree.selection()
        if not selected: return
        item = self.tree.item(selected[0])
        name = item['values'][0]
        
        if item['values'][2] == "文件夹":
            if self.current_path == "/": self.current_path = f"/{name}"
            else: self.current_path = f"{self.current_path}/{name}"
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
        if not selected:
            messagebox.showinfo("提示", "请先选择一个文件")
            return None, None
        item = self.tree.item(selected[0])
        name = item['values'][0]
        path = f"{self.current_path}/{name}" if self.current_path != "/" else f"/{name}"
        return name, path

    def download_file(self):
        name, path = self.get_selected_file()
        if not name: return
        
        save_path = filedialog.asksaveasfilename(initialfile=name, title="保存文件")
        if not save_path: return

        try:
            encoded_path = urllib.parse.quote(path)
            url = f"{self.base_url}/api/download?path={encoded_path}"
            res = requests.get(url, auth=self.auth, stream=True)
            res.raise_for_status()
            
            with open(save_path, "wb") as f:
                for chunk in res.iter_content(chunk_size=8192):
                    f.write(chunk)
            messagebox.showinfo("成功", f"文件已保存至:\n{save_path}")
        except Exception as e:
            messagebox.showerror("下载失败", str(e))

    def upload_file(self):
        file_path = filedialog.askopenfilename(title="选择要上传的文件")
        if not file_path: return
        
        filename = os.path.basename(file_path)
        encoded_name = urllib.parse.quote(filename)
        target_url = f"{self.base_url}{self.current_path}{'/' if self.current_path != '/' else ''}{encoded_name}"
        
        try:
            with open(file_path, 'rb') as f:
                headers = {'Content-Length': str(os.path.getsize(file_path))}
                res = requests.put(target_url, data=f, auth=self.auth, headers=headers, timeout=30)
            res.raise_for_status()
            messagebox.showinfo("成功", f"{filename} 上传成功")
            self.load_files()
        except Exception as e:
            messagebox.showerror("上传失败", str(e))

    def mkdir(self):
        name = simpledialog.askstring("新建文件夹", "请输入文件夹名称:")
        if not name: return
        try:
            url = f"{self.base_url}/api/mkdir"
            res = requests.post(url, data={"path": self.current_path, "name": name}, auth=self.auth)
            res.raise_for_status()
            self.load_files()
        except Exception as e:
            messagebox.showerror("创建失败", str(e))

    def delete_file(self):
        name, path = self.get_selected_file()
        if not name: return
        if not messagebox.askyesno("确认", f"确定要删除 {name} 吗？此操作不可恢复。"): return
        
        try:
            url = f"{self.base_url}/api/delete"
            res = requests.post(url, data={"path": path}, auth=self.auth)
            res.raise_for_status()
            self.load_files()
        except Exception as e:
            messagebox.showerror("删除失败", str(e))

    def format_size(self, size):
        if size < 1024: return f"{size} B"
        if size < 1024 * 1024: return f"{size / 1024:.1f} KB"
        return f"{size / 1024 / 1024:.1f} MB"

if __name__ == "__main__":
    root = tk.Tk()
    style = ttk.Style()
    if sys.platform.startswith("win"): style.theme_use("vista")
    else: style.theme_use("clam")
    
    app = DesktopNasClient(root)
    root.mainloop()
