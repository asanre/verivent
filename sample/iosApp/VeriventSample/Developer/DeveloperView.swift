import SafariServices
import SwiftUI
import Verivent

/// Verivent itself: the inspector, the shop's tracking plan, and traffic that shows what it catches.
struct DeveloperView: View {
    @State private var viewerUrl: String? = viewerAddress()
    @State private var showingViewer = false

    var body: some View {
        List {
            Section(header: Text("Inspector")) {
                if let url = viewerUrl {
                    // Loopback is shared with the host Mac, so this opens from either Safari.
                    Text(url).font(.footnote).foregroundColor(.secondary)
                    Button("Open Verivent") { showingViewer = true }.accessibilityIdentifier("developer.inspector")
                    Button("Stop web viewer") {
                        Verivent.stopWebViewer()
                        viewerUrl = nil
                    }
                } else {
                    Button("Start web viewer") { startViewer() }
                }
            }

            Section(header: Text("Tracking plan")) {
                Button("Load the shop's plan") {
                    do { try Verivent.loadTrackingPlan(samplePlan) } catch { print("Verivent sample: the shop's plan didn't load: \(error)") }
                }
                    .accessibilityIdentifier("developer.loadPlan")
                Button("Clear the plan") { Verivent.clearTrackingPlan() }
            }

            Section(header: Text("Events that break the plan")) {
                Button("Purchase without its transaction id") {
                    Verivent.log("purchase", channel: "firebase", properties: [
                        "currency": "EUR", "value": 4.5, "items": [["item_id": "SN-7", "quantity": "1"]],
                    ])
                }
                Button("A list of 60 items, 2 off pattern") {
                    Verivent.log("view_item_list", channel: "firebase", properties: [
                        "item_list_name": "catalog",
                        "items": (1...60).map { index in
                            [
                                "item_id": index % 30 == 0 ? "sn-\(index)" : "SN-\(index)",
                                "item_name": "Item \(index)", "price": 0.99, "index": index,
                            ]
                        },
                    ])
                }
                Button("debug_ping, dropped by the filter") { Verivent.log("debug_ping", channel: "internal") }
            }

            Section(header: Text("Network")) {
                ForEach(DemoCall.allCases) { call in
                    Button(call.title) { SampleNetwork.shared.perform(call) }
                }
            }
        }
        .navigationTitle("Developer")
        .sheet(isPresented: $showingViewer) {
            if let url = viewerUrl.flatMap(URL.init(string:)) {
                SafariView(url: url).ignoresSafeArea()
            }
        }
    }

    private func startViewer() {
        let url = Verivent.startWebViewer(.local)
        // The bind is asynchronous, so the returned URL is not proof the port was free.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            if Verivent.isWebViewerRunning { viewerUrl = url }
        }
    }
}

/// The running viewer's local address, token included, or nil when it isn't running.
private func viewerAddress() -> String? {
    guard Verivent.isWebViewerRunning, let token = Verivent.webViewerToken else { return nil }
    return "http://localhost:9394/?t=\(token)"
}

/// The shop's tracking plan, from `sample/shared`, which the Compose sample loads too.
let samplePlan: String = Bundle.main.url(forResource: "tracking-plan", withExtension: "json")
    .flatMap { try? String(contentsOf: $0, encoding: .utf8) } ?? ""

/// The web viewer inside the app, on the device's own loopback — no Mac needed. Safari View
/// Controller runs out of process, so the app's App Transport Security never sees the plain-HTTP URL.
private struct SafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        SFSafariViewController(url: url)
    }

    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}
