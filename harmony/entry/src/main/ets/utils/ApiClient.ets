import http from '@ohos.net.http';

export class ApiClient {
  static baseUrl = "http://192.168.1.100:8080";
  static auth = "Basic " + btoa("admin:admin");

  static async request(path: string, method: http.RequestMethod = http.RequestMethod.GET, body?: string): Promise<any> {
    let httpRequest = http.createHttp();
    let options: http.HttpRequestOptions = {
      method: method,
      header: { 'Authorization': ApiClient.auth, 'Content-Type': 'application/x-www-form-urlencoded' },
      connectTimeout: 5000,
      readTimeout: 5000
    };
    if (body) options.extraData = body;

    try {
      let response = await httpRequest.request(ApiClient.baseUrl + path, options);
      if (response.responseCode === 200) return response.result;
      return null;
    } catch (e) { return null; }
    finally { httpRequest.destroy(); }
  }

  static async listFiles(path: string): Promise<any> {
    return await this.request(`/api/list?path=${encodeURIComponent(path)}`);
  }

  static async searchFiles(q: string): Promise<any> {
    return await this.request(`/api/search?q=${encodeURIComponent(q)}`);
  }

  static async getClipboard(): Promise<string> {
    return await this.request('/api/clipboard') as string;
  }

  static async setClipboard(text: string): Promise<boolean> {
    return await this.request('/api/clipboard', http.RequestMethod.POST, `content=${encodeURIComponent(text)}`) !== null;
  }
}
