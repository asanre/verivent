import ComposeApp
import SwiftUI

/// Hosts the Compose sample on iOS. Everything on screen is `SampleApp()` from commonMain; this is
/// only the window it runs in.
@main
struct ComposeSampleApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea(.keyboard)
        }
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
