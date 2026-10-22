
const app = getApp()

Page({
  data: {
    files: [],
    currentPath: "/",
    searchQuery: "",
    showAction: false,
    selectedFile: {},
    showClipboardModal: false,
    clipboardText: ""
  },

  onLoad() {
    this.loadFiles("/")
  },

  loadFiles(path) {
    wx.showLoading({ title: '加载中' })
    wx.request({
      url: `${app.globalData.baseUrl}/api/list?path=${encodeURIComponent(path)}`,
      header: { 'Authorization': app.globalData.authHeader },
      success: (res) => {
        if (res.statusCode === 200) {
          this.setData({ files: res.data.files, currentPath: path })
        }
      },
      complete: () => wx.hideLoading()
    })
  },

  // --- 搜索功能 ---
  onSearchInput(e) {
    this.setData({ searchQuery: e.detail.value })
  },
  doSearch() {
    const q = this.data.searchQuery
    if (!q) { this.loadFiles("/"); return; }
    wx.showLoading({ title: '搜索中' })
    wx.request({
      url: `${app.globalData.baseUrl}/api/search?q=${encodeURIComponent(q)}`,
      header: { 'Authorization': app.globalData.authHeader },
      success: (res) => {
        if (res.statusCode === 200) {
          this.setData({ files: res.data, currentPath: "搜索结果" })
        }
      },
      complete: () => wx.hideLoading()
    })
  },

  // --- 列表点击 ---
  onFileClick(e) {
    const item = e.currentTarget.dataset.item
    if (item.dir) {
      let path = this.data.currentPath === "/" ? `/${item.name}` : `${this.data.currentPath}/${item.name}`
      this.loadFiles(path)
    } else {
      this.setData({ selectedFile: item, showAction: true })
    }
  },
  closeAction() { this.setData({ showAction: false }) },

  // --- 分享外链 ---
  shareLink() {
    const f = this.data.selectedFile
    let path = this.data.currentPath === "/" ? `/${f.name}` : `${this.data.currentPath}/${f.name}`
    if (this.data.currentPath === "搜索结果") {
      // 如果是搜索结果，直接用完整路径（后端返回的 path 字段可能带有路径）
      path = f.path || path
    }

    wx.showLoading({ title: '生成链接' })
    wx.request({
      url: `${app.globalData.baseUrl}/api/share`,
      method: 'POST',
      header: { 
        'Authorization': app.globalData.authHeader,
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      data: { path: path },
      success: (res) => {
        if (res.statusCode === 200) {
          const link = `${app.globalData.baseUrl}/share/${res.data}`
          wx.setClipboardData({
            data: link,
            success: () => {
              wx.showToast({ title: '链接已复制', icon: 'success' })
              this.closeAction()
            }
          })
        }
      },
      complete: () => wx.hideLoading()
    })
  },

  // --- 控制台入口 ---
  openWebConsole() {
    const url = app.globalData.baseUrl
    wx.setClipboardData({
      data: url,
      success: () => {
        wx.showModal({
          title: 'Web 控制台',
          content: '管理链接已复制到剪贴板，请打开浏览器粘贴访问以使用终端和远控功能。',
          showCancel: false
        })
        this.closeAction()
      }
    })
  },

  // --- 局域网剪贴板 ---
  showClipboard() {
    this.closeAction()
    wx.showLoading({ title: '获取中' })
    wx.request({
      url: `${app.globalData.baseUrl}/api/clipboard`,
      header: { 'Authorization': app.globalData.authHeader },
      success: (res) => {
        if (res.statusCode === 200) {
          this.setData({ clipboardText: res.data, showClipboardModal: true })
        }
      },
      complete: () => wx.hideLoading()
    })
  },
  onClipInput(e) { this.setData({ clipboardText: e.detail.value }) },
  closeClipboard() { this.setData({ showClipboardModal: false }) },
  saveClipboard() {
    wx.request({
      url: `${app.globalData.baseUrl}/api/clipboard`,
      method: 'POST',
      header: { 
        'Authorization': app.globalData.authHeader,
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      data: { content: this.data.clipboardText },
      success: () => {
        wx.showToast({ title: '同步成功', icon: 'success' })
        this.closeClipboard()
      }
    })
  }
})
