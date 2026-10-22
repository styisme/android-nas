import { ApiClient } from '../utils/ApiClient';

@Entry
@Component
struct Index {
  @State files: any[] = [];
  @State currentPath: string = "/";
  @State searchQuery: string = "";
  @State showClipboard: boolean = false;
  @State clipboardText: string = "";

  aboutToAppear() { this.loadFiles("/"); }

  async loadFiles(path: string) {
    this.currentPath = path;
    let res = await ApiClient.listFiles(path);
    if (res) {
      this.files = JSON.parse(res).files || [];
    }
  }

  async doSearch() {
    if (!this.searchQuery) { this.loadFiles(this.currentPath); return; }
    let res = await ApiClient.searchFiles(this.searchQuery);
    if (res) {
      this.files = JSON.parse(res) || [];
    }
  }

  build() {
    Column() {
      // 顶部工具栏
      Row() {
        Button("Web控制台")
          .onClick(() => {
             // 调用系统浏览器打开
             // 实际项目中需使用 @ohos.app.ability.common 和 want 启动
          })
          .margin({ right: 8 })
          
        Button("剪贴板")
          .onClick(async () => {
            this.clipboardText = await ApiClient.getClipboard() || "";
            this.showClipboard = true;
          })
          .margin({ right: 8 })
          
        Button("刷新")
          .onClick(() => this.loadFiles(this.currentPath))
      }
      .width('100%')
      .padding(16)
      .justifyContent(FlexAlign.End)

      // 搜索框
      Row() {
        TextInput({ placeholder: "全局搜索...", text: this.searchQuery })
          .onChange((v) => this.searchQuery = v)
          .onSubmit(() => this.doSearch())
          .layoutWeight(1)
        Button("搜索").onClick(() => this.doSearch()).margin({ left: 8 })
      }
      .width('100%')
      .padding({ left: 16, right: 16, bottom: 16 })

      // 文件列表
      List({ space: 12 }) {
        ForEach(this.files, (item: any) => {
          ListItem() {
            Row() {
              Text(item.dir ? "📁" : "📄").fontSize(24).margin({ right: 12 })
              Text(item.name).fontSize(16)
            }
            .width('100%')
            .padding(16)
            .backgroundColor('#FFFFFF')
            .borderRadius(12)
            .onClick(() => {
              if (item.dir) {
                let nextPath = this.currentPath === "/" ? `/${item.name}` : `${this.currentPath}/${item.name}`;
                this.loadFiles(nextPath);
              }
            })
          }
        }, (item: any) => item.name)
      }
      .layoutWeight(1)
      .padding({ left: 16, right: 16 })
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F3EDF7')

    // 剪贴板弹窗
    if (this.showClipboard) {
      Column() {
        Text("局域网剪贴板").fontSize(20).fontWeight(FontWeight.Bold).margin(16)
        TextArea({ text: this.clipboardText })
          .onChange((v) => this.clipboardText = v)
          .layoutWeight(1)
          .margin({ bottom: 16 })
        Row() {
          Button("保存同步").onClick(async () => {
            await ApiClient.setClipboard(this.clipboardText);
            this.showClipboard = false;
          })
          Button("取消").onClick(() => this.showClipboard = false)
        }
      }
      .width('80%')
      .height('50%')
      .backgroundColor('#FFFFFF')
      .borderRadius(16)
      .padding(24)
      .position({ x: '10%', y: '25%' })
    }
  }
}
