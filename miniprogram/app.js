App({
  globalData: {
    baseUrl: "http://192.168.1.100:8080", // 请修改为你的 NAS IP
    authHeader: ""
  },
  onLaunch() {
    // 初始化 Basic Auth 认证头
    this.globalData.authHeader = "Basic " + wx.arrayBufferToBase64(new TextEncoder().encode("admin:admin").buffer);
  }
})
