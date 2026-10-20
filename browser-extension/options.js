
document.getElementById('save').onclick = () => {
  const url = document.getElementById('url').value.replace(/\/$/, "")
  const auth = "Basic " + btoa(document.getElementById('user').value + ":" + document.getElementById('pass').value)
  chrome.storage.local.set({ nasUrl: url, nasAuth: auth }, () => {
    alert("保存成功！")
  })
}
