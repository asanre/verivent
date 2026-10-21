import Verivent
import SwiftUI

/// Consumes the Swift Package the way an app off Xcode's *Add Package Dependency* would — except
/// by local path, so it exercises the sources in this repo rather than the last release.
@main
struct VeriventSampleApp: App {

    init() {
        // The same rules as the Compose sample's `AnalyticsSink`.
        Verivent.configure(
            keepEvent: { !$0.name.hasPrefix("debug_") },
            redactProperty: { $0 == "user_email" },
            groupByEvent: "screen_view",
            groupLabelProperty: "screen_name"
        )
    }

    var body: some Scene {
        WindowGroup { ShopView() }
    }
}
