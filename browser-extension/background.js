
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: "send-to-nas",
    title: "发送到 Android NAS 下载",
    contexts: ["link", "image", "video"]
  })
})

chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId === "send-to-nas") {
    chrome.storage.local.get(["nasUrl", "nasAuth"], (res) => {
      if (!res.nasUrl) {
        alert("请先在扩展设置中配置 NAS 地址和密码！")
        return
      }
      const targetUrl = info.linkUrl || info.srcUrl
      if (!targetUrl) return
      
      // 告诉 NAS 下载该 URL
      const filename = targetUrl.split('/').pop().split('?')[0] || "downloaded_file"
      fetch(`${res.nasUrl}/api/download?path=/Remote/${filename}`, {
        method: "PUT",
        headers: { "Authorization": res.nasAuth }
      })
      alert("已发送下载指令到 NAS！")
    })
  }
})
