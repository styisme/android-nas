import http from '@ohos.net.http';

export interface NasDevice {
  name: string;
  ip: string;
  port: string;
  user: string;
  password: string;
}

export class ApiClient {
  static async listFiles(device: NasDevice, path: string): Promise<string> {
    let httpRequest = http.createHttp();
    try {
      let url = `http://${device.ip}:${device.port}/api/list?path=${encodeURIComponent(path)}`;
      let auth = `Basic ` + btoa(`${device.user}:${device.password}`);
      
      let response = await httpRequest.request(url, {
        method: http.RequestMethod.GET,
        header: {
          'Authorization': auth
        },
        connectTimeout: 5000,
        readTimeout: 5000
      });
      
      if (response.responseCode === 200) {
        return response.result as string;
      }
      return '';
    } catch (error) {
      return '';
    } finally {
      httpRequest.destroy();
    }
  }
}