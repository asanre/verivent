import Snoop
import SwiftUI

/// Consumes the Swift Package the way an app off Xcode's *Add Package Dependency* would — except
/// by local path, so it exercises the sources in this repo rather than the last release.
@main
struct SnoopSampleApp: App {

    init() {
        // The same shape as the Compose sample's `Sampler`: a filter, a redaction rule and grouping
        // on `screen_view`. Both closures are held until the next `configure`, so they are installed
        // once here rather than per screen.
        Snoop.configure(
            keepEvent: { !$0.name.hasPrefix("debug_") },
            redactProperty: { $0 == "user_email" },
            groupByEvent: "screen_view",
            groupLabelProperty: "screen_name"
        )
    }

    var body: some Scene {
        WindowGroup { ContentView() }
    }
}
