import Foundation

struct FileItem: Identifiable, Codable {
    var id: String { name }
    let name: String
    let dir: Bool
    let size: Int64
}

struct ListResponse: Codable {
    let path: String
    let files: [FileItem]
}

class NetworkManager {
    static let shared = NetworkManager()
    
    var baseUrl = "http://192.168.1.100:8080"
    var authHeader = ""
    
    init() {
        let authData = "admin:admin".data(using: .utf8)?.base64EncodedString() ?? ""
        authHeader = "Basic \(authData)"
    }
    
    private func request(url: String, method: String = "GET", body: Data? = nil, completion: @escaping (Data?, Error?) -> Void) {
        guard let requestUrl = URL(string: url) else { return }
        var req = URLRequest(url: requestUrl)
        req.httpMethod = method
        req.setValue(authHeader, forHTTPHeaderField: "Authorization")
        if let body = body {
            req.httpBody = body
            req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        }
        
        URLSession.shared.dataTask(with: req) { data, response, error in
            DispatchQueue.main.async { completion(data, error) }
        }.resume()
    }
    
    func listFiles(path: String, completion: @escaping ([FileItem]) -> Void) {
        let encodedPath = path.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "/"
        request(url: "\(baseUrl)/api/list?path=\(encodedPath)") { data, _ in
            if let data = data, let res = try? JSONDecoder().decode(ListResponse.self, from: data) {
                completion(res.files)
            } else { completion([]) }
        }
    }
    
    func searchFiles(query: String, completion: @escaping ([FileItem]) -> Void) {
        let q = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        request(url: "\(baseUrl)/api/search?q=\(q)") { data, _ in
            if let data = data, let files = try? JSONDecoder().decode([FileItem].self, from: data) {
                completion(files)
            } else { completion([]) }
        }
    }
    
    func getClipboard(completion: @escaping (String) -> Void) {
        request(url: "\(baseUrl)/api/clipboard") { data, _ in
            completion(String(data: data ?? Data(), encoding: .utf8) ?? "")
        }
    }
    
    func setClipboard(text: String, completion: @escaping (Bool) -> Void) {
        let body = "content=\(text.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")".data(using: .utf8)
        request(url: "\(baseUrl)/api/clipboard", method: "POST", body: body) { _, _ in completion(true) }
    }
}
