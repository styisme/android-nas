import SwiftUI
#if os(iOS)
import UIKit
#elseif os(macOS)
import AppKit
#endif

struct ContentView: View {
    @State private var files: [FileItem] = []
    @State private var currentPath = "/"
    @State private var searchQuery = ""
    
    @State private var showClipboard = false
    @State private var clipboardText = ""
    
    var body: some View {
        NavigationView {
            VStack {
                // 搜索框
                HStack {
                    TextField("全局搜索...", text: $searchQuery, onCommit: {
                        if searchQuery.isEmpty { loadFiles(path: currentPath) }
                        else { NetworkManager.shared.searchFiles(query: searchQuery) { self.files = $0 } }
                    })
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    
                    Button("重置") {
                        searchQuery = ""
                        loadFiles(path: currentPath)
                    }
                }
                .padding(.horizontal)
                
                List(files) { file in
                    HStack {
                        Text(file.dir ? "📁" : "📄").font(.title2)
                        VStack(alignment: .leading) {
                            Text(file.name).font(.headline)
                        }
                    }
                    .onTapGesture {
                        if file.dir {
                            let newPath = currentPath == "/" ? "/\(file.name)" : "\(currentPath)/\(file.name)"
                            currentPath = newPath
                            loadFiles(path: newPath)
                        }
                    }
                }
            }
            .navigationTitle("Android NAS")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button("控制台") {
                        if let url = URL(string: NetworkManager.shared.baseUrl) {
                            #if os(iOS)
                            UIApplication.shared.open(url)
                            #elseif os(macOS)
                            NSWorkspace.shared.open(url)
                            #endif
                        }
                    }
                    Button("剪贴板") {
                        NetworkManager.shared.getClipboard { text in
                            clipboardText = text
                            showClipboard = true
                        }
                    }
                    Button("刷新") { loadFiles(path: currentPath) }
                }
            }
            .sheet(isPresented: $showClipboard) {
                VStack {
                    Text("局域网剪贴板").font(.headline).padding()
                    TextEditor(text: $clipboardText).padding()
                    Button("保存至 NAS") {
                        NetworkManager.shared.setClipboard(text: clipboardText) { _ in
                            showClipboard = false
                        }
                    }.padding()
                }
            }
        }
        .onAppear { loadFiles(path: "/") }
    }
    
    func loadFiles(path: String) {
        NetworkManager.shared.listFiles(path: path) { fetchedFiles in
            self.files = fetchedFiles
        }
    }
}
