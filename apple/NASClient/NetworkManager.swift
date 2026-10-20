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
    var authString: String? = nil
    
    func login(user: String, pass: String) {
        let authData = "\(user):\(pass)".data(using: .utf8)?.base64EncodedString() ?? ""
        authString = "Basic \(authData)"
    }
    
    func listFiles(path: String, completion: @escaping ([FileItem]) -> Void) {
        let urlStr = "\(baseUrl)/api/list?path=\(path.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "/")"
        guard let url = URL(string: urlStr) else { return }
        
        var request = URLRequest(url: url)
        if let auth = authString {
            request.setValue(auth, forHTTPHeaderField: "Authorization")
        }
        
        URLSession.shared.dataTask(with: request) { data, _, error in
            guard let data = data, error == nil else { return }
            do {
                let response = try JSONDecoder().decode(ListResponse.self, from: data)
                DispatchQueue.main.async {
                    completion(response.files)
                }
            } catch {
                print("Parse Error: \(error)")
            }
        }.resume()
    }
}
