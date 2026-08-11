import Foundation
import SnoopKit

/// Snoop for native iOS hosts: a vendor-agnostic analytics sink plus the web viewer.
///
/// Wraps the Kotlin bridge so nothing Objective-C shaped reaches the call site — no `KotlinBoolean`
/// in your closures, no `.shared` singletons, no `Int32` ports.
public enum Snoop {

    // MARK: - Events

    /// An event as you described it, handed to the `keepEvent` filter.
    public struct Event {
        public let name: String
        public let channel: String
        public let properties: [String: Any]

        fileprivate init(_ raw: SnoopEvent) {
            self.name = raw.name
            self.channel = raw.channel
            self.properties = raw.properties as? [String: Any] ?? [:]
        }
    }

    /// Install filtering and redaction. Call once during startup, before the first `log`.
    public static func configure(
        keepEvent: ((Event) -> Bool)? = nil,
        redactProperty: ((String) -> Bool)? = nil
    ) {
        SnoopEvents.shared.configure(
            keepEvent: keepEvent.map { predicate in
                { raw in KotlinBoolean(bool: predicate(Event(raw))) }
            },
            redactProperty: redactProperty.map { predicate in
                { key in KotlinBoolean(bool: predicate(key)) }
            }
        )
    }

    /// Record one event. Wire this to your own analytics facade, alongside the real SDK call.
    public static func log(_ name: String, channel: String, properties: [String: Any] = [:]) {
        SnoopEvents.shared.log(name: name, channel: channel, properties: properties)
    }

    // MARK: - Web viewer

    /// Where the viewer listens. `.lan` and `.host` are reachable off-device and always token-gated.
    public enum Bind {
        case local
        case lan
        case host(String)

        fileprivate var raw: any SnoopViewerBind {
            switch self {
            case .local: return SnoopViewerBindLocal.shared
            case .lan: return SnoopViewerBindLan.shared
            case .host(let address): return SnoopViewerBindHost(address: address)
            }
        }
    }

    /// Start the viewer (idempotent). Returns the URL to open, token included when gated.
    @discardableResult
    public static func startWebViewer(
        _ bind: Bind = .local,
        port: Int? = nil,
        token: String? = nil
    ) -> String {
        SnoopViewer.shared.start(
            bind: bind.raw,
            port: port.map(Int32.init) ?? SnoopViewer.shared.defaultPort,
            token: token
        )
    }

    public static func stopWebViewer() {
        SnoopViewer.shared.stop()
    }

    public static var isWebViewerRunning: Bool { SnoopViewer.shared.isRunning }

    /// The URL to reach this device from another machine, or nil on a `.local` bind.
    public static var lanURL: String? { SnoopViewer.shared.lanUrl() }
}
