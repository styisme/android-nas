import SwiftUI

struct ContentView: View {
    @State private var files: [FileItem] = []
    @State private var currentPath = "/"
    
    var body: some View {
        NavigationView {
            List(files) { file in
                HStack {
                    Text(file.dir ? "📁" : "📄")
                        .font(.title2)
                    VStack(alignment: .leading) {
                        Text(file.name)
                            .font(.headline)
                        if !file.dir {
                            Text(formatSize(file.size))
                                .font(.caption)
                                .foregroundColor(.gray)
                        }
                    }
                }
                .padding(.vertical, 4)
                .onTapGesture {
                    if file.dir {
                        let newPath = currentPath == "/" ? "/\(file.name)" : "\(currentPath)/\(file.name)"
                        currentPath = newPath
                        loadFiles(path: newPath)
                    }
                }
            }
            .listStyle(InsetGroupedListStyle())
            .navigationTitle("Android NAS")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("刷新") {
                        loadFiles(path: currentPath)
                    }
                }
            }
        }
        .onAppear {
            // 默认连本地测试
            NetworkManager.shared.login(user: "admin", pass: "admin")
            loadFiles(path: "/")
        }
    }
    
    func loadFiles(path: String) {
        NetworkManager.shared.listFiles(path: path) { fetchedFiles in
            self.files = fetchedFiles
        }
    }
    
    func formatSize(_ size: Int64) -> String {
        if size < 1024 { return "\(size) B" }
        if size < 1024 * 1024 { return String(format: "%.1f KB", Double(size) / 1024.0) }
        return String(format: "%.1f MB", Double(size) / 1024.0 / 1024.0)
    }
}
