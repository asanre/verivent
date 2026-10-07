import SafariServices
import Snoop
import SwiftUI

/// Analytics, network capture and the viewer — the same three halves the Compose sample shows.
struct ContentView: View {
    @State private var viewerUrl: String?
    @State private var trail: [String] = []
    @State private var showingViewer = false

    var body: some View {
        NavigationView {
            List {
                Section(header: Text("Analytics")) {
                    Button("Log screen_view") {
                        Snoop.log("screen_view", channel: "firebase", properties: ["screen_name": "home"])
                        note("screen_view — opens the group “home”")
                    }
                    Button("Log purchase") {
                        Snoop.log("purchase", channel: "firebase", properties: [
                            "sku": "SKU-9",
                            "value": 42.5,
                            "quantity": 3,
                            "first_order": true,
                            "user_email": "someone@example.com",
                        ])
                        note("purchase — user_email comes back redacted")
                    }
                    Button("Log debug_ping") {
                        Snoop.log("debug_ping", channel: "internal")
                        note("debug_ping — dropped by the filter, never stored")
                    }
                }

                Section(header: Text("Network")) {
                    ForEach(SampleCall.allCases) { call in
                        Button(call.title) {
                            SampleNetwork.shared.perform(call) { note($0) }
                            note("\(call.title) — \(call.hint)")
                        }
                    }
                }

                Section(header: Text("Inspector")) {
                    if let url = viewerUrl {
                        // Loopback is shared with the host Mac, so this opens from either Safari.
                        Text(url).font(.footnote).foregroundColor(.secondary)
                        Button("Open in app") { showingViewer = true }
                        Button("Open in Safari") {
                            URL(string: url).map { UIApplication.shared.open($0) }
                        }
                        Button("Stop web viewer") {
                            Snoop.stopWebViewer()
                            viewerUrl = nil
                            note("viewer stopped")
                        }
                    } else {
                        Button("Start web viewer") {
                            let url = Snoop.startWebViewer(.local)
                            // The bind is asynchronous, so the returned URL is not proof the port
                            // was free — a restart loses to the socket the last one left behind.
                            // Read the flag back instead of offering a link that answers nothing.
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                                guard Snoop.isWebViewerRunning else {
                                    note("viewer failed to bind — check the device console")
                                    return
                                }
                                viewerUrl = url
                                note("viewer started")
                            }
                        }
                    }
                }

                if !trail.isEmpty {
                    Section(header: Text("Last actions")) {
                        // By index, not by value: tapping the same button twice puts the same
                        // string in the list, and ForEach needs the ids to stay unique.
                        ForEach(trail.indices, id: \.self) { index in
                            Text(trail[index]).font(.footnote).foregroundColor(.secondary)
                        }
                    }
                }
            }
            .listStyle(GroupedListStyle())
            .navigationTitle("Snoop sample")
        }
        .navigationViewStyle(StackNavigationViewStyle())
        .sheet(isPresented: $showingViewer) {
            if let url = viewerUrl.flatMap(URL.init(string:)) {
                SafariView(url: url).ignoresSafeArea()
            }
        }
    }

    private func note(_ message: String) {
        trail = Array(([message] + trail).prefix(5))
    }
}

/// The web viewer inside the app, on the device's own loopback — no Mac needed. Safari View
/// Controller runs out of process, so the app's App Transport Security never sees the plain-HTTP URL.
private struct SafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        SFSafariViewController(url: url)
    }

    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}
