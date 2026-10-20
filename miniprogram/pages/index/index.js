
const app = getApp()
Page({
  data: { files: [], currentPath: "/" },
  onLoad() {
    // 默认 admin/admin
    app.globalData.authHeader = "Basic " + wx.arrayBufferToBase64(new TextEncoder().encode("admin:admin").buffer)
    this.loadFiles("/")
  },
  loadFiles(path) {
    wx.showLoading({ title: '加载中' })
    wx.request({
      url: `${app.globalData.baseUrl}/api/list?path=${path}`,
      header: { 'Authorization': app.globalData.authHeader },
      success: (res) => {
        if (res.statusCode === 200) {
          this.setData({ files: res.data.files, currentPath: path })
        }
      },
      complete: () => wx.hideLoading()
    })
  },
  onFileClick(e) {
    const { dir, name } = e.currentTarget.dataset
    if (dir) {
      let path = this.data.currentPath === "/" ? `/${name}` : `${this.data.currentPath}/${name}`
      this.loadFiles(path)
    }
  }
})
