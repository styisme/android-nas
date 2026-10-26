App({
  globalData: {
    baseUrl: "http://192.168.1.100:8080", // 请修改为你的 NAS IP
    authHeader: ""
  },
  onLaunch() {
    // 初始化 Basic Auth 认证头
    // 手写字符串转 ArrayBuffer 兼容老基础库
    function strToUtf8Bytes(str) {
      var bytes = [];
      for (var i = 0; i < str.length; i++) {
        var c = str.charCodeAt(i);
        if (c < 128) bytes.push(c);
        else if (c < 2048) { bytes.push(192 | (c >> 6), 128 | (c & 63)); }
        else if ((c & 0xFC00) === 0xD800) {
          var c2 = str.charCodeAt(++i);
          var code = 0x10000 + ((c & 0x03FF) << 10) + (c2 & 0x03FF);
          bytes.push(240 | (code >> 18), 128 | ((code >> 12) & 63), 128 | ((code >> 6) & 63), 128 | (code & 63));
        }
        else { bytes.push(224 | (c >> 12), 128 | ((c >> 6) & 63), 128 | (c & 63)); }
      }
      return new Uint8Array(bytes).buffer;
    }
    this.globalData.authHeader = "Basic " + wx.arrayBufferToBase64(strToUtf8Bytes("admin:admin"));
  }
})
