import { ApiClient, NasDevice } from '../utils/ApiClient';

@Entry
@Component
struct Index {
  @State devices: NasDevice[] = [
    { name: "我的 NAS", ip: "192.168.1.100", port: "8080", user: "admin", password: "admin" }
  ];
  @State isScanning: boolean = false;

  build() {
    Column() {
      // 顶部导航
      Row() {
        Text("Android NAS")
          .fontSize(24)
          .fontWeight(FontWeight.Bold)
          .fontColor('#6750A4')
          .layoutWeight(1)
        
        Button("扫描设备")
          .backgroundColor('#EADDFF')
          .fontColor('#21005D')
          .fontSize(14)
          .onClick(() => {
            this.isScanning = true;
            setTimeout(() => { this.isScanning = false; }, 2000);
          })
      }
      .width('100%')
      .padding(16)

      // 设备列表 (响应式：平板/PC端可以多列排布)
      List({ space: 12 }) {
        ForEach(this.devices, (device: NasDevice) => {
          ListItem() {
            Row() {
              Text("🖥️")
                .fontSize(32)
                .margin({ right: 16 })
              
              Column() {
                Text(device.name)
                  .fontSize(18)
                  .fontWeight(FontWeight.Medium)
                Text(`http://${device.ip}:${device.port}`)
                  .fontSize(14)
                  .fontColor('#49454F')
                  .margin({ top: 4 })
              }
              .alignItems(HorizontalAlign.Start)
              .layoutWeight(1)
            }
            .width('100%')
            .padding(20)
            .backgroundColor('#FEF7FF')
            .borderRadius(16)
            .shadow({ radius: 8, color: 'rgba(0,0,0,0.1)', offsetY: 2 })
          }
        }, (item: NasDevice) => item.ip)
      }
      .width('100%')
      .padding({ left: 16, right: 16 })
      .layoutWeight(1)
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F3EDF7')
  }
}